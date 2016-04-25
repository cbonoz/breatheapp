package com.breatheplatform.beta.messaging;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.breatheplatform.beta.sensors.SensorService;
import com.breatheplatform.beta.shared.Constants;

/**
 * Created by cbono on 4/24/16.
 */
public class NotificationPublisher extends BroadcastReceiver {
    private static String TAG = "NotificationPublisher";

    public static String NOTIFICATION_ID = "notification-id";
    public static String NOTIFICATION = "notification";

    public void onReceive(Context context, Intent intent) {
        int id = intent.getIntExtra(NOTIFICATION_ID, 0);
        Log.d(TAG, "Alarm Called - notification_id: " + id);

        switch (id) {
            case Constants.SPIRO_ALARM_ID:
                Log.d(TAG, "SpiroAlarm");
                NotificationManager notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
                Notification notification = intent.getParcelableExtra(NOTIFICATION);
                notificationManager.notify(id, notification);
                break;
            case Constants.QUESTION_ALARM_ID:
                //TODO: create reminder and activity intent for questionnaire
                break;
            case Constants.SENSOR_ALARM_ID:
                Log.d(TAG, "SensorAlarm");
                Intent i = new Intent(context, SensorService.class);
                try {
                    context.stopService(i);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                context.startService(i);

                break;

        }


    }
}