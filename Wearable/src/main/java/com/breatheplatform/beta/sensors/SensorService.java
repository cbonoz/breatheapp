package com.breatheplatform.beta.sensors;

import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.breatheplatform.beta.ClientPaths;
import com.breatheplatform.beta.data.SensorAddService;
import com.breatheplatform.beta.shared.Constants;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import me.denley.courier.Courier;

/**
 * Created by cbono on 4/5/16.
 * http://www.tutorialspoint.com/android/android_services.htm
 */
public class SensorService extends Service implements SensorEventListener {

    private static final String TAG = "SensorService";

    private static float lastHeartRate = Constants.NO_VALUE;

    //Sensor related
    private ScheduledExecutorService mScheduler;
    private SensorManager mSensorManager;
    private Sensor heartRateSensor;
    private Sensor linearAccelerationSensor;
    private Sensor gyroSensor;


    private static final int SENS_LINEAR_ACCELERATION = Sensor.TYPE_LINEAR_ACCELERATION;
    private static final int SENS_HEARTRATE = Sensor.TYPE_HEART_RATE;
    private static final int SENS_GYRO = Sensor.TYPE_GYROSCOPE;
//    private static final Context context;


    private void eventCallBack(SensorEvent event) {
//        Log.d(TAG, "onSensorChanged");
        int sensorId = event.sensor.getType();
        long timestamp = event.timestamp;//System.currentTimeMillis();
        if (sensorId == Constants.HEART_SENSOR_ID) {
            Float heartRate = event.values[0];

            if (event.accuracy > 1) {
                checkForQuestionnaire(heartRate);
                addSensorData(sensorId, event.accuracy, timestamp, event.values);
            } else {
                event.values[0] = Constants.NO_VALUE;
            }
            //update the heart UI (just heart rate currently)
            //http://stackoverflow.com/questions/8802157/how-to-use-localbroadcastmanager
            Intent i = new Intent(Constants.HEART_EVENT);
            i.putExtra("heartrate",event.values[0]);

            LocalBroadcastManager.getInstance(SensorService.this).sendBroadcast(i);
        } else {
            addSensorData(sensorId, event.accuracy, timestamp, event.values);
        }
//            updateLastView(sensorId);
    }

    /*
    Have listeners/messages set up to when heart rate is high
    (115 bpm for 12 year olds, 130 bpm for 8-12 year olds,
    find age using sensor ID), activity classified as running
    (from Anahita's classifier), and inhaler is used
    (from pulled data from Propeller Health's AsthmaPollus app)
    so Ohmage can use this data to create triggers
    for the EMA questionnaire on the phone.
     */
    private void checkForQuestionnaire(Float heartRate) {
        if (ClientPaths.activityDetail.contains("RUN")) {
            if (ClientPaths.userAge <= 12) {
                if (heartRate >= 130) {
                    requestQuestionnaire();
                }
            } else { //older than 12
                if (heartRate >= 115) {
                    requestQuestionnaire();
                }
            }
        }
    }

//    SensorEventListener sensorEventListener = new SensorEventListener() {
//        @Override
//        public void onSensorChanged(SensorEvent event) {
//            eventCallBack(event);
//        }
//        @Override
//        public void onAccuracyChanged(Sensor sensor, int accuracy) {
//
//        }
//    };

    @Override
    public void onSensorChanged(SensorEvent event) {
        eventCallBack(event);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }


    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Let it continue running until it is stopped.
//        Toast.makeText(sensorEventListener, "SensorService Started", Toast.LENGTH_LONG).show();
        Log.d(TAG, "onStartCommand");

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        heartRateSensor = mSensorManager.getDefaultSensor(SENS_HEARTRATE);
        linearAccelerationSensor = mSensorManager.getDefaultSensor(SENS_LINEAR_ACCELERATION);
        gyroSensor = mSensorManager.getDefaultSensor(SENS_GYRO);
        //final Sensor heartrateSamsungSensor = mSensorManager.getDefaultSensor(ActivityConstants.REG_HEART_SENSOR_ID );//65562

        Log.i(TAG, "Start Measurement");



        mScheduler = Executors.newScheduledThreadPool(2);


        if (Constants.slowSensorRate) { //normal speed of sensor logging
            if (linearAccelerationSensor != null && gyroSensor != null) {
                //sum of these achieves sampling rate of 1hz
                final int measurementDuration = 300;   // ms
                final int measurementBreak = 700;    // Seconds
                mScheduler.scheduleAtFixedRate(
                        new Runnable() {
                            @Override
                            public void run() {
//                            Log.d(TAG, "register LA Sensor");
                                mSensorManager.registerListener(SensorService.this, linearAccelerationSensor, SensorManager.SENSOR_DELAY_NORMAL);
                                mSensorManager.registerListener(SensorService.this, gyroSensor, SensorManager.SENSOR_DELAY_NORMAL);

                                try {
                                    Thread.sleep(measurementDuration);
                                } catch (InterruptedException e) {
                                    Log.e(TAG, "Interrupted while waitting to unregister LA Sensor");
                                }

//                            Log.d(TAG, "unregister LA Sensor");
                                mSensorManager.unregisterListener(SensorService.this, linearAccelerationSensor);
                                mSensorManager.unregisterListener(SensorService.this, gyroSensor);

                            }
                        }, 1, measurementDuration + measurementBreak, TimeUnit.MILLISECONDS);


            } else {
                Log.d(TAG, "No Linear Acceleration or Gyro Sensor found");
            }

//            Integer oneHz = 1000000;
//            if (linearAccelerationSensor != null) {
//                mSensorManager.registerListener(SensorService.this, linearAccelerationSensor, oneHz,0);// 1000000, 1000000);
//            }  else {
//                Log.d(TAG, "No Linear Acceleration Sensor found");
//            }
//
//
//            if (gyroSensor != null) {
//                mSensorManager.registerListener(SensorService.this, gyroSensor, oneHz,0);
//            } else {
//                Log.w(TAG, "No Gyroscope Sensor found");
//            }

            Log.d(TAG, "Registered sensors at slow rate");
        } else { //schedule at normal rate (about 5hz)
            //http://stackoverflow.com/questions/30153904/android-how-to-set-sensor-delay
            if (linearAccelerationSensor != null) {
                mSensorManager.registerListener(SensorService.this, linearAccelerationSensor, SensorManager.SENSOR_DELAY_NORMAL);// 1000000, 1000000);
            }  else {
                Log.d(TAG, "No Linear Acceleration Sensor found");
            }


            if (gyroSensor != null) {
                mSensorManager.registerListener(SensorService.this, gyroSensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Log.w(TAG, "No Gyroscope Sensor found");
            }
            Log.d(TAG, "Registered sensors at fast rate");
        }


        if (heartRateSensor != null) {
            final int measurementDuration = 10;   // Seconds
            final int measurementBreak = 5;    // Seconds


            mScheduler.scheduleAtFixedRate(
                    new Runnable() {
                        @Override
                        public void run() {
//                            Log.d(TAG, "register Heartrate Sensor");
                            Log.d(TAG, "Reading Heartrate Sensor");
                            mSensorManager.registerListener(SensorService.this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);

                            try {
                                Thread.sleep(measurementDuration * 1000);
                            } catch (InterruptedException e) {
                                Log.e(TAG, "Interrupted while waitting to unregister Heartrate Sensor");
                            }

//                            Log.d(TAG, "unregister Heartrate Sensor");
                            mSensorManager.unregisterListener(SensorService.this, heartRateSensor);
                        }
                    }, 1, measurementDuration + measurementBreak, TimeUnit.SECONDS);


        } else {
            Log.d(TAG, "No Heartrate Sensor found");
        }


//        if (heartrateSamsungSensor != null) {
//            mSensorManager.registerListener(sensorEventListener, heartrateSamsungSensor, ActivityConstants.SENSOR_DELAY_CUSTOM);
//        } else {
//            Log.d(TAG, "Samsungs Heartrate Sensor not found");
//        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
//        Toast.makeText(sensorEventListener, "SensorService Destroyed", Toast.LENGTH_LONG).show();
        Log.d(TAG, "onDestroy");

        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this);
        }
        if (mScheduler != null && !mScheduler.isTerminated()) {
            mScheduler.shutdown();
        }
    }

    //append sensor data
    private void addSensorData(final Integer sensorType, final Integer accuracy, final Long t, final float[] values) {

        Intent i = new Intent(this, SensorAddService.class);
        i.putExtra("sensorType", sensorType);
        i.putExtra("accuracy", accuracy);
        i.putExtra("time", t);
        i.putExtra("values", values);

        startService(i);
    }

    //TODO: this intent will launch questionnaire activity on the phone
    private void requestQuestionnaire() {
        Courier.deliverMessage(this, Constants.QUESTION_API, "");
    }
}