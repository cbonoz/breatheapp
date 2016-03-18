package com.breatheplatform.beta.data;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.breatheplatform.beta.ClientPaths;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.concurrent.TimeUnit;

/**
 * Created by cbono on 3/10/16.
 */
public class DataSendService extends IntentService implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    public static final String API_KEY = "I3jmM2DI4YabH8937pRwK7MwrRWaJBgziZTBFEDTpec";
    private static final String TAG = "DataSendService";
    private GoogleApiClient mGoogleApiClient;

    public DataSendService() {
        super("DataSendService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        long t = intent.getLongExtra("time", ClientPaths.NO_VALUE);
        float[] values = intent.getFloatArrayExtra("values");
    }


    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended");
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected to Mobile - " + ClientPaths.mobileNodeId);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(TAG, "SendPostToMobile: Connection Failed");
    }



    private Boolean routeDataToMobile(String data, String urlString) {

        Log.d(TAG, "Called sendDataToMobile with " + urlString);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        ConnectionResult connectionResult = mGoogleApiClient.blockingConnect(10, TimeUnit.SECONDS);

        // Extract the payload from the message

        if (this.mGoogleApiClient.isConnected()) {

            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/data-api");
            putDataMapRequest.getDataMap().putString(data, "data");
//            putDataMapRequest.getDataMap().putString(Constants.KEY_TITLE, "title");
            PutDataRequest request = putDataMapRequest.asPutDataRequest();

            // push data to wear app here
            Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                    .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                        @Override
                        public void onResult(DataApi.DataItemResult dataItemResult) {
                            if (!dataItemResult.getStatus().isSuccess()) {
                                Log.e(TAG, "Failed to set the data, status: " + dataItemResult.getStatus().getStatusCode());
                            } else {
                                // get here, but no message received from wear
                                Log.i(TAG, "SUCCESSFUL RESPONSE RECEIVED FROM MOBILE " + ClientPaths.mobileNodeId);

                            }
                            mGoogleApiClient.disconnect();
                        }
                    });

        } else {
            Log.e(TAG, "no Google API Client connection");
        }
        return true;
    }




//
//    private void createDataPostRequest() {
//        Log.d(TAG, "createDataPostRequest");
//        JSONObject jsonBody = new JSONObject();
//
//        String sensorDataString = "";
//        byte[] encSensorData;
//
//        try {
//
//            // \n becomes the delimiter on the server to split data entries
//            sensorDataString = "";
////
////
////            if (ClientPaths.encrypting && hybridEncrypter!=null) {
////                Log.d(TAG, "pre-encrypted length: " + sensorDataString.length());
//////                sensorDataString = new String(hybridEncrypter.stringEncrypter(sensorDataString));
////                encSensorData = hybridEncrypter.stringEncrypter(sensorDataString);
////                Log.d(TAG, "encrypted length: " + encSensorData.length);
////                jsonBody.put("data", encSensorData);
////
////                if (writing) {
////                    writeDataToFile(sensorDataString, sensorFile, false);
////                    hybridEncrypter.fileEncrypter(sensorDirectory, encSensorDirectory, false);//change false to true for append
////                    Log.d(TAG, "Wrote to Sensorfile, size now: " + sensorFile.length() + "B");
////                }
////            } else {
////                jsonBody.put("data", sensorDataString);
////            }
////
////
////            if (writing) {
////                writeDataToFile(sensorDataString, sensorFile, false);
////                Log.d(TAG, "Wrote to Sensorfile, size now: " + sensorFile.length() /1024 + "kB");
////            }
//
//            jsonBody.put("data", sensorDataString);
//
//            if (ClientPaths.SUBJECT_ID == ClientPaths.NO_VALUE)
//                ClientPaths.SUBJECT_ID = ClientPaths.getSubjectID();
//
//            jsonBody.put("subject_id", ClientPaths.SUBJECT_ID);
//            jsonBody.put("key", API_KEY);
//            jsonBody.put("connection", ClientPaths.connectionInfo);
//
//            //data is the only object that needs to be encrypted
//
//
//        } catch (Exception e) {
//            Log.e(TAG, "Error creating jsonBody");
//            e.printStackTrace();
//            return;
//        }
//
//
//        String data = jsonBody.toString();
//        //start post
//
//
//
//        int statusCode = 0;
//        InputStream is=null;
//        OutputStream os;
//        HttpURLConnection conn=null;
//
//        String result = null;
//
//
//
//
//        try {
//            String currentNetwork = ClientPaths.connectionInfo;
//            Log.d(TAG, "Connection: " + currentNetwork);
//
////            if (currentNetwork==null) {
////                return "0";
////            }
//
//            //if proxy, route request to phone
//            if (currentNetwork.equals("PROXY")) {
//                //ClientPaths.sendDataToMobile(data, urlString);
////                routeDataToMobile()
//                routeDataToMobile(data, urlString);
//                return;
//
////                String proxyString = Settings.Global.getString(ClientPaths.mainContext.getContentResolver(), Settings.Global.HTTP_PROXY);
////                if (proxyString != null) {
////
////                    String proxyAddress = proxyString.split(":")[0];
////                    int proxyPort = Integer.parseInt(proxyString.split(":")[1]);
////                    Log.d(TAG, "Proxyinfo: " + proxyAddress + " " + proxyPort);
////
////                    Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyAddress, proxyPort));
////                    conn = (HttpURLConnection) url.openConnection(proxy);
////                } else {
////                    Log.d(TAG, "No Proxyinfo found");
////                    conn = (HttpURLConnection) url.openConnection();
////                }
//
//
//            } else {
//                conn = (HttpURLConnection) url.openConnection();
//            }
//
//
////            conn = (HttpURLConnection) url.openConnection();
//            long dataLength = data.getBytes().length;
//            conn.setReadTimeout(10000 /*milliseconds*/);
//            conn.setConnectTimeout(15000 /* milliseconds */);
//            conn.setRequestMethod("POST");
//            conn.setDoInput(true);
//            conn.setDoOutput(true);
//            conn.setFixedLengthStreamingMode(dataLength);
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
//
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
//
//            Log.i(TAG, "Response: " + result);
//            Log.i(TAG, "From " + urlString);
//
//            if (result!=null) {
//                if (result.contains("done")) {
//                    Log.d(TAG, "Successful data post");
//                    //clear saved data files
//                    //ClientPaths.writeDataToFile("", ClientPaths.sensorFile, false); //clears file
//                    //ClientPaths.writeDataToFile("", ClientPaths.encSensorFile, false); //clears enc sensorfile
//                }
//            }
//
//
//        } catch (Exception e) {
//            Log.e(TAG, "[Handled] Returning from SensorAddService - Could not Connect to Internet (timeout)");
//            e.printStackTrace();
//
//        } finally {
//            if (conn != null)
//                conn.disconnect();
//            //clear sensorDataFile if needed
//            if (ClientPaths.sensorFile.length()>0) {
//                Log.d(TAG, "clearing sensorDataFile");
//                ClientPaths.writeDataToFile("", ClientPaths.sensorFile, false);
//            }
//
////            clearData();
//
//        }
//    }

    //Mobile COMM Method




}
