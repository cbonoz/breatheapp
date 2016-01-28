package com.example.android.google.wearable.app;

import android.hardware.Sensor;
import android.location.Location;
import android.util.Log;
import android.util.SparseLongArray;

import com.example.android.google.wearable.app.data.SensorNames;
import com.example.android.google.wearable.app.encryption.EncryptionDecryption;
import com.example.android.google.wearable.app.encryption.HybridEncrypter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.TimeZone;

/* Class: ClientPaths
 * This class contains all the shared constants used by client services
 */
public class ClientPaths {

    private static final String TAG = "ClientPaths";

    public static final String START_MEASUREMENT = "/start";
    public static final String STOP_MEASUREMENT = "/stop";
    public static final String BASE = "http://www.breatheplatform.com";
    public static final String SUBJECT_API = BASE + "/api/subject/add";
    public static final String MULTI_FULL_API = BASE + "/api/multisensor/add";
    public static final String RISK_API = BASE + "/api/risk/get";
    public static final String KEY_API = BASE + "/api/publickey/get";
    public static final String DUST_BT_NAME = "HaikRF";

    public static final String API_KEY = "I3jmM2DI4YabH8937pRwK7MwrRWaJBgziZTBFEDTpec";//"GWTgVdeNeVwsGqQHHhChfiPgDxxgXJzLoxUD0R64Gns";

    //public static final String filePath = Environment.getExternalStorageDirectory().getAbsoluteFile().toString();
    public static final String SENSOR_FNAME = "SensorData.txt";
    //public static final String LOG_DATA_FILE = "BTLogData.txt";
    public static final int GARBAGE_SENSOR_ID = 2752;
    public static final int DUST_SENSOR_ID = 999;
    public static final int SPIRO_SENSOR_ID = 998;
    public static final int SS_HEART_SENSOR_ID = 65562;
    public static final int SENSOR_DELAY_CUSTOM = 2000;//SensorManager.SENSOR_DELAY_NORMAL*100;//1000000*5;
    public static final int NO_VALUE = -1;

    private static final int MAX_FILE_SIZE = (1024*1024)*300;//max size 300MB
    public static Boolean BT_SCANNING = true;

    private static int riskLevel= NO_VALUE;
    //number of sensor data entries between each send
    private static final Integer RECORD_LIMIT = 100;

    //controls whether data should be sent to server
    private static Boolean sending = true;
    //controls encryption in post request
    private static Boolean encrypting = false;
    //controls writing sensorData to file
    private static Boolean writing = false;
    public Boolean isWriting() {
        return writing;
    }

    public static int TURBO = 0;
    public static int bytesWritten=0;

    private static SparseLongArray lastSensorData = new SparseLongArray();
    private static SparseLongArray lastSensorReading = new SparseLongArray();

    //./adb pull /storage/sdcard0/SensorData.txt ~/Downloads



    public static Location currentLocation=null;
    private static SensorNames sensorNames = new SensorNames();
    private static EncryptionDecryption encryptionDecryption = new EncryptionDecryption();

    //private static EncryptionDecryption encryptionDecryption; = new EncryptionDecryption();

    public  static final File root = android.os.Environment.getExternalStorageDirectory();

    private static String subjectDirectory = root + "/SubjectData.txt";
    private static File subjectFile = createFile(subjectDirectory);

    private static String sensorDirectory = root + "/SensorData.txt";
    public static File sensorFile = createFile(sensorDirectory);

    private static String publicKeyDirectory = root + "/PublicKey.pem";
    public static File publicKeyFile = createFile(sensorDirectory);


    public static Integer SUBJECT_ID = getSubjectID();

    private static JSONArray sensorData = new JSONArray();
    private static Integer recordCount = 0;
    private static String timezone = initTimeZone();


    private static HybridEncrypter hybridEncrypter;


    public static Boolean createEncrypter() {
        encrypting=true;
        int publicKeyLength = 0;
        try {
            hybridEncrypter = new HybridEncrypter(publicKeyDirectory, publicKeyLength, SUBJECT_ID.toString());
            return true;
        } catch (Exception e) {
            //e.printStackTrace();
            Log.e(TAG, "[Handled] Could not create HybridEncrypter (key file not present)");
            encrypting=false;
            return false;
        }
    }

    public static void requestAndSaveKey() {

        Log.d(TAG, "Called requestAndSaveKey");
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("key", API_KEY);
            jsonBody.put("subject_id", SUBJECT_ID);
            jsonBody.put("timestamp",System.currentTimeMillis());
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        UploadTask uploadTask = new UploadTask(KEY_API, null);//, root + File.separator + SENSOR_FNAME);
        uploadTask.execute(jsonBody.toString());
    }


    public static void updateRisklevel() {

        Log.d(TAG, "Called updateRiskLevel");
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("key", API_KEY);
            jsonBody.put("subject_id", SUBJECT_ID);
            jsonBody.put("timestamp",System.currentTimeMillis());
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        UploadTask uploadTask = new UploadTask(RISK_API, null);//, root + File.separator + SENSOR_FNAME);
        uploadTask.execute(jsonBody.toString());
        //uploadTask.cleanUp();
    }


    private static void createDataPostRequest() {
        JSONObject jsonBody = new JSONObject();
        String sensorDataString="";

        try {
            sensorDataString = sensorData.join("\n");

            if (encrypting) {
                sensorDataString = hybridEncrypter.stringEncrypter(sensorDataString).toString();
//                sensorDataString = encryptionDecryption.encrypt(sensorDataString.toCharArray());
            }
            jsonBody.put("subject_id", getSubjectID());
            jsonBody.put("key", API_KEY);
            jsonBody.put("data", sensorDataString);
        } catch (Exception e) {
            Log.e(TAG,"[Handled] Error creating jsonBody");
            e.printStackTrace();
        }

        Log.d("JSONBODY RESULT: ", jsonBody.toString());

        if (sending) {
            Log.d(TAG, "Uploading sensor data");
            //uploadTask will take care of the post request, and writing the data to the auxiliary sensorData subjectFile should the post request be unsuccessful
            //this is done on a background task thread
            UploadTask uploadTask = new UploadTask(MULTI_FULL_API, null);//, root + File.separator + SENSOR_FNAME);
            uploadTask.execute(jsonBody.toString());

        }
        Log.d(TAG, "Exiting send block");
        clearData();
    }

    private static File createFile(String fname) {

        Log.d(TAG, "Creating file: " + fname);
        File f = new File(fname);
        //f.mkdirs();
        return f;
    }

    public static synchronized void incrementCount() {

        recordCount++;
        Log.d(TAG, "recordCount: " + recordCount);

        if (recordCount.equals(RECORD_LIMIT)) {
            createDataPostRequest();
            recordCount = 0;
        }
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
            e.printStackTrace();
            tzone="US - Default";
        }

//        if (tzone.equals("Pacific Standard Time")) {
//            tzone = "PST";
//        }

        return tzone;

    }

    public static double round5(double v) {

        return Math.round(v*100000.0)/100000.0;
    }

    public static void sendKeyToServer() {
        Log.d(TAG, "Sending Key to Server");
        return;
    }


    public static Integer getSubjectID() {
        //attempt to read subject_ID
        String sid="";
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

            sid=rd.readLine();
            Log.i(TAG, "READ: " + sid);
            rd.close();
            in.close();
            fIn.close();
            res = Integer.parseInt(sid);

        } catch (Exception e) {
            Log.e(TAG, "Invalid subjectID (" + sid + ") in subjectFile");
            e.printStackTrace();
            //erase the current subject file and replace with garbage.
            //res=GARBAGE_SENSOR_ID;
            //setSubjectID(res);
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

    public static void setSubjectID(Integer sid) {
        Log.i(TAG, "setSubjectID: " + sid);
        if (writeDataToFile(sid.toString(), subjectFile,false))
            SUBJECT_ID=sid;//getSubjectID();





    }


    public static synchronized void appendData(JSONObject jObj) {
        sensorData.put(jObj);
    }

    public static synchronized void clearData() {
        sensorData = new JSONArray();
    }

    public static synchronized JSONArray getData() {
        return sensorData;
    }


    // END WRITE AND SEND BLOCK

    private static void createDataEntry(final int sensorType, final int accuracy, final long timestamp, final float[] values) {
        JSONObject jsonDataEntry = new JSONObject();
        JSONObject jsonValue = new JSONObject();
        String sensorName = sensorNames.getName(sensorType);
        Boolean validEvent = false;
        //Log.d(TAG, "Received " + sensorName + " (" + sensorType + ") = " + Arrays.toString(values));

        try {

            switch (sensorType) {
                case (Sensor.TYPE_LINEAR_ACCELERATION):
                    jsonValue.put("x", round5(values[0]));
                    jsonValue.put("y", round5(values[1]));
                    jsonValue.put("z", round5(values[2]));
                    //jsonDataEntry.put("sensor_type", sensorType);
                    validEvent = true;
                    break;
                case (Sensor.TYPE_HEART_RATE):
                case (65562):
                case(ClientPaths.DUST_SENSOR_ID):
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
                case (ClientPaths.SPIRO_SENSOR_ID):
                    //add spirometer data point
//                    fev1, pef, fev1_best, pef_best, fev1_percent, pef_percent, green_zone, yellow_zone, orange_zone
                    jsonValue.put("fev1", values[0]);
                    jsonValue.put("pef", values[1]);
                    jsonValue.put("goodtest", values[2]);
//                    jsonValue.put("fev1_best", values[2]);
//                    jsonValue.put("pef_best", values[3]);
//                    jsonValue.put("fev1_percent", values[4]);
//                    jsonValue.put("pef_percent", values[5]);
//                    jsonValue.put("green_zone", values[6]);
//                    jsonValue.put("yellow_zone", values[7]);
//                    jsonValue.put("orange_zone", values[8]);

                    validEvent=true;
                    break;

                default:
                    break;
            }



            //jsonDataEntry.put("sensor_name", sensorName);

            jsonDataEntry.put("value", jsonValue);

            jsonDataEntry.put("timestamp", System.currentTimeMillis());
            jsonDataEntry.put("timezone", ClientPaths.getTimeZone());
            jsonDataEntry.put("accuracy",accuracy);
            jsonDataEntry.put("sensor_id", sensorNames.getServerID(sensorType));//will be changed to actual sensor (sensorType)
            //jsonDataEntry.put("sensor_id",sensorType);
            if (ClientPaths.currentLocation!=null) {
                jsonDataEntry.put("lat", round5(ClientPaths.currentLocation.getLatitude()));
                jsonDataEntry.put("long", round5(ClientPaths.currentLocation.getLongitude()));
                //jsonDataEntry.put("accuracy", ClientPaths.currentLocation.getAccuracy());
            } else {
                jsonDataEntry.put("lat",NO_VALUE);
                jsonDataEntry.put("long",NO_VALUE);
            }

        } catch (Exception e) {
            Log.e(TAG, "error in creating jsonDataEntry");
            e.printStackTrace();
            return;
        }

        //if not one of the desired sensors

        if (!validEvent) {
            Log.d(TAG, "Encountered undesired sensor (" + sensorType + "): " + sensorName + ". skipping..");
            return;
        }

        appendData(jsonDataEntry);
        incrementCount();
        Log.d(TAG, "Data Added: " + jsonDataEntry.toString());
    }


    public static void sendSensorData(final int sensorType, final int accuracy, final long timestamp, final float[] values) {
        long t = System.currentTimeMillis();

        long lastTimestamp = lastSensorData.get(sensorType);
        long timeAgo = t - lastTimestamp;

        if (lastTimestamp != 0) {
            if (timeAgo < SENSOR_DELAY_CUSTOM) {
                return; //wait until SENSOR_DELAY_CUSTOM until next reading
            }
        }

        long lastReading = lastSensorReading.get(sensorType);
//        if (lastReading)

        //if accuracy rating too low, reject
        if (accuracy < 2) {
            Log.d(TAG, "Blocked " + sensorType + " " + values.toString() + " reading - accuracy < 2");
            return;

        }

        ClientPaths.createDataEntry(sensorType, accuracy, timestamp, values);

        lastSensorData.put(sensorType, t);
//        lastSensorReading.put(sensorType,values);

    }

//    public static void setRiskLevel(int risk) {
//        riskLevel=risk;
//    }
//
//    public static int getRiskLevel() {
//        return riskLevel;
//    }
}

