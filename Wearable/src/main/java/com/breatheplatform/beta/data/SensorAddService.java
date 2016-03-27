package com.breatheplatform.beta.data;

import android.app.IntentService;
import android.content.Intent;
import android.hardware.Sensor;
import android.util.Log;
import android.util.SparseLongArray;

import com.breatheplatform.beta.ClientPaths;
import com.breatheplatform.beta.messaging.DeviceClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.TimeZone;

import me.denley.courier.Courier;

/**
 * Created by cbono on 3/2/16.
 */
public class SensorAddService extends IntentService {

    private static final String TAG = "SensorAddService";

    private static final SensorNames sensorNames = new SensorNames();

//    private static final HybridEncrypter hybridEncypter = ClientPaths.createEncrypter();

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

    private static final int ENERGY_LIMIT = 10;

    private static Boolean encrypting = true;
    private static Boolean sending = true;
    private static Boolean writing = true;

    //for energy measurements
    private static float sumX =0, sumY = 0, sumZ = 0;

    private static float energy = 0;

    //    private static int bytesWritten = 0;
    private static long dataSent = 0;

    private static SparseLongArray lastSensorData = initLastData();

    private static SparseLongArray initLastData() {
        SparseLongArray temp = new SparseLongArray();
        long last = System.currentTimeMillis();
        temp.put(ENERGY_SENSOR_ID, last);
        return temp;
    }


    private static JSONArray sensorData = new JSONArray();
    private static Integer recordCount = 0;

    private static int RECORD_LIMIT = 10;

    private static String urlString = ClientPaths.BASE + ClientPaths.MULTI_API;
    private static URL url = createURL();

    private static URL createURL() {
        try {
            return new URL(urlString);// URL(MULTI_API);
        } catch (Exception e) {
            Log.d(TAG, "Error creating URL");
            e.printStackTrace();
            return null;
        }
    }

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

    private DeviceClient client=null;


//    private static double round5(double v) {
//        return Math.round(v * 100000.0) / 100000.0;
//    }
//

    public SensorAddService() {
        super("SensorAddService");
        if (ClientPaths.mainContext!=null)
            client = DeviceClient.getInstance(ClientPaths.mainContext);
        else
            client = DeviceClient.getInstance(this);
    }

    public static void appendData(JSONObject jObj) {
        sensorData.put(jObj);
    }

    public static void clearData() {
        sensorData = new JSONArray();
        recordCount = 0;
    }

    //is synchronized necessary here?
    public void incrementCount() {
        recordCount++;
        Log.d(TAG, "recordCount: " + recordCount);
//        if (recordCount.equals(RECORD_LIMIT)) {
        if (recordCount % RECORD_LIMIT == 0 && recordCount > 0) {
            if (sending)
                createDataPostRequest();
//            clearData();
        }
    }



    @Override
    protected void onHandleIntent(Intent intent) {
        // Gets data from the incoming Intent
        long t = intent.getLongExtra("time", ClientPaths.NO_VALUE);
        float[] values = intent.getFloatArrayExtra("values");
        int acc = intent.getIntExtra("accuracy", ClientPaths.NO_VALUE);
        int sType = intent.getIntExtra("sensorType",ClientPaths.NO_VALUE);
        addSensorData(sType, acc,t,values);

        // Do work here, based on the contents of dataString

    }

    private static float x,y,z;
    private static int energyCount = 0;

    // END WRITE AND SEND BLOCK
    private void addSensorData(final int sensorType, final int accuracy, final long currentTime, final float[] values) {

        long lastTimeStamp = lastSensorData.get(sensorType);
        long timeAgo = currentTime - lastTimeStamp;
        String sensorName = sensorNames.getName(sensorType);

        //if accuracy rating too low, reject
//        if (accuracy < 2 && (sensorName.contains("Heart"))) {
//            Log.d(TAG, "Blocked " + sensorName + "(" + sensorType + ")" + Arrays.toString(values) + " reading, accuracy " + accuracy + " < 2");
//            return;
//        }

        //ClientPaths.createDataEntry(sensorType, accuracy, timestamp, values);
        JSONObject jsonDataEntry = new JSONObject();
        JSONObject jsonValue = new JSONObject();

        Boolean validEvent = false;
        //Log.d(TAG, "Received " + sensorName + " (" + sensorType + ") = " + Arrays.toString(values));

        try {

            switch (sensorType) {
                case (Sensor.TYPE_LINEAR_ACCELERATION):
                    x=values[0];
                    y=values[1];
                    z=values[2];
                    jsonValue.put("x", x);
                    jsonValue.put("y", y);
                    jsonValue.put("z", z);
                    validEvent = true;
                    break;
                case (Sensor.TYPE_HEART_RATE):
                case (REG_HEART_SENSOR_ID):
                    if (values[0]<=0) {
                        Log.d(TAG, "Received heart data<=0 - skip");
                        return;
                    }
                    jsonValue.put("v", values[0]);
                    validEvent = true;
                    break;
                case (DUST_SENSOR_ID):
                    if (values[0]<=0) {
                        Log.d(TAG, "Received dust data<=0 - skip");
                        return;
                    }
                    jsonValue.put("v", values[0]);
                    validEvent = true;
                    break;
                //                    fev1, pef, fev1_best, pef_best, fev1_percent, pef_percent, green_zone, yellow_zone, orange_zone
                case (SPIRO_SENSOR_ID):
                    jsonValue.put("fev1", values[0]);
                    jsonValue.put("pef", values[1]);
                    jsonValue.put("goodtest", values[2]);
                    validEvent = true;
                    break;
                case (ENERGY_SENSOR_ID):
                    jsonValue.put("energy", values[0]);
                    jsonValue.put("activity", ClientPaths.activityName);
                    jsonValue.put("activity_confidence", ClientPaths.activityConfidence);
                    validEvent = true;

                    break;
                case (Sensor.TYPE_AMBIENT_TEMPERATURE):
                    //case (Sensor.TYPE_STEP_COUNTER):
                    jsonValue.put("v", values[0]);
                    validEvent = true;
                    break;
                default:
                    break;
            }

            if (lastTimeStamp != 0) {
                if (timeAgo < ClientPaths.SENSOR_DELAY_CUSTOM) {
                    Log.d(TAG, "Blocked " + sensorName + " " + Arrays.toString(values) + " too soon ");
                    return; //wait until SENSOR_DELAY_CUSTOM until next reading
                }
            }

            jsonValue.put("sensor_accuracy",accuracy);

            jsonDataEntry.put("value", jsonValue);

            jsonDataEntry.put("last", lastTimeStamp);
            jsonDataEntry.put("timestamp", currentTime);//System.currentTimeMillis());
            jsonDataEntry.put("timezone", tz);

            jsonDataEntry.put("sensor_id", sensorNames.getServerID(sensorType));//will be changed to actual sensor (sensorType)

            if (ClientPaths.currentLocation!=null) {
                jsonDataEntry.put("lat", ClientPaths.currentLocation.getLatitude());
                jsonDataEntry.put("long", ClientPaths.currentLocation.getLongitude());
                jsonDataEntry.put("location_accuracy", ClientPaths.currentLocation.getAccuracy());
            } else {
                jsonDataEntry.put("lat",ClientPaths.NO_VALUE);
                jsonDataEntry.put("long",ClientPaths.NO_VALUE);
                jsonDataEntry.put("location_accuracy", ClientPaths.NO_VALUE);
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

        appendData(jsonDataEntry);
        incrementCount();
        lastSensorData.put(sensorType, currentTime);

        Log.d(TAG, "Data Added: " + jsonDataEntry.toString());

        //if spirometer send immediately
        if(sensorType==SPIRO_SENSOR_ID) {
            Log.d(TAG, "Received spiro: " + values[1]);
            Log.d(TAG, "Immediately sending " + jsonDataEntry.toString());
            createDataPostRequest();
        }
        else if (sensorType==LA_SENSOR_ID) {
            energyCount++;
            sumX += x;
            sumY += y;
            sumZ += z;
            if (energyCount==ENERGY_LIMIT) {
                energy+=Math.pow(sumX,2) + Math.pow(sumY,2) + Math.pow(sumZ,2);
                addSensorData(ClientPaths.ENERGY_SENSOR_ID, 3, currentTime, new float[]{energy});
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
            String sensorDataString = sensorData.join("\n");
            byte[] sensorDataBytes = null;


            if (ClientPaths.SUBJECT_ID == ClientPaths.NO_VALUE)
                ClientPaths.SUBJECT_ID = ClientPaths.getSubjectID();

            jsonBody.put("timestamp",System.currentTimeMillis());
            jsonBody.put("subject_id", ClientPaths.SUBJECT_ID);
            jsonBody.put("key", ClientPaths.API_KEY);
            jsonBody.put("battery",ClientPaths.batteryLevel);
            jsonBody.put("connection", ClientPaths.connectionInfo);

            if (encrypting) {
                sensorDataBytes = ClientPaths.encString(sensorDataString);
                jsonBody.put("data", sensorDataBytes);// String(sensorDataBytes, "ISO-8859-1"));
                jsonBody.put("data_key", ClientPaths.getSymKey());//new String(ClientPaths.getSymKey(), "ISO-8859-1"));
                Log.d(TAG, "Sym key: " + jsonBody.get("data_key"));
                Log.d(TAG, ClientPaths.decString(sensorDataBytes));
            } else {
                jsonBody.put("data", sensorDataString);
            }

            String jsonString = jsonBody.toString();

            byte[] data = jsonString.getBytes("ISO-8859-1");

            if (writing) {
                Log.d(TAG, "jsonBody length: " + jsonString.length());
                Log.d(TAG, "bytes length: " + data.length);


                FileOutputStream fos = new FileOutputStream(ClientPaths.sensorFile);
//                fos.write(data);
                fos.write(data); //write encrypted data
                fos.close();


//                fos = new FileOutputStream(ClientPaths.aesKeyFile);
////                fos.write(data);
//                fos.write(ClientPaths.getSymKey()); //write aes key
//                fos.close();



                writing = false;
            }

            //start post

            if (true) { //send post body to mobile for forwarding to server
//            if (ClientPaths.connectionInfo.equals("PROXY")) {
//                Log.d(TAG, "data len1: " + data.length);
//                Intent i = new Intent("upload-data");
//                // You can also include some extra data.
//                i.putExtra("data", data);
//                LocalBroadcastManager.getInstance(this).sendBroadcast(i);
                Log.d(TAG, "multi post: " + jsonString);
//                if (client!=null)
//                    client.sendPostRequest(jsonString, ClientPaths.MULTI_API);
//                else {
//                    Log.e(TAG, "postcancel - client is null (multi)");
//                }
//                clearData();
//                return;

                Courier.deliverMessage(ClientPaths.mainContext, ClientPaths.MULTI_API, jsonString);
                Log.d(TAG, "courier sent multiapi data");
                clearData();
                return;

            }


            int statusCode = 0;
            InputStream is=null;
            OutputStream os;
            HttpURLConnection conn=null;

            String result = null;


            Log.i(url.toString(), "NOW Sending: " + data);

            try {
                String currentNetwork = ClientPaths.connectionInfo;
                Log.d(TAG, "Connection: " + currentNetwork);

                conn = (HttpURLConnection) url.openConnection();



                long dataLength = data.length;
                conn.setReadTimeout(10000 /*milliseconds*/);
                conn.setConnectTimeout(15000 /* milliseconds */);
                conn.setRequestMethod("POST");
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(dataLength);
                conn.setRequestProperty("connection", "close"); // disables Keep Alive
                //conn.setChunkedStreamingMode(0);

                //make some HTTP header nicety
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");

                conn.connect();

                os = new BufferedOutputStream(conn.getOutputStream());

                os.write(data);
                dataSent += dataLength;
                os.flush();
                os.close();

                statusCode = conn.getResponseCode();

                StringBuffer sb = new StringBuffer();

                try {
                    is = new BufferedInputStream(conn.getInputStream());
                    BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    String inputLine = "";
                    while ((inputLine = br.readLine()) != null) {
                        sb.append(inputLine);
                    }
                    result = sb.toString();
                }    catch (Exception e) {
                    e.printStackTrace();
                    Log.i(TAG, "Error reading InputStream");
                    result = null;
                }
                finally {
                    if (is != null) {
                        try {
                            is.close();
                        }
                        catch (Exception e) {
                            Log.i(TAG, "Error closing InputStream");
                            e.printStackTrace();
                        }
                    }
                }


                Log.i(TAG, "Response: " + result);
                Log.i(TAG, "From " + urlString);

                if (result!=null) {
                    if (result.contains("done")) {
                        Log.d(TAG, "Successful data post");
                        //clear saved data files
                        //ClientPaths.writeDataToFile("", ClientPaths.sensorFile, false); //clears file
                        //ClientPaths.writeDataToFile("", ClientPaths.encSensorFile, false); //clears enc sensorfile
                    }
                }


            } catch (Exception e) {
                Log.e(TAG, "[Handled] Returning from SensorAddService - Could not Connect to Internet (timeout)");
                e.printStackTrace();

            } finally {
                if (conn != null)
                    conn.disconnect();
                //clear sensorDataFile if needed
//                if (ClientPaths.sensorFile.length()>0) {
//                    Log.d(TAG, "clearing sensorDataFile");
//                    ClientPaths.writeDataToFile("", ClientPaths.sensorFile, false);
//                }

                clearData();
                Log.d(TAG, "dataSent: " + dataSent/1000 + "kB");
            }

//            client.sendPostRequest(data, ClientPaths.MULTI_CASE);

            //data is the only object that needs to be encrypted


        } catch (Exception e) {
            Log.e(TAG, "[Handled] Error creating sensor post request");
            e.printStackTrace();
            clearData();
        }
    }
}
