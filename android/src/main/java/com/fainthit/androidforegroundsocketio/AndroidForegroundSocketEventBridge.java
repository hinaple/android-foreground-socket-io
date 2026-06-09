package com.fainthit.androidforegroundsocketio;

import com.getcapacitor.JSObject;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

final class AndroidForegroundSocketEventBridge {

    private static final Object LOCK = new Object();
    private static final ArrayDeque<QueuedEvent> QUEUE = new ArrayDeque<>();
    private static WeakReference<AndroidForegroundSocketPlugin> pluginRef = new WeakReference<>(null);
    private static boolean webviewActive = false;
    private static int maxQueueSize = 100;

    private AndroidForegroundSocketEventBridge() {}

    static void attach(AndroidForegroundSocketPlugin plugin) {
        synchronized (LOCK) {
            pluginRef = new WeakReference<>(plugin);
        }
    }

    static void detach(AndroidForegroundSocketPlugin plugin) {
        synchronized (LOCK) {
            AndroidForegroundSocketPlugin current = pluginRef.get();
            if (current == plugin) {
                pluginRef = new WeakReference<>(null);
            }
        }
    }

    static void setWebviewActive(boolean active) {
        synchronized (LOCK) {
            webviewActive = active;
        }
    }

    static boolean isWebviewActive() {
        synchronized (LOCK) {
            return webviewActive && pluginRef.get() != null;
        }
    }

    static void setMaxQueueSize(int size) {
        synchronized (LOCK) {
            maxQueueSize = Math.max(1, size);
            trimQueueLocked();
        }
    }

    static int queuedCount() {
        synchronized (LOCK) {
            return QUEUE.size();
        }
    }

    static void publishSocketEvent(String socketEvent, JSObject payload) {
        payload.put("event", socketEvent);
        payload.put("receivedAt", System.currentTimeMillis());
        publish(socketEvent, payload, true);
    }

    static void publishStatus(JSObject payload) {
        payload.put("receivedAt", System.currentTimeMillis());
        publish("status", payload, false);
    }

    static void publishAction(JSObject payload) {
        payload.put("receivedAt", System.currentTimeMillis());
        publish("actionExecuted", payload, false);
    }

    static void publishError(String message, Throwable error) {
        JSObject payload = new JSObject();
        payload.put("message", message);
        if (error != null) {
            payload.put("detail", error.getMessage());
        }
        payload.put("receivedAt", System.currentTimeMillis());
        publish("error", payload, false);
    }

    static void flush() {
        List<QueuedEvent> events = new ArrayList<>();
        AndroidForegroundSocketPlugin plugin;
        synchronized (LOCK) {
            plugin = pluginRef.get();
            if (!webviewActive || plugin == null) {
                return;
            }
            while (!QUEUE.isEmpty()) {
                events.add(QUEUE.removeFirst());
            }
        }

        for (QueuedEvent event : events) {
            dispatch(plugin, event.name, event.payload, event.socketEvent);
        }
    }

    private static void publish(String eventName, JSObject payload, boolean socketEvent) {
        AndroidForegroundSocketPlugin plugin;
        synchronized (LOCK) {
            plugin = pluginRef.get();
            if (!webviewActive || plugin == null) {
                QUEUE.addLast(new QueuedEvent(eventName, payload, socketEvent));
                trimQueueLocked();
                return;
            }
        }
        dispatch(plugin, eventName, payload, socketEvent);
    }

    private static void dispatch(AndroidForegroundSocketPlugin plugin, String eventName, JSObject payload, boolean socketEvent) {
        if (socketEvent) {
            plugin.dispatchToJs("socketEvent", payload);
        }
        plugin.dispatchToJs(eventName, payload);
    }

    private static void trimQueueLocked() {
        while (QUEUE.size() > maxQueueSize) {
            QUEUE.removeFirst();
        }
    }

    private static final class QueuedEvent {

        final String name;
        final JSObject payload;
        final boolean socketEvent;

        QueuedEvent(String name, JSObject payload, boolean socketEvent) {
            this.name = name;
            this.payload = payload;
            this.socketEvent = socketEvent;
        }
    }
}
