package com.fainthit.androidforegroundsocketio;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

final class AndroidForegroundSocketActionRegistry {

    private final Object lock = new Object();
    private final Map<String, JSObject> actions = new HashMap<>();
    private final Map<String, Long> lastExecutedAt = new HashMap<>();

    void replaceFromConfig(JSObject config) {
        synchronized (lock) {
            actions.clear();
            lastExecutedAt.clear();
            JSONArray array = config.optJSONArray("actions");
            if (array == null) {
                return;
            }
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) {
                    JSObject action = copy(item);
                    String id = action.optString("id", "");
                    if (!id.isEmpty()) {
                        actions.put(id, action);
                    }
                }
            }
        }
    }

    void put(JSObject action) {
        synchronized (lock) {
            actions.put(action.optString("id"), copy(action));
        }
    }

    void remove(String id) {
        synchronized (lock) {
            actions.remove(id);
            lastExecutedAt.remove(id);
        }
    }

    void clear() {
        synchronized (lock) {
            actions.clear();
            lastExecutedAt.clear();
        }
    }

    void setEnabled(String id, boolean enabled) {
        synchronized (lock) {
            JSObject action = actions.get(id);
            if (action != null) {
                action.put("enabled", enabled);
            }
        }
    }

    int size() {
        synchronized (lock) {
            return actions.size();
        }
    }

    JSArray list() {
        JSArray result = new JSArray();
        synchronized (lock) {
            for (JSObject action : actions.values()) {
                result.put(copy(action));
            }
        }
        return result;
    }

    void execute(String event, Object data, AndroidForegroundSocketActionExecutor executor, String baseUrl) {
        List<JSObject> matched = new ArrayList<>();
        synchronized (lock) {
            long now = System.currentTimeMillis();
            for (JSObject action : actions.values()) {
                if (!event.equals(action.optString("event")) || !action.optBoolean("enabled", true)) {
                    continue;
                }
                if (!matches(action.opt("match"), data)) {
                    continue;
                }
                long cooldownMs = action.optLong("cooldownMs", 0L);
                Long last = lastExecutedAt.get(action.optString("id"));
                if (last != null && cooldownMs > 0L && now - last < cooldownMs) {
                    continue;
                }
                lastExecutedAt.put(action.optString("id"), now);
                matched.add(copy(action));
            }
        }

        for (JSObject action : matched) {
            executor.executeAction(action, data, baseUrl);
        }
    }

    private boolean matches(Object matcher, Object data) {
        if (matcher == null || matcher == JSONObject.NULL) {
            return true;
        }
        if (!(matcher instanceof JSONObject)) {
            return true;
        }

        JSONObject match = (JSONObject) matcher;
        JSONArray all = match.optJSONArray("all");
        if (all != null) {
            for (int i = 0; i < all.length(); i++) {
                if (!matches(all.opt(i), data)) {
                    return false;
                }
            }
            return true;
        }

        JSONArray any = match.optJSONArray("any");
        if (any != null) {
            for (int i = 0; i < any.length(); i++) {
                if (matches(any.opt(i), data)) {
                    return true;
                }
            }
            return false;
        }

        String path = match.optString("path", "");
        Object value = path.isEmpty() ? data : resolvePath(data, path);

        if (match.has("exists")) {
            return match.optBoolean("exists") == (value != null && value != JSONObject.NULL);
        }
        if (match.has("equals")) {
            return valuesEqual(value, match.opt("equals"));
        }
        if (match.has("contains")) {
            return contains(value, match.opt("contains"));
        }
        if (match.has("gt")) {
            return asDouble(value) > match.optDouble("gt");
        }
        if (match.has("gte")) {
            return asDouble(value) >= match.optDouble("gte");
        }
        if (match.has("lt")) {
            return asDouble(value) < match.optDouble("lt");
        }
        if (match.has("lte")) {
            return asDouble(value) <= match.optDouble("lte");
        }
        return true;
    }

    private Object resolvePath(Object root, String path) {
        Object current = root;
        String[] parts = path.split("\\.");
        for (String part : parts) {
            if (current instanceof JSONObject) {
                current = ((JSONObject) current).opt(part);
            } else if (current instanceof JSONArray) {
                try {
                    current = ((JSONArray) current).opt(Integer.parseInt(part));
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return current;
    }

    private boolean valuesEqual(Object left, Object right) {
        if (left == right) {
            return true;
        }
        if (left == null || left == JSONObject.NULL || right == null || right == JSONObject.NULL) {
            return false;
        }
        return String.valueOf(left).equals(String.valueOf(right));
    }

    private boolean contains(Object value, Object expected) {
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                if (valuesEqual(array.opt(i), expected)) {
                    return true;
                }
            }
            return false;
        }
        return value != null && String.valueOf(value).contains(String.valueOf(expected));
    }

    private double asDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    private JSObject copy(JSONObject object) {
        try {
            return JSObject.fromJSONObject(object);
        } catch (Exception e) {
            return new JSObject();
        }
    }
}
