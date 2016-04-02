package com.breatheplatform.beta;

import android.util.Log;

import com.breatheplatform.beta.shared.Constants;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

/**
 * Created by cbono on 3/18/16.
 */
public class ClientPaths {

    private static final String TAG = "ClientPaths";

    public static final File ROOT = android.os.Environment.getExternalStorageDirectory();
//    public static final String rsaKeyDirectory = ROOT + "/PublicKey.pem";
//    public static final File rsaKeyFile = createFile(rsaKeyDirectory);

    public static final String sensorDirectory = ROOT + "/SensorData.txt";
    public static final File sensorFile = createFile(sensorDirectory);


//    public static final String PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuvzFRohXhgcG7y5Ly3QX\n" +
//            "ypBF7IrC1x6coF3Ok/87dVxcTQJv7uFbhOlqQcka/1S6gNZ2huc23BWdMGB9UIb1\n" +
//            "owx/QNPZrb7m4En6wvgHIngkBc+5YgxgG5oTRUzG9AsemyrPbBQl+kL5cdpZWmPb\n" +
//            "AEfVx+72WtlUkdbsuVSw58oAG4CjuDxu4eLpYVQ+CI3l60kfWXf0yK/quiq/uSMq\n" +
//            "r8D5hUURNICQhq6Ub5Wy4vxs4IZjuzw5UjBDUTyjqYnXL2QQ+8/t6SuUloCMc7RN\n" +
//            "bvksBlqwVUQW67vmFfv/zpjeEFK+ADnGLcCgvmK+b+nMfhpqO7/2xczvqeXK11XP\n" +
//            "jwIDAQAB";


    public static int activityConfidence = Constants.NO_VALUE;
    public static String activityName = "None";


    public static File createFile(String fname) {
        Log.d(TAG, "Creating file: " + fname);
        return new File(fname);
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
//
//    public static String compress(String str) throws IOException {
//        if (str == null || str.length() == 0) {
//            return str;
//        }
//        System.out.println("String length : " + str.length());
//        ByteArrayOutputStream out = new ByteArrayOutputStream();
//        GZIPOutputStream gzip = new GZIPOutputStream(out);
//        gzip.write(str.getBytes());
//
//        gzip.close();
//
//        String outStr = out.toString("ISO-8859-1");//ISO-8859-1
//        System.out.println("Output String length : " + outStr.length());
//
//        return outStr;
//    }
//
//    public static String decompress(String str) throws IOException {
//        if (str == null || str.length() == 0) {
//            return str;
//        }
//        System.out.println("Input String length : " + str.length());
//        GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(str.getBytes("ISO-8859-1")));
//        BufferedReader bf = new BufferedReader(new InputStreamReader(gis, "ISO-8859-1"));
//        String outStr = "";
//        String line;
//        while ((line=bf.readLine())!=null) {
//            outStr += line;
//        }
//        System.out.println("Output String length : " + outStr.length());
//        return outStr;
//    }


//    public static String readDataFromFile(File f) throws IOException {
//        BufferedReader bufferedReader = new BufferedReader(new FileReader(f));
//        StringBuilder everything = new StringBuilder();
//        String line;
//        while( (line = bufferedReader.readLine()) != null) {
//            everything.append(line);
//        }
//        return everything.toString();
//    }
}
