package com.fainthit.androidforegroundsocketio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class AndroidForegroundSocketService extends Service {

    private static final String CHANNEL_ID = "android_foreground_socket";
    private static final int NOTIFICATION_ID = 7721;
    private static final String ACTION_START = "com.fainthit.androidforegroundsocketio.ANDROID_FOREGROUND_SOCKET_START";
    private static final String ACTION_STOP = "com.fainthit.androidforegroundsocketio.ANDROID_FOREGROUND_SOCKET_STOP";

    static void start(Context context) {
        Intent intent = new Intent(context, AndroidForegroundSocketService.class);
        intent.setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    static void stop(Context context) {
        Intent intent = new Intent(context, AndroidForegroundSocketService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    static void refresh(Context context) {
        start(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        AndroidForegroundSocketManager.getInstance().initialize(getApplicationContext());
        AndroidForegroundSocketManager.getInstance().restoreIfNeeded(getApplicationContext());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            AndroidForegroundSocketManager.getInstance().stop(getApplicationContext());
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification());
        AndroidForegroundSocketManager.getInstance().restoreIfNeeded(getApplicationContext());
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Android Foreground Socket", NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this);

        AndroidForegroundSocketManager manager = AndroidForegroundSocketManager.getInstance();
        return builder
            .setContentTitle(manager.getNotificationTitle())
            .setContentText(manager.getNotificationText())
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build();
    }
}
