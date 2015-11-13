package com.breatheplatform.common;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Created by cbono on 11/10/15.
 */

public class PhoneListener implements SensorEventListener {

    /*
    This class will listen to device-specific (mobile) sensors and collect data for upload.
    This will work almost identically to the Watch-specific version (will return a JSON object on request)
     */

    private static final SimpleDateFormat nameFormat = new SimpleDateFormat("'_'yyyy-MM-dd-HH",
            Locale.US);

//    private static final int GYRO_SAMPLE_RATE     = 10;     // 10 Hz
//    private static final int ACCEL_SAMPLE_RATE = 10;     // 10 Hz
//    private static final int COMPASS_SAMPLE_RATE = 10;     // 10 Hz
//    private static final int LIGHT_SAMPLE_RATE = 10;     // 10 Hz
//    private static final int FILE_BUFFER_SIZE = 65536;  // 64 * 1024 byte, 64 kB file buffer
//    private static boolean receiverSet = false;

    private final String TAG = this.getClass().getSimpleName();


    // Time of Boot = Current Wall Clock Time - Time since boot.
    // mFileHour indicates the which hour the CSV file was generated.
    private static final long timeOfBoot = (System.currentTimeMillis() * 1000000) - SystemClock.elapsedRealtimeNanos();

    //private static BufferedWriter mGyroBuffer = null, mAccelBuffer = null, mCompassBuffer = null;
    //private static SensorManager mSensorManager;
    private static PowerManager.WakeLock mWakeLock = null;
    private static final int USER_ID = 5;
    /*
    This class will listen to watch-specific sensors
     and send data to phone for collection and upload
     (may need to be in wear folder)
     */
    private SensorManager mSensorManager;


    private LocationManager locationManager;

    private JSONObject jsonObj;
    //private ArrayList<JSONObject> jsonArr;
    private JSONArray jsonArr;

    private Boolean listening;

    public PhoneListener(Context ctx) {
        mSensorManager = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        locationManager = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);

        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                JSONObject temp = new JSONObject();

                try {
                    temp.put("Time", location.getTime());
                    temp.put("Sensor", Context.LOCATION_SERVICE);
                    temp.put("User", USER_ID);
                    temp.put("Latitude", location.getLatitude());
                    temp.put("Longitude", location.getLongitude());
                    temp.put("Speed", location.getSpeed());
                    temp.put("Accuracy", location.getAccuracy());

                } catch (JSONException e) {
                    resetSensors();
                }


                jsonArr.put(temp);
                // Called when a new location is found by the network location provider.

            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }
        };


        //locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

        PowerManager mgr = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        mWakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        mWakeLock.acquire();


        // Initializing the sensors
        Sensor mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
//        Sensor mGyroscope     = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
//        Sensor mCompass       = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
//        Sensor mLight         = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
//        Sensor mHeartRateSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
//        Sensor mStepCountSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
//        Sensor mStepDetectSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);

        Sensor mTemp = mSensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);


        // Start recording data
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mTemp, SensorManager.SENSOR_DELAY_NORMAL);

//        mSensorManager.registerListener(this, mGyroscope, (1000000 / GYRO_SAMPLE_RATE));
//        mSensorManager.registerListener(this, mCompass, (1000000 / COMPASS_SAMPLE_RATE));
//        mSensorManager.registerListener(this, mLight, (1000000 / LIGHT_SAMPLE_RATE));
//        mSensorManager.registerListener(this, mHeartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
//        mSensorManager.registerListener(this, mStepCountSensor, SensorManager.SENSOR_DELAY_NORMAL);
//        mSensorManager.registerListener(this, mStepDetectSensor, SensorManager.SENSOR_DELAY_NORMAL);

        jsonObj = new JSONObject();
        //jsonArr = new ArrayList<JSONObject>();
        jsonArr = new JSONArray();

        listening = true;

        //Toast.makeText(this, "Scanning", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1) {
        // TODO
        return;

    }


    public void unregister() {
        mSensorManager.unregisterListener(this);
    }

    public JSONObject getJson() {
        try {
            jsonObj.put("Data", jsonArr);
            return jsonObj;
        } catch (JSONException e) {
            resetSensors();
            return null;
        }


    }

    public void clearJson() {
        jsonArr = new JSONArray();
        jsonObj = new JSONObject();
    }


    public void onDestroy() {

        // Write-out any remaining data (in sensor) to file buffer(s).
        //mSensorManager.flush(this);
        mSensorManager.unregisterListener(this);

        // Release the Wake Lock so CPU can go to sleep.
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }

        //Toast.makeText(this,"Stopped",Toast.LENGTH_SHORT).show();
    }

    /* Upon receiving values from the sensor, detect the correct file buffer to write to
       and fill out file-buffer. All files are written as CSV files.

       Data values :: timestamp, x, y, z
     */

    //in case of exception
    //can add Sensor reset here if needed
    public void resetSensors() {
        clearJson();

    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        // get Nanos timestamp in epoch time (with Nanos precision).

        if (listening) {
            JSONObject temp = new JSONObject();

            try {
                int s_type = event.sensor.getType();
                final long absolute_timestamp = timeOfBoot + event.timestamp;


                temp.put("Time", absolute_timestamp);
                temp.put("Sensor", s_type);
                temp.put("User", USER_ID);
                /* // switch for sensor specific field naming
                switch(s_type) {

                }
                */
                int i = 0;
                for (float v : event.values) {
                    temp.put("v" + i++, v);
                }
            } catch (JSONException e) {
                resetSensors();
            }


            jsonArr.put(temp);
        }


    }
}