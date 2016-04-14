package com.breatheplatform.beta.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by cbono on 4/13/16.
 */
public class BootCompletedIntentReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if ("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) {
            Log.d("boot","onBootCompleted");
//            Intent pushIntent = new Intent(context, MobileReceiverService.class);
//            context.startService(pushIntent);
        }
    }
}