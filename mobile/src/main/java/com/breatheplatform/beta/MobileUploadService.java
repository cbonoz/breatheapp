package com.breatheplatform.beta;

import android.app.IntentService;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPOutputStream;

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

    private static URL multiUrl = createURL(ClientPaths.BASE + ClientPaths.MULTI_API);
    private static URL riskUrl = createURL(ClientPaths.BASE + ClientPaths.RISK_API);

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

    private DeviceClient client;

    @Override
    protected void onHandleIntent(Intent intent) {
        // Gets data from the incoming Intent

        client = DeviceClient.getInstance(this);

        String data = intent.getStringExtra("data");
        String urlCase = intent.getStringExtra("url");

        int statusCode = 0;
        InputStream is = null;
        OutputStream os;
//        GZIPOutputStream os;
        HttpURLConnection conn = null;

        String result = null;

        try {



            switch(urlCase) {
                case ClientPaths.RISK_API:
                    conn = (HttpURLConnection) riskUrl.openConnection();
                    break;
                case ClientPaths.MULTI_API:
                    conn = (HttpURLConnection) multiUrl.openConnection();
                    break;
                case ClientPaths.SUBJECT_API:
                    URL url = createURL(ClientPaths.BASE + ClientPaths.SUBJECT_API);
                    if (url!=null)
                        conn = (HttpURLConnection) url.openConnection();
                    else {
                        Log.e(TAG, "Could not open Subject_API url");
                        return;
                    }
                    break;
                default:
                    Log.e(TAG, "Unexpected url case for mobile post message: " + urlCase);
                    return;
            }

            byte[] dataBytes = data.getBytes("ISO-8859-1");

            Log.d(TAG, "Data: " + data);
            Log.d(TAG, "Connecting to: " + conn.getURL().toString());

            conn.setReadTimeout(10000 /*milliseconds*/);
            conn.setConnectTimeout(15000 /* milliseconds */);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(dataBytes.length);
            conn.setRequestProperty("connection", "close"); // disables Keep Alive
            //conn.setChunkedStreamingMode(0);

            //make some HTTP header nicety
            conn.setRequestProperty("Content-Type", "gzip");
            conn.setRequestProperty("Accept-Encoding", "gzip");
//            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");

            conn.connect();

            os = new BufferedOutputStream(new GZIPOutputStream(conn.getOutputStream()));
//            os = new BufferedOutputStream(conn.getOutputStream()); //was this

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
            } catch (Exception e) {
                Log.i(TAG, "Error reading InputStream");
                result = null;
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (Exception e) {
                        Log.i(TAG, "Error closing InputStream");
                        e.printStackTrace();
                    }
                }
            }

            Log.i(TAG, "Response: " + result);
            Log.i(TAG, "From " + urlCase);

            switch (urlCase) {
                case ClientPaths.SUBJECT_API:
                    try {
                        String jsonString = result.substring(result.indexOf("{"), result.indexOf("}") + 1);
                        final JSONObject resJson = new JSONObject(jsonString);
                        int newID = Integer.parseInt(resJson.getString("subject_id"));
                        Log.i(TAG, "Setting new SubjectID: " + newID);
//                        ClientPaths.setSubjectID(newID);
                        //send subject ID back to watch (or potentially store on phone
                        Log.d(TAG, "Registered " + newID);
                    } catch (Exception e) {
                        Log.e(TAG, "[Handled] Error response from subject api");
                        Log.e(TAG, statusCode + ": JSON Parse Error");
                    }


                case ClientPaths.RISK_API:
                    try {
                        String jsonString = result.substring(result.indexOf("{"), result.indexOf("}") + 1);
                        final JSONObject resJson = new JSONObject(jsonString);
                        newRisk = Integer.parseInt(resJson.getString("risk"));
                        Log.i(TAG, "Setting new riskLevel: " + newRisk);
//                        ClientPaths.setRiskLevel(newRisk);

                    } catch (Exception e) {
                        newRisk = ClientPaths.NO_VALUE;
                        Log.e(TAG, "[Handled] Error response from risk api");

                    }
                    break;

                case ClientPaths.MULTI_API:
                    try {
                        String jsonString = result.substring(result.indexOf("{"), result.indexOf("}") + 1);
//                        Log.d(TAG, "MULTI_API response: " + jsonString);
//                        final JSONObject resJson = new JSONObject(jsonString);
                        newResponse = jsonString;


//                        ClientPaths.setRiskLevel(newRisk);

                    } catch (Exception e) {

                        Log.e(TAG, "[Handled] Error response from multi api");

                    }
                    break;
//                case ClientPaths.PUBLIC_KEY_API:
//                    try {
//                        String jsonString = result.substring(result.indexOf("{"),result.indexOf("}")+1);
//                        final JSONObject resJson = new JSONObject(jsonString);
//                        String key = "";
//                        ClientPaths.writeDataToFile(key, ClientPaths.publicKeyFile, false);
//                        ClientPaths.createEncrypter();
//                        break;
//                    } catch (Exception e) {
//
//                        Log.e(TAG, "[Handled] Error response from key api");
//                        return statusCode + ": JSON Parse Error";
//                    }
//

            }


        } catch (Exception e) {

            Log.e(TAG, "[Handled] Could not Connect to Internet (" + urlCase + ")");
            newRisk = ClientPaths.NO_VALUE;

        } finally {

            if (conn != null)
                conn.disconnect();

            Intent i = new Intent("upload-done");
            i.putExtra("url", urlCase);
            switch (urlCase) {
                case ClientPaths.RISK_API:
                    Log.d(TAG, "returning from phone RISK_API - value " + newRisk);
                    i.putExtra("risk", newRisk);
                    break;
                case ClientPaths.MULTI_API:
                    i.putExtra("response", newResponse);
                    break;
            }

            LocalBroadcastManager.getInstance(this).sendBroadcast(i);

        }
    }

}
