package com.breatheplatform.beta.receivers;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.breatheplatform.beta.QuestionActivity;
import com.breatheplatform.beta.R;
import com.breatheplatform.beta.shared.Constants;

import me.denley.courier.Courier;

/**
 * Created by cbono on 4/27/16.
 */
public class AlarmReceiver extends BroadcastReceiver {
    private static String TAG = "AlarmReceiver";

//    public static String ALARM_ID = "alarm-id";
    public static String NOTIFICATION = "notification";

    public void onReceive(Context context, Intent intent) {
        String subject = intent.getStringExtra("subject");
        Integer id = intent.getIntExtra("alarm-id", Constants.NO_VALUE);

        Log.d(TAG, "Alarm Called - alarm_id: " + id);
        if (subject.equals("")) {
            Log.i(TAG, "Subject is null - muting alarm");
            return;
        }

        switch (id) {
            case Constants.SPIRO_ALARM_ID:
                Log.d(TAG, "Spiro alarm called");
                Courier.deliverMessage(context, Constants.REMINDER_API, "spiro");

                int mNotificationId = 001;
                NotificationManager mNotifyMgr = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                mNotifyMgr.notify(mNotificationId, buildSpiroReminder(context));
                Toast.makeText(context, "Spirometer Time", Toast.LENGTH_LONG).show();
                break;
            case Constants.QUESTION_ALARM_ID:
                Log.d(TAG, "Question alarm called");
                Intent i = new Intent();
                i.setClass(context, QuestionActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
                Toast.makeText(context, "Question Time", Toast.LENGTH_LONG).show();

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
                break;
        }
    }


    private Notification buildSpiroReminder(Context c) {
//        Intent viewIntent = new Intent(this, MainActivity.class);
//        viewIntent.putExtra("event-id", 0);
//        PendingIntent viewPendingIntent = PendingIntent.getActivity(this, 0, viewIntent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(c)
//                        .setSmallIcon(R.drawable.ic_spiro)
                .setLargeIcon(BitmapFactory.decodeResource(c.getResources(), R.drawable.ic_spiro))
                .setContentTitle("Breathe Reminder!")
                .setVibrate(new long[]{500})
                .setContentText("Time to use Spirometer on Watch")
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true);
//                .setContentIntent(viewPendingIntent);
        return builder.build();
    }


}
