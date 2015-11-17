package com.breatheplatform.asthma;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Vibrator;
import android.widget.Toast;

import java.util.Calendar;

public class BootReceiver extends BroadcastReceiver
{
    public static final String INTENT_NOTIFY_HOURLY = "erlab.ucla.whi.gimbal_home.action.NOTIFY_HOURLY";
    public static final String INTENT_LAUNCH_APP    = "erlab.ucla.whi.gimbal_home.action.LAUNCH_APP";
    public static final String INTENT_SYNC_COMPLETE = "erlab.ucla.whi.gimbal_home.action.SYNC_COMPLETE";
    private static final long[] VIBRATION_PATTERN = new long[]{300, 500, 300, 500, 300, 500};
    private static final int LOW_BATTERY_THRESHOLD = 6;
    private static final int ALARM_HOUR = 13; // Daily at 1:00 PM

    @Override
    public void onReceive(Context context, Intent intent) {

        switch (intent.getAction()) {
            /* Set Hourly repeating Alarm - executed at the top of every hour */
            case Intent.ACTION_BOOT_COMPLETED:
            case INTENT_LAUNCH_APP:
                Toast.makeText(context, "LAUNCHED", Toast.LENGTH_SHORT).show();
                /* get Current watch time, as synced with server */
                Calendar nextAlarm = Calendar.getInstance();
                nextAlarm.setTimeInMillis(System.currentTimeMillis());
                nextAlarm.set(Calendar.HOUR, nextAlarm.get(Calendar.HOUR) + 1); //nextAlarm.get(Calendar.HOUR) + 1
                nextAlarm.set(Calendar.MINUTE, 0);
                nextAlarm.set(Calendar.SECOND, 0);
                nextAlarm.set(Calendar.MILLISECOND, 0);

                /* Set repeating half-hourly alarm */
                PendingIntent clockIntent = PendingIntent.getBroadcast(context, 0, new Intent(INTENT_NOTIFY_HOURLY), PendingIntent.FLAG_CANCEL_CURRENT);
                AlarmManager AppClock = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                AppClock.setRepeating(AlarmManager.RTC_WAKEUP, nextAlarm.getTimeInMillis(), AlarmManager.INTERVAL_HOUR, clockIntent);

            case INTENT_NOTIFY_HOURLY:
                /* Start background services only if not plugged in && battery less than 6% */
                if(!statePlugged(context) && (getBatteryLevel(context) < LOW_BATTERY_THRESHOLD))
                    break;

                //Calendar current_time = Calendar.getInstance();
                //current_time.setTimeInMillis(System.currentTimeMillis());
                //if(current_time.get(Calendar.HOUR) == ALARM_HOUR)
                //  startSurvey(context,Calendar.DAY_OF_WEEK);


            case Intent.ACTION_BATTERY_OKAY:
                context.startService(new Intent(context,WatchSensors.class));
                break;

            case Intent.ACTION_BATTERY_LOW:
                if(statePlugged(context))
                    break;

            case Intent.ACTION_SHUTDOWN:
                context.stopService(new Intent(context, WatchSensors.class));
                break;

            case INTENT_SYNC_COMPLETE:
                Toast.makeText(context, "SYNC COMPLETE", Toast.LENGTH_LONG).show();
                Vibrator vibe = (Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
                vibe.vibrate(VIBRATION_PATTERN, -1);
                break;
        }
    }


    private static int getBatteryLevel(Context context) {
        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent == null)
            return -1;

        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        return(level == -1 || scale < 1 ? -1 : (100*level/scale));
    }

    private static boolean statePlugged(Context context){
        Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return(batteryStatus == null || batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) > 0);
    }


}
