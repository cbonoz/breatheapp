package com.breatheplatform.beta.data;

import android.app.IntentService;
import android.content.Intent;
import android.hardware.Sensor;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.SparseLongArray;

import com.breatheplatform.beta.ClientPaths;
import com.breatheplatform.beta.shared.Constants;
import com.breatheplatform.beta.shared.PostData;

import org.json.JSONObject;

import java.util.TimeZone;

import me.denley.courier.Courier;

/**
 * Created by cbono on 3/2/16.
 */
public class SensorAddService extends IntentService {
    private static final String TAG = "SensorAddService";

    private static final SensorNames sensorNames = new SensorNames();
    private static SparseLongArray lastSensorData = initLastData();

    private static final int ENERGY_LIMIT = 10;

    //for energy measurements
    private static float sumX =0, sumY = 0, sumZ = 0;
    private static float energy = 0;

    private static SparseLongArray initLastData() {
        SparseLongArray temp = new SparseLongArray();
        long last = System.currentTimeMillis();
        temp.put(Constants.ENERGY_SENSOR_ID, last);
        return temp;
    }

//    private static JSONArray sensorData = new JSONArray();
    private static StringBuilder sensorData = new StringBuilder();

    private static Integer recordCount = 0;

    private static Integer RECORD_LIMIT = 50;

    private static String tz = initTimeZone();
    private static String initTimeZone() {
        TimeZone tz = TimeZone.getDefault();

        String tzone;
        try {
            tzone = tz.getDisplayName();
        } catch (Exception e) {
            Log.e(TAG, "[Handled] could not get time zone");
            tzone = "US - Default";
        }
        return tzone;
    }

    public SensorAddService() {
        super("SensorAddService");
    }

//    public static void appendData(JSONObject jObj) {
//        sensorData.put(jObj);
//    }

    public static void clearData() {
        sensorData.setLength(0);
        recordCount = 0;
    }

    //is synchronized necessary here?
    public void incrementCount() {
        recordCount++;
        if (recordCount >= RECORD_LIMIT) {
            createDataPostRequest();
            clearData();
        } else {
            sensorData.append("\n"); //else add a newline to sensorData
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // Gets data from the incoming Intent
        long t = intent.getLongExtra("time", Constants.NO_VALUE);
        float[] values = intent.getFloatArrayExtra("values");
        int acc = intent.getIntExtra("accuracy", Constants.NO_VALUE);
        int sType = intent.getIntExtra("sensorType",Constants.NO_VALUE);


        if (sType == Constants.TERMINATE_SENSOR_ID) {
            createDataPostRequest();
            clearData();
            return;
        }



        processSensorData(sType, acc,t,values);


    }

    public static PostData pd = new PostData();



    private static float x,y,z;
    private static int energyCount = 0;

    // END WRITE AND SEND BLOCK
    private void processSensorData(final int sensorType, final int accuracy, final long currentTime, final float[] values) {

        long lastTimeStamp = lastSensorData.get(sensorType);
        long timeAgo = currentTime - lastTimeStamp;
        String sensorName = sensorNames.getName(sensorType);

        //update UI with new Sensor Name value
        Intent i = new Intent(Constants.LAST_SENSOR_EVENT);
        i.putExtra("sensorName", sensorName);
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);

        //if accuracy rating too low, reject
//        if (accuracy < 2 && (sensorName.contains("Heart"))) {
//            Log.d(TAG, "Blocked " + sensorName + "(" + sensorType + ")" + Arrays.toString(values) + " reading, accuracy " + accuracy + " < 2");
//            return;
//        }

        //ActivityConstants.createDataEntry(sensorType, accuracy, timestamp, values);
        JSONObject jsonDataEntry = new JSONObject();
        JSONObject jsonValue = new JSONObject();


        Boolean validEvent = true;


        //Log.d(TAG, "Received " + sensorName + " (" + sensorType + ") = " + Arrays.toString(values));

        try {

            switch (sensorType) {
                case (Sensor.TYPE_LINEAR_ACCELERATION): //units m/s^2
                case (Sensor.TYPE_GYROSCOPE): //units rad/s
                    x=values[0];
                    y=values[1];
                    z=values[2];
                    jsonValue.put("x", x);
                    jsonValue.put("y", y);
                    jsonValue.put("z", z);
                    break;
                case (Sensor.TYPE_HEART_RATE):
//                case (REG_HEART_SENSOR_ID):
                    if (values[0]<=0) {
                        Log.d(TAG, "Received heart data<=0 - skip");
                        return;
                    }
                    jsonValue.put("v", values[0]);
                    break;
                case (Constants.DUST_SENSOR_ID):
                    if (values[0]<=0) {
                        Log.d(TAG, "Received dust data<=0 - skip");
                        return;
                    }
                    jsonValue.put("v", values[0]);
                    break;
                case (Constants.SPIRO_SENSOR_ID):
                    jsonValue.put("fev1", values[0]);
                    jsonValue.put("pef", values[1]);
                    jsonValue.put("goodtest", values[2]);
                    break;
                case (Constants.ENERGY_SENSOR_ID):
                    jsonValue.put("energy", values[0]);
                    break;
                case (Constants.ACTIVITY_SENSOR_ID):
                    jsonValue.put("type", values[0]);
                    break;
                case (Sensor.TYPE_AMBIENT_TEMPERATURE):
                    //case (Sensor.TYPE_STEP_COUNTER):
                    jsonValue.put("temp", values[0]);
                    break;
                default:
                    validEvent = false;
                    break;
            }

//            if (lastTimeStamp != 0) {
//                if (timeAgo < ClientPaths.SENSOR_DELAY_CUSTOM) {
//                    Log.d(TAG, "Blocked " + sensorName + " " + Arrays.toString(values) + " too soon ");
//                    return; //wait until SENSOR_DELAY_CUSTOM until next reading
//                }
//            }

            jsonValue.put("sensor_accuracy",accuracy);
            jsonDataEntry.put("value", jsonValue);

//            jsonDataEntry.put("last", lastTimeStamp);
            jsonDataEntry.put("timestamp", currentTime);//System.currentTimeMillis());
            jsonDataEntry.put("timezone", tz);

            jsonDataEntry.put("sensor_id", sensorNames.getServerID(sensorType));//will be changed to actual sensor (sensorType)

            //check if the location is currently available
            if (ClientPaths.currentLocation!=null) {
                jsonDataEntry.put("lat", ClientPaths.currentLocation.getLatitude());
                jsonDataEntry.put("long", ClientPaths.currentLocation.getLongitude());
                jsonDataEntry.put("location_accuracy", ClientPaths.currentLocation.getAccuracy());
            } else {
                jsonDataEntry.put("lat",Constants.NO_VALUE);
                jsonDataEntry.put("long",Constants.NO_VALUE);
                jsonDataEntry.put("location_accuracy", Constants.NO_VALUE);
            }

        } catch (Exception e) {
            Log.e(TAG, "error in creating jsonDataEntry");
            e.printStackTrace();
            return;
        }

        if (!validEvent) {
            Log.d(TAG, "Encountered undesired sensor (" + sensorType + "): " + sensorName + ". skipping..");
            return;
        }

//        appendData(jsonDataEntry);
        String dataEntry = jsonDataEntry.toString();
        sensorData.append(dataEntry);

        incrementCount();
        lastSensorData.put(sensorType, currentTime);

        Log.d(TAG, "Data Added #"+ recordCount + ": " + dataEntry);

        //if spirometer send immediately
        if(sensorType==Constants.SPIRO_SENSOR_ID) {
            Log.d(TAG, "Received spiro: " + values[1]);
            Log.d(TAG, "Immediately sending " + dataEntry);
            createDataPostRequest();
            clearData();
        }
        else if (sensorType==Constants.LA_SENSOR_ID) {
            energyCount++;
            sumX += x;
            sumY += y;
            sumZ += z;
            if (energyCount==ENERGY_LIMIT) {
                energy+=Math.pow(sumX,2) + Math.pow(sumY,2) + Math.pow(sumZ,2);
                processSensorData(Constants.ENERGY_SENSOR_ID, Constants.NO_VALUE, currentTime, new float[]{energy});
                sumX = 0;
                sumY = 0;
                sumZ = 0;
                energy = 0;
                energyCount=0;
            }
        }
    }

    private void createDataPostRequest() {
        Log.d(TAG, "createDataPostRequest");
        JSONObject jsonBody = new JSONObject();

        try {
            // \n becomes the delimiter on the server to split data entries
//            String sensorDataString = sensorData.join("\n");
            String sensorDataString = sensorData.toString();

            if (ClientPaths.subjectId == null) {
                Log.d(TAG, "No Subject detected - blocking multi post");
            }

            jsonBody.put("timestamp",System.currentTimeMillis());
            jsonBody.put("subject_id", ClientPaths.subjectId);
            jsonBody.put("key", Constants.API_KEY);
            jsonBody.put("battery",ClientPaths.batteryLevel);
            jsonBody.put("connection", ClientPaths.connectionInfo);

//            MultiData multiData = new MultiData();
//            multiData.timestamp = System.currentTimeMillis();
//            multiData.subject_id = ClientPaths.subjectId;
//            multiData.key = ClientPaths.API_KEY;
//            multiData.battery = ClientPaths.batteryLevel;
//            multiData.connection = ClientPaths.connectionInfo;
//            multiData.data = sensorDataString;

            jsonBody.put("data", sensorDataString);


            String pdString = jsonBody.toString();// + "^^" + sensorDataString;



            Courier.deliverData(ClientPaths.mainContext, Constants.MULTI_API, pdString);
            Log.d(TAG, "courier sent multiapi data");

        } catch (Exception e) {
            Log.e(TAG, "[Handled] Error requesting multi post request");
            e.printStackTrace();

        }
    }
}
