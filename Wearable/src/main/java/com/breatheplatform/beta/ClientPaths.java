package com.breatheplatform.beta;

import android.content.Context;
import android.location.Location;

import com.breatheplatform.beta.shared.Constants;

/* Class: ClientPaths
 * This class contains shared variables used by watch client services
 */
public class ClientPaths {
    private static final String TAG = "ClientPaths";

//    public static Boolean writing = true;

    public static int batteryLevel = Constants.NO_VALUE;
    public static Integer userAge = Constants.NO_VALUE;
    public static String connectionInfo = "Waiting";
    public static String activityDetail = "None:"+Constants.NO_VALUE;
    public static Location currentLocation = null;
    public static Context mainContext = null;
    public static String subjectId = "";
    
}


