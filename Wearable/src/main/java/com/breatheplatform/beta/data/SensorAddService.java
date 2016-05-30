package com.breatheplatform.beta.data;

import android.app.IntentService;
import android.content.Intent;
import android.hardware.Sensor;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.SparseLongArray;

import com.breatheplatform.beta.ClientPaths;
import com.breatheplatform.beta.shared.Constants;

import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import me.denley.courier.Courier;

/**
 * Created by cbono on 3/2/16.
 */
public class SensorAddService extends IntentService {
    private static final String TAG = "SensorAddService";

    private static final SensorNames sensorNames = new SensorNames();
    private static final Integer RECORD_LIMIT = 100;//200;
//    private static SparseLongArray lastSensorData = initLastData();

    private static SparseLongArray initLastData() {
        SparseLongArray temp = new SparseLongArray();
        long last = System.currentTimeMillis();
        temp.put(Constants.ENERGY_SENSOR_ID, last);
        return temp;
    }

    private static StringBuilder sensorData = new StringBuilder();

    public static void clearData() {
        sensorData.setLength(0);
        recordCount = 0;
    }

    private static Integer recordCount = 0;



    private static String tz = initTimeZone();
    private static String initTimeZone() {
        Date now = new Date();
        TimeZone tz = Calendar.getInstance().getTimeZone();
        return tz.getDisplayName(tz.inDaylightTime(now), TimeZone.SHORT);
    }

    public SensorAddService() {
        super("SensorAddService");
    }


    private static JSONObject jsonDataEntry = new JSONObject();

    //intents are queued - no synchronized needed
    public void incrementCount() {
        recordCount++;
        if (recordCount >= RECORD_LIMIT) {
            createDataPostRequest();
            clearData();
        } else {
            sensorData.append("\n"); //else add a newline to sensorData
        }
    }

    private static Float precision(Float d, int decimalPlace) {
        BigDecimal bd = new BigDecimal(Float.toString(d));
        bd = bd.setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
        return bd.floatValue();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // Gets data from the incoming Intent
        long currentTime = intent.getLongExtra("time", Constants.NO_VALUE);
        float[] values = intent.getFloatArrayExtra("values");
        int accuracy = intent.getIntExtra("accuracy", Constants.NO_VALUE);
        int sensorType = intent.getIntExtra("sensorType", Constants.NO_VALUE);


        if (sensorType == Constants.TERMINATE_SENSOR_ID) {
            createDataPostRequest();
            clearData();
            return;
        }
        String sensorName = sensorNames.getName(sensorType);

        //update UI with new Sensor Name value
        Intent i = new Intent(Constants.LAST_SENSOR_EVENT);
        i.putExtra("sensorName", sensorName);
//        i.putExtra("value",values[0]);
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);

        JSONObject jsonValue = new JSONObject();

        try {
            switch (sensorType) {
                case (Sensor.TYPE_LINEAR_ACCELERATION): //units m/s^2
                case (Sensor.TYPE_GYROSCOPE): //units rad/s

                    jsonValue.put("x", values[0]);
                    jsonValue.put("y", values[1]);
                    jsonValue.put("z", values[2]);
                    jsonValue.put("sensor_accuracy",accuracy);
                    break;
                case (Sensor.TYPE_HEART_RATE):
                    jsonValue.put("sensor_accuracy",accuracy);
                case (Constants.DUST_SENSOR_ID):
                    if (values[0]<=0) {
                        Log.d(TAG, "Received " + sensorName + " data <= 0 -> skip");
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

                case (Constants.AIRBEAM_SENSOR_ID):

                    jsonValue.put("PM",precision(values[0],2));
                    jsonValue.put("F",values[1]);
                    jsonValue.put("RH",values[2]);
                    break;
                case (Constants.ACTIVITY_SENSOR_ID):
                    jsonValue.put("type", values[0]);
                    jsonValue.put("confidence",accuracy);
                    break;
                default:
                    Log.e(TAG, "Unexpected Sensor " + sensorName + " " + sensorType);
                    return;
            }

            jsonDataEntry.put("value", jsonValue);
            jsonDataEntry.put("timestamp", currentTime);//System.currentTimeMillis());
            jsonDataEntry.put("timezone", tz);
            jsonDataEntry.put("sensor_id", sensorNames.getServerID(sensorType));//will be changed to actual sensor (sensorType)

            //check if the location is currently available
            if (ClientPaths.currentLocation!=null) {
                jsonDataEntry.put("lat", ClientPaths.currentLocation.getLatitude());
                jsonDataEntry.put("lon", ClientPaths.currentLocation.getLongitude());
                jsonDataEntry.put("location_accuracy", ClientPaths.currentLocation.getAccuracy());
            } else {
                jsonDataEntry.put("lat",Constants.NO_VALUE);
                jsonDataEntry.put("lon",Constants.NO_VALUE);
                jsonDataEntry.put("location_accuracy", Constants.NO_VALUE);
            }

        } catch (Exception e) {
            Log.e(TAG, "error in creating jsonDataEntry");
            e.printStackTrace();
            return;
        }

        String dataEntry = jsonDataEntry.toString();

        //sensorData is a stringBuilder
        sensorData.append(dataEntry);
        incrementCount();

        Log.d(TAG, "Data Added #"+ recordCount + ": " + dataEntry);

        //if spirometer send immediately
        if(sensorType==Constants.SPIRO_SENSOR_ID) {
            Log.d(TAG, "Received spiro: " + values[1]);
            Log.d(TAG, "Immediately sending " + dataEntry);
            createDataPostRequest();
            clearData();
        }

    }


    // Post request forwarding logic (to mobile)

    private void createDataPostRequest() {
        Log.d(TAG, "createDataPostRequest");
        JSONObject jsonBody = new JSONObject();

        try {
            // \n becomes the delimiter on the server to split data entries
//            String sensorDataString = sensorData.join("\n");
            String sensorDataString = sensorData.toString();

            if (ClientPaths.subject.equals("")) {
                Log.e(TAG, "No Subject detected - blocking multi post");
                return;
            }

            jsonBody.put("timestamp",System.currentTimeMillis());
            jsonBody.put("subject_id", ClientPaths.subject);
            jsonBody.put("key", Constants.API_KEY);
            jsonBody.put("battery",ClientPaths.batteryLevel);
            jsonBody.put("testing",Constants.TESTING);
//            jsonBody.put("connection", ClientPaths.connectionInfo);

            jsonBody.put("data", sensorDataString);

            String data = jsonBody.toString();// + "^^" + sensorDataString;


            Courier.deliverData(this, Constants.MULTI_API, data);
            Log.d(TAG, "Courier sent multiapi data " + data.length());


        } catch (Exception e) {
            Log.e(TAG, "[Handled] Error requesting multi post request");
            e.printStackTrace();

        }
    }
}
