package com.breatheplatform.beta.sensors;

import android.app.Service;
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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.breatheplatform.beta.ClientPaths;
import com.breatheplatform.beta.bluetooth.HexAsciiHelper;
import com.breatheplatform.beta.bluetooth.RFduinoService;
import com.breatheplatform.beta.data.SensorAddService;
import com.breatheplatform.beta.shared.Constants;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import me.denley.courier.Courier;

/**
 * Created by cbono on 4/5/16.
 * http://www.tutorialspoint.com/android/android_services.htm
 * lowest sampling rate from sensors is 5hz (sensor delay normal)
 */
public class SensorService extends Service implements SensorEventListener, BluetoothAdapter.LeScanCallback {
    private static final String TAG = "SensorService";

    //Sensor related
    private ScheduledExecutorService mScheduler;
    private SensorManager mSensorManager;
    private Sensor heartRateSensor;
    private Sensor heartRateSamsungSensor;
    private Sensor linearAccelerationSensor;
    private Sensor gyroSensor;


    private static final int SENS_LINEAR_ACCELERATION = Sensor.TYPE_LINEAR_ACCELERATION;
    private static final int SENS_HEARTRATE = Sensor.TYPE_HEART_RATE;
    private static final int SENS_GYRO = Sensor.TYPE_GYROSCOPE;

    private static final int MAX_DELAY = 1000000*2; //2*10^6 us
//    private static final Context context;
    private static final int PTS_PER_DATA = 5;
    private static final int ENERGY_LIMIT =  PTS_PER_DATA *2;
    private static int accelerationCount = 0;

    //for energy measurements
    private static float sumX = 0, sumY = 0, sumZ = 0;
    private static float sumX2, sumY2, sumZ2;
    private static float energy = 0;


    private static int gyroCount = 0;
    private static float sumGyroX = 0, sumGyroY = 0, sumGyroZ = 0;

    private void eventCallBack(SensorEvent event) {
//        Log.d(TAG, "onSensorChanged");
        int sensorId = event.sensor.getType();
        long timestamp = event.timestamp;//System.currentTimeMillis();
        switch (sensorId) {
            case ClientPaths.HEART_SENSOR_ID:
            case ClientPaths.SS_HEART_SENSOR_ID:
                float heartRate = event.values[0];

                if (event.accuracy > 1) {//or 2 for higher accuracy requirement
                    checkForQuestionnaire(heartRate);
                    addSensorData(sensorId, event.accuracy, timestamp, event.values);

                    //update the heart UI (just heart rate currently)
                    //http://stackoverflow.com/questions/8802157/how-to-use-localbroadcastmanager
                    Intent i = new Intent(Constants.HEART_EVENT);
                    i.putExtra("heartrate", heartRate);
                    LocalBroadcastManager.getInstance(SensorService.this).sendBroadcast(i);

                } else {
                    Log.d(TAG, "heart rate " + heartRate + " accuracy too low: " + event.accuracy);
                }

                break;
            case SENS_GYRO:
                gyroCount++;
                sumGyroX += event.values[0];
                sumGyroY += event.values[1];
                sumGyroZ += event.values[2];
                if (gyroCount == PTS_PER_DATA) {
                    //add averaged sensor measurement
//                    Log.d(TAG, gyroCount + " gyro points");
                    addSensorData(sensorId, event.accuracy, timestamp, new float[]{sumGyroX/ PTS_PER_DATA,sumGyroY/ PTS_PER_DATA, sumGyroZ/ PTS_PER_DATA});
                    sumGyroX = 0;
                    sumGyroY = 0;
                    sumGyroZ = 0;
                    gyroCount = 0;
                }
                break;
            case SENS_LINEAR_ACCELERATION:
                accelerationCount++;
                sumX += event.values[0];
                sumY += event.values[1];
                sumZ += event.values[2];
                if (accelerationCount == PTS_PER_DATA) {
                    //add averaged sensor measurement
                    sumX2=sumX;
                    sumY2=sumY;
                    sumZ2=sumZ;
//                    Log.d(TAG, accelerationCount + " acc points");
                    addSensorData(sensorId, event.accuracy, timestamp, new float[]{sumX / PTS_PER_DATA, sumY / PTS_PER_DATA, sumZ / PTS_PER_DATA});
                }
                else if (accelerationCount == ENERGY_LIMIT) {
                    addSensorData(sensorId, event.accuracy, timestamp, new float[]{(sumX-sumX2)/ PTS_PER_DATA,(sumY-sumY2)/ PTS_PER_DATA, (sumZ-sumZ2)/ PTS_PER_DATA});
                    float energy = (float) (Math.pow(sumX,2) + Math.pow(sumY,2) + Math.pow(sumZ,2));
                    addSensorData(Constants.ENERGY_SENSOR_ID, Constants.NO_VALUE, timestamp, new float[]{energy});
                    sumX = 0;
                    sumY = 0;
                    sumZ = 0;
//                    Log.d(TAG, accelerationCount + " acc points -> add energy data point");
                    accelerationCount = 0;
                }
                break;
        }
//            updateLastView(sensorId);
    }

    /*
    Have listeners/messages set up to when heart rate is high
    (115 bpm for 12 year olds, 130 bpm for 8-12 year olds,
    find age using sensor ID), activity classified as running
    (from Anahita's classifier), and inhaler is used
    (from pulled data from Propeller Health's AsthmaPollus app)
    so Ohmage can use this data to create triggers
    for the EMA questionnaire on the phone.
     */
    private void checkForQuestionnaire(Float heartRate) {
        if (ClientPaths.activityDetail.contains("RUN")) {
            if (ClientPaths.userAge <= 12) {
                if (heartRate >= 130) {
                    requestQuestionnaire();
                }
            } else { //older than 12
                if (heartRate >= 115) {
                    requestQuestionnaire();
                }
            }
        }
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        eventCallBack(event);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }


    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Let it continue running until it is stopped.
//        Toast.makeText(sensorEventListener, "SensorService Started", Toast.LENGTH_LONG).show();
        Log.d(TAG, "onStartCommand");

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);


        heartRateSensor = mSensorManager.getDefaultSensor(SENS_HEARTRATE);
        linearAccelerationSensor = mSensorManager.getDefaultSensor(SENS_LINEAR_ACCELERATION);
        gyroSensor = mSensorManager.getDefaultSensor(SENS_GYRO);
        heartRateSamsungSensor = mSensorManager.getDefaultSensor(ClientPaths.SS_HEART_SENSOR_ID);//65562
//        Log.d(TAG, "sensor delays (ms): heart, lin, gyro");
//        Log.d("heart",heartRateSensor.getMaxDelay()/1000+"");
//        Log.d("lin",linearAccelerationSensor.getMaxDelay()/1000+"");
//        Log.d("gyro",gyroSensor.getMaxDelay()/1000+"");


        Log.i(TAG, "Start Measurement");


        //http://stackoverflow.com/questions/30153904/android-how-to-set-sensor-delay
        if (linearAccelerationSensor != null) {
            mSensorManager.registerListener(SensorService.this, linearAccelerationSensor, SensorManager.SENSOR_DELAY_NORMAL, MAX_DELAY);// 1000000, 1000000);
        }  else {
            Log.d(TAG, "No Linear Acceleration Sensor found");
        }


        if (gyroSensor != null) {
            mSensorManager.registerListener(SensorService.this, gyroSensor, SensorManager.SENSOR_DELAY_NORMAL, MAX_DELAY);
        } else {
            Log.w(TAG, "No Gyroscope Sensor found");
        }


        if (heartRateSensor != null) {
            final int measurementDuration   = 10;   // Seconds
            final int measurementBreak      = 5;    // Seconds

            mScheduler = Executors.newScheduledThreadPool(1);
            mScheduler.scheduleAtFixedRate(
                    new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "register Heartrate Sensor");
                            mSensorManager.registerListener(SensorService.this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL, MAX_DELAY);

                            try {
                                Thread.sleep(measurementDuration * 1000);
                            } catch (InterruptedException e) {
                                Log.e(TAG, "Interrupted while waitting to unregister Heartrate Sensor");
                            }

                            Log.d(TAG, "unregister Heartrate Sensor");
                            mSensorManager.unregisterListener(SensorService.this, heartRateSensor);
                        }
                    }, 3, measurementDuration + measurementBreak, TimeUnit.SECONDS);

        } else {
            Log.d(TAG, "No Heartrate Sensor found");
        }

        dustRequest();
        scheduleDustRequest();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this);
        }
        if (mScheduler != null && !mScheduler.isTerminated()) {
            mScheduler.shutdown();
        }

        try {
            unbindService(rfduinoServiceConnection);
            Log.d(TAG, "remove rfduinoService");
        } catch (Exception e) {
            Log.e(TAG, "[Handled] unbinding rfduinoService");
        }

        unregisterDust();
//        if (connReceiver != null)
//            unregisterReceiver(connReceiver);

        try {
            taskHandler.removeCallbacks(dustTask);
        } catch (Exception e) {
            Log.e(TAG, "Dust scan off");
        }
    }

    //append sensor data
    public void addSensorData(final Integer sensorType, final Integer accuracy, final Long t, final float[] values) {
        Intent i = new Intent(this, SensorAddService.class);
        i.putExtra("sensorType", sensorType);
        i.putExtra("accuracy", accuracy);
        i.putExtra("time", t);
        i.putExtra("values", values);
        startService(i);
    }

    //TODO: this intent will launch questionnaire activity on the phone
    private void requestQuestionnaire() {
        Courier.deliverMessage(this, Constants.QUESTION_API, "");
    }

         /* BLUETOOTH LOGIC BELOW */

    private static final int BT_TASK_PERIOD = 150000; //ms
    private static final int REQUEST_ENABLE_BT = 1;


    //    private ConnectionReceiver connReceiver;
    BluetoothAdapter bluetoothAdapter;
    private RFduinoService rfduinoService;
    private BluetoothDevice bluetoothDevice;
    ;
    BluetoothDevice dustDevice;

    private ServiceConnection rfduinoServiceConnection = null;

    // Bluetooth (Dust) State machine
    final private static int STATE_BLUETOOTH_OFF = 1;
    final private static int STATE_DISCONNECTED = 2;
    final private static int STATE_CONNECTING = 3;
    final private static int STATE_CONNECTED = 4;

    private int state;

    private Handler taskHandler = new Handler();

//    private static StringBuilder dustData = new StringBuilder();
//    public static String getDustData() {
//        String data = dustData.toString();
//        dustData.setLength(0);
//        recordCount = 0;
//        return data;
//    }


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

        //scan for bluetooth device that contains RF
        if (bluetoothDevice.getName().contains(Constants.DUST_BT_NAME)) {
            Log.i(TAG, "Found RF Device: " + bluetoothDevice.getName());
            Intent rfduinoIntent = new Intent(this, RFduinoService.class);
            bindService(rfduinoIntent, rfduinoServiceConnection, BIND_AUTO_CREATE);
        }
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
                ClientPaths.dustConnected = true;
                try {
                    unbindService(rfduinoServiceConnection);
                    Log.d(TAG, "remove rfduinoService");
                } catch (Exception e) {
                    Log.e(TAG, "[Handled] unbinding rfduinoService");
                }
                Log.d("rfduinoReceiver", "connected");
            } else if (RFduinoService.ACTION_DISCONNECTED.equals(action)) {
                downgradeState(STATE_DISCONNECTED);
                ClientPaths.dustConnected = false;
                scheduleDustRequest();
                Log.d("rfduinoReceiver", "disconnected");
            } else if (RFduinoService.ACTION_DATA_AVAILABLE.equals(action)) {
                addData(intent.getByteArrayExtra(RFduinoService.EXTRA_DATA));
            }
        }
    };

    public Boolean openDust() {
        try {
//            if (rfduinoServiceConnection != null) {
//                try {
//                    unregisterDust();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                    Log.e(TAG, "unregisterDust");
//                }
//            }

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

            Intent rfduinoIntent = new Intent(SensorService.this, RFduinoService.class);
            bindService(rfduinoIntent, rfduinoServiceConnection, BIND_AUTO_CREATE);

            try {
                registerReceiver(scanModeReceiver, new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));
                registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
                registerReceiver(rfduinoReceiver, RFduinoService.getIntentFilter());
                Log.d(TAG, "Dust Receivers Registered");

            } catch (Exception e) {
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

//    public void closeDust() {
//        try {
//            unregisterDust();
//            Log.d(TAG, "dust bluetooth closed");
//        } catch (Exception e) {
//            e.printStackTrace();
//            Log.e(TAG, "[Handled] closeDust");
//        }
//    }

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
        String dustData = receiveBuffer.substring(2, 6);

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
    }


    private Runnable dustTask = new Runnable()
    {
        public void run()
        {
            dustRequest();
//            updateBatteryLevel();
//            connectivityRequest();
//            updateConnectivityText();
            taskHandler.postDelayed(this, BT_TASK_PERIOD);

        }
    };

    private void dustRequest() {
        Log.d(TAG, "dustRequest");

        if (!ClientPaths.dustConnected) {
            Log.d(TAG, "Dust not connected - attempting to reconnect");
//            registerDust();
            findBT();
            if (dustDevice != null) {
                if (openDust()) {
                    Log.d(TAG, "Opened dust connection");
                }
            }
        }
    }

    private void scheduleDustRequest() {
        Log.d(TAG, "scheduleDustRequest");
        taskHandler.postDelayed(dustTask, BT_TASK_PERIOD);
    }



    //Spirometer connection:
    public Boolean findBT()
    {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if(bluetoothAdapter == null)
        {
            Log.d(TAG, "No bluetooth adapter available");

        }

//        if(!bluetoothAdapter.isEnabled())
//        {
//            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//            startActivityForResult(enableBluetooth, REQUEST_ENABLE_BT);
//        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();


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

        return false;

    }



    //OTHER
    //get connectivity and save it
    private void connectivityRequest() {
        final int LEVELS = 5;
        ConnectivityManager cm = ((ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE));
        if (cm == null)
            return;
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        String connectionInfo = "None";
        if (activeNetwork != null) {
            connectionInfo = activeNetwork.getTypeName();

            if (connectionInfo.equals("WIFI")) {
                WifiManager wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int level = WifiManager.calculateSignalLevel(wifiInfo.getRssi(), LEVELS);
                connectionInfo += " " + level;
            }

        }
//        ClientPaths.connectionInfo = connectionInfo;
        Log.d(TAG, "Connectivity " + connectionInfo);
    }



}
