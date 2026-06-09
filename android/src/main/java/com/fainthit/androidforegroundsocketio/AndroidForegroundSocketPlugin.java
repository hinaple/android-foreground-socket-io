package com.fainthit.androidforegroundsocketio;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "AndroidForegroundSocket")
public class AndroidForegroundSocketPlugin extends Plugin {

    private final AndroidForegroundSocketManager manager = AndroidForegroundSocketManager.getInstance();

    @Override
    public void load() {
        AndroidForegroundSocketEventBridge.attach(this);
        AndroidForegroundSocketEventBridge.setWebviewActive(true);
        manager.initialize(getContext());
    }

    @Override
    protected void handleOnDestroy() {
        AndroidForegroundSocketEventBridge.detach(this);
        AndroidForegroundSocketEventBridge.setWebviewActive(false);
        super.handleOnDestroy();
    }

    @PluginMethod
    public void start(PluginCall call) {
        JSObject config = call.getData();
        if (config == null) {
            call.reject("Config is required");
            return;
        }

        try {
            AndroidForegroundSocketService.start(getContext());
            manager.start(getContext(), config);
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to start socket service", e);
        }
    }

    @PluginMethod
    public void stop(PluginCall call) {
        manager.stop(getContext());
        AndroidForegroundSocketService.stop(getContext());
        call.resolve();
    }

    @PluginMethod
    public void restart(PluginCall call) {
        try {
            AndroidForegroundSocketService.start(getContext());
            manager.restart(getContext(), call.getData());
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to restart socket service", e);
        }
    }

    @PluginMethod
    public void connect(PluginCall call) {
        manager.connect();
        call.resolve();
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        manager.disconnect();
        call.resolve();
    }

    @PluginMethod
    public void watchEvent(PluginCall call) {
        String event = call.getString("event");
        if (event == null || event.isEmpty()) {
            call.reject("Event name is required");
            return;
        }

        manager.watchEvent(event);
        call.resolve();
    }

    @PluginMethod
    public void unwatchEvent(PluginCall call) {
        String event = call.getString("event");
        if (event == null || event.isEmpty()) {
            call.reject("Event name is required");
            return;
        }

        manager.unwatchEvent(event);
        call.resolve();
    }

    @PluginMethod
    public void emit(PluginCall call) {
        String event = call.getString("event");
        if (event == null || event.isEmpty()) {
            call.reject("Event name is required");
            return;
        }

        if (!manager.emit(event, call.getData().opt("data"))) {
            call.reject("Socket is not initialized");
            return;
        }

        call.resolve();
    }

    @PluginMethod
    public void registerAction(PluginCall call) {
        JSObject action = call.getData();
        if (action == null || action.optString("id", "").isEmpty() || action.optString("event", "").isEmpty()) {
            call.reject("Action id and event are required");
            return;
        }

        manager.registerAction(getContext(), action);
        call.resolve();
    }

    @PluginMethod
    public void unregisterAction(PluginCall call) {
        String id = call.getString("id");
        if (id == null || id.isEmpty()) {
            call.reject("Action id is required");
            return;
        }

        manager.unregisterAction(getContext(), id);
        call.resolve();
    }

    @PluginMethod
    public void clearActions(PluginCall call) {
        manager.clearActions(getContext());
        call.resolve();
    }

    @PluginMethod
    public void listActions(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("actions", manager.listActions());
        call.resolve(ret);
    }

    @PluginMethod
    public void setActionEnabled(PluginCall call) {
        String id = call.getString("id");
        Boolean enabled = call.getBoolean("enabled");
        if (id == null || id.isEmpty() || enabled == null) {
            call.reject("Action id and enabled are required");
            return;
        }

        manager.setActionEnabled(getContext(), id, enabled);
        call.resolve();
    }

    @PluginMethod
    public void setWebviewActive(PluginCall call) {
        AndroidForegroundSocketEventBridge.setWebviewActive(true);
        AndroidForegroundSocketEventBridge.flush();
        call.resolve();
    }

    @PluginMethod
    public void setWebviewInactive(PluginCall call) {
        AndroidForegroundSocketEventBridge.setWebviewActive(false);
        call.resolve();
    }

    @PluginMethod
    public void setOnConnectEmit(PluginCall call) {
        manager.setOnConnectEmit(getContext(), call.getString("event"), call.getData().opt("data"));
        call.resolve();
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        call.resolve(manager.getStatus());
    }

    void dispatchToJs(String eventName, JSObject data) {
        notifyListeners(eventName, data);
    }
}
