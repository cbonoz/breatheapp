package com.breatheplatform.beta.messaging;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.breatheplatform.beta.shared.Constants;

/**
 * Created by cbono on 5/6/16.
 */
public class SensorAlarm extends BroadcastReceiver {
    private static final String TAG = "SensorAlarm";



    @Override
    public void onReceive(Context context, Intent intent) {
        Integer alarmId = intent.getIntExtra("alarm_id", Constants.NO_VALUE);
        Log.d(TAG, "Received alarm " + alarmId);
//        switch(alarmId) {
//            case Constants.STOP_ALARM_ID:
//                stopMeasurement(context);
//                //set up the significant motion listener for regulating the sensor service
//                mSensorManager.requestTriggerSensor(mListener, mSigMotionSensor);
//
//                break;
//            case Constants.START_ALARM_ID:
//                if (mSensorManager==null) {
//                    mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
//                    mSigMotionSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);

//                }
//                startMeasurement(context);
//                break;
//        }
    }



}
