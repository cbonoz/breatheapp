package com.breatheplatform.beta.services;

import android.app.IntentService;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.breatheplatform.beta.shared.Constants;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

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
    private static URL registerUrl = createURL(Constants.BASE + Constants.REG_CHECK_API);

    private static Integer newRisk;

    //loading a json object could have a large amount of temporary data overhead if the app is not
    // connected to the internet and the sensors are running. Going to use a file approach, where the
    // server will process the files

    private WifiManager wifiManager;
    private WifiManager.WifiLock lock;

    private static HttpsURLConnection conn = initConnection();

    class NullHostNameVerifier implements HostnameVerifier {

        @Override
        public boolean verify(String hostname, SSLSession session) {
            Log.i("RestUtilImpl", "Approving certificate for " + hostname);
            return true;
        }

    }

    private static HttpsURLConnection initConnection() {
//        Log.d(TAG, "init secure url connection");
//        HttpsURLConnection.setDefaultHostnameVerifier(new NullHostNameVerifier());
//        SSLContext context = SSLContext.getInstance("TLS");
//        context.init(null, new X509TrustManager[]{new X509TrustManager()}, new SecureRandom());
//        HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
        return null;
    }

    public MobileUploadService() {
        super("MobileUploadService");
    }



    @Override
    protected void onHandleIntent(Intent intent) {
//      Gets data from the incoming Intent
//      client = DeviceClient.getInstance(this);

        String data = intent.getStringExtra("data");
        String urlString = intent.getStringExtra("url");
//        int statusCode = 0;
        InputStream is = null;
        OutputStream os;

        String result = null;

        //determine connection endpoint
        try {
            switch(urlString) {
                case Constants.RISK_API:
                    conn = (HttpsURLConnection) riskUrl.openConnection();
                    break;
                case Constants.MULTI_API:
                    conn = (HttpsURLConnection) multiUrl.openConnection();
                    break;
                case Constants.REG_CHECK_API:
                    conn = (HttpsURLConnection) registerUrl.openConnection();
                    break;
                default:
                    Log.e(TAG, "Unexpected url case for mobile post message: " + urlString);
                    conn = null;
                    return;
            }
        } catch (Exception e) {
            e.printStackTrace();
            conn = null;
            return;
        }

        //start connection
        try {

            //Post Request Code follows
            Log.d(TAG, urlString + " Data: " + data);

            byte[] dataBytes = data.getBytes();//.getBytes("ISO-8859-1");

            Log.d(TAG, "data bytes length: " + dataBytes.length);
            Log.d(TAG, "Connecting to: " + conn.getURL().toString());

            // Create the SSL connection
            SSLContext sc;
            sc = SSLContext.getInstance("TLS");
            sc.init(null, null, new java.security.SecureRandom());
            conn.setSSLSocketFactory(sc.getSocketFactory());

            conn.setReadTimeout(10000 /*milliseconds*/);
            conn.setConnectTimeout(15000 /* milliseconds */);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(dataBytes.length);


            //make some HTTP header nicety
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");

            conn.connect();

            os = new BufferedOutputStream(conn.getOutputStream());

            os.write(dataBytes);
            os.flush();
            os.close();

//            statusCode = conn.getResponseCode();

            StringBuilder sb = new StringBuilder();

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

            Log.i(TAG, "Response: " + result + "\nFrom " + urlString);


        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "[Handled] Could not Connect to Internet (" + urlString + ")");
            newRisk = Constants.NO_VALUE;

        } finally {
            //process response result
            if (conn != null)
                conn.disconnect();

            Boolean success;
            //handle the response result
            switch (urlString) {
                case Constants.RISK_API:
                    try {
                        String jsonString = result.substring(result.indexOf("{"),result.indexOf("}")+1);
                        final JSONObject resJson = new JSONObject(jsonString);
                        newRisk = Integer.parseInt(resJson.getString("risk"));
                        Log.i(TAG, "Setting new riskLevel: " + newRisk);
                    } catch (Exception e) {
                        newRisk=Constants.NO_VALUE;
                        Log.e(TAG, "[Handled] Error response from risk api");
                    }

                    if (newRisk == null)
                        newRisk = Constants.NO_VALUE;
                    Log.d(TAG, "returning from phone RISK_API - value " + newRisk);
                    //send risk level back to wear
                    Courier.deliverMessage(this, Constants.RISK_API, newRisk);
                    break;
                case Constants.MULTI_API:

                    success = (result!=null && result.contains("done"));
                    Log.d(TAG, "Multi Api Success: " + success);
                    //send multi api success/failure status back to wear
                    Courier.deliverMessage(this, Constants.MULTI_API, success);
                    break;
                case Constants.REG_CHECK_API:

                    final JSONObject resJson;
                    success = false;
                    try {
                        String jsonString = result.substring(result.indexOf("{"), result.indexOf("}") + 1);
                        resJson = new JSONObject(jsonString);
                        success = resJson.getString("success").equals("True");

                    } catch (Exception e) {
                        e.printStackTrace();
                        success=false;
                    } finally {
                        //broadcast success/fail of registration attempt
                        Intent i = new Intent(Constants.REGISTER_EVENT);
                        i.putExtra("success", success);
                        i.putExtra("subject_id",intent.getStringExtra("subject_id"));
                        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
                    }
                    break;
            }
//            LocalBroadcastManager.getInstance(this).sendBroadcast(i);

        }
    }

}
