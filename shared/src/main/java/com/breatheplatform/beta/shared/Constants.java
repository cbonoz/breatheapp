package com.breatheplatform.beta.shared;

import android.hardware.Sensor;

/**
 * Created by cbono on 4/1/16.
 */
public class Constants {
    private static final String TAG = "Constants";

    public static final String BASE = "http://www.breatheplatform.com";

//    public static final String ACTIVITY_API = "/activity";
    public static final String QUESTION_API = "/question";
    public static final String REGISTER_API = "/register";
    public static final String SUBJECT_API = "/api/subject/add";
    public static final String MULTI_API = "/api/multisensor/add";
    public static final String RISK_API = "/api/risk/get";

    public static final String CALENDAR_API = "/calendar";
    public static final String LABEL_API = "/label";
    public static final String FILE_API = "/file";

    public static final int DUST_SENSOR_ID = 999;
    public static final int SPIRO_SENSOR_ID = 998;
    public static final int ENERGY_SENSOR_ID = 997;
    public static final int ACTIVITY_SENSOR_ID = 996;
    public static final int TERMINATE_SENSOR_ID = 555;

    public static final int REG_HEART_SENSOR_ID = 65562;
    //    public static final int SS_HEART_SENSOR_ID = 21;
    public static final int HEART_SENSOR_ID = Sensor.TYPE_HEART_RATE;
    public static final int LA_SENSOR_ID = Sensor.TYPE_LINEAR_ACCELERATION;


    public static final int SENSOR_DELAY_CUSTOM = 1000; //ms
    public static final int ONE_SEC_IN_MICRO = 1000000;
    public static final String DUST_BT_NAME = "HaikRF";


    //for broadcast receivers
    public static final String HEART_EVENT = "heart-event";
    public static final String LAST_SENSOR_EVENT = "last-sensor-event";

    public static final int NO_VALUE = -1;

    public static final String START_WRITE = "start";
    public static final String END_WRITE = "end";

    public static final Boolean collecting = false;
    public static final Boolean encrypting = false;


    public static final Boolean slowSensorRate = true;
    public static final Boolean staticApp = true;

    public static final String BROADCAST_ACTION = ".BROADCAST_ACTION";

}
