package com.breatheplatform.beta;

import android.content.Context;
import android.hardware.Sensor;
import android.location.Location;
import android.util.Log;
import android.util.SparseLongArray;

import com.breatheplatform.beta.data.SensorNames;
import com.breatheplatform.beta.encryption.HybridEncrypter;
import com.breatheplatform.beta.messaging.UploadTask;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.TimeZone;

/* Class: ClientPaths
 * This class contains all the shared constants used by client services
 */
public class ClientPaths {
    private static final String TAG = "ClientPaths";

    public static final File ROOT = android.os.Environment.getExternalStorageDirectory();
    public static final String DUST_BT_NAME = "HaikRF";

    public static final String BASE = "http://www.breatheplatform.com";
    public static final String SUBJECT_API = BASE + "/api/subject/add";
    public static final String MULTI_FULL_API = BASE + "/api/multisensor/add";
    public static final String RISK_API = BASE + "/api/risk/get";
    public static final String PUBLIC_KEY_API = BASE + "/api/publickey/get";
    public static final String PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuvzFRohXhgcG7y5Ly3QX\n" +
            "ypBF7IrC1x6coF3Ok/87dVxcTQJv7uFbhOlqQcka/1S6gNZ2huc23BWdMGB9UIb1\n" +
            "owx/QNPZrb7m4En6wvgHIngkBc+5YgxgG5oTRUzG9AsemyrPbBQl+kL5cdpZWmPb\n" +
            "AEfVx+72WtlUkdbsuVSw58oAG4CjuDxu4eLpYVQ+CI3l60kfWXf0yK/quiq/uSMq\n" +
            "r8D5hUURNICQhq6Ub5Wy4vxs4IZjuzw5UjBDUTyjqYnXL2QQ+8/t6SuUloCMc7RN\n" +
            "bvksBlqwVUQW67vmFfv/zpjeEFK+ADnGLcCgvmK+b+nMfhpqO7/2xczvqeXK11XP\n" +
            "jwIDAQAB";

    public static final String API_KEY = "I3jmM2DI4YabH8937pRwK7MwrRWaJBgziZTBFEDTpec";//"GWTgVdeNeVwsGqQHHhChfiPgDxxgXJzLoxUD0R64Gns";

    public static final int DUST_SENSOR_ID = 999;
    public static final int SPIRO_SENSOR_ID = 998;
    public static final int ENERGY_SENSOR_ID = 997;
    public static final int REG_HEART_SENSOR_ID = 65562;
//    public static final int SS_HEART_SENSOR_ID = 21;
    public static final int HEART_SENSOR_ID = Sensor.TYPE_HEART_RATE;
    public static final int LA_SENSOR_ID = Sensor.TYPE_LINEAR_ACCELERATION;


    public static final int SENSOR_DELAY_CUSTOM = 900;//1000; //ms
    //2000;//SensorManager.SENSOR_DELAY_NORMAL*100;//1000000*5;//ms
    public static final int ONE_SEC_IN_MICRO = 1000000;
    public static final int NO_VALUE = -1;

    //number of sensor data entries between each send
    private static final Integer RECORD_LIMIT = 50;


    private static final SensorNames sensorNames = new SensorNames();

    public static String getSensorName(int id) {
        return sensorNames.getName(id);
    }

    //File information
    private static final String subjectDirectory = ROOT + "/SubjectData.txt";
    private static final File subjectFile = createFile(subjectDirectory);


    private static final String sensorDirectory = ROOT + "/SensorData.txt";
    public static final File sensorFile = createFile(sensorDirectory);

    private static final String encSensorDirectory = ROOT + "/EncSensorData.txt";
    public static final File encSensorFile = createFile(encSensorDirectory);

    private static final String publicKeyDirectory = ROOT + "/PublicKey.pem";
    public static final File publicKeyFile = createFile(publicKeyDirectory);

    private static final String timezone = initTimeZone();

    //controls whether data should be sent to server
    private static final Boolean sending = true;
    //controls encryption in post request
    private static Boolean encrypting = true;
    //controls writing sensorData to file
    public static Boolean writing = true;

    public static Context mainContext = null;
    public static void setContext(Context c) {
        mainContext = c;
    }

    public volatile static Boolean dustConnected = false;
    public volatile static int batteryLevel = NO_VALUE;
    public volatile static String connectionInfo = "Waiting";

//    private static DeviceClient client = null;

    private static int bytesWritten = 0;

    private static SparseLongArray lastSensorData = new SparseLongArray();
    private static HybridEncrypter hybridEncrypter = createEncrypter();

    private static JSONArray sensorData = new JSONArray();
    private static Integer recordCount = 0;


    public static Location currentLocation = null;
    public static Integer SUBJECT_ID = getSubjectID();


    private static File createFile(String fname) {
        Log.d(TAG, "Creating file: " + fname);
        File f = new File(fname);
        //f.mkdirs();
        return f;
    }

    //is synchronized necessary here?
    public synchronized static void incrementCount() {
        recordCount++;
        Log.d(TAG, "recordCount: " + recordCount);
        if (recordCount.equals(RECORD_LIMIT)) {
            createDataPostRequest();
        }
    }

    public static HybridEncrypter createEncrypter() {
        int key_size_bits = PUBLIC_KEY.length()*8;


        try {
            writeDataToFile(PUBLIC_KEY, publicKeyFile, false);
            String k= readDataFromFile(publicKeyFile);
            Log.d("Public keyfile contents", k);
            if (SUBJECT_ID!=NO_VALUE) {
                return new HybridEncrypter(publicKeyDirectory, key_size_bits, SUBJECT_ID.toString());
            }

        } catch (Exception e) {
            Log.e(TAG, "[Handled] Could not create hybridEncrypter (keyfile may not exist)");
            e.printStackTrace();
            encrypting=false;
        }

        return null;
    }

    public static String getTimeZone() {
        return timezone;
    }

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

    private static double round5(double v) {
        return Math.round(v * 100000.0) / 100000.0;
    }

    public static Integer getSubjectID() {
        //attempt to read subject_ID
        String sid = "";
        int res;
        if (SUBJECT_ID != null && SUBJECT_ID > 0) {
            Log.i(TAG, "retrieved existing subject ID: " + SUBJECT_ID);
            return SUBJECT_ID;
        }

        try {
            Log.i(TAG, "getSubjectID open: " + subjectFile.toString());
            FileInputStream fIn = new FileInputStream(subjectFile);
            DataInputStream in = new DataInputStream(fIn);
            BufferedReader rd = new BufferedReader(new InputStreamReader(in));

            sid = rd.readLine();
            Log.i(TAG, "READ: " + sid);
            rd.close();
            in.close();
            fIn.close();
            res = Integer.parseInt(sid);

        } catch (Exception e) {
            Log.e(TAG, "Invalid subjectID (" + sid + ") in subjectFile");
            return NO_VALUE;
        }
        Log.i(TAG, "getSubjectID returning: " + res);
        return res;

    }

    public static boolean writeDataToFile(String data, File file, Boolean append) {
        try {

            FileOutputStream f = new FileOutputStream(file, append);
            f.write(data.getBytes());

            f.close();

            Log.d(TAG, "wrote to " + file.toString());
            Log.d(TAG, "filelength " + file.length());

            if (file.toString().equals(sensorFile.toString())) {
                bytesWritten += data.length();
                Log.d(TAG, "bytesWritten " + bytesWritten);
            }
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            writing = false;
            return false;

        }

    }

    public static String readDataFromFile(File f) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new FileReader(f));
        StringBuilder everything = new StringBuilder();
        String line;
        while( (line = bufferedReader.readLine()) != null) {
            everything.append(line);
        }
        return everything.toString();
    }

    public static void setSubjectID(Integer sid) {
        Log.i(TAG, "setSubjectID: " + sid);
        if (writeDataToFile(sid.toString(), subjectFile, false)) {
            SUBJECT_ID = sid;//getSubjectID();
        } else {
            Log.e(TAG, "Failed to write to sid to " + subjectFile);
        }
    }

    public static void checkLastDust() {
        long last_time = lastSensorData.get(DUST_SENSOR_ID);
        if ((System.currentTimeMillis()-last_time)>30000) {
            dustConnected=false;
        }
    }


    public static void appendData(JSONObject jObj) {
        sensorData.put(jObj);
    }

    public static void clearData() {
        sensorData = new JSONArray();
        recordCount = 0;
    }

    public static JSONArray getData() {
        return sensorData;
    }

    private static void createDataPostRequest() {
        Log.d(TAG, "createDataPostRequest");
        JSONObject jsonBody = new JSONObject();
        checkLastDust();
        String sensorDataString = "";
        byte[] encSensorData;

        try {

            // \n becomes the delimiter on the server to split data entries
            sensorDataString = sensorData.join("\n");
//
//
//            if (encrypting && hybridEncrypter!=null) {
//                Log.d(TAG, "pre-encrypted length: " + sensorDataString.length());
////                sensorDataString = new String(hybridEncrypter.stringEncrypter(sensorDataString));
//                encSensorData = hybridEncrypter.stringEncrypter(sensorDataString);
//                Log.d(TAG, "encrypted length: " + encSensorData.length);
//                jsonBody.put("data", encSensorData);
//
//                if (writing) {
//                    writeDataToFile(sensorDataString, sensorFile, false);
//                    hybridEncrypter.fileEncrypter(sensorDirectory, encSensorDirectory, false);//change false to true for append
//                    Log.d(TAG, "Wrote to Sensorfile, size now: " + sensorFile.length() + "B");
//                }
//            } else {
//                jsonBody.put("data", sensorDataString);
//            }
//
//
//            if (writing) {
//                writeDataToFile(sensorDataString, sensorFile, false);
//                Log.d(TAG, "Wrote to Sensorfile, size now: " + sensorFile.length() /1024 + "kB");
//            }

            jsonBody.put("data", sensorDataString);

            if (SUBJECT_ID==NO_VALUE) {
                SUBJECT_ID = getSubjectID();


            }
            jsonBody.put("subject_id", SUBJECT_ID);
            jsonBody.put("key", API_KEY);
            jsonBody.put("connection", connectionInfo);

            //data is the only object that needs to be encrypted



        } catch (Exception e) {
            Log.e(TAG, "Error creating jsonBody");
            e.printStackTrace();
        }

//        Log.d("JSONBODY RESULT: ", jsonBody.toString());

        if (sending) {

            Log.d(TAG, "Uploading sensor data");

            //uploadTask will take care of the post request, and writing the data to the auxiliary sensorData subjectFile should the post request be unsuccessful
            //this is done on a background task thread
            UploadTask uploadTask = new UploadTask(MULTI_FULL_API, null);//, ROOT + File.separator + SENSOR_FNAME);
            uploadTask.execute(jsonBody.toString());


        }

        Log.d(TAG, "Exiting send block");
        //reset recordCount value
        clearData();
    }

    //for energy measurements
    private static float sumX =0, sumY = 0, sumZ = 0;
    private static float timeEnergyStart = System.currentTimeMillis();

    private static float energy = 0;

    private static void addEnergyData() {
        long t = System.currentTimeMillis();
        addSensorData(ClientPaths.ENERGY_SENSOR_ID, 0, t, new float[]{energy, timeEnergyStart});
        sumX = 0;
        sumY = 0;
        sumZ = 0;
        timeEnergyStart=t;
        energy = 0;
    }

//     END WRITE AND SEND BLOCK
    public static void addSensorData(final int sensorType, final int accuracy, final long t, final float[] values) {

        long lastTimeStamp = lastSensorData.get(sensorType);
        long timeAgo = t - lastTimeStamp;
        String sensorName = sensorNames.getName(sensorType);
        if (lastTimeStamp != 0) {
            if (timeAgo < ClientPaths.SENSOR_DELAY_CUSTOM) {
                Log.d(TAG, "Blocked " + sensorName + " " + Arrays.toString(values) + " too soon  ");
                return; //wait until SENSOR_DELAY_CUSTOM until next reading
            }
        }


        //if accuracy rating too low, reject
        if (accuracy < 2 && !(sensorName.equals("Linear Acceleration"))) {
            Log.d(TAG, "Blocked " + sensorName+ " " + Arrays.toString(values) + " reading, accuracy " + accuracy + " < 2");
            return;
        }
        //ClientPaths.createDataEntry(sensorType, accuracy, timestamp, values);

        JSONObject jsonDataEntry = new JSONObject();
        JSONObject jsonValue = new JSONObject();

        Boolean validEvent = false;
        //Log.d(TAG, "Received " + sensorName + " (" + sensorType + ") = " + Arrays.toString(values));

        try {

            switch (sensorType) {
                case (Sensor.TYPE_LINEAR_ACCELERATION):

                    jsonValue.put("x", round5(values[0]));
                    jsonValue.put("y", round5(values[1]));
                    jsonValue.put("z", round5(values[2]));
                    sumX+=round5(values[0]);
                    sumY+=round5(values[1]);
                    sumZ+=round5(values[2]);
                    energy += Math.pow(sumX,2) + Math.pow(sumY,2) + Math.pow(sumZ, 2);
                    //jsonDataEntry.put("sensor_type", sensorType);
                    validEvent = true;
                    break;
                case (Sensor.TYPE_HEART_RATE):
                case (REG_HEART_SENSOR_ID):
                case (DUST_SENSOR_ID):
                    if (values[0]<=0) {
                        Log.d(TAG, "Received "+sensorName+" data=0 - skip");
                        return;
                    }
                    //jsonDataEntry.put("sensor_type", 21);
                    jsonValue.put("v", values[0]);
                    validEvent = true;
                    break;
                case (Sensor.TYPE_AMBIENT_TEMPERATURE):
                    //case (Sensor.TYPE_STEP_COUNTER):
                    jsonValue.put("v", values[0]);
                    //jsonDataEntry.put("sensor_type", sensorType);
                    validEvent = true;
                    break;
                case (SPIRO_SENSOR_ID):
                    //add spirometer data point
//                    fev1, pef, fev1_best, pef_best, fev1_percent, pef_percent, green_zone, yellow_zone, orange_zone
                    jsonValue.put("fev1", values[0]);
                    jsonValue.put("pef", values[1]);
                    jsonValue.put("goodtest", values[2]);

                    validEvent = true;
                    break;
                case (ENERGY_SENSOR_ID):
                    jsonValue.put("energy", values[0]);
                    jsonValue.put("start",values[1]);
                    validEvent = true;

                    break;
                default:
                    break;
            }

            jsonValue.put("accuracy",accuracy);
            jsonValue.put("battery", batteryLevel);

            //jsonDataEntry.put("sensor_name", sensorName);
            jsonDataEntry.put("value", jsonValue);

            jsonDataEntry.put("last", lastTimeStamp);
            jsonDataEntry.put("timestamp", t);//System.currentTimeMillis());
            jsonDataEntry.put("timezone", getTimeZone());

            jsonDataEntry.put("sensor_id", sensorNames.getServerID(sensorType));//will be changed to actual sensor (sensorType)
            //jsonDataEntry.put("sensor_id",sensorType);

            if (currentLocation!=null) {
                jsonDataEntry.put("lat", currentLocation.getLatitude());
                jsonDataEntry.put("long", currentLocation.getLongitude());
                jsonDataEntry.put("accuracy", currentLocation.getAccuracy());
            } else {
                jsonDataEntry.put("lat",NO_VALUE);
                jsonDataEntry.put("long",NO_VALUE);
                jsonDataEntry.put("accuracy", NO_VALUE);
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
        Log.d(TAG, "Data Added: " + jsonDataEntry.toString());
        lastSensorData.put(sensorType, t);

        //if spirometer send immediately
        if(sensorType==SPIRO_SENSOR_ID) {
            Log.d(TAG, "Received spiro: " + values[1]);
            Log.d(TAG, "Immediately sending " + jsonDataEntry.toString());
            createDataPostRequest();
        }
    }



}


