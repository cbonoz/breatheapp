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

package com.breatheplatform.beta;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.speech.RecognizerIntent;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.breatheplatform.beta.bluetooth.HexAsciiHelper;
import com.breatheplatform.beta.bluetooth.RFduinoService;
import com.breatheplatform.beta.data.DustData;
import com.breatheplatform.beta.messaging.UploadTask;
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

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
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

public class MainActivity extends WearableActivity implements BluetoothAdapter.LeScanCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        LocationListener, SensorEventListener
        //,DataApi.DataListener, MessageApi.MessageListener,NodeApi.NodeListener
{
    private static final SimpleDateFormat AMBIENT_DATE_FORMAT =
            new SimpleDateFormat("HH:mm", Locale.US);

    private static final String TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int MY_PERMISSIONS_REQUEST_READ_CONTACTS = 2;
    private static final int MIN_BATTERY_LEVEL = 5;


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
    private static final int RISK_TASK_PERIOD=10000; //10 seconds
    private static final int SPEECH_ID_REQUEST_CODE = 0;
    private int state;

    BluetoothAdapter bluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    volatile boolean stopWorker;

    //Sensor related
    private SensorManager mSensorManager;
    private Sensor mHeartrateSensor;
//    private static BatteryReceiver batteryReceiver;


    private static final int NAME_REQUEST_CODE = 1;
    private static final int ID_REQUEST_CODE = 2;
    private final int MIN_CLICK_INTERVAL =5000;
    private long mLastClickTime=0;

    private final Random RAND = new Random();

    private ToggleButton sensorToggleButton;
    private ToggleButton spiroToggleButton;
    private Boolean sensorChecked;

    private Boolean isConnected;
    private static String statusString;

    private TextView mClockView;
    private TextView riskText;
    private TextView riskView;
    private TextView subjectView;
    private TextView lastSensorView=null;
    private ImageView smileView;

    private static TimerTask riskTask;
    private static Timer riskTimer;
    private static int lastRiskValue;
    private static String mobileNodeId;

    private RelativeLayout mRectBackground;
    private RelativeLayout mRoundBackground;

    // Create an intent that can start the Speech Recognizer activity
    private void registerIDFromSpeech() {
        Log.d(TAG, "registerIDFromSpeech");
        Toast.makeText(MainActivity.this, "Please say your ID #",Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
// Start the activity, the intent will be populated with the speech text
        startActivityForResult(intent, SPEECH_ID_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (requestCode == SPEECH_ID_REQUEST_CODE && resultCode == RESULT_OK) {
            List<String> results = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
            String spokenText = results.get(0);

            // Do something with spokenText
            Log.d(TAG, "onSpeechResult ID");
            Integer sid=null;
            try {
                sid = Integer.parseInt(spokenText);
            } catch (Exception e) {
                Toast.makeText(this, "Heard " + spokenText + " not a valid # - start app again",Toast.LENGTH_SHORT).show();
                //restart activity
                Intent intent = getIntent();
                finish();
                startActivity(intent);
                return;
            }
            Toast.makeText(MainActivity.this, "Success SID: " + sid, Toast.LENGTH_SHORT).show();
            ClientPaths.setSubjectID(sid);
            subjectView = (TextView) findViewById(R.id.subjectText);
            subjectView.setText("Subject: " + ClientPaths.SUBJECT_ID);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public void onCreate(Bundle b) {
        super.onCreate(b);
//        ClientPaths.client = new DeviceClient(this);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        sensorChecked=false;
        receiveBuffer = "";
        statusString="";
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        Log.d(TAG, "Sensor delay normal: " + SensorManager.SENSOR_DELAY_NORMAL);

        ClientPaths.setContext(this);


        setContentView(R.layout.main_activity);

        WatchViewStub stub = (WatchViewStub) findViewById(R.id.stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mRectBackground = (RelativeLayout) findViewById(R.id.rect_layout);
                mRoundBackground = (RelativeLayout) findViewById(R.id.round_layout);
//                updateDisplay();

                initMainUI();

                if (ClientPaths.getSubjectID() == ClientPaths.NO_VALUE)
                    registerIDFromSpeech();
            }
        });

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();

        retrieveDeviceNode(); //set up mobileNode
        //registerDust();
        scheduleGetRisk();
        setAmbientEnabled();
        Log.d(TAG, "MainActivity setup done");
    }



    private void retrieveDeviceNode() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                mGoogleApiClient.blockingConnect(10000, TimeUnit.MILLISECONDS);
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
        Log.d(TAG, "updateUI - got " + value);

        lastRiskValue = value;
        //riskText.setText("Risk: " + getRisk());
        riskText = (TextView) findViewById(R.id.riskView);
        smileView = (ImageView) findViewById(R.id.smileView);

        switch(lastRiskValue) {
            case 2:
                smileView.setImageResource(R.drawable.happy_face);
                statusString = "Risk: Low";
                riskText.setTextColor(Color.GREEN);
                break;
            case 1:
                smileView.setImageResource(R.drawable.neutral_face);
                statusString = "Risk: Medium";
                riskText.setTextColor(Color.parseColor("#ffa500"));
                break;
            case 0:
                smileView.setImageResource(R.drawable.frowny_face);
                statusString = "Risk: High";
                riskText.setTextColor(Color.RED);
                break;
            case ClientPaths.NO_VALUE:
                statusString = "Risk: Low";
                smileView.setImageResource(R.drawable.happy_face);
                riskText.setTextColor(Color.GREEN);
                riskText.setText(statusString);
                return;
            default:
                smileView.setImageResource(R.drawable.happy_face);
                statusString = "Risk: Low (Waiting)";
                riskText.setTextColor(Color.GREEN);
                riskText.setText(statusString);
                return;
        }

        statusString += " (" + lastRiskValue + ")";
        riskText.setText(statusString);
    }

    private void scheduleGetRisk() {
        riskTask = new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        createRiskPostRequest();
                        updateBatteryLevel();

                        //check that dust sensor is connected every 10 sec
                        ClientPaths.dustConnected=true;
                        if (!ClientPaths.dustConnected) {
                            Log.d(TAG, "dustConnected = false, attempting to reconnect");
                            //unregisterDust();
                            registerDust();
                        }

                    }
                });
            }
        };
        riskTimer = new Timer();
        riskTimer.scheduleAtFixedRate(riskTask, 0, RISK_TASK_PERIOD);
    }


    public void updateBatteryLevel(){
        //get battery level and save it
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = this.registerReceiver(null, ifilter);
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            ClientPaths.batteryLevel = level;
            if (level < MIN_BATTERY_LEVEL) {

                onDestroy();
            }
        } catch (Exception e) {
            Log.e(TAG, "[Handled] Error getting battery level value");
            return;
        }
    }



    private void registerDust() {
        Log.d(TAG, "registerDust");
        try {
            bluetoothAdapter.startLeScan(new UUID[]{RFduinoService.UUID_SERVICE}, MainActivity.this);
        } catch ( Exception e) {
            Log.d(TAG, "[Handled] registerDust called when scan started already");

        }
        try {
            registerReceiver(scanModeReceiver, new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));
            registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
            registerReceiver(rfduinoReceiver, RFduinoService.getIntentFilter());
        } catch ( Exception e) {
            Log.d(TAG, "[Handled] registerDust called when scan started already");
            return;
        }
    }

    private void unregisterDust() {
        Log.d(TAG, "Unregister Dust");
        try {
            bluetoothAdapter.stopLeScan(this);

            if(scanModeReceiver!=null)
                unregisterReceiver(scanModeReceiver);

            if(bluetoothStateReceiver!=null)
                unregisterReceiver(bluetoothStateReceiver);

            if(rfduinoReceiver!=null)
                unregisterReceiver(rfduinoReceiver);
            unbindService(rfduinoServiceConnection);
        } catch (Exception e) {
            Log.e(TAG, "[Handled] Error unregistering dust receiver");

        }
        
    }

    private void initMainUI() {
        lastSensorView = (TextView) findViewById(R.id.lastSensorView);
        spiroToggleButton = (ToggleButton) findViewById(R.id.spiroToggleButton);
//        ClientPaths.dustConnected=false;

        Log.i(TAG, "startSensors");
        startMeasurement();
//        registerDust();

        spiroToggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked)
                    Toast.makeText(MainActivity.this, "Connecting...", Toast.LENGTH_SHORT).show();


                if (isChecked) {
                    Log.d(TAG, "startSpiro");

                    if (findBT()) {
                        if (!openBT()) {
                            Toast.makeText(MainActivity.this, "Could not connect to Spirometer", Toast.LENGTH_SHORT).show();
                            spiroToggleButton.setChecked(false);
                        }

                    } else {
                        Toast.makeText(MainActivity.this, "Could not find Spirometer", Toast.LENGTH_SHORT).show();
                        spiroToggleButton.setChecked(false);
                    }


                } else {
                    Log.i(TAG, "stopSpiro");
                    closeBT();

                }
            }
        });

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        String st = "Subject: " + ClientPaths.SUBJECT_ID;
        String tt = "Risk: Please Wait..";
        subjectView = (TextView) findViewById(R.id.subjectText);
        riskView = (TextView) findViewById(R.id.riskView);

        subjectView.setText(st);
        riskView.setText(tt);
    }

    private void createRiskPostRequest() {
        try {
            if (!ClientPaths.dustConnected) registerDust();
        } catch (Exception e) {
            ;
        }
        Log.d(TAG, "Called createRiskPostRequest");
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("key", ClientPaths.API_KEY);
            jsonBody.put("subject_id", ClientPaths.SUBJECT_ID);
            jsonBody.put("timestamp",System.currentTimeMillis());
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        UploadTask uploadTask = new UploadTask(ClientPaths.RISK_API, this);//, root + File.separator + SENSOR_FNAME);
        uploadTask.execute(jsonBody.toString());
        //uploadTask.cleanUp();
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

        try {
            riskTimer.cancel();
        } catch (Exception e) {
            Log.d(TAG, "Risk Timer off");
        }

        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi
                    .removeLocationUpdates(mGoogleApiClient, this);
        }
        mGoogleApiClient.disconnect();

        stopMeasurement();
        unregisterDust();
        closeBT();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }
    //
    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        //startMeasurement();
        if (!ClientPaths.dustConnected)
            registerDust();
        super.onResume();
    }


    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }


    public void ProcessReceivedData(String data) {
//        Log.d(TAG, "ProcessReceivedData (Dust) called");
        if (!ClientPaths.dustConnected) {
            ClientPaths.dustConnected = true; //we received the data
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
                    Log.d(TAG, "Dust Reading: " + vals[0]);
                    //client.sendSensorData(event.sensor.getType(), event.accuracy, event.timestamp, event.values);
                    ClientPaths.addSensorData(ClientPaths.DUST_SENSOR_ID, 3, System.currentTimeMillis(), vals);
                    if (lastSensorView!=null)
                        lastSensorView.setText("Last: " + ClientPaths.getSensorName(ClientPaths.DUST_SENSOR_ID) + "\nConnected Dust: " + ClientPaths.dustConnected);
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
//        if (true)
//            return;
        mHeartrateSensor = mSensorManager.getDefaultSensor(SENS_HEARTRATE);
        final Sensor linearAccelerationSensor = mSensorManager.getDefaultSensor(SENS_LINEAR_ACCELERATION);
        //final Sensor heartrateSamsungSensor = mSensorManager.getDefaultSensor(ClientPaths.REG_HEART_SENSOR_ID );//65562

        Log.i(TAG, "Start Measurement");
        if (mHeartrateSensor != null) {
            final int measurementDuration = 10;   // Seconds
            final int measurementBreak = 5;    // Seconds

            mScheduler = Executors.newScheduledThreadPool(1);
            mScheduler.scheduleAtFixedRate(
                    new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "register Heartrate Sensor");
                            mSensorManager.registerListener(MainActivity.this, mHeartrateSensor, SensorManager.SENSOR_DELAY_NORMAL);

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



//        if (heartrateSamsungSensor != null) {
//            mSensorManager.registerListener(this, heartrateSamsungSensor, ClientPaths.SENSOR_DELAY_CUSTOM);
//        } else {
//            Log.d(TAG, "Samsungs Heartrate Sensor not found");
//        }

        if (linearAccelerationSensor != null) {
            mSensorManager.registerListener(this, linearAccelerationSensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            Log.d(TAG, "No Linear Acceleration Sensor found");
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
        long timestamp = event.timestamp/1000000; // nanoseconds since boot, ms since boot
        //long ct = System.currentTimeMillis();
        ClientPaths.addSensorData(event.sensor.getType(), event.accuracy, timestamp, event.values);

        if (lastSensorView!=null) {
            String lt = "Last: " + ClientPaths.getSensorName(event.sensor.getType()) + "\nConnected Dust: " + ClientPaths.dustConnected;
            lastSensorView.setText(lt);
        }
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

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
        try {
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
        } catch(SecurityException e) {
            e.printStackTrace();
            Log.e(TAG, "Error Requesting location service (lack of permission)");
            return;
        }
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
        //updateDisplay();
    }

    @Override
    public void onUpdateAmbient() {
        super.onUpdateAmbient();
        //updateDisplay();
    }

    @Override
    public void onExitAmbient() {
        //updateDisplay();
        super.onExitAmbient();
    }

    //Spirometer connection:
    public Boolean findBT()
    {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if(bluetoothAdapter == null)
        {
            Log.d(TAG, "No bluetooth adapter available");

        }

        if(!bluetoothAdapter.isEnabled())
        {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, REQUEST_ENABLE_BT);
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        //spirometer must be pre-paired for connection
        if(pairedDevices.size() > 0)
        {
            String deviceName;
            for(BluetoothDevice device : pairedDevices)
            {
                deviceName = device.getName();


                // TODO(yjchoi): remove hardcoded names
                if(deviceName.matches("ASMA_\\d\\d\\d\\d"))   // Find a device with name ASMA_####
                {
                    mmDevice = device;
                    Log.d("yjcode", "Detected spiro device: " + deviceName);
                    return true;

                } else if (deviceName.contains(ClientPaths.DUST_BT_NAME)) {
                    Log.d("yjcode", "Detected RFduino device: " + deviceName);
                    //add connection for RF duino here as well
                }
            }
        }

        Log.d(TAG, "findBT did not find spiro device");

        return false;
    }

    public Boolean openBT()
    {
        try {
            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
            mmSocket.connect();
            mmInputStream = mmSocket.getInputStream();
            beginListenForData();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        Log.d(TAG, "Spiro Bluetooth Opened");
        Toast.makeText(MainActivity.this, "Connected!",Toast.LENGTH_SHORT).show();
        return true;
    }

    public void beginListenForData()
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
                        int bytesAvailable = mmInputStream.available();
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
                                            try {
                                                ClientPaths.addSensorData(ClientPaths.SPIRO_SENSOR_ID, 3, System.currentTimeMillis(), data.toArray());
                                                //Toast.makeText(MainActivity.this, "PEF Received: " + data.pef + "!", Toast.LENGTH_SHORT).show();
                                                Toast.makeText(MainActivity.this, "Data Received!", Toast.LENGTH_SHORT).show();
                                            } catch (Exception e) {
                                                Toast.makeText(MainActivity.this, "Bad Reading: Please try again!", Toast.LENGTH_SHORT).show();
                                            }
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
            if (mmInputStream!=null)
                mmInputStream.close();
            if (mmSocket!=null)
                mmSocket.close();

        } catch (Exception e) {
            //e.printStackTrace();
            Log.e(TAG, "[Handled] closeBT");
            return;

        }
        Log.d(TAG, "spiro bluetooth closed");
    }

    //Message / Data Layer API methods
//    @Override
//    public void onDataChanged(DataEventBuffer dataEvents) {
//        Log.d(TAG, "onDataChanged(): " + dataEvents);
//
//        for (DataEvent event : dataEvents) {
//            String path = event.getDataItem().getUri().getPath();
//            String data = event.getDataItem().toString();
//            Log.d(TAG, "Received: " + data);
////
////            if (event.getType() == DataEvent.TYPE_CHANGED) {
////
////                if (DataLayerListenerService.IMAGE_PATH.equals(path)) {
////                    DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
////                    Asset photoAsset = dataMapItem.getDataMap()
////                            .getAsset(DataLayerListenerService.IMAGE_KEY);
////                    // Loads image on background thread.
////                    new LoadBitmapAsyncTask().execute(photoAsset);
////
////                } else if (DataLayerListenerService.COUNT_PATH.equals(path)) {
////                    Log.d(TAG, "Data Changed for COUNT_PATH");
////                    mDataFragment.appendItem("DataItem Changed", event.getDataItem().toString());
////                } else {
////                    Log.d(TAG, "Unrecognized path: " + path);
////                }
////
////            } else if (event.getType() == DataEvent.TYPE_DELETED) {
////                mDataFragment.appendItem("DataItem Deleted", event.getDataItem().toString());
////            } else {
////                mDataFragment.appendItem("Unknown data event type", "Type = " + event.getType());
////            }
//        }
//    }
//
//    @Override
//    public void onMessageReceived(MessageEvent event) {
//        Log.d(TAG, "onMessageReceived: " + event);
//
//    }
//
//    @Override
//    public void onPeerConnected(Node node) {
//        Log.d(TAG, "onPeerConnected: " + node.getId());
//
//    }
//
//    @Override
//    public void onPeerDisconnected(Node node) {
//        Log.d(TAG, "onPeerDisconnected: " + node.getId());
//
//    }
}