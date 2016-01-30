//package com.example.android.google.wearable.app.data;
//
//import android.app.IntentService;
//import android.content.Intent;
//import android.util.Log;
//
//import com.example.android.google.wearable.app.ClientPaths;
//import com.example.android.google.wearable.app.MainActivity;
//
//import org.json.JSONObject;
//
//import java.io.BufferedInputStream;
//import java.io.BufferedOutputStream;
//import java.io.BufferedReader;
//import java.io.InputStream;
//import java.io.InputStreamReader;
//import java.io.OutputStream;
//import java.net.HttpURLConnection;
//import java.net.URL;
//
///**
// * Created by cbono on 1/14/16.
// */
//public class SensorUploadService extends IntentService {
//
//    public static final String PARAM_IN_MSG = "imsg";
//    public static final String PARAM_OUT_MSG = "omsg";
//    private static final String urlString = ClientPaths.BASE + ClientPaths.MULTI_FULL_API;
//    private static final String TAG = "SensorUploadService";
//
//    private static URL url;
//
//    public SensorUploadService() {
//        super("SensorUploadService");
//
//        try {
//            url = new URL(urlString);// URL(MULTI_FULL_API);
//        } catch (Exception e) {
//            Log.d(TAG, "Error creating URL");
//            e.printStackTrace();
//        }
//
//    }
//
//    @Override
//    protected void onHandleIntent(Intent intent) {
//
//        String data = intent.getStringExtra(PARAM_IN_MSG);
//        int statusCode;
//        InputStream is=null;
//        OutputStream os=null;
//        HttpURLConnection conn=null;
//
//        String result = null;
//
//        Log.i(url.toString(), "NOW Sending: " + data);
////        if (urlString.equals(ClientPaths.MULTI_FULL_API)) {
////            ClientPaths.writeDataToFile(data, ClientPaths.sensorFile,false);
////            Log.d(TAG, "write data to sensorFile");
////        }
//
////        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
////        lock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "LockTag");
////        lock.acquire();
//
//        try {
//            conn = (HttpURLConnection) url.openConnection();
//            conn.setReadTimeout(10000 /*milliseconds*/);
//            conn.setConnectTimeout(15000 /* milliseconds */);
//            conn.setRequestMethod("POST");
//            conn.setDoInput(true);
//            conn.setDoOutput(true);
//            conn.setFixedLengthStreamingMode(data.getBytes().length);
//            conn.setRequestProperty("connection", "close"); // disables Keep Alive
//            //conn.setChunkedStreamingMode(0);
//
//            //make some HTTP header nicety
//            conn.setRequestProperty("Content-Type", "application/json");
//            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
//
//            conn.connect();
//
//            os = new BufferedOutputStream(conn.getOutputStream());
//
//            os.write(data.getBytes());
//            os.flush();
//            os.close();
//
//            statusCode = conn.getResponseCode();
//
//            StringBuffer sb = new StringBuffer();
//
//            try {
//                is = new BufferedInputStream(conn.getInputStream());
//                BufferedReader br = new BufferedReader(new InputStreamReader(is));
//                String inputLine = "";
//                while ((inputLine = br.readLine()) != null) {
//                    sb.append(inputLine);
//                }
//                result = sb.toString();
//            }    catch (Exception e) {
//                Log.i(TAG, "Error reading InputStream");
//                result = null;
//            }
//            finally {
//                if (is != null) {
//                    try {
//                        is.close();
//                    }
//                    catch (Exception e) {
//                        Log.i(TAG, "Error closing InputStream");
//                        e.printStackTrace();
//                    }
//                }
//            }
//
//            Log.i(TAG, "Response " + statusCode + ": " + result);
//            Log.i(TAG, "From " + urlString);
//
//            if (result!=null) {
//                if (result.contains("done")) {
//                    ClientPaths.writeDataToFile("",ClientPaths.sensorFile,false); //clears file
//                    Log.d(TAG,"Sent Data: Cleared Sensor data cache");
//                }
//            }
//
//
//
//        } catch (Exception e) {
//            Log.d(TAG, "Could not Connect to Internet");
//            e.printStackTrace();
//
//        } finally {
//
//            if (conn != null)
//                conn.disconnect();
//
//
//            }
//        }
//
//    }
//}
