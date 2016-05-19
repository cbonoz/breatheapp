package com.breatheplatform.beta.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.breatheplatform.beta.ClientPaths;
import com.breatheplatform.beta.shared.Constants;

/**
 * Created by cbono on 2/11/16.
 */
public class BatteryReceiver extends BroadcastReceiver {
    private static final String TAG = "BatteryReceiver";
//
//    @Override
//    public void onReceive(Context arg0, Intent intent) {
//
//        Log.d(TAG, "receive battery");
//        try{
//            ClientPaths.batteryLevel = intent.getIntExtra("level", 0);
//
////            int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
////            int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
////            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
////
////            String BStatus = "No Data";
////            if (status == BatteryManager.BATTERY_STATUS_CHARGING){BStatus = "Charging";}
////            if (status == BatteryManager.BATTERY_STATUS_DISCHARGING){BStatus = "Discharging";}
////            if (status == BatteryManager.BATTERY_STATUS_FULL){BStatus = "Full";}
////            if (status == BatteryManager.BATTERY_STATUS_NOT_CHARGING){BStatus = "Not Charging";}
////            if (status == BatteryManager.BATTERY_STATUS_UNKNOWN){BStatus = "Unknown";}
////
////            int chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
////            String BattPowerSource = "No Data";
////            if (chargePlug == BatteryManager.BATTERY_PLUGGED_AC){BattPowerSource = "AC";}
////            if (chargePlug == BatteryManager.BATTERY_PLUGGED_USB){BattPowerSource = "USB";}
////
////            //String BattLevel = String.valueOf(level);
//
//            int BHealth = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
//            String BatteryHealth = "No Data";
//            if (BHealth == BatteryManager.BATTERY_HEALTH_COLD){BatteryHealth = "Cold";}
//            if (BHealth == BatteryManager.BATTERY_HEALTH_DEAD){BatteryHealth = "Dead";}
//            if (BHealth == BatteryManager.BATTERY_HEALTH_GOOD){BatteryHealth = "Good";}
//            if (BHealth == BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE){BatteryHealth = "Over-Voltage";}
//            if (BHealth == BatteryManager.BATTERY_HEALTH_OVERHEAT){BatteryHealth = "Overheat";}
//            if (BHealth == BatteryManager.BATTERY_HEALTH_UNKNOWN){BatteryHealth = "Unknown";}
//            if (BHealth == BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE){BatteryHealth = "Unspecified Failure";}
//
//            //Do whatever with the data here
//
//            Log.d(TAG, "Battery health: " + BatteryHealth);
//
//
//        } catch (Exception e){
//            Log.v(TAG, "Battery Info Error");
//        }
//    }

    @Override
    public void onReceive(Context arg0, Intent arg1) {
        int bLevel = arg1.getIntExtra("level", Constants.NO_VALUE); // gets the battery level
        ClientPaths.batteryLevel = bLevel;
        Log.d(" Battery Level", bLevel+"");
        // Here you do something useful with the battery level...
    }
}
