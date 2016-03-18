package com.breatheplatform.beta.messaging;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.util.Log;

import com.breatheplatform.beta.ClientPaths;
import com.breatheplatform.beta.MainActivity;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
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


    private Integer newRisk=ClientPaths.NO_VALUE;
    private String urlString;
    private Context context;
    private String data;

    //loading a json object could have a large amount of temporary data overhead if the app is not
    // connected to the internet and the sensors are running. Going to use a file approach, where the
    // server will process the files

    public UploadTask(String urlName, Context c) {
        context = c;
        if (context!=null)
            Log.d(TAG, "context: " + context.toString());
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
//    private static int linkSpeed = ClientPaths.NO_VALUE;



    protected String doInBackground(String... strings) {
        data = strings[0];

//        if (ClientPaths.writing) {
//            Boolean res = ClientPaths.writeDataToFile(data, ClientPaths.sensorFile, true);
//            if (res)
//                Log.d(TAG, "Appended " + data.length() + " data points to " + ClientPaths.sensorFile.toString());
//        }


        int statusCode = 0;
        InputStream is=null;
        OutputStream os;
        HttpURLConnection conn=null;

        String result = null;

        Log.i(url.toString(), "NOW Sending: "+data);

        try {
            String currentNetwork = ClientPaths.connectionInfo;
            Log.d(TAG, "Connection: " + currentNetwork);

//            if (currentNetwork==null) {
//                return "0";
//            }

             if (currentNetwork.equals("PROXY")) {
                //ClientPaths.sendDataToMobile(data, urlString);




//
//                String proxyString = Settings.Global.getString(ClientPaths.mainContext.getContentResolver(), Settings.Global.HTTP_PROXY);
//                if (proxyString != null) {
//                    String proxyAddress = proxyString.split(":")[0];
//                    int proxyPort = Integer.parseInt(proxyString.split(":")[1]);
//                    Log.d(TAG, "Proxyinfo: " + proxyAddress + " " + proxyPort);
//
//                    Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyAddress, proxyPort));
//                    conn = (HttpURLConnection) url.openConnection(proxy);
//                }
//                else {
//                    Log.d(TAG, "No Proxyinfo found");
//                    conn = (HttpURLConnection) url.openConnection();
//                }


            } else {
                conn = (HttpURLConnection) url.openConnection();
                 
            }



//            conn = (HttpURLConnection) url.openConnection();
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

            switch (urlString) {
                case ClientPaths.SUBJECT_API:
                    try {
                        String jsonString = result.substring(result.indexOf("{"),result.indexOf("}")+1);
                        final JSONObject resJson = new JSONObject(jsonString);
                        int newID = Integer.parseInt(resJson.getString("subject_id"));
                        Log.i(TAG, "Setting new SubjectID: " + newID);
                        ClientPaths.setSubjectID(newID);
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
                        Log.i(TAG, "Setting new riskLevel: " + newRisk);
//                        ClientPaths.setRiskLevel(newRisk);

                    } catch (Exception e) {
                        newRisk=ClientPaths.NO_VALUE;
                        Log.e(TAG, "[Handled] Error response from risk api");
                        return statusCode + ": JSON Parse Error";
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

            Log.e(TAG, "[Handled] Could not Connect to Internet (" + urlString + ")");
            newRisk = ClientPaths.NO_VALUE;

        } finally {

            if (conn != null)
                conn.disconnect();

            if (urlString.equals(ClientPaths.RISK_API)) {
                if (ClientPaths.mainContext!=null) {
                    ((MainActivity) ClientPaths.mainContext).runOnUiThread(new Runnable() {
                        public void run() {
                            //Do something on UiThread
                            Log.d(TAG, "updateRiskUI " + newRisk);
                            ((MainActivity) ClientPaths.mainContext).updateRiskUI(newRisk);

                        }
                    });
                }
            }
//            else if (urlString.equals(ClientPaths.MULTI_FULL_API)) {
//                if (ClientPaths.mainContext!=null) {
//                    ((MainActivity) ClientPaths.mainContext).runOnUiThread(new Runnable() {
//                        public void run() {
//                            //Do something on UiThread
//                            Toast.makeText(ClientPaths.mainContext, "Sent Data", Toast.LENGTH_SHORT).show();
//
//                        }
//                    });
//                }
//            }

            //clear sensorDataFile if needed
            if (ClientPaths.sensorFile.length()>0) {
                Log.d(TAG, "clearing sensorDataFile");
                ClientPaths.writeDataToFile("", ClientPaths.sensorFile, false);
            }


        }
        return statusCode+"";
    }

    @Override
    protected void onPostExecute(String result){

        if (result!=null)
            Log.d("onPostExecute", result);
    }

}
