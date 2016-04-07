package com.breatheplatform.beta;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import com.breatheplatform.beta.data.SensorNames;
import com.breatheplatform.beta.shared.Constants;

import java.io.File;

/* Class: ClientPaths
 * This class contains all the shared constants used by client services
 */
public class ClientPaths {
    private static final String TAG = "ClientPaths";

    private static final File ROOT = android.os.Environment.getExternalStorageDirectory();

    public static final String PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuvzFRohXhgcG7y5Ly3QX\n" +
            "ypBF7IrC1x6coF3Ok/87dVxcTQJv7uFbhOlqQcka/1S6gNZ2huc23BWdMGB9UIb1\n" +
            "owx/QNPZrb7m4En6wvgHIngkBc+5YgxgG5oTRUzG9AsemyrPbBQl+kL5cdpZWmPb\n" +
            "AEfVx+72WtlUkdbsuVSw58oAG4CjuDxu4eLpYVQ+CI3l60kfWXf0yK/quiq/uSMq\n" +
            "r8D5hUURNICQhq6Ub5Wy4vxs4IZjuzw5UjBDUTyjqYnXL2QQ+8/t6SuUloCMc7RN\n" +
            "bvksBlqwVUQW67vmFfv/zpjeEFK+ADnGLcCgvmK+b+nMfhpqO7/2xczvqeXK11XP\n" +
            "jwIDAQAB";

    public static final String API_KEY = "I3jmM2DI4YabH8937pRwK7MwrRWaJBgziZTBFEDTpec";//"GWTgVdeNeVwsGqQHHhChfiPgDxxgXJzLoxUD0R64Gns";

    private static final SensorNames sensorNames = new SensorNames();

    public static String getSensorName(int id) {
        return sensorNames.getName(id);
    }

//    //File information
//    private static final String subjectDirectory = ROOT + "/SubjectData.txt";
//    private static final File subjectFile = createFile(subjectDirectory);
//
//    private static final String sensorDirectory = ROOT + "/SensorData.txt";
//    public static final File sensorFile = createFile(sensorDirectory);
//
//    private static final String encSensorDirectory = ROOT + "/EncSensorData.txt";
//    public static final File encSensorFile = createFile(encSensorDirectory);
//
//    private static final String rsaKeyDirectory = ROOT + "/PublicKey.pem";
//    public static final File rsaKeyFile = createFile(rsaKeyDirectory);
//
//    private static final String aesKeyDirectory = ROOT + "/AesKey.pem";
//    public static final File aesKeyFile = createFile(aesKeyDirectory);

//    public static int activityConfidence = NO_VALUE;
//    public static String activityName = "None";

    public static Boolean writing = true;

    public static Context mainContext = null;
    public static void setContext(Context c) {
        mainContext = c;
    }

    public volatile static int batteryLevel = Constants.NO_VALUE;
    public volatile static String connectionInfo = "Waiting";
    public static String activityDetail = "None:"+Constants.NO_VALUE;

    public static Location currentLocation = null;
    public static String SUBJECT_ID = "";
    public static String mobileNodeId = null;
    public static Integer userAge = Constants.NO_VALUE;

    private static File createFile(String fname) {
        Log.d(TAG, "Creating file: " + fname);
        File f = new File(fname);
        //f.mkdirs();
        return f;
    }


    private static double round5(double v) {
        return Math.round(v * 100000.0) / 100000.0;
    }



//
//
//    public static Integer getSubjectID() {
//        //attempt to read subject_ID
//        String sid = "";
//        int res;
//        if (SUBJECT_ID != null && SUBJECT_ID > 0) {
//            Log.i(TAG, "retrieved existing subject ID: " + SUBJECT_ID);
//            return SUBJECT_ID;
//        }
//
//        try {
//            Log.i(TAG, "getSubjectID open: " + subjectFile.toString());
//            FileInputStream fIn = new FileInputStream(subjectFile);
//            DataInputStream in = new DataInputStream(fIn);
//            BufferedReader rd = new BufferedReader(new InputStreamReader(in));
//
//            sid = rd.readLine();
//            Log.i(TAG, "READ: " + sid);
//            rd.close();
//            in.close();
//            fIn.close();
//            res = Integer.parseInt(sid);
//
//        } catch (Exception e) {
//            Log.e(TAG, "Invalid subjectID (" + sid + ") in subjectFile");
//            return NO_VALUE;
//        }
//        Log.i(TAG, "getSubjectID returning: " + res);
//        return res;
//
//    }





}


