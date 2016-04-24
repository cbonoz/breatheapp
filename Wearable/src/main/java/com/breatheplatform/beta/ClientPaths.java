package com.breatheplatform.beta;

import android.content.Context;
import android.hardware.Sensor;
import android.location.Location;

import com.breatheplatform.beta.shared.Constants;

/* Class: ClientPaths
 * This class contains shared variables used by watch client services
 */
public class ClientPaths {
    private static final String TAG = "ClientPaths";

//    public static Boolean writing = true;
    public static final int HEART_SENSOR_ID = Sensor.TYPE_HEART_RATE;
    public static final int SS_HEART_SENSOR_ID = 65562;

    public static int batteryLevel = Constants.NO_VALUE;
    public static Integer userAge = Constants.NO_VALUE;
    public static String connectionInfo = "Waiting";
    public static String activityDetail = "None:"+Constants.NO_VALUE;
    public static Location currentLocation = null;
    public static Context mainContext = null;
    public static String subjectId = "";

}


