package com.fainthit.androidforegroundsocketio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AndroidForegroundSocketBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        AndroidForegroundSocketManager manager = AndroidForegroundSocketManager.getInstance();
        if (manager.shouldStartOnBoot(context)) {
            AndroidForegroundSocketService.start(context);
        }
    }
}
