package com.breatheplatform.beta.data;

import android.app.IntentService;
import android.content.Intent;
import android.hardware.Sensor;
import android.util.Log;
import android.util.SparseLongArray;

import org.json.JSONArray;

/**
 * Created by cbono on 3/2/16.
 */
public class SensorAddService extends IntentService {

    private static final String TAG = "SensorAddService";

    private static SparseLongArray lastSensorData = new SparseLongArray();
//    private static HybridEncrypter hybridEncrypter = createEncrypter();

    private static JSONArray sensorData = new JSONArray();
    private static Integer recordCount = 0;


    private static final SensorNames sensorNames = new SensorNames();

    public static String getSensorName(int id) {
        return sensorNames.getName(id);
    }

    public static final String API_KEY = "I3jmM2DI4YabH8937pRwK7MwrRWaJBgziZTBFEDTpec";//"GWTgVdeNeVwsGqQHHhChfiPgDxxgXJzLoxUD0R64Gns";

    public static final int DUST_SENSOR_ID = 999;
    public static final int SPIRO_SENSOR_ID = 998;
    public static final int ENERGY_SENSOR_ID = 997;
    public static final int REG_HEART_SENSOR_ID = 65562;
    //    public static final int SS_HEART_SENSOR_ID = 21;
    public static final int HEART_SENSOR_ID = Sensor.TYPE_HEART_RATE;
    public static final int LA_SENSOR_ID = Sensor.TYPE_LINEAR_ACCELERATION;

    private static double round5(double v) {
        return Math.round(v * 100000.0) / 100000.0;
    }


    public SensorAddService() {
        super("SensorAddService");
    }


    @Override
    protected void onHandleIntent(Intent workIntent) {
        // Gets data from the incoming Intent
        String dataString = workIntent.getDataString();

        // Do work here, based on the contents of dataString

    }

//    // END WRITE AND SEND BLOCK
//    public static void addSensorData(final int sensorType, final int accuracy, final long t, final float[] values) {
//
//        long lastTimeStamp = lastSensorData.get(sensorType);
//        long timeAgo = t - lastTimeStamp;
//        String sensorName = sensorNames.getName(sensorType);
//        if (lastTimeStamp != 0) {
//            if (timeAgo < ClientPaths.SENSOR_DELAY_CUSTOM) {
//                Log.d(TAG, "Blocked " + sensorName + " " + Arrays.toString(values) + " too soon  ");
//                return; //wait until SENSOR_DELAY_CUSTOM until next reading
//            }
//        }
//
//
//        //if accuracy rating too low, reject
//        if (accuracy < 2 && !(sensorName.equals("Linear Acceleration"))) {
//            Log.d(TAG, "Blocked " + sensorName+ " " + Arrays.toString(values) + " reading, accuracy " + accuracy + " < 2");
//            return;
//        }
//        //ClientPaths.createDataEntry(sensorType, accuracy, timestamp, values);
//
//        JSONObject jsonDataEntry = new JSONObject();
//        JSONObject jsonValue = new JSONObject();
//
//        Boolean validEvent = false;
//        //Log.d(TAG, "Received " + sensorName + " (" + sensorType + ") = " + Arrays.toString(values));
//
//        try {
//
//            switch (sensorType) {
//                case (Sensor.TYPE_LINEAR_ACCELERATION):
//
//                    jsonValue.put("x", round5(values[0]));
//                    jsonValue.put("y", round5(values[1]));
//                    jsonValue.put("z", round5(values[2]));
//                    sumX+=round5(values[0]);
//                    sumY+=round5(values[1]);
//                    sumZ+=round5(values[2]);
//                    energy += Math.pow(sumX,2) + Math.pow(sumY,2) + Math.pow(sumZ, 2);
//                    //jsonDataEntry.put("sensor_type", sensorType);
//                    validEvent = true;
//                    break;
//                case (Sensor.TYPE_HEART_RATE):
//                case (REG_HEART_SENSOR_ID):
//                case (DUST_SENSOR_ID):
//                    if (values[0]<=0) {
//                        Log.d(TAG, "Received "+sensorName+" data=0 - skip");
//                        return;
//                    }
//                    //jsonDataEntry.put("sensor_type", 21);
//                    jsonValue.put("v", values[0]);
//                    validEvent = true;
//                    break;
//                case (Sensor.TYPE_AMBIENT_TEMPERATURE):
//                    //case (Sensor.TYPE_STEP_COUNTER):
//                    jsonValue.put("v", values[0]);
//                    //jsonDataEntry.put("sensor_type", sensorType);
//                    validEvent = true;
//                    break;
//                case (SPIRO_SENSOR_ID):
//                    //add spirometer data point
////                    fev1, pef, fev1_best, pef_best, fev1_percent, pef_percent, green_zone, yellow_zone, orange_zone
//                    jsonValue.put("fev1", values[0]);
//                    jsonValue.put("pef", values[1]);
//                    jsonValue.put("goodtest", values[2]);
//
//                    validEvent = true;
//                    break;
//                case (ENERGY_SENSOR_ID):
//                    jsonValue.put("energy", values[0]);
//                    jsonValue.put("start",values[1]);
//                    validEvent = true;
//
//                    break;
//                default:
//                    break;
//            }
//
//            jsonValue.put("accuracy",accuracy);
//            jsonValue.put("battery", batteryLevel);
//
//            //jsonDataEntry.put("sensor_name", sensorName);
//            jsonDataEntry.put("value", jsonValue);
//
//            jsonDataEntry.put("last", lastTimeStamp);
//            jsonDataEntry.put("timestamp", t);//System.currentTimeMillis());
//            jsonDataEntry.put("timezone", getTimeZone());
//
//            jsonDataEntry.put("sensor_id", sensorNames.getServerID(sensorType));//will be changed to actual sensor (sensorType)
//            //jsonDataEntry.put("sensor_id",sensorType);
//
//            if (currentLocation!=null) {
//                jsonDataEntry.put("lat", currentLocation.getLatitude());
//                jsonDataEntry.put("long", currentLocation.getLongitude());
//                jsonDataEntry.put("accuracy", currentLocation.getAccuracy());
//            } else {
//                jsonDataEntry.put("lat",NO_VALUE);
//                jsonDataEntry.put("long",NO_VALUE);
//                jsonDataEntry.put("accuracy", NO_VALUE);
//            }
//
//        } catch (Exception e) {
//            Log.e(TAG, "error in creating jsonDataEntry");
//            e.printStackTrace();
//            return;
//        }
//
//        if (!validEvent) {
//            Log.d(TAG, "Encountered undesired sensor (" + sensorType + "): " + sensorName + ". skipping..");
//            return;
//        }
//
//        appendData(jsonDataEntry);
//        incrementCount();
//        Log.d(TAG, "Data Added: " + jsonDataEntry.toString());
//        lastSensorData.put(sensorType, t);
//
//        //if spirometer send immediately
//        if(sensorType==SPIRO_SENSOR_ID) {
//            Log.d(TAG, "Received spiro: " + values[1]);
//            Log.d(TAG, "Immediately sending " + jsonDataEntry.toString());
//            createDataPostRequest();
//        }
//    }

    //Mobile COMM Method

    public static boolean sendDataToMobile(String d, String urlString) {
//        client.
        Log.d(TAG, "Called sendDataToMobile with " + urlString);

        return true;
    }
}
