package com.fainthit.androidforegroundsocketio;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import com.getcapacitor.JSObject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

final class AndroidForegroundSocketActionExecutor {

    private final AndroidForegroundSocketManager socketManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<String, MediaPlayer> players = new HashMap<>();
    private Context context;

    AndroidForegroundSocketActionExecutor(AndroidForegroundSocketManager socketManager) {
        this.socketManager = socketManager;
    }

    void initialize(Context context) {
        this.context = context.getApplicationContext();
    }

    void executeAction(JSObject action, Object socketData, String baseUrl) {
        JSONArray steps = action.optJSONArray("run");
        if (steps == null) {
            return;
        }

        executor.execute(() -> {
            for (int i = 0; i < steps.length(); i++) {
                JSONObject step = steps.optJSONObject(i);
                if (step != null) {
                    executeStep(copy(step), socketData, baseUrl);
                }
            }

            JSObject payload = new JSObject();
            payload.put("id", action.optString("id"));
            payload.put("event", action.optString("event"));
            AndroidForegroundSocketEventBridge.publishAction(payload);
        });
    }

    void executeLegacyPayload(Object data, String baseUrl) {
        if (!(data instanceof JSONObject)) {
            return;
        }

        JSONObject json = unwrapData((JSONObject) data);
        if (json.optBoolean("wake", false)) {
            JSObject step = new JSObject();
            step.put("type", "wakeScreen");
            step.put("durationMs", json.optLong("wakeDurationMs", 8000L));
            executeStep(step, data, baseUrl);
        }
        if (json.has("vibrationDuration")) {
            JSObject step = new JSObject();
            step.put("type", "vibrate");
            step.put("durationMs", json.optLong("vibrationDuration", 300L));
            executeStep(step, data, baseUrl);
        }
        if (json.has("audio")) {
            JSObject step = new JSObject();
            step.put("type", "playSound");
            step.put("source", json.optString("audio"));
            step.put("volume", 1.0);
            executeStep(step, data, baseUrl);
        }
    }

    private void executeStep(JSObject step, Object socketData, String baseUrl) {
        if (context == null) {
            return;
        }

        String type = step.optString("type");
        try {
            switch (type) {
                case "wakeScreen":
                    wakeScreen(step.optLong("durationMs", 8000L));
                    break;
                case "vibrate":
                    vibrate(step);
                    break;
                case "playSound":
                    playSound(step, baseUrl);
                    break;
                case "stopSound":
                    stopSound(step.optString("channel", "default"));
                    break;
                case "setVolume":
                    setVolume(step.optDouble("level", 1.0));
                    break;
                case "delay":
                    Thread.sleep(Math.max(0L, step.optLong("durationMs", 0L)));
                    break;
                case "emit":
                    socketManager.emit(step.optString("event"), step.opt("data"));
                    break;
                case "notifyWebview":
                    JSObject payload = new JSObject();
                    payload.put("data", step.has("data") ? step.opt("data") : socketData);
                    AndroidForegroundSocketEventBridge.publishSocketEvent(step.optString("event", "nativeAction"), payload);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            AndroidForegroundSocketEventBridge.publishError("Failed to execute action step: " + type, e);
        }
    }

    private void wakeScreen(long durationMs) {
        Intent intent = new Intent(context, WakeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(WakeActivity.EXTRA_DURATION_MS, durationMs);
        context.startActivity(intent);
    }

    private void vibrate(JSObject step) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null) {
            return;
        }

        JSONArray pattern = step.optJSONArray("pattern");
        if (pattern != null && pattern.length() > 0) {
            long[] values = new long[pattern.length()];
            for (int i = 0; i < pattern.length(); i++) {
                values[i] = pattern.optLong(i);
            }
            int repeat = step.optInt("repeat", -1);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(values, repeat));
            } else {
                vibrator.vibrate(values, repeat);
            }
            return;
        }

        long durationMs = step.optLong("durationMs", 300L);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(durationMs);
        }
    }

    private void playSound(JSObject step, String baseUrl) throws Exception {
        String source = step.optString("source", "");
        if (source.isEmpty()) {
            return;
        }

        String channel = step.optString("channel", "default");
        stopSound(channel);
        setVolume(step.optDouble("volume", 1.0));

        MediaPlayer player = new MediaPlayer();
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        player.setLooping(step.optBoolean("loop", false));

        if (source.startsWith("asset://")) {
            String assetPath = source.substring("asset://".length());
            AssetFileDescriptor afd = context.getAssets().openFd(assetPath);
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
        } else if (source.startsWith("url://")) {
            player.setDataSource(context, Uri.parse(source.substring("url://".length())));
        } else if (source.startsWith("file://") || source.startsWith("http://") || source.startsWith("https://")) {
            player.setDataSource(context, Uri.parse(source));
        } else if (baseUrl != null && !baseUrl.isEmpty()) {
            String separator = source.startsWith("/") ? "" : "/";
            player.setDataSource(context, Uri.parse(baseUrl + separator + source));
        } else {
            player.setDataSource(context, Uri.parse(source));
        }

        players.put(channel, player);
        player.setOnCompletionListener(mp -> {
            if (!mp.isLooping()) {
                stopSound(channel);
            }
        });
        player.prepare();
        player.start();
    }

    private void stopSound(String channel) {
        MediaPlayer player = players.remove(channel);
        if (player == null) {
            return;
        }
        try {
            if (player.isPlaying()) {
                player.stop();
            }
        } catch (Exception ignored) {}
        player.release();
    }

    private void setVolume(double level) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            return;
        }
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int value = (int) Math.round(max * Math.max(0.0, Math.min(1.0, level)));
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
    }

    private JSONObject unwrapData(JSONObject json) {
        JSONObject nested = json.optJSONObject("data");
        return nested == null ? json : nested;
    }

    private JSObject copy(JSONObject object) {
        try {
            return JSObject.fromJSONObject(object);
        } catch (Exception e) {
            return new JSObject();
        }
    }
}
