package com.breatheplatform.beta;

import android.app.IntentService;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.breatheplatform.beta.shared.Constants;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;

import me.denley.courier.Courier;

/**
 * Created by cbono on 2/15/16.
 */
public class MobileUploadService extends IntentService {

    private static final String TAG = MobileUploadService.class.getSimpleName();

    private static final String CHARSET_UTF8 = "UTF-8";


    private static URL createURL(String url) {
        try {
            return new URL(url);// URL(MULTI_API);
        } catch (Exception e) {
            Log.d(TAG, "Error creating URL");
            e.printStackTrace();
            return null;
        }
    }

    private static URL multiUrl = createURL(Constants.BASE + Constants.MULTI_API);
    private static URL riskUrl = createURL(Constants.BASE + Constants.RISK_API);

    private static String currentNetwork = "";

    private static Integer newRisk;
    private static String newResponse = "";


    //loading a json object could have a large amount of temporary data overhead if the app is not
    // connected to the internet and the sensors are running. Going to use a file approach, where the
    // server will process the files

    public MobileUploadService() {
        super("MobileUploadService");
    }

    private WifiManager wifiManager;
    private WifiManager.WifiLock lock;

    private Boolean sending = false;

//    private DeviceClient client;

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


    @Override
    protected void onHandleIntent(Intent intent) {
        // Gets data from the incoming Intent

//        client = DeviceClient.getInstance(this);

        String data = intent.getStringExtra("data");
        String urlString = intent.getStringExtra("url");


        int statusCode = 0;
        InputStream is = null;
        OutputStream os;
//        GZIPOutputStream os;
        HttpURLConnection conn = null;

        String result = null;


        try {

            byte[] dataBytes = data.getBytes();//.getBytes("ISO-8859-1");

            switch(urlString) {
                case Constants.RISK_API:
                    conn = (HttpURLConnection) riskUrl.openConnection();
                    break;
                case Constants.MULTI_API:
                    conn = (HttpURLConnection) multiUrl.openConnection();
//                    try {
//                        data = decompress(data);
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                        Log.e(TAG, "Error decompressing data");
//                        return;
//                    }


                    break;
                case Constants.SUBJECT_API:
                    URL url = createURL(Constants.BASE + Constants.SUBJECT_API);
                    if (url!=null)
                        conn = (HttpURLConnection) url.openConnection();
                    else {
                        Log.e(TAG, "Could not open Subject_API url");
                        return;
                    }
                    break;
                default:
                    Log.e(TAG, "Unexpected url case for mobile post message: " + urlString);
                    return;
            }


            if (!sending)
                return;

            Log.d(TAG, "Data: " + data);
            Log.d(TAG, "data bytes length: " + dataBytes.length);
            Log.d(TAG, "Connecting to: " + conn.getURL().toString());


//            conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(10000 /*milliseconds*/);
            conn.setConnectTimeout(15000 /* milliseconds */);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(dataBytes.length);
//            conn.setRequestProperty("connection", "close"); // disables Keep Alive

            //conn.setChunkedStreamingMode(0);

            //make some HTTP header nicety
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");

            conn.connect();

            os = new BufferedOutputStream(conn.getOutputStream());

            os.write(dataBytes);
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

            switch (urlString) {
                case Constants.SUBJECT_API:
                    try {
                        String jsonString = result.substring(result.indexOf("{"),result.indexOf("}")+1);
                        final JSONObject resJson = new JSONObject(jsonString);
                        int newID = Integer.parseInt(resJson.getString("subject_id"));
                        Log.i(TAG, "Setting new SubjectID: " + newID);


                    }
                    catch (Exception e) {
                        e.printStackTrace();

                    }


                case Constants.RISK_API:
                    try {
                        String jsonString = result.substring(result.indexOf("{"),result.indexOf("}")+1);
                        final JSONObject resJson = new JSONObject(jsonString);
                        newRisk = Integer.parseInt(resJson.getString("risk"));
                        Log.i(TAG, "Setting new riskLevel: " + newRisk);
//                        Constants.setRiskLevel(newRisk);

                    } catch (Exception e) {
                        newRisk=Constants.NO_VALUE;
                        Log.e(TAG, "[Handled] Error response from risk api");

                    }
                    break;
//                case Constants.PUBLIC_KEY_API:
//                    try {
//                        String jsonString = result.substring(result.indexOf("{"),result.indexOf("}")+1);
//                        final JSONObject resJson = new JSONObject(jsonString);
//                        String key = "";
//                        Constants.writeDataToFile(key, Constants.publicKeyFile, false);
//                        Constants.createEncrypter();
//                        break;
//                    } catch (Exception e) {
//
//                        Log.e(TAG, "[Handled] Error response from key api");
//                        return statusCode + ": JSON Parse Error";
//                    }
//

            }


        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "[Handled] Could not Connect to Internet (" + urlString + ")");
            newRisk = Constants.NO_VALUE;

        } finally {

            if (conn != null)
                conn.disconnect();

//            Intent i = new Intent("upload-done");
//            i.putExtra("url", urlCase);

            switch (urlString) {
                case Constants.RISK_API:
                    if (newRisk == null)
                        newRisk = Constants.NO_VALUE;
                    Log.d(TAG, "returning from phone RISK_API - value " + newRisk);
                    Courier.deliverMessage(this, Constants.RISK_API, newRisk);

//                    Courier.deliverData(this, Constants.ACTIVITY_API, newRisk, Constants.activityName);
//                    i.putExtra("risk", newRisk);
                    break;
                case Constants.MULTI_API:
                    Courier.deliverMessage(this, Constants.MULTI_API, 1);
//                    i.putExtra("response", newResponse);
                    break;
            }


//            LocalBroadcastManager.getInstance(this).sendBroadcast(i);

        }
    }

}
