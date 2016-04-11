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
 * https://github.com/cyfung/ActivityRecognitionSample/tree/master/app/src/main/java/com/aucy/activityrecognitionsample
 */

package com.breatheplatform.beta;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.breatheplatform.beta.activity.ActivityDetectionService;
import com.breatheplatform.beta.bluetooth.BluetoothConnection;
import com.breatheplatform.beta.bluetooth.HexAsciiHelper;
import com.breatheplatform.beta.bluetooth.RFduinoService;
import com.breatheplatform.beta.data.ConnectionReceiver;
import com.breatheplatform.beta.data.SensorAddService;
import com.breatheplatform.beta.messaging.DeviceClient;
import com.breatheplatform.beta.sensors.SensorService;
import com.breatheplatform.beta.shared.Constants;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import me.denley.courier.BackgroundThread;
import me.denley.courier.Courier;
import me.denley.courier.ReceiveMessages;



public class MainActivity extends WearableActivity implements BluetoothAdapter.LeScanCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        LocationListener,  ResultCallback<Status>
        //,DataApi.DataListener, MessageApi.MessageListener,NodeApi.NodeListener
{
    private static final SimpleDateFormat AMBIENT_DATE_FORMAT =
            new SimpleDateFormat("HH:mm", Locale.US);

    private static final String TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 1;


    private GoogleApiClient mGoogleApiClient;

    private static final int RISK_TASK_PERIOD=10000; //10 seconds

    private static final int BT_TASK_PERIOD=RISK_TASK_PERIOD*18; //180 seconds

    BluetoothAdapter bluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    private RFduinoService rfduinoService;
    private BluetoothDevice bluetoothDevice;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;

    volatile boolean stopWorker;



    private static final int NAME_REQUEST_CODE = 1;
    private static final int ID_REQUEST_CODE = 2;
    private static final int LOW_RISK = 2;
    private static final int MED_RISK = 1;
    private static final int HIGH_RISK = 0;



    private ToggleButton spiroToggleButton;
    private ConnectionReceiver connReceiver;
    private Boolean dustConnected = false;



    private TextView lastSensorView=null;
    private TextView mClockView;
    private TextView riskText;

    private TextView subjectView;
    private ImageView smileView;

    private static int lastRiskValue = LOW_RISK;//ActivityConstants.NO_VALUE;

    private RelativeLayout mRectBackground;
    private RelativeLayout mRoundBackground;

    private DeviceClient client=null;

    // Bluetooth (Dust) State machine
    final private static int STATE_BLUETOOTH_OFF = 1;
    final private static int STATE_DISCONNECTED = 2;
    final private static int STATE_CONNECTING = 3;
    final private static int STATE_CONNECTED = 4;

    private int state;

    //get battery level and save it
    public void updateBatteryLevel(){
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = this.registerReceiver(null, ifilter);
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            ClientPaths.batteryLevel = level;

        } catch (Exception e) {
            Log.e(TAG, "[Handled] Error getting battery level value");

        }
    }

    //get connectivity and save it
    private void updateConnectivity() {
        final int LEVELS = 5;
        ConnectivityManager cm = ((ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE));
        if (cm == null)
            return;
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        String conn = "None";
        if (activeNetwork != null) {
            conn = activeNetwork.getTypeName();

            if (conn.equals("WIFI") && ClientPaths.mainContext!=null) {
                WifiManager wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int level = WifiManager.calculateSignalLevel(wifiInfo.getRssi(), LEVELS);
                conn += " " + level;
            }

        }
        Log.d(TAG, "Connectivity " + conn);
        ClientPaths.connectionInfo = conn;
    }


    private long mLastClickTime = 0;

    //Intervals in MS
    private static final Integer ACTIVITY_INTERVAL = 0;//15000;//20000;//ms (0 fastest)
    private static final Integer LOCATION_INTERVAL = 1000 * 60 * 2; //request ever 2 min
    private final int MIN_CLICK_INTERVAL =5000;


    private RelativeLayout progressBar;
    //this function is called during onCreate (if the user has not registered an ID yet, will be called after
//    a valid ID has been registered during the boot up registration process)
    private void setupUI() {
        Log.d(TAG, "MainActivity setupUI");

        //http://stackoverflow.com/questions/5442183/using-the-animated-circle-in-an-imageview-while-loading-stuff
//        progressBar = (RelativeLayout) findViewById(R.id.loadingPanel);
//        progressBar.setVisibility(View.GONE);

        //set to 1 minute timeout (for ambient)
        Settings.System.putString(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, "60000");

        LocalBroadcastManager.getInstance(this).registerReceiver(mLastReceiver,
                new IntentFilter(Constants.LAST_SENSOR_EVENT));

        LocalBroadcastManager.getInstance(this).registerReceiver(mHeartReceiver,
                new IntentFilter(Constants.HEART_EVENT));


        lastSensorView = (TextView) findViewById(R.id.lastSensorView);
        spiroToggleButton = (ToggleButton) findViewById(R.id.spiroToggleButton);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        spiroToggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.d(TAG, "onCheckChanged");
                if (SystemClock.elapsedRealtime() - mLastClickTime < MIN_CLICK_INTERVAL) {
                    if (isChecked) {
                        spiroToggleButton.setChecked(false);
                        Log.d(TAG, "Spiro Button blocked - currently connecting");
                    }
                    return;
                }

                mLastClickTime = SystemClock.elapsedRealtime();

//                progressBar.setVisibility(View.VISIBLE);
//                progressBar.bringToFront();

                if (isChecked) {
                    Log.d(TAG, "startSpiro");
                    Toast.makeText(MainActivity.this, "Connecting...", Toast.LENGTH_SHORT).show();

                    if (findBT(Constants.SPIRO_SENSOR_ID)) {
                        if (!openSpiro()) {
                            Toast.makeText(MainActivity.this, "Spirometer Paired: Could not connect", Toast.LENGTH_SHORT).show();
                            spiroToggleButton.setChecked(false);
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "Spirometer not Paired", Toast.LENGTH_SHORT).show();
                        spiroToggleButton.setChecked(false);
                    }
                } else {
                    Log.i(TAG, "stopSpiro");
                    closeSpiro();
                    spiroToggleButton.setChecked(false);
                }

//                progressBar.setVisibility(View.GONE);
            }
        });

        Switch sensorSwitch = (Switch) findViewById(R.id.sensorSwitch);
        if (Constants.collecting) {

            sensorSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
//                    Courier.deliverMessage(MainActivity.this, Constants.FILE_API, Constants.START_WRITE);
                        startMeasurement();
                        try {
                            ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(mGoogleApiClient, 0, getActivityDetectionPendingIntent()).setResultCallback(MainActivity.this);
                            Log.d(TAG, "requested activity updates");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        Log.d(TAG, "sensorToggle Checked");
                    } else {
                        stopMeasurement();
                        ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(mGoogleApiClient, getActivityDetectionPendingIntent()).setResultCallback(MainActivity.this);
                        //trigger a data send event to the mobile device
                        addSensorData(Constants.TERMINATE_SENSOR_ID, null, null, null);
                        Courier.deliverMessage(MainActivity.this, Constants.FILE_API, Constants.END_WRITE);
                        Log.d(TAG, "sensorToggle Not Checked");
                    }
                }
            });
        } else {
            sensorSwitch.setVisibility(View.GONE);
//            TextView helpText1 = (TextView) findViewById(R.id.helpText1);
//            helpText1.setVisibility(View.GONE);
            startMeasurement();
            try {
                ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(mGoogleApiClient, 0, getActivityDetectionPendingIntent()).setResultCallback(MainActivity.this);
                Log.d(TAG, "requested activity updates");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

//        updateRiskUI(Constants.NO_VALUE);
        updateRiskUI(LOW_RISK);
        updateSubjectUI(ClientPaths.SUBJECT_ID);

        riskRequest();
        dustAndConnectivityRequest();

        Log.i(TAG, "start scheduled risk and dust tasks");
        scheduleRiskRequest();
        scheduleDustRequest();

//        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
//        registerReceiver(connReceiver, filter);

        //prevent app screen from dimming - lock app on screen (confirmed)
//        getWindowonDestory().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //end setupUI
    }

    private void createSpiroNotification() {
//        FragmentManager fragmentManager = getFragmentManager();
//        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
//        CardFragment cardFragment = CardFragment.create(getString(R.string.spiro_title),
//                getString(R.string.spiro_desc));
//        fragmentTransaction.add(R.id.frame_layout, cardFragment);
//        fragmentTransaction.commit();

        final AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle(R.string.spiro_title);
        alertDialog.setMessage("Please take Spirometer Reading");
        alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                alertDialog.dismiss();
            }
        });
//        alertDialog.setIcon(R.drawable.error_icon);
//        LayoutInflater inflater = getLayoutInflater();
//        View dialoglayout = inflater.inflate(R.layout.frame_layout, null);
        alertDialog.show();

//        cardFragment.dismiss();
        //remember to set on click dismissal for card
    }


    private void createDangerNotification() {
        final AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle(R.string.danger_title);
        alertDialog.setMessage("Please Contact your Doctor!");
        alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                alertDialog.dismiss();
            }
        });
        alertDialog.show();
    }


    private void requestSubject() {
        //check if SUBJECT ID is "" (null), using "" for serialization purposes via data api
        if (ClientPaths.SUBJECT_ID.equals("")) {
            Log.d(TAG, "Requesting Subject");
            Courier.deliverMessage(this, Constants.SUBJECT_API, "");
        } else {
            Log.i(TAG, "Requested Subject, but already have it.");
        }
    }

    WatchViewStub stub;


    public void onCreate(Bundle b) {
        super.onCreate(b);

        ClientPaths.setContext(this);

        WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = 0F; //value between 0 and 1
        getWindow().setAttributes(layout);

        Courier.startReceiving(this);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        //register sensor listener service


        setContentView(R.layout.main_activity);
        requestSubject();

        stub = (WatchViewStub) findViewById(R.id.stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                if (!isAmbient()) {
                    Log.d(TAG, "Layout Inflated - not ambient");
                    mRectBackground = (RelativeLayout) findViewById(R.id.rect_layout);
                    mRoundBackground = (RelativeLayout) findViewById(R.id.round_layout);
                    setupUI();
                } else {
                    Log.d(TAG, "Layout Inflated - ambient");
                }
            }
        });



        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addApi(ActivityRecognition.API)
//                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();

//        retrieveDeviceNode(); //set up mobileNode
        setAmbientEnabled();
        updateBatteryLevel();


        //TODO: Fix connectivity updates (currently this is just single request)
        updateConnectivity();


    }

    private Boolean healthDanger = false;

    public void updateSubjectUI(String sub) {
//        ActivityConstants.SUBJECT_ID = sub;
        subjectView = (TextView) findViewById(R.id.subjectText);
        if (subjectView != null) {
            String st = "Subject: " + sub;
            subjectView.setText(st);
        } else {
            Log.e(TAG, "Received subject before layout inflated");
        }

    }

    public void updateRiskUI(int value) {

        riskText = (TextView) findViewById(R.id.riskText);
        smileView = (ImageView) findViewById(R.id.smileView);
        String statusString;

        if (riskText == null || smileView == null) {
            return;
        }


        switch(value) {
            case LOW_RISK:
                smileView.setImageResource(R.drawable.happy_face);
                statusString = "Risk: Low";
                riskText.setTextColor(Color.GREEN);

                if (lastRiskValue!=LOW_RISK)//handle risk transition message
                    Toast.makeText(MainActivity.this, "Normal Reading", Toast.LENGTH_SHORT).show();

                break;
            case MED_RISK:
                smileView.setImageResource(R.drawable.neutral_face);
                statusString = "Risk: Medium";
                riskText.setTextColor(Color.parseColor("#ffa500"));

                if (lastRiskValue!=MED_RISK)//handle risk transition message
                    Toast.makeText(MainActivity.this, "Risk Warning - Please use Spirometer", Toast.LENGTH_SHORT).show();

                break;
            case HIGH_RISK:
                smileView.setImageResource(R.drawable.frowny_face);
                statusString = "Risk: High";
                riskText.setTextColor(Color.RED);

                if (lastRiskValue!=HIGH_RISK)//handle risk transition message
                    Toast.makeText(MainActivity.this, "Risk Warning - Please use Spirometer", Toast.LENGTH_SHORT).show();

                break;
            case Constants.NO_VALUE:
                statusString = "Risk: Low";
                smileView.setImageResource(R.drawable.happy_face);
                riskText.setTextColor(Color.GREEN);
                break;
            default:
                smileView.setImageResource(R.drawable.happy_face);
                statusString = "Risk: Low (Waiting)";
                riskText.setTextColor(Color.GREEN);
                break;
        }

        lastRiskValue = value;
        riskText.setText(statusString);
        Log.d(TAG, "updateRiskUI - " + statusString);
    }


    private Handler taskHandler = new Handler();

    private Runnable riskTask = new Runnable()
    {
        public void run()
        {
            riskRequest();
            updateConnText();
//            Log.d(TAG, "Connection: " + ClientPaths.connectionInfo);
            updateBatteryLevel();
            taskHandler.postDelayed(this, RISK_TASK_PERIOD);
        }
    };

    private void updateConnText() {
        TextView connText = (TextView) findViewById(R.id.connText);
        connText.setText(ClientPaths.connectionInfo);

    }

    private Runnable dustTask = new Runnable()
    {
        public void run()
        {
            dustAndConnectivityRequest();
            taskHandler.postDelayed(this, BT_TASK_PERIOD);

        }
    };

    private void dustAndConnectivityRequest() {
        updateConnectivity();

        if (!dustConnected) {
            Log.d(TAG, "Dust not connected - attempting to reconnect");
//            registerDust();
            findBT(Constants.DUST_SENSOR_ID);
            if (dustDevice != null) {
                if (openDust()) {
                    Log.d(TAG, "Opened dust connection");
                }
            }
        }
    }

    private void scheduleRiskRequest() {
        Log.d(TAG, "scheduleRiskRequest");
        taskHandler.postDelayed(riskTask, RISK_TASK_PERIOD);
    }

    private void scheduleDustRequest() {
        Log.d(TAG, "scheduleDustRequest");
        taskHandler.postDelayed(dustTask, BT_TASK_PERIOD);
    }



    @BackgroundThread
    @ReceiveMessages(Constants.RISK_API)
    void onRiskReceived(int value) { // The nodeId parameter is optional
        Log.d(TAG, "ReceiveMessage risk: " + value);
        updateRiskUI(value);

        // ...
    }

    @BackgroundThread
    @ReceiveMessages(Constants.SUBJECT_API)
    void onSubjectReceived(String sub) { // The nodeId parameter is optional
        Log.d(TAG, "ReceiveMessage subject: " + sub);
        try{
            int num = Integer.parseInt(sub);
            // is an integer!
        } catch (NumberFormatException e) {
            Log.e(TAG, "bad onSubjectReceived - received " + sub);
            return;
        }

        if(!sub.equals("")) {
            ClientPaths.SUBJECT_ID = sub;
            updateSubjectUI(sub);
        }


    }


    @BackgroundThread
    @ReceiveMessages(Constants.LABEL_API)
    void onLabelReceived(String data) { // The nodeId parameter is optional
        Log.d(TAG, "ReceiveMessage label: " + data);
        Toast.makeText(this,data, Toast.LENGTH_LONG).show();
    }

//
//    @BackgroundThread
//    @ReceiveMessages(Constants.MULTI_API)
//    void onMultiReceived(int val) { // The nodeId parameter is optional
//        Log.d(TAG, "ReceiveMessage multi received " + val);
//        // ...
//    }



    private void riskRequest() {

        Log.d(TAG, "Called riskRequest");
        try {
            JSONObject jsonBody = new JSONObject();

            jsonBody.put("timestamp",System.currentTimeMillis());
            jsonBody.put("subject_id", ClientPaths.SUBJECT_ID);
            jsonBody.put("key", ClientPaths.API_KEY);
            jsonBody.put("battery",ClientPaths.batteryLevel);
            jsonBody.put("connection", ClientPaths.connectionInfo);

            String data = jsonBody.toString();
            Log.d(TAG, "risk post: " + data);

            if (true) { //(ClientPaths.connectionInfo.equals("PROXY")) {

                try {
                    Courier.deliverMessage(this, Constants.RISK_API, data);
                } catch (Exception e) {
                    Log.d(TAG, "courier sent riskapi data (with error)");
                }

            }

        } catch (Exception e) {

            e.printStackTrace();
            Log.e(TAG, "[Handled] Error creating risk post request");
        }


    }
    /**
     * Builds a GoogleApiClient. Uses the addApi() method to request the LocationServices API.
     */
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
//                .addApi(Wearable.API)  // used for data layer API
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }


    @Override
    public void onResult(Status status) {
        if (status.isSuccess()) {

            Log.d(TAG, "Successfully requested activity updates");

        } else {
            Log.e(TAG, "Failed in requesting activity updates, "
                    + "status code: "
                    + status.getStatusCode() + ", message: " + status
                    .getStatusMessage());
        }


    }

    private PendingIntent getActivityDetectionPendingIntent() {
        Intent intent = new Intent(this, ActivityDetectionService.class);

        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MainActivity onDestroy");

//        unregisterReceiver(mBroadcastReceiver);

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mHeartReceiver);
        Courier.stopReceiving(this);

        //LocalBroadcastManager.getInstance(this).unregisterReceiver(mHeartReceiver);
//
        if (mGoogleApiClient.isConnected()) {
            ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(
                    mGoogleApiClient,
                    getActivityDetectionPendingIntent()
            ).setResultCallback(this);
            Log.d(TAG, "Removed activity updates");
        }


        try {
            taskHandler.removeCallbacks(riskTask);
        } catch (Exception e) {
            Log.e(TAG, "Risk Timer off");
        }

        try {
            taskHandler.removeCallbacks(dustTask);
        } catch (Exception e) {
            Log.e(TAG, "Dust scan off");
        }


        try {
            unbindService(rfduinoServiceConnection);
            Log.d(TAG, "unbound rfduinoService");
        } catch (Exception e) {
            Log.e(TAG, "unbind rfduinoService");
        }

        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi
                    .removeLocationUpdates(mGoogleApiClient, this);
        }

//        if (mGoogleApiClient.isConnected()) {
//            ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(
//                    mGoogleApiClient,
//                    getActivityDetectionPendingIntent()
//            ).setResultCallback(this);
//        }

//        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();

        stopMeasurement();
        closeSpiro();
        closeDust();
//        unregisterDust(); //old method of connecting to dust sensor

        try {
            if (connReceiver != null)
                unregisterReceiver(connReceiver);

            LocalBroadcastManager.getInstance(this).unregisterReceiver(mLastReceiver);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mHeartReceiver);
        } catch( Exception e) {
            Log.e(TAG, "connReceiver off");
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }
    //
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        requestSubject();

    }


    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");

//        LocalBroadcastManager.getInstance(this).unregisterReceiver(activityReceiver);
//        onResume();
    }

    private void updateLastView(Integer sensorId) {
        if (lastSensorView!=null)
            lastSensorView.setText("Last: " + ClientPaths.getSensorName(sensorId) + "\nDust Sensor: " + (dustConnected ? "Yes" : "No"));
    }

    private void unregisterDust() {
        Log.d(TAG, "Unregister Dust");
//        try {
//            bluetoothAdapter.stopLeScan(this);
//        } catch (Exception e) {
//            Log.e(TAG, "stopLeScan");
//        }
        try {
            if (scanModeReceiver != null)
                unregisterReceiver(scanModeReceiver);

            if (bluetoothStateReceiver != null)
                unregisterReceiver(bluetoothStateReceiver);

            if (rfduinoReceiver != null)
                unregisterReceiver(rfduinoReceiver);
            unbindService(rfduinoServiceConnection);
        } catch (Exception e) {
            Log.e(TAG, "[Handled] Error unregistering dust receiver");

        }
    }

    private void addData(byte[] data) {
        //Log.i(TAG, "in BT addData");
        String ascii = HexAsciiHelper.bytesToAsciiMaybe(data);
        if (ascii != null) {
            processReceivedDustData(ascii);
        }
    }

    public void processReceivedDustData(String receiveBuffer) {
//        Log.d("processDust receiveBuffer", receiveBuffer);
        //example: B:0353E
        String dustData = receiveBuffer.substring(2,6);

        float[] vals = new float[]{Constants.NO_VALUE};
        try {
            vals[0] = Integer.parseInt(dustData);
        } catch (Exception e) {
            e.printStackTrace();
            vals[0] = Constants.NO_VALUE;
            return;
        }
        Log.d(TAG, receiveBuffer + " Dust Reading: " + vals[0]);

        addSensorData(Constants.DUST_SENSOR_ID, Constants.NO_VALUE, System.currentTimeMillis(), vals);
        updateLastView(Constants.DUST_SENSOR_ID);

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


    @Override
    public void onLeScan(BluetoothDevice device, final int rssi, final byte[] scanRecord) {
        bluetoothAdapter.stopLeScan(this);
        bluetoothDevice = device;

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //scan for bluetooth device that contains RF
                if (bluetoothDevice.getName().contains(Constants.DUST_BT_NAME)) {
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


    private final BroadcastReceiver rfduinoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (RFduinoService.ACTION_CONNECTED.equals(action)) {
                upgradeState(STATE_CONNECTED);
                dustConnected = true;
                Log.d("rfduinoReceiver", "connected");
            } else if (RFduinoService.ACTION_DISCONNECTED.equals(action)) {
                downgradeState(STATE_DISCONNECTED);
                dustConnected = false;
                Log.d("rfduinoReceiver", "disconnected");
            } else if (RFduinoService.ACTION_DATA_AVAILABLE.equals(action)) {
                addData(intent.getByteArrayExtra(RFduinoService.EXTRA_DATA));
            }
        }
    };

    public void onFinishActivity(View view) {
        setResult(RESULT_OK);
        finish();
    }

    //sensor BroadCast Listener
    private BroadcastReceiver mHeartReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            Float lastHeartRate = intent.getFloatExtra("heartrate", Constants.NO_VALUE);
//            String message = intent.getStringExtra("message");
            Log.d("sensor receiver", "Got heart rate: " + lastHeartRate.intValue());
            updateHeartUI(lastHeartRate.intValue());
        }
    };



    private void updateHeartUI(int lastHeartRate) {
        TextView heartText = (TextView) findViewById(R.id.heartText);

        Log.d(TAG, "Updating heart UI " + lastHeartRate);
        try {
            if (lastHeartRate == Constants.NO_VALUE)
                heartText.setText("--");
            else
                heartText.setText(lastHeartRate + "");
        } catch (Exception e) {
            e.printStackTrace();
//            Log.d(TAG, "Error updating heart UI");
        }
    }

    //Sensor Related
    private void startMeasurement() {
        Log.i(TAG, "Start Measurement");
        startService(new Intent(getBaseContext(), SensorService.class));
    }


    private void stopMeasurement() {
        Log.i(TAG, "Stop Measurement");
        stopService(new Intent(getBaseContext(), SensorService.class));
    }




//    private GoogleApiClient activityClient;
    /**
     * Runs when a GoogleApiClient object successfully connects.
     * http://stackoverflow.com/questions/27779974/getting-googleapiclient-to-work-with-activity-recognition
     * http://www.sitepoint.com/google-play-services-location-activity-recognition/
     */

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "GoogleApiClient onConnected");


        try {

            final PendingResult<Status>
                    statusPendingResult =
                    ActivityRecognition.ActivityRecognitionApi
                            .requestActivityUpdates(mGoogleApiClient, ACTIVITY_INTERVAL, getActivityDetectionPendingIntent());
            statusPendingResult.setResultCallback(this);
        } catch (Exception e) {
            e.printStackTrace();
        }


        //Request Location Updates from Google API Client
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setNumUpdates(1)
                .setSmallestDisplacement(10) //10m
                .setInterval(LOCATION_INTERVAL)
                .setFastestInterval(LOCATION_INTERVAL);
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
            Log.e(TAG, "[Handled] Error Requesting location service (lack of permission)");
        }

    }


    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended called");
        try {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(mGoogleApiClient, getActivityDetectionPendingIntent());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(TAG, "onConnectionFailed called");
    }

    @Override
    public void onLocationChanged (Location location)
    {
        Log.d(TAG, "onLocationChanged: " + location.getLatitude() + "," + location.getLongitude());
        ClientPaths.currentLocation = location;
    }

    @Override
    public void onEnterAmbient(Bundle ambientDetails) {
        super.onEnterAmbient(ambientDetails);
        Log.d(TAG, "onEnterAmbient - remove task callbacks");
        try {
            taskHandler.removeCallbacks(riskTask);
            taskHandler.removeCallbacks(dustTask);


            LocalBroadcastManager.getInstance(this).unregisterReceiver(mLastReceiver);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mHeartReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
//        setContentView(R.layout.black_layout);

        setContentView(R.layout.black_layout);
    }


    @Override
    public void onExitAmbient() {
        super.onExitAmbient();
        Log.d(TAG, "onExitAmbient - add task callbacks");

        riskRequest();
        dustAndConnectivityRequest();
        scheduleRiskRequest();
        scheduleDustRequest();

        Log.d(TAG, "Set main layout");
        setContentView(R.layout.main_activity);
        stub = (WatchViewStub) findViewById(R.id.stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                if (!isAmbient()) {
                    Log.d(TAG, "Layout Inflated - not ambient");
                    mRectBackground = (RelativeLayout) findViewById(R.id.rect_layout);
                    mRoundBackground = (RelativeLayout) findViewById(R.id.round_layout);
                    setupUI();
                } else {
                    Log.d(TAG, "Layout Inflated - ambient");
                }
            }
        });
    }

    @Override
    public void onUpdateAmbient() {
        super.onUpdateAmbient();
        Log.d(TAG, "onUpdateAmbient");

    }


    //Spirometer connection:
    public Boolean findBT(int type)
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
        if (type == Constants.SPIRO_SENSOR_ID) {
            mmDevice = null;
            if (pairedDevices.size() > 0) {
                String deviceName;
                for (BluetoothDevice device : pairedDevices) {
                    deviceName = device.getName();

                    try {
                        if (deviceName.matches("ASMA_\\d\\d\\d\\d"))   // Find a device with name ASMA_####
                        {
                            mmDevice = device;
                            Log.d("yjcode", "Detected spiro device: " + deviceName);
                            return true;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Handled exception in device name");

                    }
                }
            }
            Log.d(TAG, "findBT did not find paired spiro device");
        } else if (type == Constants.DUST_SENSOR_ID){
            dustDevice = null;
            if (pairedDevices.size() > 0) {
                String deviceName;
                for (BluetoothDevice device : pairedDevices) {
                    deviceName = device.getName();

                    if (deviceName.contains(Constants.DUST_BT_NAME)) {
                        dustDevice = device;
                        Log.d("yjcode", "Detected RFduino device: " + deviceName + " " + dustDevice.getAddress());
                        //add connection for RF duino here as well
                        return true;
                    }
                }
            }
            Log.d(TAG, "findBT did not find paired dustdevice");

        }



        return false;

    }

    private BluetoothConnection spiroConn = null;

    public Boolean openSpiro()
    {
        try {
            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID

            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
            mmSocket.connect();
            mmInputStream = mmSocket.getInputStream();
            beginListenForData();
//            spiroConn = new BluetoothConnection(mmDevice, this);
//            spiroConn.run();

        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "[Handled] openSpiro unsuccessful");
            return false;
        }
        Log.d(TAG, "Spiro Bluetooth Opened");
        Toast.makeText(MainActivity.this, "Connected!",Toast.LENGTH_SHORT).show();
        return true;
    }

    public void closeSpiro() {
        try {
            stopWorker = true;
            if (mmInputStream!=null)
                mmInputStream.close();
            if (mmSocket!=null)
                mmSocket.close();

//            spiroConn.cancel();

        } catch (Exception e) {
            //e.printStackTrace();
            Log.e(TAG, "[Handled] closeSpiro");
            return;

        }
        Log.d(TAG, "spiro bluetooth closed");
    }


    public int pefToRisk(float p) {
        if (p<150)
            return HIGH_RISK;
        else if (p<250)
            return MED_RISK;
        else
            return LOW_RISK;
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
                                                addSensorData(Constants.SPIRO_SENSOR_ID, 3, System.currentTimeMillis(), data.toArray());

//                                                Toast.makeText(MainActivity.this, "PEF Received: " + data.pef + "!", Toast.LENGTH_SHORT).show();
                                                Toast.makeText(MainActivity.this, "Data Received!", Toast.LENGTH_SHORT).show();

//                                                //FOR STATIC DEMO: UPDATE IMAGE BASED ON PEF
                                                if (Constants.staticApp) {
                                                    lastRiskValue = pefToRisk(data.getPef());
                                                    updateRiskUI(lastRiskValue);
                                                }
                                                //END STATIC DEMO SECTION
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



    BluetoothSocket dustSocket;
    BluetoothDevice dustDevice;
    InputStream dustStream;
    int dustPosition;
    byte[] dustBuffer;
    Thread dustThread;

    private BluetoothConnection dustConn=null;

    private ServiceConnection rfduinoServiceConnection=null;

    public Boolean openDust() {
        try {
//            UUID uuid = UUID.fromString("00002221-0000-1000-8000-00805f9b34fb"); //rfduino listen service
//            bluetoothAdapter.cancelDiscovery();
//            dustSocket = dustDevice.createRfcommSocketToServiceRecord(uuid);
//            Method m = dustDevice.getClass().getMethod("createRfcommSocket", new Class[]{int.class});
//            dustSocket = (BluetoothSocket) m.invoke(dustDevice, 1);
//
////            dustSocket = dustDevice.createRfcommSocketToServiceRecord(uuid);
//            dustSocket.connect();
//            dustStream = dustSocket.getInputStream();
//            dustListen();

//            dustConn = new BluetoothConnection(dustDevice,this);
//            dustConn.run();

//            try {
//                unbindService(rfduinoServiceConnection);
//                Log.d(TAG, "unbound rfduinoService");
//            } catch (Exception e) {
//                Log.e(TAG, "unbind rfduinoService");
//            }
            if (rfduinoServiceConnection!=null) {
                try {
                    unregisterDust();
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, "unregisterDust");
                }
            }

            rfduinoServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    rfduinoService = ((RFduinoService.LocalBinder) service).getService();
                    if (rfduinoService.initialize()) {
                        boolean result = rfduinoService.connect(dustDevice);

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

            Intent rfduinoIntent = new Intent(MainActivity.this, RFduinoService.class);
            bindService(rfduinoIntent, rfduinoServiceConnection, BIND_AUTO_CREATE);

            try {
                registerReceiver(scanModeReceiver, new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));
                registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
                registerReceiver(rfduinoReceiver, RFduinoService.getIntentFilter());
            } catch ( Exception e) {
                e.printStackTrace();
                Log.d(TAG, "[Handled] Receivers registered already");

            }


        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "[Handled] openDust unsuccessful");
            return false;
        }
        Log.d(TAG, "Dust Bluetooth Opened");
        return true;
    }

    public void closeDust() {
        try {
            unregisterDust();
            Log.d(TAG, "dust bluetooth closed");
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "[Handled] closeDust");
        }
    }


    public void dustListen()
    {
        Log.d(TAG, "dustListen");
        final Handler handler = new Handler();

        dustPosition = 0;
        dustBuffer = new byte[1024];

        //listener worker thread
        dustThread = new Thread(new Runnable()
        {
            public void run()
            {
                while(!Thread.currentThread().isInterrupted())
                {
                    try
                    {
                        int bytesAvailable = dustStream.available();
                        if(bytesAvailable > 0)
                        {
                            byte[] packetBytes = new byte[bytesAvailable];
                            dustStream.read(packetBytes);
                            for(int i=0;i<bytesAvailable;i++)
                            {
                                byte b = packetBytes[i];
                                if(b == 0x03)
                                {
                                    final byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);

                                    readBufferPosition = 0;

                                    handler.post(new Runnable()
                                    {
                                        public void run()
                                        {

                                            Log.d(TAG, "Received dust data: " + encodedBytes.toString());
                                            try {

//                                                addSensorData(ActivityConstants.SPIRO_SENSOR_ID, 3, System.currentTimeMillis(), encodedBytes);
                                                //Toast.makeText(MainActivity.this, "PEF Received: " + data.pef + "!", Toast.LENGTH_SHORT).show();


                                            } catch (Exception e) {
                                                Log.e(TAG, "[handled] error with receiving dust data");
                                                e.printStackTrace();
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
                        if (dustThread!=null)
                            dustThread.stop();
                    }
                }
            }
        });
        dustThread.start();
    }


    //append sensor data
    private void addSensorData(final Integer sensorType, final Integer accuracy, final Long t, final float[] values) {

        Intent i = new Intent(this, SensorAddService.class);
        i.putExtra("sensorType", sensorType);
        i.putExtra("accuracy", accuracy);
        i.putExtra("time",t);
        i.putExtra("values",values);

        startService(i);
    }

    private BroadcastReceiver mLastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            Integer sensorId = intent.getIntExtra("sensorId",Constants.NO_VALUE);
//            Log.d("receiver", "Got message: " + sensorId);
            updateLastView(sensorId);

        }
    };


}
