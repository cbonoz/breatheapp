package com.breatheplatform.beta.shared;

/**
 * Created by cbono on 4/1/16.
 * Constants file used to keep shared parameters used by Watch and mobile
 */
public class Constants {

    //string to identify the preferences object
    public static final String MY_PREFS_NAME = "MyPrefs";

    //secure https
    public static final String BASE = "https://www.breatheplatform.com";
    public static final String API_KEY = "I3jmM2DI4YabH8937pRwK7MwrRWaJBgziZTBFEDTpec";

    //API routes used for watch to mobile

    public static final String REGISTERED_API = "/registered";
    public static final String QUESTION_API = "/question";
    public static final String REMINDER_API = "/reminder";
    public static final String CALENDAR_API = "/calendar";
    public static final String WEB_STATUS_API = "/web";



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

//    public static final int ONE_SEC_IN_MICRO = 1000000;
    public static final Integer ONE_MIN_MS = 60000;
    public static final int REQUEST_GOOGLE_PLAY_SERVICES = 1972;

    //BT Name of dust sensors
    public static final String DUST_BT_NAME = "HaikRF";
    public static final String AIRBEAM_BT_NAME = "AirBeam";

    //for broadcast receivers
    public static final String HEART_EVENT = "heart-event";
    public static final String LAST_SENSOR_EVENT = "last-sensor-event";
    public static final String REGISTER_EVENT = "register-event";
    public static final String WEAR_ALARM_ACTION = "start-wear-action";
    public static final String MOBILE_ALARM_ACTION = "start-mobile-action";

    //default value for underdetermined values (such as when location is not available)
    public static final int NO_VALUE = -1;

    //Alarm Codes
    public static final int START_ALARM_ID = 0;
    public static final int SPIRO_ALARM_ID = 1;
    public static final int QUESTION_ALARM_ID = 2;
    public static final int CLOSE_SPIRO_ALARM_ID = 3;
    public static final int ALARM_CODE_START = 4;
    public static final int TRIGGER_ALARM_ID = 5;
//    public static final int START_BLUETOOTH_ID = 6;

    //Constants specifying Sensor Duty Cycle constants used on Watch
    public static final Integer SENSOR_ON_TIME = 10000; // seconds on time
    public static final Integer SENSOR_OFF_TIME = 35000; // second off time

    //fixedSensorRate is a mode for the watch sensor scheduling, if fixed, watch will alternate between SENSOR_ON_TIME and SENSOR_OFF_TIME
    //with the sensors on during SENSOR_ON_TIME. A post request of the collected data will be triggered
    //once sensor off time starts. If this is false, then we have user activity-based sensor scheduling (in
    //which the scheduling rate for the sensors is not fixed, but triggered on motion).
    public static final Boolean fixedSensorRate = true;

    //minimum interval after sensors go low (ms) for movement trigger to be turned on
    public static final Integer TRIGGER_DELAY = 15000;

    //send encrypted or unencrypted data to the server?
    public static final Boolean encrypting = true;

    //field used by server to indicate of the data should be added or not
    public static final Integer TESTING = 0;

    //Record Count limit (used in SensorAddService). Threshold value such that once we have RC_LIMIT sensor data points
    // trigger a post request of that data
    public static final Integer RC_LIMIT = 100;


}
