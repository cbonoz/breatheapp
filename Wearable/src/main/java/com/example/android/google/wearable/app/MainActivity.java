/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.google.wearable.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.speech.RecognizerIntent;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.example.android.google.wearable.app.bluetooth.HexAsciiHelper;
import com.example.android.google.wearable.app.bluetooth.RFduinoService;
import com.example.android.google.wearable.app.data.DustData;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import org.eazegraph.lib.charts.PieChart;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends WearableActivity implements BluetoothAdapter.LeScanCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener, SensorEventListener
{
    private static final SimpleDateFormat AMBIENT_DATE_FORMAT =
            new SimpleDateFormat("hh:mm aa", Locale.US);
            //new SimpleDateFormat("HH:mm", Locale.US);

    private static final String TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 1;

    private PieChart mPieChart;
    //https://github.com/blackfizz/EazeGraph
//    https://github.com/blackfizz/EazeGraph/blob/b4b0785e1e080f5ff6e826aa105deb49b8451fbc/EazeGraphLibrary/src/main/java/org/eazegraph/lib/charts/PieChart.java

    private String receiveBuffer;
    private RFduinoService rfduinoService;
    private BluetoothDevice bluetoothDevice;
    private GoogleApiClient mGoogleApiClient;
    private ScheduledExecutorService mScheduler;

    // State machine
    final private static int STATE_BLUETOOTH_OFF = 1;
    final private static int STATE_DISCONNECTED = 2;
    final private static int STATE_CONNECTING = 3;
    final private static int STATE_CONNECTED = 4;


    private static final int SENS_LINEAR_ACCELERATION = Sensor.TYPE_LINEAR_ACCELERATION;
    private static final int SENS_HEARTRATE = Sensor.TYPE_HEART_RATE;
    private static final int RISK_TASK_PERIOD=10000;
    private int state;
    private int subjectID;

    BluetoothAdapter bluetoothAdapter;

    BluetoothSocket spiroSocket=null;
    BluetoothDevice spiroDevice =null;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    volatile boolean stopWorker;

    //Sensor related

    private SensorManager mSensorManager;
    private Sensor mHeartrateSensor;


    private static final int NAME_REQUEST_CODE = 1;
    private static final int ID_REQUEST_CODE = 2;

    private final Random RAND = new Random();

    private ToggleButton sensorToggleButton;
    private ToggleButton spiroToggleButton;
    private Boolean sensorChecked;
    private Boolean spiroChecked;

    private Boolean bluetoothConnected;
    private Boolean isConnected;
    private static String statusString;

    private TextView mClockView;
    private TextView riskText;
    private TextView riskView;
    private TextView subjectView;
    private ImageView smileView;

    private static TimerTask riskTask;
    private static Timer riskTimer;
    private static int lastRiskValue;
    private static String mobileNodeId;

    private WifiManager wifiManager;
    private WifiManager.WifiLock wifiLock;

    public void keepWiFiOn(Context context, boolean on) {
        if (wifiLock == null) {
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL, TAG);
                wifiLock.setReferenceCounted(true);
            }
        }
        if (wifiLock != null) { // May be null if wm is null
            if (on) {
                wifiLock.acquire();
                Log.d(TAG, "Acquired WiFi lock");
            } else if (wifiLock.isHeld()) {
                wifiLock.release();
                Log.d(TAG, "Released WiFi lock");
            }
        }
    }

    public void onCreate(Bundle b) {
        super.onCreate(b);

        ConnectivityManager cm =
                (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        isConnected = (activeNetwork != null && activeNetwork.isConnectedOrConnecting());

        Boolean wifiConnected = isConnected && (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI);

        if (!isConnected){
            Toast.makeText(this, "No Connection Detected", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "No connection detected");
        }

        Log.d(TAG, "wifiConnected " + wifiConnected);
        Log.d(TAG, "Connection " + activeNetwork.toString());

        //create a lock on the wifi connection (so phone isn't used)

        keepWiFiOn(this, true);
        wifiConnected = isConnected && (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI);

        Log.d(TAG, "wifiConnected " + wifiConnected);

        //register User
        if (subjectID == ClientPaths.NO_VALUE) {
            Toast.makeText(this, "Please say your ID #",Toast.LENGTH_SHORT).show();
            getIDFromSpeech();
        } else {
            if (!ClientPaths.createEncrypter()) {
                ClientPaths.requestAndSaveKey();
            }
        }

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();

        retrieveDeviceNode(); //set up mobileNodeId

        //init bluetooth
        try
        {
            if(findBT()) {
                //openBT();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "bluetooth could not be opened");
        }

        setAmbientEnabled();

        mSensorManager = ((SensorManager) getSystemService(SENSOR_SERVICE));
//        client = DeviceClient.getInstance(this);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        bluetoothConnected = false;
        sensorChecked=false;
        spiroChecked=false;
        receiveBuffer="";
        statusString="";
        subjectID= ClientPaths.getSubjectID();

        updateDisplay();

        riskTask = new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ClientPaths.updateRisklevel();

                    }
                });
            }
        };

        scheduleGetRisk();
        Log.d(TAG, "MainActivity onCreate");
    }

    //http://toastdroid.com/2014/08/18/messageapi-simple-conversations-with-android-wear/
    private void retrieveDeviceNode() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                //mGoogleApiClient.blockingConnect(10000, TimeUnit.MILLISECONDS);
                NodeApi.GetConnectedNodesResult result =
                        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
                List<Node> nodes = result.getNodes();
                Log.d(TAG, "retrieveDeviceNode found: " + nodes.toString());
                if (nodes.size() > 0) {
                    mobileNodeId = nodes.get(0).getId();
                    Log.d(TAG, "connecting to first node for comm " + mobileNodeId);
                }
                //client.disconnect();
            }
        }).start();
    }

    public void updateRiskUI(int value, boolean shouldAnimate) {
        lastRiskValue=value;
        riskText = (TextView) findViewById(R.id.riskText);

        if (isAmbient()) {
            //updatePieChart(shouldAnimate);
            updateSmileView();
            TextView noConnView = (TextView) findViewById(R.id.noConnView);
            if (lastRiskValue==ClientPaths.NO_VALUE) {
                noConnView.setVisibility(View.VISIBLE);
            } else {
                noConnView.setVisibility(View.GONE);

            }
        } else { //not ambient
            riskText.setText("Risk: " + getRisk());

        }

    }

    private static void scheduleGetRisk() {
        riskTimer = new Timer();
        riskTimer.scheduleAtFixedRate(riskTask, 0, RISK_TASK_PERIOD);
    }

    private void initUI() {
        final Button idButton = (Button) findViewById(R.id.idButton);
        idButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getIDFromSpeech();
            }
        });

        sensorToggleButton = (ToggleButton) findViewById(R.id.sensorToggleButton);
        spiroToggleButton = (ToggleButton) findViewById(R.id.spiroToggleButton);

        sensorToggleButton.setChecked(sensorChecked);
        spiroToggleButton.setChecked(spiroChecked);

        sensorToggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                //remoteSensorManager will log the change if called.
                if (isChecked) {
                    Log.i(TAG, "startSensors");
                    startMeasurement();

                    if (!bluetoothConnected)
                        bluetoothAdapter.startLeScan(new UUID[]{RFduinoService.UUID_SERVICE}, MainActivity.this);

                    registerReceiver(scanModeReceiver, new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));
                    registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
                    registerReceiver(rfduinoReceiver, RFduinoService.getIntentFilter());


                    if (ClientPaths.currentLocation == null) {
//                        Toast.makeText(MainActivity.this, "Could not find Location", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Could not find Location");
                    } else {
//                        Toast.makeText(MainActivity.this, "Location Found", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Location Found: " + ClientPaths.currentLocation.getLatitude() + ", " + ClientPaths.currentLocation.getLongitude());
                    }
                    sensorChecked = true;

                } else {
                    Log.i(TAG, "stopSensors");
                    stopMeasurement();

                    unregisterReceiver(scanModeReceiver);
                    unregisterReceiver(bluetoothStateReceiver);
                    unregisterReceiver(rfduinoReceiver);

                    bluetoothAdapter.stopLeScan(MainActivity.this);

                    bluetoothConnected = false;
                    sensorChecked = false;
                }
            }
        });

        spiroToggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                //remoteSensorManager will log the change if called.


                if (isChecked) {
                    Log.i(TAG, "startSpiro");

                    if (spiroDevice.equals(null)) {
                        if (findBT()) {
                            openBT();
                        } else {
                            spiroToggleButton.setChecked(false);
                            spiroChecked=false;
                            Toast.makeText(MainActivity.this, "No Spirometer Paired",Toast.LENGTH_SHORT).show();
                        }
                    } else { //device exists already

                        openBT();
                    }
                    spiroChecked=true;


                } else {
                    Log.i(TAG, "stopSpiro");
                    closeBT();

                    spiroChecked=false;

                }
            }
        });

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);


        subjectView = (TextView) findViewById(R.id.subjectText);
        subjectID = ClientPaths.getSubjectID();

        subjectView.setText("Subject: " + subjectID);

        riskText = (TextView) findViewById(R.id.riskText);
        riskText.setText("Risk: " + getRisk());

    }

    private void updateSmileView() {
        smileView = (ImageView) findViewById(R.id.smileView);
        riskView = (TextView) findViewById(R.id.riskView);

        try {
            TextView bytesView = (TextView) findViewById(R.id.bytesView);
            bytesView.setText("DB: " + ClientPaths.sensorFile.length()/1024 + " kB");
        } catch (Exception e) {
            e.printStackTrace();
        }

        statusString = "";//lastRiskValue + " ";

        if (lastRiskValue < 0) {
            smileView.setVisibility(View.GONE);

            return;
        } else {
            smileView.setVisibility(View.VISIBLE);
        }

        if (lastRiskValue<30) {
            smileView.setImageResource(R.drawable.happy_face);

            statusString+="low";


        } else if (lastRiskValue<70) {
            smileView.setImageResource(R.drawable.neutral_face);
            statusString += "medium";

        } else {
            smileView.setImageResource(R.drawable.frowny_face);
            statusString += "high";

        }
        riskView.setText("Your Risk: " + statusString);

    }

    private void updateDisplay() {
        Log.d(TAG, "Update Display Called");

        if (isAmbient()) {
            //watch face view
            setContentView(R.layout.main_activity_risk_level);

            //set clock
            mClockView = (TextView) findViewById(R.id.clock);
            mClockView.setVisibility(View.VISIBLE);
            mClockView.setText(AMBIENT_DATE_FORMAT.format(new Date()));

            //update pie chart and data
            updateRiskUI(lastRiskValue, true);

        } else {
            //main app screen
            setContentView(R.layout.main_activity);

            initUI();

        }
    }

    // Create an intent that can start the Speech Recognizer activity
    private void getSubjectFromSpeech() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
// Start the activity, the intent will be populated with the speech text
        startActivityForResult(intent, NAME_REQUEST_CODE);
    }

    private void getIDFromSpeech() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
// Start the activity, the intent will be populated with the speech text
        startActivityForResult(intent, ID_REQUEST_CODE);
    }

    // This callback is invoked when the Speech Recognizer returns.
// This is where you process the intent and extract the speech text from the intent.
    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (resultCode==RESULT_OK) {

            List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            String spokenText = results.get(0);
            // Do something with spokenText

            if (spokenText.equals("")) {
                return;
            }

            try {
                //Integer sid = idNumberPicker.getValue()//.parseInt(sidString);
                switch(requestCode) {
                    case NAME_REQUEST_CODE:
                        Log.d(TAG, "onSpeechResult Name");
                        if (!isConnected) {
                            Toast.makeText(this, "No Network",Toast.LENGTH_SHORT).show();
                            return;
                        }
                        UploadTask userTask = new UploadTask(ClientPaths.SUBJECT_API, this);

                        JSONObject nameJson = new JSONObject();
                        try {
                            nameJson.put("key", ClientPaths.API_KEY);
                            nameJson.put("name", spokenText);
                        } catch (Exception e) {
                            e.printStackTrace();
                            return;
                        }

                        userTask.execute(nameJson.toString());
                        break;
                    case ID_REQUEST_CODE:
                        Log.d(TAG, "onSpeechResult ID");
                        Integer sid = Integer.parseInt(spokenText);
                        ClientPaths.setSubjectID(sid);
                        TextView t = (TextView) findViewById(R.id.subjectText);
                        t.setText("SubjectID: " + ClientPaths.getSubjectID());
                        Toast.makeText(MainActivity.this, "Success SID: " + sid, Toast.LENGTH_SHORT).show();

                        //generate and send encryption key to server based on result of user id

                        ClientPaths.sendKeyToServer();
                        break;
                    }

//
//                Integer sid = Integer.parseInt(spokenText);
//                ClientPaths.setSubjectID(sid);
//                TextView t = (TextView) findViewById(R.id.subjectText);
//                t.setText("SubjectID: " + ClientPaths.getSubjectID());
//                Toast.makeText(MainActivity.this, "Success SID: " + sid, Toast.LENGTH_SHORT).show();
                } catch (Exception e) { //parse error of integer in input field
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this, "Heard '" + spokenText + "' - not a valid #", Toast.LENGTH_SHORT).show();

                }

            }
        super.onActivityResult(requestCode, resultCode, data);
    }

    //return the risk level for the user (will be retrieved from user at a regular interval) - will need to establish timer in onCreate
    private String getRisk() {
        return lastRiskValue >= 0 ? lastRiskValue + "%" : "Waiting for Value..";

    }

    /**
     * Builds a GoogleApiClient. Uses the addApi() method to request the LocationServices API.
     */
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addApi(Wearable.API)  // used for data layer API
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "MainActivity onDestroy");

        stopMeasurement();
        closeBT();

        try {
            riskTimer.cancel();
        } catch (Exception e) {
            Log.d(TAG, "Risk Timer off");
        }

        try {

            //stop scan if still running
            bluetoothAdapter.stopLeScan(this);

            if (scanModeReceiver != null)
                unregisterReceiver(scanModeReceiver);

            if (bluetoothStateReceiver != null)
                unregisterReceiver(bluetoothStateReceiver);

            if (rfduinoReceiver != null)
                unregisterReceiver(rfduinoReceiver);
        } catch (Exception e) {
            Log.d(TAG, e.toString());
        }

        try {
            unbindService(rfduinoServiceConnection);
        } catch (Exception e) {
            Log.e(TAG, "[Handled] Attempted to unbind when rfduinoService was already unbound)");
        }

        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi
                    .removeLocationUpdates(mGoogleApiClient, this);
        }
        mGoogleApiClient.disconnect();

//        if (wifiLock.isHeld())
//            wifiLock.release();

        keepWiFiOn(this,false);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }
//
    @Override
    protected void onResume() {
        super.onResume();

    }

//    @Override
//    protected void onPause() {
//        super.onPause();
//    }

//    @Override
//    protected void onStop(){
//        super.onStop();
//
//
//    }

    public void ProcessReceivedData(String data) {
//        Log.d(TAG, "ProcessReceivedData (Dust) called");
        if (!bluetoothConnected) {
            bluetoothConnected = true; //we received the data
            receiveBuffer = "";
        }

        //receiveBuffer = receiveBuffer + data;
        receiveBuffer = data;

        int begin = receiveBuffer.indexOf("B");
        int end = receiveBuffer.indexOf("E");
        if (end > begin) {
            String newString = receiveBuffer.substring(begin, end + 1);
            receiveBuffer = receiveBuffer.replace(newString, "");
            newString = newString.replace(" ", "");
            newString = newString.replace("B", "");
            newString = newString.replace("E", "");

            if (newString.contains(":")) {
                String[] data_split = newString.split(":");
                if (data_split.length == 2) {
                    DustData NewData = new DustData(data_split[0], data_split[1]);
                    float[] vals = new float[1];
                    try {
                        vals[0] = Integer.parseInt(NewData.iValue);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return;
                    }
                    //Log.d(TAG, "Dust Reading: " + vals[0]);
                    //client.sendSensorData(event.sensor.getType(), event.accuracy, event.timestamp, event.values);
                    ClientPaths.sendSensorData(ClientPaths.DUST_SENSOR_ID, 3, System.currentTimeMillis(), vals);

                }

            }
        }
    }

    private void upgradeState(int newState) {
        if (newState > state) {
            updateState(newState);
        }
    }

    private void downgradeState(int newState) {
        if (newState < state) {
            updateState(newState);
        }
    }

    private void updateState(int newState) {
        state = newState;
    }


    private void addData(byte[] data) {
        //Log.i(TAG, "in BT addData");
        String ascii = HexAsciiHelper.bytesToAsciiMaybe(data);
        if (ascii != null) {
            ProcessReceivedData(ascii);
        }
    }

    @Override
    public void onLeScan(BluetoothDevice device, final int rssi, final byte[] scanRecord) {
        bluetoothAdapter.stopLeScan(this);
        bluetoothDevice = device;

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //scan for bluetooth device that contains RF
                if (bluetoothDevice.getName().contains(ClientPaths.DUST_BT_NAME)) {
                    Log.i(TAG, "Found RF Device: " + bluetoothDevice.getName());
                    Intent rfduinoIntent = new Intent(MainActivity.this, RFduinoService.class);
                    bindService(rfduinoIntent, rfduinoServiceConnection, BIND_AUTO_CREATE);

                }
            }
        });
    }

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
            if (state == BluetoothAdapter.STATE_ON) {
                upgradeState(STATE_DISCONNECTED);
            } else if (state == BluetoothAdapter.STATE_OFF) {
                downgradeState(STATE_BLUETOOTH_OFF);
            }
        }
    };


    private final BroadcastReceiver scanModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // = (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_NONE);
        }
    };

    private final ServiceConnection rfduinoServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            rfduinoService = ((RFduinoService.LocalBinder) service).getService();
            if (rfduinoService.initialize()) {
                boolean result = rfduinoService.connect(bluetoothDevice.getAddress());

                if (result) {
                    upgradeState(STATE_CONNECTING);
                }
            }
        }

        @Override

        public void onServiceDisconnected(ComponentName name) {
            rfduinoService = null;
            downgradeState(STATE_DISCONNECTED);
        }
    };

    private final BroadcastReceiver rfduinoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (RFduinoService.ACTION_CONNECTED.equals(action)) {

                upgradeState(STATE_CONNECTED);
            } else if (RFduinoService.ACTION_DISCONNECTED.equals(action)) {
                downgradeState(STATE_DISCONNECTED);
            } else if (RFduinoService.ACTION_DATA_AVAILABLE.equals(action)) {
                addData(intent.getByteArrayExtra(RFduinoService.EXTRA_DATA));
            }
        }
    };

    public void onFinishActivity(View view) {
        setResult(RESULT_OK);
        finish();
    }

    //Sensor Related

    private void startMeasurement() {

        mHeartrateSensor = mSensorManager.getDefaultSensor(SENS_HEARTRATE);
        Sensor linearAccelerationSensor = mSensorManager.getDefaultSensor(SENS_LINEAR_ACCELERATION);
        Sensor heartrateSamsungSensor = mSensorManager.getDefaultSensor(ClientPaths.SS_HEART_SENSOR_ID);

        Log.i(TAG, "Start Measurement");
        if (mSensorManager != null) {


            if (mHeartrateSensor != null) {
                final int measurementDuration = 10;   // Seconds
                final int measurementBreak = 5;    // Seconds

                mScheduler = Executors.newScheduledThreadPool(1);
                mScheduler.scheduleAtFixedRate(
                        new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, "register Heartrate Sensor");
                                mSensorManager.registerListener(MainActivity.this, mHeartrateSensor, ClientPaths.SENSOR_DELAY_CUSTOM);//SensorManager.SENSOR_DELAY_NORMAL);

                                try {
                                    Thread.sleep(measurementDuration * 1000);
                                } catch (InterruptedException e) {
                                    Log.e(TAG, "Interrupted while waitting to unregister Heartrate Sensor");
                                }

                                Log.d(TAG, "unregister Heartrate Sensor");
                                mSensorManager.unregisterListener(MainActivity.this, mHeartrateSensor);
                            }
                        }, SensorManager.SENSOR_DELAY_NORMAL, measurementDuration + measurementBreak, TimeUnit.MICROSECONDS);

            } else {
                Log.d(TAG, "No Heartrate Sensor found");
            }

//            if (heartrateSamsungSensor != null) {
//                mSensorManager.registerListener(this, heartrateSamsungSensor, ClientPaths.SENSOR_DELAY_CUSTOM/2);
//            } else {
//                Log.d(TAG, "Samsungs Heartrate Sensor not found");
//            }

            if (linearAccelerationSensor != null) {
                mSensorManager.registerListener(this, linearAccelerationSensor, ClientPaths.SENSOR_DELAY_CUSTOM/2);//SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Log.d(TAG, "No Linear Acceleration Sensor found");
            }
        }
    }

    private void stopMeasurement() {
        Log.i(TAG, "Stop Measurement");
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this);
        }
        if (mScheduler != null && !mScheduler.isTerminated()) {
            mScheduler.shutdown();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
//        ClientPaths.sendSensorData(event.sensor.getType(), event.accuracy, event.timestamp, event.values);
        ClientPaths.sendSensorData(event.sensor.getType(), event.accuracy, System.currentTimeMillis(), event.values);
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private static final Integer UPDATE_INTERVAL_MS = 1000 * 60;
    private static final Integer FASTEST_INTERVAL_MS = UPDATE_INTERVAL_MS;
    /**
     * Runs when a GoogleApiClient object successfully connects.
     */
    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "location onConnected");
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(UPDATE_INTERVAL_MS)
                .setFastestInterval(FASTEST_INTERVAL_MS);

        LocationServices.FusedLocationApi
                .requestLocationUpdates(mGoogleApiClient, locationRequest, this)
                .setResultCallback(new ResultCallback<Status>() {

                    @Override
                    public void onResult(Status status) {
                        if (status.getStatus().isSuccess()) {

                            Log.d(TAG, "Successfully requested location updates");

                        } else {
                            Log.e(TAG, "Failed in requesting location updates, "
                                    + "status code: "
                                    + status.getStatusCode() + ", message: " + status
                                    .getStatusMessage());
                        }
                    }
                });
    }

    @Override
    public  void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended called");
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed called");
    }

    @Override
    public void onLocationChanged (Location location)
    {
        Log.d(TAG, "onLocationChanged: " + location.getLatitude() + "," + location.getLongitude());
        if (location!=null)
            ClientPaths.currentLocation = location;
    }

    @Override
    public void onEnterAmbient(Bundle ambientDetails) {
        super.onEnterAmbient(ambientDetails);
        updateDisplay();
    }

    @Override
    public void onUpdateAmbient() {
        super.onUpdateAmbient();
        updateDisplay();
    }

    @Override
    public void onExitAmbient() {
        updateDisplay();
        super.onExitAmbient();
    }

    //Spirometer connection:

    public Boolean findBT()
    {
        Log.d(TAG, "findBT called");
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if(bluetoothAdapter == null)
            Log.d(TAG, "No bluetooth adapter available");


        if(!bluetoothAdapter.isEnabled())
        {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, REQUEST_ENABLE_BT);
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        //spirometer must be pre-paired for connection
        spiroDevice=null;
        if(pairedDevices.size() > 0)
        {
            String deviceName;
            for(BluetoothDevice device : pairedDevices)
            {
                deviceName = device.getName();


                // TODO(yjchoi): remove hardcoded names
                if(deviceName.matches("ASMA_\\d\\d\\d\\d"))   // Find a device with name ASMA_####
                {
                    spiroDevice = device;
                    Log.d("yjcode", "Detected spiro device: " + deviceName);
                    return true;

                } else if (deviceName.contains(ClientPaths.DUST_BT_NAME)) {

                    Log.d("yjcode", "Detected RFduino device: " + deviceName);
                    //add connection for RF duino here as well
                }
            }
        }

        //Log.d(TAG, "findBT did not find spiro device");
        return spiroDevice!=null;
    }

    public void openBT()
    {
        try {
            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
            spiroSocket = spiroDevice.createRfcommSocketToServiceRecord(uuid);
            spiroSocket.connect();
            mmInputStream = spiroSocket.getInputStream();
            beginListenForData(mmInputStream);
        } catch (Exception e) {
            e.printStackTrace();
            spiroToggleButton.setChecked(false);
            spiroChecked=false;
            return;
        }
        Log.d(TAG, "Spiro Bluetooth Opened");
    }

    public void beginListenForData(final InputStream iStream)
    {
        Log.d(TAG, "beginListenForData");
        final Handler handler = new Handler();

        readBufferPosition = 0;
        readBuffer = new byte[1024];
        stopWorker = false;

        //listener worker thread
        workerThread = new Thread(new Runnable()
        {
            public void run()
            {
                while(!Thread.currentThread().isInterrupted() && !stopWorker)
                {
                    try
                    {
                        int bytesAvailable = iStream.available();
                        if(bytesAvailable > 0)
                        {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for(int i=0;i<bytesAvailable;i++)
                            {
                                byte b = packetBytes[i];
                                if(b == 0x03)
                                {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final TestData data = new TestData(encodedBytes);
                                    readBufferPosition = 0;

                                    handler.post(new Runnable()
                                    {
                                        public void run()
                                        {

                                            Log.d(TAG, "Received spiro data: " + data.toString());

                                            ClientPaths.sendSensorData(ClientPaths.SPIRO_SENSOR_ID, 3, System.currentTimeMillis(), data.toArray());
                                            Toast.makeText(MainActivity.this, "PEF Received: " + data.pef + (data.good_test>0 ? "" : "!"), Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                    break;
                                }
                                else
                                {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    }
                    catch (IOException ex)
                    {
                        stopWorker = true;
                    }
                }
            }
        });
        workerThread.start();
    }

    public void closeBT() {
        try {
            stopWorker = true;
            mmInputStream.close();
            spiroSocket.close();

        } catch (Exception e) {
           Log.e(TAG, "[Handled] Error Closing spiroBT (may have been null already)");
            return;

        }
        Log.d(TAG, "spiro bluetooth closed");
    }
}
