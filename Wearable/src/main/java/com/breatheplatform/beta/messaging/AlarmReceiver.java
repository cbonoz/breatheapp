package com.breatheplatform.beta.messaging;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.breatheplatform.beta.shared.Constants;

/**
 *
 * Created by cbono on 4/14/16.
 * http://developer.android.com/training/wearables/notifications/creating.html
 * https://gist.github.com/BrandonSmith/6679223
 *
 */
public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmReceiver";

    public static String NOTIFICATION_ID = "notification-id";
    public static String NOTIFICATION = "notification";

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO Auto-generated method stub
        int id = intent.getIntExtra(NOTIFICATION_ID, 0);
        Log.d(TAG, "Alarm Called - notification_id: " + id);

        switch (id) {
            case Constants.SPIRO_ALARM_ID:
                NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                Notification notification = intent.getParcelableExtra(NOTIFICATION);
                notificationManager.notify(id, notification);
                break;
            case Constants.QUESTION_ALARM_ID:
                //TODO: create reminder and activity intent for questionnaire
                break;
            case Constants.SENSOR_ALARM_ID:

                break;

        }
    }
}
