package com.breatheplatform.beta;
import android.net.Uri;
import android.util.Log;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;

/**
 * Created by cbono on 2/6/16.
 */


public class ReceiverService extends WearableListenerService {
    private static final String TAG = "ReceiverService";


    @Override
    public void onCreate() {
        super.onCreate();

    }

    @Override
    public void onPeerConnected(Node peer) {
        super.onPeerConnected(peer);

        Log.i(TAG, "Connected: " + peer.getDisplayName() + " (" + peer.getId() + ")");
    }

    @Override
    public void onPeerDisconnected(Node peer) {
        super.onPeerDisconnected(peer);

        Log.i(TAG, "Disconnected: " + peer.getDisplayName() + " (" + peer.getId() + ")");
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.d(TAG, "Mobile receiver onDataChanged()");

        for (DataEvent dataEvent : dataEvents) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                DataItem dataItem = dataEvent.getDataItem();
                Uri uri = dataItem.getUri();
                String path = uri.getPath();

                createPostRequest(DataMapItem.fromDataItem(dataItem).getDataMap());

//                if (path.startsWith("/sensors/")) {
//                    unpackSensorData(
//                            DataMapItem.fromDataItem(dataItem).getDataMap()
//                    );
//                } else if (path.startsWith("/subject/")) {
//                    return;
//                }
            }
        }
    }

    private void createPostRequest(DataMap dataMap) {
        Log.d(TAG, "Mobile createPostRequest with: " + dataMap.toString());

        String data = dataMap.getString("data");
        String urlString = dataMap.getString("url");

        Log.d(TAG, "data: " + data);
        Log.d(TAG, "urlString: " + urlString);



        int statusCode = 0;
        InputStream is=null;
        OutputStream os=null;
        HttpURLConnection conn=null;

        String result = null;
//
//
//
//        try {
//            URL url = new URL(urlString);
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
//            Log.i(TAG, "Response: " + result);
//            Log.i(TAG, "From " + urlString);
//
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
//                            ClientPaths.writeDataToFile("", ClientPaths.sensorFile, false); //clears file
//                            return "Sent Data: Cleared Sensor data cache";
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
//        } catch (Exception e) {
//
//            Log.e(TAG, "[Handled] Returning from Upload Task - Could not Connect to Internet (timeout)");
//            newRisk = ClientPaths.NO_VALUE;
//            writing = true;
//            //if exception thrown in sensor post, cache data to file for next send
////            if (urlString.equals(ClientPaths.MULTI_FULL_API)) {
////                Boolean res = ClientPaths.writeDataToFile(data, ClientPaths.sensorFile, true);
////                if (res) {
////                    Log.d(TAG, "Appended " + data.length() + " data points to " + ClientPaths.sensorFile.toString());
////                }
////            }
//
//
//        } finally {
//
//            if (conn != null)
//                conn.disconnect();
//
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
//            } else if (urlString.equals(ClientPaths.MULTI_FULL_API)) {
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
//
//
//        }
//        return statusCode+"";
//
    }
}
