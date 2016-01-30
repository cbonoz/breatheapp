package com.breatheplatform.common;

import android.location.Location;
import android.util.Log;

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
    public static final String BASE = "http://beta.breatheplatform.com";
    public static final String SUBJECT_API = BASE + "/api/subject/add";
    public static final String MULTI_FULL_API = BASE + "/api/multisensor/add";

    public static final String API_KEY = "GWTgVdeNeVwsGqQHHhChfiPgDxxgXJzLoxUD0R64Gns";

    //public static final String filePath = Environment.getExternalStorageDirectory().getAbsoluteFile().toString();
    public static final String SENSOR_FNAME = "SensorData.txt";
    //public static final String LOG_DATA_FILE = "BTLogData.txt";
    public static final int GARBAGE_SENSOR_ID = 2752;
    public static final int DUST_SENSOR_ID = 999;
    public static Boolean BT_SCANNING = true;

    private static final Integer RECORD_LIMIT = 50;
    private static final Boolean sending = true;
    private static final Boolean encrypting = false;
    public static int TURBO = 0;



    public static Location currentLocation=null;

    //private static EncryptionDecryption encryptionDecryption; = new EncryptionDecryption();

    public  static final File root = android.os.Environment.getExternalStorageDirectory();
    private static String subjectDirectory = root + "/SubjectData.txt";
    private static File subjectFile = createFile(subjectDirectory);

    public static Integer SUBJECT_ID = getSubjectID();

    private static JSONArray jsonData = new JSONArray();
    private static Integer recordCount = 0;
    private static String timezone = initTimeZone();




    //private static BufferedWriter wr;// = new BufferedWriter(new FileWriter(subjectFile));
    //private static BufferedReader rd;// = new BufferedReader(new FileReader(subjectFile));


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

        return tzone;

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
            Log.e(TAG, "Invalid subjectID ("+sid+") in subjectFile...resetting to " + GARBAGE_SENSOR_ID);
            e.printStackTrace();
            //erase the current subject file and replace with garbage.
            res=GARBAGE_SENSOR_ID;
            setSubjectID(res);

        }
        Log.i(TAG, "getSubjectID returning: " + res);
        return res;

    }

    public static void setSubjectID(Integer sid) {
        Log.i(TAG, "setSubjectID: " + sid);
        try {

            FileOutputStream f = new FileOutputStream(subjectFile);

            f.write(sid.toString().getBytes());
            f.close();


        } catch (Exception e) {
            e.printStackTrace();


        }


        SUBJECT_ID=sid;//getSubjectID();

    }

    //safe push method
    public static /*synchronized*/ void appendData(JSONObject jObj) {
        jsonData.put(jObj);
    }

    public static void clearData() {
        jsonData = new JSONArray();
    }

    public static JSONArray getData() {
        return jsonData;
    }

    private static void createDataPostRequest() {
        JSONObject jsonBody = new JSONObject();

        String jsonDataString="";

        try {
            //jsonDataString = (encrypting ? encryptionDecryption.encrypt(jsonData.join("\n").toCharArray()) : jsonData.join("\n"));
            jsonDataString = jsonData.join("\n");

            jsonBody.put("subject_id", getSubjectID());
            jsonBody.put("key", API_KEY);

            //data is the only object that needs to be encrypted
            jsonBody.put("data", jsonDataString);


        } catch (Exception e) {
            Log.e(TAG,"Error creating jsonBody");
            e.printStackTrace();
        }

        Log.d("JSONBODY RESULT: ", jsonBody.toString());

        if (sending) {

            Log.d(TAG, "Uploading sensor datum");

            //uploadTask will take care of the post request, and writing the data to the auxiliary sensorData subjectFile should the post request be unsuccessful
            //this is done on a background task thread
            UploadTask uploadTask = new UploadTask(MULTI_FULL_API, null);//, root + File.separator + SENSOR_FNAME);
            uploadTask.execute(jsonBody.toString());
            uploadTask.cleanUp();

            //only sending one value for initial test
            //sending = false;

        }

        Log.d(TAG, "Exiting send block");

        //reset recordCount value
        clearData();


    }
    // END WRITE AND SEND BLOCK
}

