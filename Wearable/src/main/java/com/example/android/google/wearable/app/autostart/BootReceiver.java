package com.example.android.google.wearable.app.autostart;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.android.google.wearable.app.MainActivity;

/**
 * Created by cbono on 1/7/16.
 * stackoverflow.com/questions/10428510/how-to-start-launch-application-at-boot-time-android
 * used for launching the application on watch start up
 *
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        if(intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)){
            Intent i = new Intent(context, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        }
    }
}