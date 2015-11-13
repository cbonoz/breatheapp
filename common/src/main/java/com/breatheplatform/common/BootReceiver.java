package com.breatheplatform.common;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import java.util.Calendar;


/**


Simple state machine to handle events based on power & screen state.
 Sensor-heavy app
 May be needed later for battery power management
 */
public class BootReceiver extends BroadcastReceiver
{
    public static final String NOTIFY_HOURLY   = "com.breatheplatform.asthma.action.NOTIFY_HOURLY";

    @Override
    public void onReceive(Context context, Intent intent) {

        switch (intent.getAction()) {

            /* Set Hourly repeating Alarm - executed at the top of every hour */
            case Intent.ACTION_BOOT_COMPLETED:
                /* get Current watch time, as synced with server */
                Calendar nextAlarm = Calendar.getInstance();
                nextAlarm.setTimeInMillis(System.currentTimeMillis());
                nextAlarm.set(Calendar.HOUR,nextAlarm.get(Calendar.HOUR)+1);
                nextAlarm.set(Calendar.MINUTE, 0);
                nextAlarm.set(Calendar.SECOND,0);
                nextAlarm.set(Calendar.MILLISECOND,0);
                /* Set repeating hourly alarm */
                PendingIntent clockIntent = PendingIntent.getBroadcast(context, 0, new Intent(NOTIFY_HOURLY), PendingIntent.FLAG_UPDATE_CURRENT);
                AlarmManager AppClock = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                AppClock.setRepeating(AlarmManager.RTC_WAKEUP, nextAlarm.getTimeInMillis(),AlarmManager.INTERVAL_HOUR,clockIntent);
                /* Start background services only if not plugged in */
                Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if((batteryStatus == null) || !(batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) > 0))
                    break;

            case Intent.ACTION_POWER_DISCONNECTED:
            case Intent.ACTION_BATTERY_OKAY:
            case NOTIFY_HOURLY:
                //context.startService(new Intent(context, Sensors.class));
                break;

            case Intent.ACTION_POWER_CONNECTED:
            case Intent.ACTION_BATTERY_LOW:
                //context.stopService(new Intent(context, Sensors.class));
                break;
        }
    }
}


