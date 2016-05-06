package com.breatheplatform.beta.receivers;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.breatheplatform.beta.shared.Constants;

/**
 * Created by cbono on 4/27/16.
 */
public class AlarmReceiver extends BroadcastReceiver {
    private static String TAG = "AlarmReceiver";

    public static String ALARM_ID = "alarm-id";
    public static String NOTIFICATION = "notification";

    public void onReceive(Context context, Intent intent) {
        int id = intent.getIntExtra(ALARM_ID, Constants.NO_VALUE);
        Log.d(TAG, "Alarm Called - alarm_id: " + id);

        switch (id) {
            case Constants.SPIRO_ALARM_ID:
                Log.d(TAG, "Spiro alarm called");
                NotificationManager notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
                Notification notification = intent.getParcelableExtra(NOTIFICATION);
                notificationManager.notify(id, notification);
                break;
            case Constants.QUESTION_ALARM_ID:
                Log.d(TAG, "Question alarm called");
//                Intent i = new Intent();
//                i.setClass(context, QuestionActivity.class);
//                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                context.startActivity(i);

                //TODO: create reminder and activity intent for questionnaire
                break;

            case Constants.CLOSE_SPIRO_ALARM_ID:
                Log.d(TAG, "Close spiro alarm called");
                break;
//            case Constants.SENSOR_ALARM_ID:
//                Log.d(TAG, "SensorAlarm");
//                Intent i = new Intent(context, SensorService.class);
//                try {
//                    context.stopService(i);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//                context.startService(i);
//
//                break;
//            case Constants.STOP_ALARM_ID:
//                Log.d(TAG, "Stop sensor alarm called");
//                try {
//                    ((MainActivity) context).stopMeasurement(context);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//                break;
            case Constants.NO_VALUE:
                Log.d(TAG, "No value alarm called");
                break;
            default:
                Log.d(TAG, "Unknown Alarm");


        }

    }


    private void startQuestionActivity() {

    }


}
