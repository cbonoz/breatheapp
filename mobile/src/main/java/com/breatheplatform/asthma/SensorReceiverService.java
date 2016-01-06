package com.breatheplatform.asthma;

/*
THIS IS THE MAIN SERVICE CONTROLLER FOR THE BREATHE APP (RUNNING ON MOBILE SIDE)
INCLUDES WEARABLE LISTENER AND LOCATION SERVICES

MODULE:
SensorHubService

PURPOSE:
1. Receives sensor information from wearable and bluetooth devices (dust sensor)
2. Posts the sensor data to the breathe server (including encryption) - spins off another thread to do this
3. Write the sensor data to local external phone storage indicated by SENSOR_DATA_FILE
4. Adds data to the sensorManager for main activity graphing (can be removed)
5. Merges Location data into sensor recordings

KEY VARIABLES:
api_key: key to authorize post submissions to the server (server URL controlled in the upload service)
sending: bool to control server data post or no post
writing: bool to control server data write or no write
SENSOR_DATA_FILE: Location of (encrypted local storage data), will be send to server periodically


SensorReceiver Service
TODO:
1. Current issue is that this service keeps restarting itself (calling onCreate over and over and over again
this disconnecting google play services
2. Also undesired sensors like Magnetic field and gyroscope are popping up in the sensor feed, even though not initializing those sensors on the watch...


For location API, if running on Android 6.0+ emulator and you have a targetSdkVersion of 23 or higher,
ACCESS_COARSE_LOCATION and ACCESS_FINE_LOCATION are part of the Android 6.0 runtime permission system.
Either revise your app to participate in this system, or drop your targetSdkVersion below 23.
Reverted target api to 22.

keep sensorreceiverservice only called on connectioncallback from watch (explains why getting called so many times)
move uploadtask into aan intent

 */


import android.content.Context;
import android.hardware.Sensor;
import android.net.Uri;
import android.os.PowerManager;
import android.util.Log;

import com.breatheplatform.asthma.data.SensorNames;
import com.breatheplatform.common.ClientPaths;
import com.breatheplatform.common.DataMapKeys;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.WearableListenerService;

import org.json.JSONObject;

public class SensorReceiverService extends WearableListenerService {
    private static final String TAG = "SensorDashboard/SensorReceiverService";
    private static final String APP_NAME = "AsthmaApp";

    private PowerManager.WakeLock mWakeLock;

    private RemoteSensorManager sensorManager;

    private SensorNames sensorNames;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Called " + TAG + " onCreate");



        PowerManager mgr = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ReceiverServices");


        sensorManager = RemoteSensorManager.getInstance(this);

        sensorNames = new SensorNames();

        mWakeLock.acquire();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mWakeLock != null)
            mWakeLock.release();
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
//        if (ClientPaths.currentLocation!=null) {
//            Log.d(TAG, "onDataChanged()");
//        } else {
//            Log.i(TAG, "Sensors initiated - Waiting on Connection to Location Services...");
//            return;
//        }


        for (DataEvent dataEvent : dataEvents) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                DataItem dataItem = dataEvent.getDataItem();
                Uri uri = dataItem.getUri();
                String path = uri.getPath();

                if (path.startsWith("/sensors/")) {
                    unpackSensorData(
                            Integer.parseInt(uri.getLastPathSegment()),
                            DataMapItem.fromDataItem(dataItem).getDataMap()
                    );
                }
            }
        }
    }

    private void unpackSensorData(int sensorType, DataMap dataMap) {
//        int accuracy = dataMap.getInt(DataMapKeys.ACCURACY);
//        long timestamp = dataMap.getLong(DataMapKeys.TIMESTAMP);
        float[] values = dataMap.getFloatArray(DataMapKeys.VALUES);
        Boolean validEvent = false;
        JSONObject jsonDataEntry = new JSONObject();
        JSONObject jsonValue = new JSONObject();
        String sensorName = sensorNames.getName(sensorType);

        //Log.d(TAG, "Received " + sensorName + " (" + sensorType + ") = " + Arrays.toString(values));

        try {

            switch (sensorType) {
                case (Sensor.TYPE_LINEAR_ACCELERATION):
                    jsonValue.put("x", values[0]);
                    jsonValue.put("y", values[1]);
                    jsonValue.put("z", values[2]);
                    jsonDataEntry.put("sensor_type", sensorType);
                    validEvent = true;
                    break;
                case (Sensor.TYPE_HEART_RATE):
                case (65562):
                    if (values[0]<=0) {
                        Log.d(TAG, "Received heart rate sensor ("+sensorName+") data=0 - skip");
                        return;
                    }
                    jsonDataEntry.put("sensor_type", 21);
                    jsonValue.put("v", values[0]);
                    validEvent = true;
                    break;
                case (Sensor.TYPE_AMBIENT_TEMPERATURE):
                    //case (Sensor.TYPE_STEP_COUNTER):
                    jsonValue.put("v", values[0]);
                    jsonDataEntry.put("sensor_type", sensorType);
                    validEvent = true;
                    break;


                default:
                    break;
            }



            jsonDataEntry.put("sensor_name", sensorName);

            jsonDataEntry.put("value", jsonValue);

            jsonDataEntry.put("timestamp", System.currentTimeMillis());
            jsonDataEntry.put("timezone", ClientPaths.getTimeZone());
            jsonDataEntry.put("sensor_id", ClientPaths.GARBAGE_SENSOR_ID);//will be changed to actual sensor (sensorType)
            //jsonDataEntry.put("sensor_id",sensorType);
            if (ClientPaths.currentLocation!=null) {
                jsonDataEntry.put("lat", ClientPaths.currentLocation.getLatitude());
                jsonDataEntry.put("long", ClientPaths.currentLocation.getLongitude());
                jsonDataEntry.put("accuracy", ClientPaths.currentLocation.getAccuracy());
            } else {
                jsonDataEntry.put("accuracy", "No Location Found");
            }

        } catch (Exception e) {
            Log.e(TAG, "error in creating jsonDataEntry");
            e.printStackTrace();
            return;
        }

        //if not one of the desired sensors

        if (!validEvent) {
            Log.d(TAG, "Encountered undesired sensor (" + sensorType + "): " + sensorName + ". skipping..");
            return;
        }

        Log.d(TAG, "Data received: " + jsonDataEntry.toString());

        ClientPaths.appendData(jsonDataEntry);
        ClientPaths.incrementCount();
    }
}

//
//public class SensorReceiverService extends WearableListenerService {
//    private static final String TAG = "SensorDashboard/SensorReceiverService";
//
//    private RemoteSensorManager sensorManager;
//    private SensorNames sensorNames;
//
//    @Override
//    public void onCreate() {
//        super.onCreate();
//        sensorNames = new SensorNames();
//        sensorManager = RemoteSensorManager.getInstance(this);
//    }
//
//    @Override
//    public void onPeerConnected(Node peer) {
//        super.onPeerConnected(peer);
//
//        Log.i(TAG, "Connected: " + peer.getDisplayName() + " (" + peer.getId() + ")");
//    }
//
//    @Override
//    public void onPeerDisconnected(Node peer) {
//        super.onPeerDisconnected(peer);
//
//        Log.i(TAG, "Disconnected: " + peer.getDisplayName() + " (" + peer.getId() + ")");
//    }
//
//    @Override
//    public void onDataChanged(DataEventBuffer dataEvents) {
//        Log.d(TAG, "onDataChanged()");
//
//        for (DataEvent dataEvent : dataEvents) {
//            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
//                DataItem dataItem = dataEvent.getDataItem();
//                Uri uri = dataItem.getUri();
//                String path = uri.getPath();
//
//                if (path.startsWith("/sensors/")) {
//                    unpackSensorData(
//                            Integer.parseInt(uri.getLastPathSegment()),
//                            DataMapItem.fromDataItem(dataItem).getDataMap()
//                    );
//                }
//            }
//        }
//    }
//
//    private void unpackSensorData(int sensorType, DataMap dataMap) {
//        int accuracy = dataMap.getInt(DataMapKeys.ACCURACY);
//        long timestamp = dataMap.getLong(DataMapKeys.TIMESTAMP);
//        float[] values = dataMap.getFloatArray(DataMapKeys.VALUES);
//
//        Log.d(TAG, "Received " + sensorNames.getName(sensorType) + " (" + sensorType + ") = " + Arrays.toString(values));
//
//        //sensorManager.addSensorData(sensorType, accuracy, timestamp, values);
//    }
//}
