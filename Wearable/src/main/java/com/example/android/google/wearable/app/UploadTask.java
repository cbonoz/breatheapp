package com.example.android.google.wearable.app;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;


/**
 * Created by cbono on 11/10/15.
 *
 * UploadService provides an interface for uploading Sensor Data to the GEO_MEASUREMENT API
 * on the breatheplatform website (currently supports single and multi-datum requests
 * Will spawn a new background thread for completing the send request via postJsonToServer
 *
 *  Pushed file writing to the background process thread to sensor service can be freed to focus on doing sensor related tasks
 *
 *  Posts can be blocked if watch is connected to on phone.
 *  If your watch has Wifi and it is set up correctly, then you can make network calls on your watch when your watch is disconnected from the phone; when you connect to your phone via BT, wifi will be disabled. While it is enabled, you should be able to treat that as a usual network connectivity and make network calls. But keep in mind that if you write an app that relies on this, your app will fail to work when it gets connected to a phone so you need to handle that case and provide an alternative for your app to get the same data (i.e. using the phone's connectivity).
 */

public class UploadTask extends AsyncTask<String, Void, String> {

    private static final String TAG = UploadTask.class.getSimpleName();

    private static final String CHARSET_UTF8 = "UTF-8";

    private URL url;

    private static Boolean writing=true;
    private static Integer newRisk;
    private static String urlString;
    private static Context context;
    private static String data;

    //loading a json object could have a large amount of temporary data overhead if the app is not
    // connected to the internet and the sensors are running. Going to use a file approach, where the
    // server will process the files

    public UploadTask(String urlName, Context c) {
        context = c;

        urlString = urlName;
        data="";

        try {
            url = new URL(urlString);// URL(MULTI_FULL_API);
        } catch (Exception e) {
            Log.d(TAG, "Error creating URL");
            e.printStackTrace();
        }

    }

    private WifiManager wifiManager;
    private WifiManager.WifiLock lock;

    protected String doInBackground(String... strings) {

        int statusCode = 0;
        InputStream is=null;
        OutputStream os=null;
        HttpURLConnection conn=null;
        data = strings[0];
        String result = null;

        Log.i(url.toString(), "NOW Sending: "+data);

        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(10000 /*milliseconds*/);
            conn.setConnectTimeout(15000 /* milliseconds */);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(data.getBytes().length);
            conn.setRequestProperty("connection", "close"); // disables Keep Alive
            //conn.setChunkedStreamingMode(0);

            //make some HTTP header nicety
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");

            conn.connect();

            os = new BufferedOutputStream(conn.getOutputStream());

            os.write(data.getBytes());
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

            //handle response depending on request
            switch (urlString) {
                case ClientPaths.SUBJECT_API:
                    try {
                        String jsonString = result.substring(result.indexOf("{"),result.indexOf("}")+1);
                        final JSONObject resJson = new JSONObject(jsonString);
                        int newID = Integer.parseInt(resJson.getString("subject_id"));
                        Log.i(TAG, "Setting new SubjectID: " + newID);
                        ClientPaths.setSubjectID(newID);
                        ClientPaths.requestAndSaveKey();
                        return "Registered " + newID;

                    }
                    catch (Exception e) {
                        e.printStackTrace();
                        return statusCode + ": JSON Parse Error";
                    }
                case ClientPaths.RISK_API:
                    try {
                        String jsonString = result.substring(result.indexOf("{"),result.indexOf("}")+1);
                        final JSONObject resJson = new JSONObject(jsonString);
                        newRisk = Integer.parseInt(resJson.getString("risk"));
                        Log.d(TAG, "Setting new riskLevel: " + newRisk);
//                        ClientPaths.setRiskLevel(newRisk);

                    } catch (Exception e) {
                        Log.e(TAG, "[Handled] Error response from risk api");
                        newRisk=ClientPaths.NO_VALUE;
                        return statusCode + ": JSON Parse Error";
                    }
                case ClientPaths.MULTI_FULL_API:
                    if (result!=null) {
                        if (result.contains("done")) {
                            ClientPaths.writeDataToFile("",ClientPaths.sensorFile,false); //clears file
                            return "Sent Data: Cleared Sensor data cache";
                        }
                    }
                case ClientPaths.KEY_API:
                    if (result!=null) {
                        String pk="";
                        //extract pk from response
                        //write the received key to the external key file on the wearable
                        ClientPaths.writeDataToFile(pk,ClientPaths.publicKeyFile,false);
                        ClientPaths.createEncrypter();
                        return "Registered Key and Encrypter";
                    }
            }


        } catch (Exception e) {
            Log.d(TAG, "Could not Connect to Internet");
            e.printStackTrace();
            newRisk = ClientPaths.NO_VALUE;
            writing = true;
            //if exception thrown in sensor post, cache data to file for next send
            if (urlString.equals(ClientPaths.MULTI_FULL_API)) {
                Boolean res = ClientPaths.writeDataToFile(data, ClientPaths.sensorFile, true);
                if (res) {
                    Log.d(TAG, "Appended " + data.length() + " data points to " + ClientPaths.sensorFile.toString());
                }
            }


        } finally {

            if (conn!=null)
                conn.disconnect();

            if (urlString.equals(ClientPaths.RISK_API) && context!=null) {
                ((MainActivity) context).runOnUiThread(new Runnable() {
                    public void run() {
                        //Do something on UiThread
                        ((MainActivity)(context)).updateRiskUI(newRisk, false);
                    }
                });
            }
        }

        return statusCode+"";
    }

    @Override
    protected void onPostExecute(String result){

        if (result!=null)
            Log.d("onPostExecute", result);

    }

    private BufferedWriter writeNewFile(String filePath) {

        BufferedWriter writer = null;

        try {
            Log.i(TAG, "Attempting to open file descriptor: " + filePath);// + File.separator + fileName);

            File file = new File(filePath);

            FileWriter fileWriter = new FileWriter(file);

            writer = new BufferedWriter(fileWriter);


        } catch (Exception e) {
            Log.d("error: writeNewFile",e.toString());
            e.printStackTrace();
            writing = false;
            return null;
        }

        Log.i(TAG, "OPENED " + filePath);
        return writer;

    }
}