package com.breatheplatform.beta;

import android.content.Context;
import android.hardware.Sensor;
import android.location.Location;
import android.util.Log;

import com.breatheplatform.beta.data.SensorNames;
import com.breatheplatform.beta.encryption.HybridEncrypter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.crypto.spec.IvParameterSpec;

/* Class: ClientPaths
 * This class contains all the shared constants used by client services
 */
public class ClientPaths {
    private static final String TAG = "ClientPaths";

    public static final File ROOT = android.os.Environment.getExternalStorageDirectory();
    public static final String DUST_BT_NAME = "HaikRF";

    public static final String BASE = "http://www.breatheplatform.com";
    public static final String SUBJECT_API = "/api/subject/add";
    public static final String MULTI_API = "/api/multisensor/add";
    public static final String RISK_API = "/api/risk/get";
    public static final String ACTIVITY_API = "/activity";
    public static final String PUBLIC_KEY_API = "/api/publickey/get";
    public static final String PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuvzFRohXhgcG7y5Ly3QX\n" +
            "ypBF7IrC1x6coF3Ok/87dVxcTQJv7uFbhOlqQcka/1S6gNZ2huc23BWdMGB9UIb1\n" +
            "owx/QNPZrb7m4En6wvgHIngkBc+5YgxgG5oTRUzG9AsemyrPbBQl+kL5cdpZWmPb\n" +
            "AEfVx+72WtlUkdbsuVSw58oAG4CjuDxu4eLpYVQ+CI3l60kfWXf0yK/quiq/uSMq\n" +
            "r8D5hUURNICQhq6Ub5Wy4vxs4IZjuzw5UjBDUTyjqYnXL2QQ+8/t6SuUloCMc7RN\n" +
            "bvksBlqwVUQW67vmFfv/zpjeEFK+ADnGLcCgvmK+b+nMfhpqO7/2xczvqeXK11XP\n" +
            "jwIDAQAB";


    public static final String RISK_CASE = "r";
//    public static final String MULTI_CASE = "m";
//    public static final String SUBJECT_CASE = "s";

    public static final String API_KEY = "I3jmM2DI4YabH8937pRwK7MwrRWaJBgziZTBFEDTpec";//"GWTgVdeNeVwsGqQHHhChfiPgDxxgXJzLoxUD0R64Gns";

    public static final int DUST_SENSOR_ID = 999;
    public static final int SPIRO_SENSOR_ID = 998;
    public static final int ENERGY_SENSOR_ID = 997;
    public static final int TERMINATE_SENSOR_ID = 555;
    public static final int REG_HEART_SENSOR_ID = 65562;
    //    public static final int SS_HEART_SENSOR_ID = 21;
    public static final int HEART_SENSOR_ID = Sensor.TYPE_HEART_RATE;
    public static final int LA_SENSOR_ID = Sensor.TYPE_LINEAR_ACCELERATION;


    public static final int SENSOR_DELAY_CUSTOM = 900;//1000; //ms
    //2000;//SensorManager.SENSOR_DELAY_NORMAL*100;//1000000*5;//ms
    public static final int ONE_SEC_IN_MICRO = 1000000;
    public static final int NO_VALUE = -1;

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

    private static final String rsaKeyDirectory = ROOT + "/PublicKey.pem";
    public static final File rsaKeyFile = createFile(rsaKeyDirectory);

    private static final String aesKeyDirectory = ROOT + "/AesKey.pem";
    public static final File aesKeyFile = createFile(aesKeyDirectory);

//    public static int activityConfidence = NO_VALUE;
//    public static String activityName = "None";

    public static Boolean writing = true;
    public static Boolean encrypting = false;


    public static Context mainContext = null;
    public static void setContext(Context c) {

        mainContext = c;

    }

    public volatile static Boolean dustConnected = false;
    public volatile static int batteryLevel = NO_VALUE;
    public volatile static String connectionInfo = "Waiting";

//    private static DeviceClient client = null;

    public static Location currentLocation = null;
    public static Integer SUBJECT_ID = getSubjectID();
    public static String mobileNodeId = null;

    private static File createFile(String fname) {
        Log.d(TAG, "Creating file: " + fname);
        File f = new File(fname);
        //f.mkdirs();
        return f;
    }

    private static HybridEncrypter hybridEncrypter = createEncrypter();


    private static HybridEncrypter createEncrypter() {
        int key_size_bits = PUBLIC_KEY.length()*8;

        try {
            writeDataToFile(PUBLIC_KEY, rsaKeyFile, false);
            String k= readDataFromFile(rsaKeyFile);
            Log.d("Public keyfile contents", k);
            if (SUBJECT_ID!=NO_VALUE) {
                SUBJECT_ID = getSubjectID();
            }
            byte[] ivBytes = new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

            IvParameterSpec ivParams = new IvParameterSpec(ivBytes);

            return new HybridEncrypter(rsaKeyDirectory, key_size_bits, ivParams);

        } catch (Exception e) {
            Log.e(TAG, "[Handled] Could not create hybridEncrypter (keyfile may not exist)");
            e.printStackTrace();
            encrypting=false;
        }

        return null;
    }

    public static String getRawSymKey() {
        return hybridEncrypter.getKeyParams();
    }

    public static byte[] encString(String s) {

        try {
            return hybridEncrypter.stringEncrypter(s);

        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "error encrypting string");
            return null;
        }

    }

    public static String decString(byte[] s) {

        try {
            return hybridEncrypter.stringDecrypter(s);

        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "error decrypting string");
            return null;
        }

    }

    public static String getSymKey() {
        return hybridEncrypter.getEncryptedSymmetricKey();
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


    public static String compress(String str) throws IOException {
        if (str == null || str.length() == 0) {
            return str;
        }
        System.out.println("String length : " + str.length());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(out);
        gzip.write(str.getBytes());

        gzip.close();

        String outStr = out.toString("ISO-8859-1");//ISO-8859-1
        System.out.println("Output String length : " + outStr.length());

        return outStr;
    }

    public static String decompress(String str) throws IOException {
        if (str == null || str.length() == 0) {
            return str;
        }
        System.out.println("Input String length : " + str.length());
        GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(str.getBytes("ISO-8859-1")));
        BufferedReader bf = new BufferedReader(new InputStreamReader(gis, "ISO-8859-1"));
        String outStr = "";
        String line;
        while ((line=bf.readLine())!=null) {
            outStr += line;
        }
        System.out.println("Output String length : " + outStr.length());
        return outStr;
    }



//    public static Integer getSubjectID() {
//
//
//
//    }
//
//    public static void setSubjectID(Integer sid) {
//
//
//    }



}


