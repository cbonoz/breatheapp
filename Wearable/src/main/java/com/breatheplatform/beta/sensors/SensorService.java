package com.breatheplatform.beta.sensors;

import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.breatheplatform.beta.ClientPaths;
import com.breatheplatform.beta.MainActivity;
import com.breatheplatform.beta.activity.ActivityDetectionService;
import com.breatheplatform.beta.bluetooth.BTSocket;
import com.breatheplatform.beta.bluetooth.HexAsciiHelper;
import com.breatheplatform.beta.bluetooth.RFduinoService;
import com.breatheplatform.beta.messaging.SensorAddService;
import com.breatheplatform.beta.shared.Constants;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.wearable.Wearable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

import me.denley.courier.Courier;

/**
 * Created by cbono on 4/5/16.
 * Master Sensor class for controlling all sensors (Except spirometer which is attached to main activity UI) for the wearable while in the wake state
 * Accelerometer, Gyroscope, Heartrate, Dust Sensor, Location, Activity Recognition, Connectivity, Battery, Energy
 * http://www.tutorialspoint.com/android/android_services.htm
 * //http://stackoverflow.com/questions/8802157/how-to-use-localbroadcastmanager
 * lowest sampling rate from sensors is 5hz (sensor delay normal)
 */
public class SensorService extends Service implements SensorEventListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        LocationListener//, ResultCallback<Status>
{
    private static final String TAG = "SensorService";

    //Sensor related
    private ScheduledExecutorService mScheduler;
    private static SensorManager mSensorManager;// = (SensorManager) getSystemService(SENSOR_SERVICE);

//    private Sensor heartRateSensor;
//    private Sensor samsungHeartRateSensor;
//    private Sensor linearAccelerationSensor;
//    private Sensor gyroSensor;
//    private Sensor ppgSensor;

    private GoogleApiClient mGoogleApiClient;

    private static final int SENS_LINEAR_ACCELERATION = Sensor.TYPE_LINEAR_ACCELERATION;
    private static final int SENS_HEARTRATE = Sensor.TYPE_HEART_RATE;
    private static final int SENS_GYRO = Sensor.TYPE_GYROSCOPE;

    private static final int MAX_DELAY = 1000000*2; //2*10^6 us
    private static final int FIXED_SENSOR_RATE = 200000; //5 hz in us
    private static final int LOCATION_COUNT_LIMIT = 180000/(Constants.SENSOR_OFF_TIME+Constants.SENSOR_ON_TIME) - 2; //~3 minutes

//    private static final Integer ACTIVITY_INTERVAL = Constants.ONE_MIN_MS / 2; //request every 30s (0 runs at fastest interval)
    //request every 2 min (if updates are used, use this slow interval)
    private static final Integer LOCATION_INTERVAL = Constants.ONE_MIN_MS * 2;

//    private static final Context context;
    private static final int PTS_PER_DATA = 1000000 / FIXED_SENSOR_RATE;

    //for counting sensor events
    private static int accelerationCount = 1;
    private static int gyroCount = 1;

    //for averaging sensor events
    private static float sumAccX = 0, sumAccY = 0, sumAccZ = 0;
    private static float sumGyroX = 0, sumGyroY = 0, sumGyroZ = 0;

//    private static float sumEnergyX=0, sumEnergyY=0, sumEnergyZ=0;
//    private static Float[] gyroValues = new Float[3];
//    private static Float[] accValues = new Float[3];

    /*
    Check conditions for launching questionnaire
     */
    private void checkForQuestionnaire(Float heartRate) {
        if (ClientPaths.activityDetail.contains("RUN")) {
            if (ClientPaths.userAge <= 12) {
                if (heartRate >= 130) {
                    addSensorData(Constants.TERMINATE_SENSOR_ID, null, null, null);
                    requestQuestionnaire();
                }
            } else { //older than 12
                if (heartRate >= 115) {
                    addSensorData(Constants.TERMINATE_SENSOR_ID, null, null, null);
                    requestQuestionnaire();
                }
            }
        }
    }

    //event callback for body sensors
    // - bluetooth connected sensors are managed in their respective Bluetooth classes
    @Override
    public void onSensorChanged(SensorEvent event) {
        int sensorId = event.sensor.getType();
        long timestamp = System.currentTimeMillis(); //event.timestamp
        switch (sensorId) {
            case ClientPaths.HEART_SENSOR_ID:
            case ClientPaths.SS_HEART_SENSOR_ID:

                Float heartRate = event.values[0];
                Log.d(TAG, "heart rate: " + heartRate + ", acc " + event.accuracy);

                if (event.accuracy > 1) {//or 2 for higher accuracy requirement
//                    checkForQuestionnaire(heartRate); //if provided age, check for questionnaire launch conditions
                    addSensorData(sensorId, event.accuracy, timestamp, event.values);

                    Intent i = new Intent(Constants.HEART_EVENT);
                    i.putExtra("heart", heartRate.intValue());
                    LocalBroadcastManager.getInstance(SensorService.this).sendBroadcast(i);
                }
                break;
            case SENS_GYRO:
                sumGyroX += event.values[0];
                sumGyroY += event.values[1];
                sumGyroZ += event.values[2];
                if (gyroCount == PTS_PER_DATA) {
                    //add averaged sensor measurement
//                    Log.d(TAG, gyroCount + " gyro points");
                    addSensorData(sensorId, event.accuracy, timestamp, new float[]{sumGyroX / PTS_PER_DATA, sumGyroY / PTS_PER_DATA, sumGyroZ / PTS_PER_DATA});
                    sumGyroX = 0;
                    sumGyroY = 0;
                    sumGyroZ = 0;
                    gyroCount = 0;
                }
                gyroCount++;
                break;
            case SENS_LINEAR_ACCELERATION:

                sumAccX += event.values[0];
                sumAccY += event.values[1];
                sumAccZ += event.values[2];
//                if (accelerationCount % PTS_PER_DATA == 0) {
                if (accelerationCount == PTS_PER_DATA) {

                    addSensorData(sensorId, event.accuracy, timestamp, new float[]{sumAccX / PTS_PER_DATA, sumAccY / PTS_PER_DATA, sumAccZ / PTS_PER_DATA});
                    sumAccX = 0;
                    sumAccY = 0;
                    sumAccZ = 0;
                    accelerationCount = 0;
                }

                accelerationCount++;
                break;
            default:
                Log.e(TAG, "Unknown sensorChanged event, id: " + sensorId);
                break;

        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private PowerManager.WakeLock wakeLock;

    private BTSocket beamConn;
    private static UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static Integer locationCounter = 0;

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }


    //Enter point for SensorService, grabs wake lock and launches and listens to sensors until destroyed
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Let it continue running until it is stopped.
//        Toast.makeText(sensorEventListener, "SensorService Started", Toast.LENGTH_LONG).show();
        Log.d(TAG, "sensor onStartCommand");

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "SensorWakeLock");

        if (!wakeLock.isHeld()) {
            wakeLock.acquire();
            Log.d(TAG, "acquire lock");
        }

        registerDust();

        beamConn = new BTSocket(Constants.AIRBEAM_SENSOR_ID, uuid, this);
        new BluetoothTask().execute(Constants.AIRBEAM_SENSOR_ID);

        //update location every LOCATION_COUNT_LIMIT sensor periods
        if (locationCounter==0) {
            connectApiClient();
        }

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        final Sensor heartRateSensor  = mSensorManager.getDefaultSensor(SENS_HEARTRATE);
        final Sensor linearAccelerationSensor = mSensorManager.getDefaultSensor(SENS_LINEAR_ACCELERATION);
        final Sensor gyroSensor = mSensorManager.getDefaultSensor(SENS_GYRO);


        //http://stackoverflow.com/questions/30153904/android-how-to-set-sensor-delay
        if (linearAccelerationSensor != null) {
            mSensorManager.registerListener(SensorService.this, linearAccelerationSensor, FIXED_SENSOR_RATE, MAX_DELAY);// 1000000, 1000000);
        }  else {
            Log.d(TAG, "No Linear Acceleration Sensor found");
        }


        if (gyroSensor != null) {
            mSensorManager.registerListener(SensorService.this, gyroSensor, FIXED_SENSOR_RATE, MAX_DELAY);
        } else {
            Log.w(TAG, "No Gyroscope Sensor found");
        }

        if (heartRateSensor != null) {
            mSensorManager.registerListener(SensorService.this, heartRateSensor, FIXED_SENSOR_RATE, MAX_DELAY);
            Log.d(TAG, "register regular heartrate sensor");
        } else {
            Log.w(TAG, "No Heart Rate Sensor found");
            final Sensor samsungHeartRateSensor = mSensorManager.getDefaultSensor(ClientPaths.SS_HEART_SENSOR_ID);//65562
            mSensorManager.registerListener(SensorService.this, samsungHeartRateSensor, FIXED_SENSOR_RATE, MAX_DELAY);
        }

//        if (ppgSensor != null) {
//            mSensorManager.registerListener(SensorService.this, ppgSensor, SensorManager.SENSOR_DELAY_NORMAL, MAX_DELAY);
//            Log.d(TAG, "register regular heartrate sensor");
//        } else {
//            Log.w(TAG, "No PPG Sensor found");
//        }


//        if (samsungHeartRateSensor != null) {
//            mSensorManager.registerListener(SensorService.this, samsungHeartRateSensor, SensorManager.SENSOR_DELAY_NORMAL, MAX_DELAY);
//            Log.d(TAG, "register samsung heartrate sensor");
//        } else {
//
//            Log.w(TAG, "No Heart Rate Sensor found");
//        }


        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "sensor onDestroy");

        if (ClientPaths.dustConnected) {
            unregisterDust();
            ClientPaths.dustConnected = false;
        }

        beamConn.closeConn();


        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this);
        }
        if (mScheduler != null && !mScheduler.isTerminated()) {
            mScheduler.shutdown();
        }


        //use counter to reduce the amount of location requests (no need to request every cycle)
        if (locationCounter==0) {

            try {
                mGoogleApiClient.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
//                Log.e(TAG, "[Handled] unregistering gapiclient, locationCounter " + locationCounter);
            }

            locationCounter++;

        } else if (locationCounter>LOCATION_COUNT_LIMIT) {
            locationCounter=0;
        } else {
            locationCounter++;
        }

        Log.d(TAG, "release lock");

        if (Constants.fixedSensorRate)
            addSensorData(Constants.TERMINATE_SENSOR_ID, null, null, null);


        wakeLock.release();
    }



    //TODO: this intent will launch questionnaire activity on the phone
    private void requestQuestionnaire() {
        Courier.deliverMessage(this, Constants.QUESTION_API, "");
    }

    /* Google API Logic Below */

    /**
     * Builds a GoogleApiClient. Uses the addApi() method to request the LocationServices API.
     */
    private void connectApiClient() {
        Log.d(TAG, "Connecting location client");
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
//                .addApi(ActivityRecognition.API)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mGoogleApiClient.connect();
    }

    private PendingIntent getActivityDetectionPendingIntent() {
        Intent intent = new Intent(this, ActivityDetectionService.class);
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }


    /**
     * Runs when a GoogleApiClient object successfully connects.
     * http://stackoverflow.com/questions/27779974/getting-googleapiclient-to-work-with-activity-recognition
     * http://www.sitepoint.com/google-play-services-location-activity-recognition/
     */

//    private Boolean locationActive = false;

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "GoogleApiClient onConnected");


        Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (location == null) {
            Log.d(TAG, "Requested location updates");
            LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setNumUpdates(1)
                .setSmallestDisplacement(10) //10m
                .setInterval(LOCATION_INTERVAL)
                .setFastestInterval(LOCATION_INTERVAL);
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, locationRequest, this);
//            locationActive = true;
        } else {
            Log.d(TAG, "New Location Found: " + location.getLatitude() + "," + location.getLongitude());
            ClientPaths.currentLocation = location;
//            lastLocationTime = System.currentTimeMillis();
        }

    }


    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended called");
        try {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
//            ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(mGoogleApiClient, getActivityDetectionPendingIntent());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.e(TAG, "onConnectionFailed called");
        try {
            if (!result.hasResolution()) {
                GoogleApiAvailability.getInstance().getErrorDialog((MainActivity) ClientPaths.mainContext, result.getErrorCode(), 0).show();
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            result.startResolutionForResult((MainActivity) ClientPaths.mainContext, Constants.REQUEST_GOOGLE_PLAY_SERVICES);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Exception while starting resolution activity", e);
        }
    }

    @Override
    public void onLocationChanged (Location location)
    {
        if (location!=null) {
            Log.d(TAG, "onLocationChanged: " + location.getLatitude() + "," + location.getLongitude());
            ClientPaths.currentLocation = location;
//            lastLocationTime = System.currentTimeMillis();
        }

    }

         /* BLUETOOTH LOGIC BELOW */

    private static final int REQUEST_ENABLE_BT = 1;

    //    private ConnectionReceiver connReceiver;
    private static BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private static RFduinoService rfduinoService;
    private static BluetoothDevice dustDevice = null;
    private static ServiceConnection rfduinoServiceConnection = null;

    // Bluetooth (Dust) State machine
    final private static int STATE_BLUETOOTH_OFF = 1;
    final private static int STATE_DISCONNECTED = 2;
    final private static int STATE_CONNECTING = 3;
    final private static int STATE_CONNECTED = 4;

    private int state;

    private Handler taskHandler = new Handler();


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
                Log.d(TAG, "rfduinoReceiver connected");
            } else if (RFduinoService.ACTION_DISCONNECTED.equals(action)) {
                downgradeState(STATE_DISCONNECTED);
                unregisterDust();
                ClientPaths.dustConnected = false;
                Log.d(TAG, "rfduinoReceiver disconnected");

            } else if (RFduinoService.ACTION_DATA_AVAILABLE.equals(action)) {
                addData(intent.getByteArrayExtra(RFduinoService.EXTRA_DATA));
            }
        }
    };

    public Boolean openDust() {
        Log.d(TAG, "openDust");
        try {

            if (rfduinoServiceConnection == null) {

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
                        downgradeState(STATE_DISCONNECTED);
                    }
                };
            }

            try {
                registerReceiver(scanModeReceiver, new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));
                registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
                registerReceiver(rfduinoReceiver, RFduinoService.getIntentFilter());
                Log.d(TAG, "Dust Receivers Registered");

            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, "[Handled] Receivers registered already");
            }


            try {
                Intent rfduinoIntent = new Intent(SensorService.this, RFduinoService.class);
                bindService(rfduinoIntent, rfduinoServiceConnection, BIND_AUTO_CREATE);
            } catch (Exception e) {
                e.printStackTrace();
            }

            Log.d(TAG, "Dust Bluetooth Opened");
            return true;


        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "[Handled] openDust unsuccessful");
            return false;
        }

    }



    private void unregisterDust() {
        Log.d(TAG, "Unregister Dust");

        try {
            unregisterReceiver(scanModeReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            unregisterReceiver(bluetoothStateReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            unregisterReceiver(rfduinoReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            unbindService(rfduinoServiceConnection);
        } catch (Exception e) {
            e.printStackTrace();
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
        ClientPaths.dustConnected = true;
//        Log.d("processDust receiveBuffer", receiveBuffer);
        //example: B:0353E
        String dustData = receiveBuffer.substring(2, 6);

        float[] vals = new float[]{Constants.NO_VALUE};
        try {
            vals[0] = Integer.parseInt(dustData);
        } catch (Exception e) {
            e.printStackTrace();
            vals[0] = Constants.NO_VALUE;

        }

        Log.d(TAG, receiveBuffer + " Dust Reading: " + vals[0]);
        if (vals[0]!=Constants.NO_VALUE)
            addSensorData(Constants.DUST_SENSOR_ID, Constants.NO_VALUE, System.currentTimeMillis(), vals);
    }


    private void registerDust() {
        if (!ClientPaths.dustConnected) {
            Log.d(TAG, "Dust not connected - attempting to reconnect");
            if (findDust()) {
                if (openDust()) {
                    ClientPaths.dustConnected = true;
                    Log.d(TAG, "Opened dust connection");
                } else {
                    ClientPaths.dustConnected = false;
                }
            }
        }
    }


    //Spirometer connection:
    public Boolean findDust()
    {
        if (dustDevice != null)
            return true;

        if(bluetoothAdapter == null) {
            Log.e(TAG, "No bluetooth adapter available");
            return false;
        }

        if(!bluetoothAdapter.isEnabled())
        {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            try {
                ((MainActivity) ClientPaths.mainContext).startActivityForResult(enableBluetooth, REQUEST_ENABLE_BT);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            String deviceName;
            for (BluetoothDevice device : pairedDevices) {
                deviceName = device.getName();

                if (deviceName.contains(Constants.DUST_BT_NAME)) {//AIRBEAM_BT_NAME)) {
                    dustDevice = device;
                    Log.d("yjcode", "Detected dust RFdevice: " + deviceName + " " + dustDevice.getAddress());
                    //add connection for RF duino here as well
                    return true;
                }
            }
        }
        Log.d(TAG, "findDust did not find paired dustdevice");
        return false;

    }

    private class BluetoothTask extends AsyncTask<Integer, Void, String> {
        @Override
        protected String doInBackground(Integer... params) {

            if (beamConn.findConn()) {
                if (beamConn.openConn()) {
                    return "CONNECTED";
                } else {
                    return "NOT_ON";
                }
            } else {
                return "NOT_PAIRED";
            }


        }

        @Override
        protected void onPostExecute(String result) {
            Log.d(TAG, "bluetooth result: " + result);
            switch (result) {
                case "CONNECTED":
                    beamConn.beginListen();
                    ClientPaths.dustConnected = true;
                    break;
                default:
                    Log.e(TAG, "Could not connect to AirBeam, result: " + result);
                    break;
            }


        }
    }

    /* addSensorData method for accumulating sensor data point json objects */

    //append sensor data
    public void addSensorData(final Integer sensorType, final Integer accuracy, final Long t, final float[] values) {
        Intent i = new Intent(this, SensorAddService.class);
        i.putExtra("sensorType", sensorType);
        i.putExtra("accuracy", accuracy);
        i.putExtra("time", t);
        i.putExtra("values", values);
        startService(i);
//
//        JSONObject jsonValue = new JSONObject();
//
//        try {
//            switch (sensorType) {
//                case (Sensor.TYPE_LINEAR_ACCELERATION): //units m/s^2
//                case (Sensor.TYPE_GYROSCOPE): //units rad/s
//
//                    jsonValue.put("x", values[0]);
//                    jsonValue.put("y", values[1]);
//                    jsonValue.put("z", values[2]);
//                    jsonValue.put("sensor_accuracy",accuracy);
//                    break;
//                case (Sensor.TYPE_HEART_RATE):
//                    jsonValue.put("sensor_accuracy",accuracy);
//                case (Constants.DUST_SENSOR_ID):
//                    if (values[0]<=0) {
//                        Log.d(TAG, "Received " + sensorName + " data <= 0 -> skip");
//                        return;
//                    }
//                    jsonValue.put("v", values[0]);
//                    break;
//                case (Constants.SPIRO_SENSOR_ID):
//                    jsonValue.put("fev1", values[0]);
//                    jsonValue.put("pef", values[1]);
//                    jsonValue.put("goodtest", values[2]);
//                    break;
//                case (Constants.ENERGY_SENSOR_ID):
//                    jsonValue.put("energy", values[0]);
//                    break;
//
//                case (Constants.AIRBEAM_SENSOR_ID):
//
//                    jsonValue.put("PM",precision(values[0],2));
//                    jsonValue.put("F",values[1]);
//                    jsonValue.put("RH",values[2]);
//                    break;
//                case (Constants.ACTIVITY_SENSOR_ID):
//                    jsonValue.put("type", values[0]);
//                    jsonValue.put("confidence",accuracy);
//                    break;
//                default:
//                    Log.e(TAG, "Unexpected Sensor " + sensorName + " " + sensorType);
//                    return;
//            }
//
//            jsonDataEntry.put("value", jsonValue);
//            jsonDataEntry.put("timestamp", currentTime);//System.currentTimeMillis());
//            jsonDataEntry.put("timezone", tz);
//            jsonDataEntry.put("sensor_id", sensorNames.getServerID(sensorType));//will be changed to actual sensor (sensorType)
//
//            //check if the location is currently available
//            if (ClientPaths.currentLocation!=null) {
//                jsonDataEntry.put("lat", ClientPaths.currentLocation.getLatitude());
//                jsonDataEntry.put("lon", ClientPaths.currentLocation.getLongitude());
//                jsonDataEntry.put("location_accuracy", ClientPaths.currentLocation.getAccuracy());
//            } else {
//                jsonDataEntry.put("lat",Constants.NO_VALUE);
//                jsonDataEntry.put("lon",Constants.NO_VALUE);
//                jsonDataEntry.put("location_accuracy", Constants.NO_VALUE);
//            }
//
//        } catch (Exception e) {
//            Log.e(TAG, "error in creating jsonDataEntry");
//            e.printStackTrace();
//            return;
//        }
//
//        String dataEntry = jsonDataEntry.toString();
//
//        //sensorData is a stringBuilder
//        sensorData.append(dataEntry);
//        recordCount++;
//        if (recordCount >= RECORD_LIMIT) {
//            createDataPostRequest();
//            clearData();
//        } else {
//            sensorData.append("\n"); //else add a newline to sensorData
//        }
//
//
//        Log.d(TAG, "Data Added #" + recordCount + ": " + dataEntry);
    }







}
