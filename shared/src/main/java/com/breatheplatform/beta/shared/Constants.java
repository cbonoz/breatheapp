package com.breatheplatform.beta.shared;

/**
 * Created by cbono on 4/1/16.
 * Constants file used to keep shared parameters used by Watch and mobile
 */
public class Constants {
    private static final String TAG = "Constants";
    public static final String MY_PREFS_NAME = "MyPrefs";

    //secure https
    public static final String BASE = "https://www.breatheplatform.com";
    public static final String API_KEY = "I3jmM2DI4YabH8937pRwK7MwrRWaJBgziZTBFEDTpec";

    //API routes used for watch to mobile
    public static final String REGISTERED_API = "/registered";
    public static final String QUESTION_API = "/question";
    public static final String REMINDER_API = "/reminder";
    public static final String CALENDAR_API = "/calendar";
    public static final String LABEL_API = "/label";
    public static final String FILE_API = "/file";


    //API routes used by server
    public static final String REG_CHECK_API = "/api/reg/check";
    public static final String SUBJECT_API = "/api/subject/add";
    public static final String MULTI_API = "/api/multisensor/add";
    public static final String RISK_API = "/api/risk/get";

    //SENSOR ID's used for internal encoding (get mapped to server sensor ID's)
    public static final int DUST_SENSOR_ID = 999;
    public static final int SPIRO_SENSOR_ID = 998;
    public static final int ENERGY_SENSOR_ID = 997;
    public static final int AIRBEAM_SENSOR_ID = 995;
    public static final int ACTIVITY_SENSOR_ID = 996;
    public static final int TERMINATE_SENSOR_ID = 555;

    public static final int ONE_SEC_IN_MICRO = 1000000;
    public static final Integer ONE_MIN_MS = 60000;
    public static final int REQUEST_GOOGLE_PLAY_SERVICES = 1972;

    //BT Name of dust sensors
    public static final String DUST_BT_NAME = "HaikRF";
    public static final String AIRBEAM_BT_NAME = "AirBeam";

    //for broadcast receivers
    public static final String HEART_EVENT = "heart-event";
    public static final String LAST_SENSOR_EVENT = "last-sensor-event";
    public static final String REGISTER_EVENT = "register-event";

    public static final int NO_VALUE = -1;


    //Alarm Codes
    public static final int SPIRO_ALARM_ID = 1;
    public static final int QUESTION_ALARM_ID = 2;
    public static final int CLOSE_SPIRO_ALARM_ID = 4;
    public static final int STOP_ALARM_ID = 5;
    public static final int START_ALARM_ID = 6;

    //Sensor Period on Watch
    public static final Integer SENSOR_ON_TIME = Constants.ONE_MIN_MS/6;// / 8; //7.5s


    //Constants used for debugging
    public static final Boolean collecting = false;
    public static final Boolean encrypting = true;
    public static final Integer TESTING = 0;
//    public static final Boolean staticApp = false;
    public static final String START_WRITE = "start";
    public static final String END_WRITE = "end";

}
