package com.fainthit.simplenativesocketio;

import com.getcapacitor.Plugin;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginMethod;

import org.json.JSONObject;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import android.util.Log;
import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.PowerManager;
import android.media.AudioManager;

import java.util.HashMap;
import java.util.Map;

@CapacitorPlugin(name = "SimpleNativeSocketio")
public class SimpleNativeSocketioPlugin extends Plugin {
    private static final String TAG = "SimpleNativeSocketio";
    private Socket mSocket;
    private String host;

    // 웹뷰 활성 상태 플래그와 이벤트 타입별 최신 이벤트 캐싱
    private boolean webviewActive = false;
    private final Map<String, CachedEvent> cachedEvents = new HashMap<>();

    private String onConnectEvent;
    private String onConnectData;

    // 단일 이벤트 캐싱용 내부 클래스
    private static class CachedEvent {
        final String event;
        final JSObject data;

        CachedEvent(String event, JSObject data) {
            this.event = event;
            this.data = data;
        }
    }

    // --------------------------------------------------
    // Plugin API
    // --------------------------------------------------

    @PluginMethod
    public void setOnConnectEmit(PluginCall call) {
        onConnectEvent = call.getString("onConnectEvent");
        onConnectData = call.getString("onConnectData");
        Log.d(TAG, "On connected event: " + onConnectEvent + ", data: " + onConnectData);
        call.resolve();
    }

    @PluginMethod
    public void startSocketConnection(PluginCall call) {
        host = call.getString("url");
        if (host == null) {
            call.reject("URL is required");
            return;
        }

        if (mSocket != null) {
            // return;
            if (mSocket.connected()) {
                Log.d(TAG, "Disconnecting existing socket.");
                mSocket.disconnect();
            }
            mSocket.off();
            mSocket = null;
        }

        try {
            mSocket = IO.socket(host);
            Log.d(TAG, "Socket instance created for host: " + host);
        } catch (Exception e) {
            Log.e(TAG, "Error creating socket", e);
            call.reject("Error creating socket", e);
            return;
        }
        registerDefaultEvents();
        mSocket.connect();
        Log.d(TAG, "Socket connect() called");
        call.resolve();
    }

    // 웹뷰 활성화 상태 알림
    @PluginMethod
    public void setWebviewActive(PluginCall call) {
        webviewActive = true;
        flushCachedEvents();
        call.resolve();
    }

    // 웹뷰 비활성화 상태 알림
    @PluginMethod
    public void setWebviewInactive(PluginCall call) {
        webviewActive = false;
        call.resolve();
    }

    // 소켓 이벤트를 JS에 전달할 때 keep-alive를 유지
    @PluginMethod
    public void onSocketEvent(PluginCall call) {
        call.setKeepAlive(true);
        call.resolve();
    }

    @PluginMethod
    public void emit(PluginCall call) {
        String event = call.getString("event");
        Object data;
        try {
            data = call.getData().get("data");
        } catch (Exception e) {
            call.reject("Failed to get data from call", e);
            return;
        }
    
        if (mSocket != null && event != null && data != null) {
            Log.d(TAG, "emit called: event = " + event + ", data = " + data.toString());
            mSocket.emit(event, data);
            call.resolve();
        } else {
            Log.e(TAG, "emit failed: Socket not connected or missing event/data");
            call.reject("Socket not connected, or event/data is null");
        }
    }
    

    // 특정 이벤트에 대해 소켓 리스너 등록
    @PluginMethod
    public void addSocketListener(PluginCall call) {
        String eventName = call.getString("event");
        if (eventName == null) {
            call.reject("Event name is required");
            return;
        }
        if (mSocket == null) {
            call.reject("Socket not initialized. Call startSocketConnection first.");
            return;
        }
        mSocket.on(eventName, args -> {
            Log.d(TAG, "Received event: " + eventName);
            JSObject ret = new JSObject();
            ret.put("event", eventName);
            ret.put("data", parseSocketData(args));
            processSpecialActions(ret);
            sendOrCacheEvent(eventName, ret);
        });
        call.resolve();
    }

    @PluginMethod
    public void isConnected(PluginCall call) {
        JSObject ret = new JSObject();
        boolean connected = (mSocket != null && mSocket.connected());
        ret.put("connected", connected);
        call.resolve(ret);
    }

    // --------------------------------------------------
    // 내부 헬퍼 메서드
    // --------------------------------------------------

    // 기본 소켓 이벤트 등록
    private void registerDefaultEvents() {
        registerSocketEvent(Socket.EVENT_CONNECT, "Socket connected");
        registerSocketEvent(Socket.EVENT_DISCONNECT, "Socket disconnected");
        registerSocketEvent(Socket.EVENT_CONNECT_ERROR, args -> {
            JSObject ret = new JSObject();
            ret.put("event", Socket.EVENT_CONNECT_ERROR);
            if (args != null && args.length > 0) {
                ret.put("message", args[0].toString());
            }
            return ret;
        });
    }

    // 기본 이벤트 등록 헬퍼 (메시지 전달형)
    private void registerSocketEvent(String socketEvent, String message) {
        mSocket.on(socketEvent, args -> {
            if(socketEvent == Socket.EVENT_CONNECT && onConnectData != null && !onConnectData.isEmpty()) {
                mSocket.emit(onConnectEvent, onConnectData);
            }
            JSObject ret = new JSObject();
            ret.put("event", socketEvent);
            ret.put("message", message);
            sendOrCacheEvent(socketEvent, ret);
        });
    }

    // 기본 이벤트 등록 헬퍼 (제너릭 이벤트 핸들러)
    private void registerSocketEvent(String socketEvent, EventDataGenerator generator) {
        mSocket.on(socketEvent, args -> {
            JSObject ret = generator.generate(args);
            sendOrCacheEvent(socketEvent, ret);
        });
    }

    // 인터페이스로 람다 표현식 사용 (커스텀 이벤트 핸들링)
    private interface EventDataGenerator {
        JSObject generate(Object... args);
    }

    // Socket.io 에서 넘어온 데이터를 파싱하여 JSObject 반환
    private Object parseSocketData(Object... args) {
        if (args != null && args.length > 0) {
            try {
                Object first = args[0];
                if (first instanceof JSONObject) {
                    return new JSObject(((JSONObject) first).toString());
                } else if (first instanceof org.json.JSONArray) {
                    return first.toString();
                } else {
                    return first;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing socket data", e);
                return args[0].toString();
            }
        }
        return null;
    }

    // 웹뷰 활성 시 바로 전달, 비활성 시 캐싱 (같은 이벤트는 덮어씀)
    private void sendOrCacheEvent(String event, JSObject ret) {
        if (webviewActive) {
            Log.d(TAG, "activated, notifying " + event);
            notifyListeners(event, ret);
            notifyListeners("allEvents", ret);
        } else {
            synchronized (cachedEvents) {
                Log.d(TAG, "web paused, caching " + event);
                cachedEvents.put(event, new CachedEvent(event, ret));
            }
        }
    }

    // 캐시된 이벤트들을 웹뷰가 활성화될 때 모두 전달 후 캐시 클리어
    private void flushCachedEvents() {
        synchronized (cachedEvents) {
            for (CachedEvent ce : cachedEvents.values()) {
                notifyListeners(ce.event, ce.data);
                notifyListeners("allEvents", ce.data);
            }
            cachedEvents.clear();
        }
    }

    // 이벤트 내 특수 동작 처리 (진동, 오디오, 기기 깨우기 등)
    private void processSpecialActions(JSObject ret) {
        if (!ret.has("data")) {
            return;
        }
        
        JSObject data;
        try {
            Object dataObj = ret.get("data");
            if (!(dataObj instanceof JSObject)) {
                return;
            }
            data = (JSObject) dataObj;
        } catch (Exception e) {  // JSONException 혹은 다른 예외 처리
            Log.e(TAG, "JSONException in processSpecialActions", e);
            return;
        }
        
        // 만약 중첩된 "data"가 있다면 추출
        if (data.has("data")) {
            try {
                data = data.getJSObject("data");
            } catch (Exception e) {
                Log.e(TAG, "Error extracting nested data", e);
                return;
            }
        }
        
        // 특수 동작 처리
        handleVibration(data);
        handleAudio(data);
        handleWake(data);
    }
    

    private void handleVibration(JSObject data) {
        if (!data.has("vibrationDuration")) {
            return;
        }
        try {
            long duration = data.getLong("vibrationDuration");
            Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(duration);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Vibration error", e);
        }
    }

    private void handleAudio(JSObject data) {
        if (!data.has("audio")) {
            return;
        }
        try {
            setVolumeMax();
            String audio = data.getString("audio");
            MediaPlayer mediaPlayer = MediaPlayer.create(getContext(), Uri.parse(host + "/" + audio));
            if (mediaPlayer != null) {
                mediaPlayer.start();
                mediaPlayer.setOnCompletionListener(mp -> mp.release());
                Log.d(TAG, "Playing audio: " + audio);
            }
        } catch (Exception e) {
            Log.e(TAG, "Audio playback error", e);
        }
    }

    private void handleWake(JSObject data) {
        if (!data.has("wake")) {
            return;
        }
        try {
            boolean wake = data.getBoolean("wake");
            if (wake) {
                PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    boolean isInteractive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH ? pm.isInteractive() : pm.isScreenOn();
                    if (!isInteractive) {
                        PowerManager.WakeLock wl = pm.newWakeLock(
                                PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                                "SimpleNativeSocketio:WakelockTag");
                        wl.acquire(3000);
                        wl.release();
                        Log.d(TAG, "Device wake triggered");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing wake", e);
        }
    }

    // 오디오 볼륨 최대치 설정
    private void setVolumeMax() {
        AudioManager audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0);
        } else {
            Log.e(TAG, "AudioManager is null");
        }
    }
}
