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

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.speech.RecognizerIntent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.view.GestureDetectorCompat;
import android.support.wearable.view.DelayedConfirmationView;
import android.support.wearable.view.DismissOverlayView;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ScrollView;
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
import com.google.android.gms.wearable.Wearable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity implements DelayedConfirmationView.DelayedConfirmationListener,
        BluetoothAdapter.LeScanCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener, SensorEventListener
{

    private static final String TAG = "MainActivity";

    private static final int NOTIFICATION_ID = 1;
    private static final int NOTIFICATION_REQUEST_CODE = 1;
    private static final int NUM_SECONDS = 5;

    private GestureDetectorCompat mGestureDetector;
    private DismissOverlayView mDismissOverlayView;



    private String receiveBuffer;
    private RFduinoService rfduinoService;
    private BluetoothDevice bluetoothDevice;
    private PowerManager.WakeLock wl;
    protected GoogleApiClient mGoogleApiClient;


    // State machine
    final private static int STATE_BLUETOOTH_OFF = 1;
    final private static int STATE_DISCONNECTED = 2;
    final private static int STATE_CONNECTING = 3;
    final private static int STATE_CONNECTED = 4;

    private final static int SENS_LINEAR_ACCELERATION = Sensor.TYPE_LINEAR_ACCELERATION;
    private final static int SENS_HEARTRATE = Sensor.TYPE_HEART_RATE;

    private int state;
    private Boolean rfBound = false;


    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    private Boolean connected;

    //Sensor related

    private SensorManager mSensorManager;
    private Sensor mHeartrateSensor;
    private DeviceClient client;
    private ScheduledExecutorService mScheduler;


    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.main_activity);

        mSensorManager = ((SensorManager) getSystemService(SENSOR_SERVICE));
        client = DeviceClient.getInstance(this);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);


        mDismissOverlayView = (DismissOverlayView) findViewById(R.id.dismiss_overlay);
        mDismissOverlayView.setIntroText(R.string.intro_text);
        mDismissOverlayView.showIntroIfNecessary();
        mGestureDetector = new GestureDetectorCompat(this, new LongPressListener());

        connected = false;
        receiveBuffer = "";

        TextView t = (TextView) findViewById(R.id.subjectText);
        t.setText("SubjectID: " + ClientPaths.getSubjectID());


//
//        TextView t = (TextView) findViewById(R.id.subjectText);
//        t.setText("SubjectID: " + ClientPaths.getSubjectID());

        //remoteSensorManager = RemoteSensorManager.getInstance(this);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        ToggleButton sensorToggleButton = (ToggleButton) findViewById(R.id.sensorToggleButton);
        sensorToggleButton.setChecked(false);

        sensorToggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                //remoteSensorManager will log the change if called.
                if (isChecked) {
                    Log.i(TAG, "startSensors");
                    startMeasurement();

                    if (!connected)
                        bluetoothAdapter.startLeScan(new UUID[]{RFduinoService.UUID_SERVICE}, MainActivity.this);

                    registerReceiver(scanModeReceiver, new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));
                    registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
                    registerReceiver(rfduinoReceiver, RFduinoService.getIntentFilter());

                    //updateState(bluetoothAdapter.isEnabled() ? STATE_DISCONNECTED : STATE_BLUETOOTH_OFF);

                } else {
                    Log.i(TAG, "stopSensors");
                    stopMeasurement();

                    unregisterReceiver(scanModeReceiver);
                    unregisterReceiver(bluetoothStateReceiver);
                    unregisterReceiver(rfduinoReceiver);

                    bluetoothAdapter.stopLeScan(MainActivity.this);

                    connected = false;
                }
            }
        });



        final Button idButton = (Button) findViewById(R.id.idButton);
//        final EditText idEditText   = (EditText) findViewById(R.id.idEditText);
//        final NumberPicker idNumberPicker = (NumberPicker) findViewById(R.id.idNumberPicker);
//        idNumberPicker.setMinValue(1000);
//        idNumberPicker.setMaxValue(9999);

        idButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getSubjectFromSpeech();
            }
        });


        buildGoogleApiClient();
        mGoogleApiClient.connect();

        //acquire partial wake lock on device
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK,
                "wlTag");
        wl.acquire();




        Log.d(TAG, "MainActivity onCreate");

    }

    private static final int SPEECH_REQUEST_CODE = 0;

    // Create an intent that can start the Speech Recognizer activity
    private void getSubjectFromSpeech() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
// Start the activity, the intent will be populated with the speech text
        startActivityForResult(intent, SPEECH_REQUEST_CODE);
    }

    // This callback is invoked when the Speech Recognizer returns.
// This is where you process the intent and extract the speech text from the intent.
    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            List<String> results = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
            String spokenText = results.get(0);
            // Do something with spokenText

            try {
                //Integer sid = idNumberPicker.getValue()//.parseInt(sidString);
                Integer sid = Integer.parseInt(spokenText);

                ClientPaths.setSubjectID(sid);

                TextView t = (TextView) findViewById(R.id.subjectText);
                t.setText("SubjectID: " + ClientPaths.getSubjectID());
                Toast.makeText(MainActivity.this, "Success SID: " + sid, Toast.LENGTH_SHORT).show();
            } catch (Exception e) { //parse error of integer in input field
                e.printStackTrace();
                Toast.makeText(MainActivity.this, "Heard '" + spokenText + "' - not a valid #", Toast.LENGTH_SHORT).show();

            }
        }
        super.onActivityResult(requestCode, resultCode, data);
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

        //release partial wake lock
        if (wl != null) {
            wl.release();
        }

        Log.d(TAG, "MainActivity onDestroy");

        stopMeasurement();

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
            e.printStackTrace();

        }

        try {
            unbindService(rfduinoServiceConnection);
        } catch (Exception e) {
            Log.i(TAG, "Attempted to unbind when rfduinoService was unbound - handled");
        }

        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi
                    .removeLocationUpdates(mGoogleApiClient, this);
        }
        mGoogleApiClient.disconnect();
        //dustTask.cleanUp();

    }





    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //getMenuInflater().inflate(R.menu.menu_main, menu);
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


    public void ProcessReceivedData(String data) {
        Log.d(TAG, "ProcessReceivedData (Dust) called");
        if (!connected) {
            connected = true; //we received the data
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
                    client.sendSensorData(ClientPaths.DUST_SENSOR_ID, 0, System.currentTimeMillis(), vals);

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
        Log.i(TAG, "in BT addData");
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
                if (bluetoothDevice.getName().contains("RF")) {
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

    //Location

    private static final Integer UPDATE_INTERVAL_MS = 1000 * 60;
    private static final Integer FASTEST_INTERVAL_MS = UPDATE_INTERVAL_MS;
    /**
     * Runs when a GoogleApiClient object successfully connects.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
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
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, "Successfully requested location updates");
                            }
                        } else {
                            Log.e(TAG,
                                    "Failed in requesting location updates, "
                                            + "status code: "
                                            + status.getStatusCode()
                                            + ", message: "
                                            + status.getStatusMessage());
                        }
                    }
                });
        // Provides a simple way of getting a device's location and is well suited for
        // applications that do not require a fine-grained location and that do not need location
        // updates. Gets the best and most recent location currently available, which may be null
        // in rare cases when a location is not available.
        ClientPaths.currentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (ClientPaths.currentLocation==null) {
            Toast.makeText(this, "No Location Detected", Toast.LENGTH_SHORT).show();
            Log.i(TAG, "No Location Detected");
        } else {
            Toast.makeText(this,"Location Found", Toast.LENGTH_SHORT).show();
            Log.i(TAG, "Location Found: " + ClientPaths.currentLocation.getLatitude() + "," + ClientPaths.currentLocation.getLongitude());
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        ClientPaths.currentLocation = location;
    }


    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Refer to the javadoc for ConnectionResult to see what error codes might be returned in
        // onConnectionFailed.
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
    }


    @Override
    public void onConnectionSuspended(int cause) {
        // The connection to Google Play services was lost for some reason. We call connect() to
        // attempt to re-establish the connection.
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "connection to location client suspended");
        }
        mGoogleApiClient.connect();
    }




    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return mGestureDetector.onTouchEvent(event) || super.dispatchTouchEvent(event);
    }

    private class LongPressListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public void onLongPress(MotionEvent event) {
            mDismissOverlayView.show();
        }
    }

    /**
     * Handles the button to launch a notification.
     */
    public void showNotification(View view) {
        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_title))
                .setSmallIcon(R.drawable.ic_launcher)
                .addAction(R.drawable.ic_launcher,
                        getText(R.string.action_launch_activity),
                        PendingIntent.getActivity(this, NOTIFICATION_REQUEST_CODE,
                                new Intent(this, GridExampleActivity.class),
                                PendingIntent.FLAG_UPDATE_CURRENT))
                .build();
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
        //finish();
    }


    /**
     * Handles the button press to finish this activity and take the user back to the Home.
     */
    public void onFinishActivity(View view) {
        setResult(RESULT_OK);
        //finish();
    }

    /**
     * Handles the button to start a DelayedConfirmationView timer.
     */
    public void onStartTimer(View view) {
        DelayedConfirmationView delayedConfirmationView = (DelayedConfirmationView)
                findViewById(R.id.timer);
        delayedConfirmationView.setTotalTimeMs(NUM_SECONDS * 1000);
        delayedConfirmationView.setListener(this);
        delayedConfirmationView.start();
        scroll(View.FOCUS_DOWN);
    }

    @Override
    public void onTimerFinished(View v) {
        Log.d(TAG, "onTimerFinished is called.");
        scroll(View.FOCUS_UP);
    }

    @Override
    public void onTimerSelected(View v) {
        Log.d(TAG, "onTimerSelected is called.");
        scroll(View.FOCUS_UP);
    }

    private void scroll(final int scrollDirection) {
        final ScrollView scrollView = (ScrollView) findViewById(R.id.scroll);
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(scrollDirection);
            }
        });
    }


    //Sensor Related

    private void startMeasurement() {

        mHeartrateSensor = mSensorManager.getDefaultSensor(SENS_HEARTRATE);
        Sensor linearAccelerationSensor = mSensorManager.getDefaultSensor(SENS_LINEAR_ACCELERATION);
        Sensor heartrateSamsungSensor = mSensorManager.getDefaultSensor(65562);

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

            if (heartrateSamsungSensor != null) {
                mSensorManager.registerListener(this, heartrateSamsungSensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Log.d(TAG, "Samsungs Heartrate Sensor not found");
            }


            if (linearAccelerationSensor != null) {
                mSensorManager.registerListener(this, linearAccelerationSensor, SensorManager.SENSOR_DELAY_NORMAL);
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
        client.sendSensorData(event.sensor.getType(), event.accuracy, event.timestamp, event.values);

    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
