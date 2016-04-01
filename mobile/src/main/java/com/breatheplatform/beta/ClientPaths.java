package com.breatheplatform.beta;

import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

/**
 * Created by cbono on 3/18/16.
 */
public class ClientPaths {
    public static final String BROADCAST_ACTION = ".BROADCAST_ACTION";
    private static final String TAG = "ClientPaths";

    public static final File ROOT = android.os.Environment.getExternalStorageDirectory();
//    public static final String rsaKeyDirectory = ROOT + "/PublicKey.pem";
//    public static final File rsaKeyFile = createFile(rsaKeyDirectory);

    public static final String sensorDirectory = ROOT + "/SensorData.txt";
    public static final File sensorFile = createFile(sensorDirectory);


    public static final String BASE = "http://www.breatheplatform.com";
    public static final String ACTIVITY_API = "/activity";
    public static final String SUBJECT_API = "/api/subject/add";
    public static final String MULTI_API = "/api/multisensor/add";
    public static final String RISK_API = "/api/risk/get";
    public static final String CALENDAR_API = "/api/calendar/add";
    public static final String LABEL_API = "/label";


    public static final String PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuvzFRohXhgcG7y5Ly3QX\n" +
            "ypBF7IrC1x6coF3Ok/87dVxcTQJv7uFbhOlqQcka/1S6gNZ2huc23BWdMGB9UIb1\n" +
            "owx/QNPZrb7m4En6wvgHIngkBc+5YgxgG5oTRUzG9AsemyrPbBQl+kL5cdpZWmPb\n" +
            "AEfVx+72WtlUkdbsuVSw58oAG4CjuDxu4eLpYVQ+CI3l60kfWXf0yK/quiq/uSMq\n" +
            "r8D5hUURNICQhq6Ub5Wy4vxs4IZjuzw5UjBDUTyjqYnXL2QQ+8/t6SuUloCMc7RN\n" +
            "bvksBlqwVUQW67vmFfv/zpjeEFK+ADnGLcCgvmK+b+nMfhpqO7/2xczvqeXK11XP\n" +
            "jwIDAQAB";

    public static final int NO_VALUE = -1;

    public static int activityConfidence = NO_VALUE;
    public static String activityName = "None";


    public static File createFile(String fname) {
        Log.d(TAG, "Creating file: " + fname);
        File f = new File(fname);
        //f.mkdirs();
        return f;
    }

    public static boolean writeDataToFile(String data, File file, Boolean append) {
        try {
            BufferedWriter f = new BufferedWriter(new FileWriter(file, append));
            f.write(data);
            f.close();

            Log.d(TAG,file.toString()+ " filelength " + file.length() + "B");
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
