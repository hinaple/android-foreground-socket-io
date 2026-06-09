package com.fainthit.androidforegroundsocketio;

import android.content.Context;
import android.content.SharedPreferences;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

final class AndroidForegroundSocketManager {

    private static final AndroidForegroundSocketManager INSTANCE = new AndroidForegroundSocketManager();
    private static final String PREFS = "android_foreground_socket";
    private static final String KEY_CONFIG = "config";
    private static final Set<String> RESERVED_EVENTS = new HashSet<>();

    static {
        RESERVED_EVENTS.add(Socket.EVENT_CONNECT);
        RESERVED_EVENTS.add(Socket.EVENT_DISCONNECT);
        RESERVED_EVENTS.add(Socket.EVENT_CONNECT_ERROR);
    }

    private final Object lock = new Object();
    private final Map<String, Emitter.Listener> watchedListeners = new HashMap<>();
    private final AndroidForegroundSocketActionRegistry actionRegistry = new AndroidForegroundSocketActionRegistry();
    private final AndroidForegroundSocketActionExecutor actionExecutor = new AndroidForegroundSocketActionExecutor(this);
    private Socket socket;
    private String url;
    private String baseUrl;
    private JSObject lastConfig;
    private String onConnectEvent;
    private Object onConnectData;
    private String notificationTitle = "Android Foreground Socket";
    private String notificationText = "Socket service is running";
    private boolean initialized;

    static AndroidForegroundSocketManager getInstance() {
        return INSTANCE;
    }

    private AndroidForegroundSocketManager() {}

    void initialize(Context context) {
        synchronized (lock) {
            if (initialized) {
                return;
            }
            initialized = true;
        }
        actionExecutor.initialize(context.getApplicationContext());
    }

    void restoreIfNeeded(Context context) {
        synchronized (lock) {
            if (socket != null || lastConfig != null) {
                return;
            }
        }

        String raw = prefs(context).getString(KEY_CONFIG, null);
        if (raw == null || raw.isEmpty()) {
            return;
        }

        try {
            start(context, new JSObject(raw));
        } catch (Exception e) {
            AndroidForegroundSocketEventBridge.publishError("Failed to restore socket config", e);
        }
    }

    void start(Context context, JSObject config) throws Exception {
        initialize(context);

        JSObject socketConfig = config.getJSObject("socket");
        String nextUrl = socketConfig != null ? socketConfig.optString("url", null) : config.optString("url", null);
        if (nextUrl == null || nextUrl.isEmpty()) {
            throw new IllegalArgumentException("socket.url is required");
        }

        synchronized (lock) {
            disconnectLocked();
            url = nextUrl;
            baseUrl = buildBaseUrl(nextUrl);
            lastConfig = new JSObject(config.toString());
            configureEventBuffer(config);
            configureService(config);
            configureOnConnect(config);
            actionRegistry.replaceFromConfig(config);
            socket = IO.socket(URI.create(nextUrl), buildOptions(socketConfig != null ? socketConfig : config));
            registerDefaultEventsLocked();
            registerConfiguredEventsLocked(config);
            socket.connect();
        }

        prefs(context).edit().putString(KEY_CONFIG, config.toString()).apply();
        AndroidForegroundSocketService.refresh(context);
        publishStatus();
    }

    void restart(Context context, JSObject config) throws Exception {
        JSObject nextConfig = config != null && config.length() > 0 ? config : getLastConfigCopy(context);
        if (nextConfig == null) {
            throw new IllegalStateException("No socket config exists");
        }
        start(context, nextConfig);
    }

    void stop(Context context) {
        synchronized (lock) {
            disconnectLocked();
            socket = null;
            url = null;
            baseUrl = null;
            lastConfig = null;
            watchedListeners.clear();
            actionRegistry.clear();
        }
        prefs(context).edit().remove(KEY_CONFIG).apply();
        publishStatus();
    }

    void connect() {
        synchronized (lock) {
            if (socket != null && !socket.connected()) {
                socket.connect();
            }
        }
        publishStatus();
    }

    void disconnect() {
        synchronized (lock) {
            if (socket != null) {
                socket.disconnect();
            }
        }
        publishStatus();
    }

    boolean emit(String event, Object data) {
        synchronized (lock) {
            if (socket == null) {
                return false;
            }
            if (data == null || data == JSONObject.NULL) {
                socket.emit(event);
            } else {
                socket.emit(event, data);
            }
            return true;
        }
    }

    void watchEvent(String event) {
        synchronized (lock) {
            if (socket == null || watchedListeners.containsKey(event) || RESERVED_EVENTS.contains(event)) {
                return;
            }
            Emitter.Listener listener = args -> handleSocketEvent(event, args);
            watchedListeners.put(event, listener);
            socket.on(event, listener);
        }
    }

    void unwatchEvent(String event) {
        synchronized (lock) {
            Emitter.Listener listener = watchedListeners.remove(event);
            if (socket != null && listener != null) {
                socket.off(event, listener);
            }
        }
    }

    void registerAction(Context context, JSObject action) {
        actionRegistry.put(action);
        watchEvent(action.optString("event"));
        persistCurrentConfig(context);
    }

    void unregisterAction(Context context, String id) {
        actionRegistry.remove(id);
        persistCurrentConfig(context);
    }

    void clearActions(Context context) {
        actionRegistry.clear();
        persistCurrentConfig(context);
    }

    JSArray listActions() {
        return actionRegistry.list();
    }

    void setActionEnabled(Context context, String id, boolean enabled) {
        actionRegistry.setEnabled(id, enabled);
        persistCurrentConfig(context);
    }

    void setOnConnectEmit(Context context, String event, Object data) {
        synchronized (lock) {
            onConnectEvent = event;
            onConnectData = data;
            if (lastConfig != null) {
                JSObject onConnect = new JSObject();
                onConnect.put("event", event);
                onConnect.put("data", data);
                lastConfig.put("onConnectEmit", onConnect);
            }
        }
        persistCurrentConfig(context);
    }

    boolean isConnected() {
        synchronized (lock) {
            return socket != null && socket.connected();
        }
    }

    JSObject getStatus() {
        JSObject status = new JSObject();
        synchronized (lock) {
            status.put("serviceRunning", socket != null);
            status.put("connected", socket != null && socket.connected());
            status.put("url", url);
            status.put("registeredActions", actionRegistry.size());
            status.put("watchedEvents", watchedListeners.keySet());
        }
        status.put("webviewActive", AndroidForegroundSocketEventBridge.isWebviewActive());
        status.put("queuedEvents", AndroidForegroundSocketEventBridge.queuedCount());
        return status;
    }

    String getBaseUrl() {
        synchronized (lock) {
            return baseUrl;
        }
    }

    String getNotificationTitle() {
        synchronized (lock) {
            return notificationTitle;
        }
    }

    String getNotificationText() {
        synchronized (lock) {
            return notificationText;
        }
    }

    boolean shouldStartOnBoot(Context context) {
        String raw = prefs(context).getString(KEY_CONFIG, null);
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        try {
            JSObject config = new JSObject(raw);
            JSObject service = config.getJSObject("service");
            return service != null && service.optBoolean("startOnBoot", false);
        } catch (Exception e) {
            return false;
        }
    }

    private void registerDefaultEventsLocked() {
        socket.on(Socket.EVENT_CONNECT, args -> {
            synchronized (lock) {
                if (onConnectEvent != null && !onConnectEvent.isEmpty() && socket != null) {
                    if (onConnectData == null || onConnectData == JSONObject.NULL) {
                        socket.emit(onConnectEvent);
                    } else {
                        socket.emit(onConnectEvent, onConnectData);
                    }
                }
            }
            JSObject payload = new JSObject();
            payload.put("message", "Socket connected");
            AndroidForegroundSocketEventBridge.publishSocketEvent(Socket.EVENT_CONNECT, payload);
            publishStatus();
        });

        socket.on(Socket.EVENT_DISCONNECT, args -> {
            JSObject payload = new JSObject();
            payload.put("message", args != null && args.length > 0 ? String.valueOf(args[0]) : "Socket disconnected");
            AndroidForegroundSocketEventBridge.publishSocketEvent(Socket.EVENT_DISCONNECT, payload);
            publishStatus();
        });

        socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
            JSObject payload = new JSObject();
            payload.put("message", args != null && args.length > 0 ? String.valueOf(args[0]) : "Socket connect error");
            AndroidForegroundSocketEventBridge.publishSocketEvent(Socket.EVENT_CONNECT_ERROR, payload);
            publishStatus();
        });
    }

    private void registerConfiguredEventsLocked(JSObject config) {
        Set<String> events = new HashSet<>();

        JSObject listen = config.getJSObject("listen");
        if (listen != null) {
            JSONArray listenEvents = listen.optJSONArray("events");
            if (listenEvents != null) {
                for (int i = 0; i < listenEvents.length(); i++) {
                    events.add(listenEvents.optString(i));
                }
            }
        }

        JSONArray actions = config.optJSONArray("actions");
        if (actions != null) {
            for (int i = 0; i < actions.length(); i++) {
                JSONObject action = actions.optJSONObject(i);
                if (action != null) {
                    events.add(action.optString("event"));
                }
            }
        }

        for (String event : events) {
            if (event != null && !event.isEmpty() && !RESERVED_EVENTS.contains(event)) {
                Emitter.Listener listener = args -> handleSocketEvent(event, args);
                watchedListeners.put(event, listener);
                socket.on(event, listener);
            }
        }
    }

    private void handleSocketEvent(String event, Object... args) {
        JSObject payload = new JSObject();
        Object data = parseSocketData(args);
        payload.put("data", data);
        actionRegistry.execute(event, data, actionExecutor, getBaseUrl());
        actionExecutor.executeLegacyPayload(data, getBaseUrl());
        AndroidForegroundSocketEventBridge.publishSocketEvent(event, payload);
    }

    private Object parseSocketData(Object... args) {
        if (args == null || args.length == 0) {
            return JSONObject.NULL;
        }
        if (args.length == 1) {
            return normalizeValue(args[0]);
        }

        JSONArray array = new JSONArray();
        for (Object arg : args) {
            array.put(normalizeValue(arg));
        }
        return array;
    }

    private Object normalizeValue(Object value) {
        if (value == null) {
            return JSONObject.NULL;
        }
        if (value instanceof JSONObject || value instanceof JSONArray || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return String.valueOf(value);
    }

    private IO.Options buildOptions(JSObject config) {
        IO.Options options = new IO.Options();
        options.forceNew = config.optBoolean("forceNew", true);
        options.reconnection = config.optBoolean("reconnect", true);
        options.reconnectionAttempts = config.optInt("reconnectionAttempts", Integer.MAX_VALUE);
        options.reconnectionDelay = config.optLong("reconnectionDelay", 1000L);
        options.reconnectionDelayMax = config.optLong("reconnectionDelayMax", 5000L);
        options.timeout = config.optLong("timeout", 20000L);

        String path = config.optString("path", null);
        if (path != null && !path.isEmpty()) {
            options.path = path;
        }

        JSONArray transports = config.optJSONArray("transports");
        if (transports != null && transports.length() > 0) {
            String[] values = new String[transports.length()];
            for (int i = 0; i < transports.length(); i++) {
                values[i] = transports.optString(i);
            }
            options.transports = values;
        }

        String query = config.optString("query", null);
        if (query != null && !query.isEmpty()) {
            options.query = query;
        }

        return options;
    }

    private void configureEventBuffer(JSObject config) {
        JSObject listen = config.getJSObject("listen");
        if (listen != null) {
            AndroidForegroundSocketEventBridge.setMaxQueueSize(listen.optInt("bufferSize", 100));
        }
    }

    private void configureOnConnect(JSObject config) {
        JSObject onConnect = config.getJSObject("onConnectEmit");
        if (onConnect == null) {
            onConnectEvent = null;
            onConnectData = null;
            return;
        }
        onConnectEvent = onConnect.optString("event", null);
        onConnectData = onConnect.opt("data");
    }

    private void configureService(JSObject config) {
        JSObject service = config.getJSObject("service");
        if (service == null) {
            notificationTitle = "Android Foreground Socket";
            notificationText = "Socket service is running";
            return;
        }
        notificationTitle = service.optString("notificationTitle", "Android Foreground Socket");
        notificationText = service.optString("notificationText", "Socket service is running");
    }

    private void publishStatus() {
        AndroidForegroundSocketEventBridge.publishStatus(getStatus());
    }

    private void disconnectLocked() {
        if (socket == null) {
            return;
        }
        for (Map.Entry<String, Emitter.Listener> entry : watchedListeners.entrySet()) {
            socket.off(entry.getKey(), entry.getValue());
        }
        socket.off();
        socket.disconnect();
        watchedListeners.clear();
    }

    private JSObject getLastConfigCopy(Context context) throws Exception {
        synchronized (lock) {
            if (lastConfig != null) {
                return new JSObject(lastConfig.toString());
            }
        }
        String raw = prefs(context).getString(KEY_CONFIG, null);
        return raw == null ? null : new JSObject(raw);
    }

    private void persistCurrentConfig(Context context) {
        synchronized (lock) {
            if (lastConfig == null) {
                return;
            }
            lastConfig.put("actions", actionRegistry.list());
            prefs(context).edit().putString(KEY_CONFIG, lastConfig.toString()).apply();
        }
    }

    private SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private String buildBaseUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            StringBuilder builder = new StringBuilder();
            builder.append(uri.getScheme()).append("://").append(uri.getHost());
            if (uri.getPort() > -1) {
                builder.append(":").append(uri.getPort());
            }
            return builder.toString();
        } catch (Exception e) {
            return rawUrl;
        }
    }
}
