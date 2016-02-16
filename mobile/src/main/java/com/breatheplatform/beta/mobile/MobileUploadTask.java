package com.breatheplatform.beta.mobile;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by cbono on 2/15/16.
 */
public class MobileUploadTask {

    private static final String TAG = MobileUploadTask.class.getSimpleName();

    private static final String CHARSET_UTF8 = "UTF-8";

    private URL url;

    private static String currentNetwork = "";

    private static Integer newRisk;
    private static String urlString;
    private static Context context;
    private static String data;

    //loading a json object could have a large amount of temporary data overhead if the app is not
    // connected to the internet and the sensors are running. Going to use a file approach, where the
    // server will process the files

    public MobileUploadTask(String urlName, Context c) {
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



    private void getNetwork() {
        if (context == null) return;
        try {
            //using ClientPaths.mainContext was giving issues here (throwing exception)
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            boolean isConnected = activeNetwork != null &&
                    activeNetwork.isConnectedOrConnecting();

            currentNetwork = activeNetwork.getTypeName();
            Log.d(TAG, "Current Network Found: " + currentNetwork);


        } catch (Exception e) {
            Log.e(TAG, "[Handled] No context for getNetwork..continuing");
            currentNetwork=null;
        }

    }



    protected String doInBackground(String... strings) {
        data = strings[0];

//        if (ClientPaths.writing) {
//            Boolean res = ClientPaths.writeDataToFile(data, ClientPaths.sensorFile, true);
//            if (res)
//                Log.d(TAG, "Appended " + data.length() + " data points to " + ClientPaths.sensorFile.toString());
//        }

        getNetwork();

        if (currentNetwork.equals("PROXY")) {
            Log.d(TAG, "Proxy detected - sending message");

//            if (ClientPaths.writing) {
//                Boolean res = ClientPaths.writeDataToFile(data, ClientPaths.sensorFile, true);
//                if (res)
//                    Log.d(TAG, "Appended " + data.length() + " data points to " + ClientPaths.sensorFile.toString());
//            }

            return "0";
        } else if (currentNetwork==null) {
            Log.d(TAG, "Network Check Result - Not Connected");
            return "0";
        }



//        if (ClientPaths.sensorFile.length() > 0) {
//            //append cached file data to post request
//            try {
//                String temp = ClientPaths.readDataFromFile(ClientPaths.sensorFile);
//                data=data+temp;
//                Log.d(TAG, "Successfully appended " + data.length() + "cached data bytes");
//
//            } catch (Exception e) {
//                Log.e(TAG, "[Handled] Error reading sensorFile");
//            }
//        }
//
//        //used for using data layer api (to route request through mobile device bluetooth)
//        if (currentNetwork.equals("PROXY")) {
//            ClientPaths.client.sendSensorData(data, urlString);
//            Log.d(TAG, "Detected Proxy: Routing request to phone");
//
//            return "Placeholder for Proxy Upload message";
//        }

        int statusCode = 0;
        InputStream is=null;
        OutputStream os=null;
        HttpURLConnection conn=null;

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

//            switch (urlString) {
//                case ClientPaths.SUBJECT_API:
//                    try {
//                        String jsonString = result.substring(result.indexOf("{"),result.indexOf("}")+1);
//                        final JSONObject resJson = new JSONObject(jsonString);
//                        int newID = Integer.parseInt(resJson.getString("subject_id"));
//                        Log.i(TAG, "Setting new SubjectID: " + newID);
//                        ClientPaths.setSubjectID(newID);
//                        return "Registered " + newID;
//                    }
//                    catch (Exception e) {
//                        e.printStackTrace();
//                        return statusCode + ": JSON Parse Error";
//                    }
//
//
//                case ClientPaths.RISK_API:
//                    try {
//                        String jsonString = result.substring(result.indexOf("{"),result.indexOf("}")+1);
//                        final JSONObject resJson = new JSONObject(jsonString);
//                        newRisk = Integer.parseInt(resJson.getString("risk"));
//                        Log.i(TAG, "Setting new riskLevel: " + newRisk);
////                        ClientPaths.setRiskLevel(newRisk);
//
//                    } catch (Exception e) {
//                        newRisk=ClientPaths.NO_VALUE;
//                        Log.e(TAG, "[Handled] Error response from risk api");
//                        return statusCode + ": JSON Parse Error";
//                    }
//                    break;
//                case ClientPaths.MULTI_FULL_API:
//                    if (result!=null) {
//                        if (result.contains("done")) {
//                            Log.d(TAG, "Successful data post");
//                            //ClientPaths.writeDataToFile("", ClientPaths.sensorFile, false); //clears file
//                            return "";
////                            return "Sent Data: Cleared Sensor data cache";
//                        }
//                    }
//                    break;
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
//
//            }
//
//
        } catch (Exception e) {

            Log.e(TAG, "[Handled] Returning from Upload Task - Could not Connect to Internet (timeout)");
            //newRisk = ClientPaths.NO_VALUE;

        } finally {

            if (conn != null)
                conn.disconnect();

//            if (urlString.equals(ClientPaths.RISK_API)) {
//                if (ClientPaths.mainContext!=null) {
//                    ((MainActivity) ClientPaths.mainContext).runOnUiThread(new Runnable() {
//                        public void run() {
//                            //Do something on UiThread
//                            Log.d(TAG, "updateRiskUI " + newRisk);
//                            ((MainActivity) ClientPaths.mainContext).updateRiskUI(newRisk, false);
//
//                        }
//                    });
//                }
//            }
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


        }
        return statusCode+"";
    }

//    @Override
//    protected void onPostExecute(String result){
//
//        if (result!=null)
//            Log.d("onPostExecute", result);
//    }

//    private BufferedWriter writeNewFile(String filePath) {
//
//        BufferedWriter writer = null;
//
//        try {
//            Log.i(TAG, "Attempting to open file descriptor: " + filePath);// + File.separator + fileName);
//
//            File file = new File(filePath);
//
//            FileWriter fileWriter = new FileWriter(file);
//
//            writer = new BufferedWriter(fileWriter);
//
//        } catch (Exception e) {
//            Log.d("error: writeNewFile",e.toString());
//            e.printStackTrace();
//            writing = false;
//            return null;
//        }
//
//        Log.i(TAG, "OPENED " + filePath);
//        return writer;
//
//    }

}
