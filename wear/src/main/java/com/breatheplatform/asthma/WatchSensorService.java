package com.breatheplatform.asthma;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by cbono on 11/14/15.
 */

public class WatchSensorService extends Service implements SensorEventListener {

    /* //in case of later adding UI for notifications of sensor transmission
    public static final String NOTIFY_UI_ON  = "com.breatheplatform.asthma.action.NOTIFY_UI_ON";
    public static final String NOTIFY_UI_OFF = "com.breatheplatform.asthma.action.NOTIFY_UI_OFF";
    */
    /* Directory Structure */
    private static final SimpleDateFormat nameFormat = new SimpleDateFormat("'_'yyyy-MM-dd-HH", Locale.US);
    private  static final String rootDirectory    = android.os.Environment.getExternalStorageDirectory().getAbsolutePath()+"/Asthma";
    private  static final File accelDirectory     = new File(rootDirectory+"/Accelerometer");
    private  static final File beaconDirectory    = new File(rootDirectory+"/Beacon");
    /*private  static final File compassDirectory   = new File(rootDirectory+"/Compass");*/
    private  static final File gyroDirectory      = new File(rootDirectory+"/Gyroscope");
    private  static final File heartDirectory      = new File(rootDirectory+"/Heartrate");
    private  static final boolean GENERATE_HOURLY = true;
    private  static final boolean APPEND_FILES    = true;

    /* SAMPLING RATES */
    private static final int   ACCEL_SAMPLE_RATE    = 10;         // 10 Hz
    private static final int   HEART_SAMPLE_RATE    = 10;         // 10 Hz
    //  private static final int   COMPASS_SAMPLE_RATE  = 10;         // 10 Hz
    private static final int   GYRO_SAMPLE_RATE     = 10;         // 10 Hz
    private static final int[] BEACON_SAMPLE_RATE = { 200, 1800 };// 200ms ON, 1800ms OFF
    private static final int   FILE_BUFFER_SIZE     = 64 * 1024;      // 64 * 1024 byte, 64 kB file buffer
    private static final int   MAX_BATCHED_DELAY    = 60000;      // Sensor can batch data upto 60 seconds before flusing to AP

    // Bluetooth dust sensor configuration -- locate device
    private static final byte[] Beacon_Mask       = {2, 21, -21, 19, 0, 0, 127, -6, 17, -28, -68, -109, 0, 2, -91, -43, -59, 27, 0, 0, 0, 0,   0};
    private static final byte[] UCLA_manufac_data = {2, 21, -21, 19, 0, 0, 127, -6, 17, -28, -68, -109, 0, 2, -91, -43, -59, 27, 0, 0, 0, 0, -62};
    private static final int    UCLA_manID        = 76;
    private static final char[] hexArray = "0123456789ABCDEF".toCharArray();

    private static BluetoothLeScanner mBluetoothLeScanner;
    private static Timer DutyCycleON = new Timer(), DutyCycleOFF = new Timer();
    private static boolean BT_SCANNING = false;

    private static ArrayList<ScanFilter> filters = new ArrayList<ScanFilter>();
    private static ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();

    private static final String TAG = "Asthma_Sensors";
    private WatchSensorService singleton = null;


    // Time of Boot = Current Wall Clock Time - Time since boot.
    // mFileHour indicates the which hour the CSV file was generated.
    //private static final long timeOfBoot = (System.currentTimeMillis() * 1000000) - SystemClock.elapsedRealtimeNanos();
    private static final long timeOfBoot = System.currentTimeMillis() - SystemClock.elapsedRealtime();
    private static BufferedWriter mAccelBuffer = null, mBeaconBuffer = null, mGyroBuffer = null, mHeartBuffer=null;
    private static SensorManager mSensorManager;
    private static PowerManager.WakeLock mWakeLock = null;

    public WatchSensorService() {
        super();}

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("main","Started WatchSensors service");
        // Prevent the CPU from going to sleep while sensors are scanning.
        if(mWakeLock == null) {
            final PowerManager mgr = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            mWakeLock.acquire();
        }

        singleton = this;

        // Initializing the sensors
        mSensorManager              = (SensorManager) getSystemService(SENSOR_SERVICE);
        final Sensor mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        //final Sensor mGyroscope     = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        final Sensor mHeartrate     = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        //final Sensor mCompass       = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        // Start recording data
        mSensorManager.registerListener(this, mAccelerometer, (1000000/ACCEL_SAMPLE_RATE));
        //mSensorManager.registerListener(this, mGyroscope, (1000000/GYRO_SAMPLE_RATE));
        mSensorManager.registerListener(this, mHeartrate, (1000000/HEART_SAMPLE_RATE));
        //mSensorManager.registerListener(this, mCompass, (1000000 / COMPASS_SAMPLE_RATE),MAX_BATCHED_DELAY);

        filters.add(new ScanFilter.Builder().setManufacturerData(UCLA_manID, UCLA_manufac_data, Beacon_Mask).build());
        final BluetoothManager bluetoothManager  = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter mBluetoothAdapter = bluetoothManager.getAdapter();

        if (mBluetoothAdapter == null)
            return;

        if(!mBluetoothAdapter.isEnabled())
            mBluetoothAdapter.enable();

        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

        while (mBluetoothLeScanner == null)
            SystemClock.sleep(50);

        startDutyCycle(BEACON_SAMPLE_RATE[0], BEACON_SAMPLE_RATE[1]);

        //TODO :: Show this on watch face also
        // (this).sendBroadcast(new Intent(NOTIFY_UI_ON));
    }

    private static void startDutyCycle(int ON_TIME, int OFF_TIME) {
        final TimerTask DutyCycle1  = new TimerTask() {@Override public void run() {if(!BT_SCANNING){BT_SCANNING = true; mBluetoothLeScanner.startScan(filters, settings, mScanCallback);}}};
        final TimerTask DutyCycle2  = new TimerTask() {@Override public void run() {if(BT_SCANNING){mBluetoothLeScanner.stopScan(mScanCallback); BT_SCANNING = false;}}};
        DutyCycleON.scheduleAtFixedRate(DutyCycle1, 0, ON_TIME + OFF_TIME);
        DutyCycleOFF.scheduleAtFixedRate(DutyCycle2, ON_TIME, ON_TIME + OFF_TIME);
    }

    /* Files are reset every-time start service is called */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // Reset the Files.

        mAccelBuffer  = closeAccel()  ? writeNewFiles(accelDirectory)  : null;
        /*mGyroBuffer   = closeGyro()   ? writeNewFiles(gyroDirectory)   : null;*/
        mHeartBuffer   = closeHeart()   ? writeNewFiles(heartDirectory)   : null;
        mBeaconBuffer = closeBeacon() ? writeNewFiles(beaconDirectory) : null;

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Write-out any remaining data (in sensor) to file buffer(s).
        mSensorManager.unregisterListener(this);
        mSensorManager.flush(this);

        DutyCycleON.cancel();
        DutyCycleOFF.cancel();

        if(BT_SCANNING)
            mBluetoothLeScanner.stopScan(mScanCallback);

        closeAccel();
        closeBeacon();
        closeGyro();
        closeHeart();

        singleton = null;

        // Release the Wake Lock so CPU can go to sleep.
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }

    /* Upon receiving values from the sensor, detect the correct file buffer to write to
       and fill out file-buffer. All files are written as CSV files.

       Data values :: timestamp, x, y, z
     */
    @Override
    public void onSensorChanged(SensorEvent event) {

        final long absolute_timestamp = timeOfBoot + (event.timestamp/1000000);
        //// get Nanos timestamp in epoch time (with Nanos precision).
        // final long absolute_timestamp = timeOfBoot + event.timestamp;
        String event_string = new String();
        switch (event.sensor.getType()) {
            case (Sensor.TYPE_ACCELEROMETER):
                try {
                    event_string = absolute_timestamp + "," +
                            event.values[0] + "," +
                            event.values[1] + "," +
                            event.values[2] + "\n";
                    mAccelBuffer.write(event_string);

                } catch (Exception e) {
                    mAccelBuffer = closeAccel() ? writeNewFiles(accelDirectory) : null;
                }
                break;

            case (Sensor.TYPE_GYROSCOPE):
                try {
                    event_string = absolute_timestamp + "," +
                            event.values[0] + "," +
                            event.values[1] + "," +
                            event.values[2] + "\n";
                    mGyroBuffer.write(event_string);
                } catch (Exception e) {
                    mGyroBuffer = closeGyro() ? writeNewFiles(gyroDirectory) : null;
                }
                break;
            case (Sensor.TYPE_HEART_RATE):
                try {
                    event_string = absolute_timestamp + "," +
                            event.values[0] + "\n";
                    mHeartBuffer.write(event_string);
                } catch (Exception e) {
                    mHeartBuffer = closeHeart() ? writeNewFiles(heartDirectory) : null;
                }
                break;
        }
        Log.d("Sensor Event: ",event_string);
    }

    /**
     *  Called every time a UCLA beacon is detected. For each beacon, scans the results and writes to
     * file only if the RSSI value is lesser than -25. This is so that beacons that are relatively closer
     * do not magnify and distort the overall readings. In general, if the beacon is extremely close to
     * the watch, the RSSI may even become positive. */

    private static ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            int RSSI;
            if ((RSSI = result.getRssi()) >= -25)
                return;

            try {
                String MINOR = bytesToHex(new byte[] {result.getScanRecord().getManufacturerSpecificData().valueAt(0)[20], result.getScanRecord().getManufacturerSpecificData().valueAt(0)[21]});
                mBeaconBuffer.write(((result.getTimestampNanos()/1000000) + timeOfBoot) + "," + MINOR + "," + RSSI + ",0" + "\n");
            } catch (Exception e) { mBeaconBuffer = closeBeacon() ? writeNewFiles(beaconDirectory) : null;}
        }

        @Override
        public void onBatchScanResults(java.util.List<android.bluetooth.le.ScanResult> results) {
            for(ScanResult result : results)
                onScanResult(1,result);
        }
        @Override
        public void onScanFailed(int errorCode) {
            if(errorCode == SCAN_FAILED_ALREADY_STARTED)
                BT_SCANNING = true;
        }
    };

    /**
     * Convert raw data to a string in hexadecimal values. Optimized to work for UCLA beacon strings
     *
     * @param bytes Raw data buffer
     * @return String containing the hex representation
     * <p/>
     * hexChars[j * 2] = (char) hexArray[v >>> 4];
     * hexChars[j * 2 + 1] =  (char) hexArray[v & 0x0F];
     */
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[3];
        int v = bytes[0] & 0xFF;
        hexChars[0] = hexArray[v & 0x0F];
        v = bytes[1] & 0xFF;
        hexChars[1] = hexArray[v >>> 4];
        hexChars[2] = hexArray[v & 0x0F];
        return new String(new char[]{hexChars[0], hexChars[1], hexChars[2]});
    }
   /* private static boolean resetFiles(){
       closeLogFiles();
       return writeNewFiles();
    }*/

  /*  private static boolean writeNewFiles() {
        try {
         mAccelBuffer    = new BufferedWriter(new FileWriter(createUniqueFile(accelDirectory,GENERATE_HOURLY),APPEND_FILES),FILE_BUFFER_SIZE);
         mBeaconBuffer   = new BufferedWriter(new FileWriter(createUniqueFile(beaconDirectory,GENERATE_HOURLY),APPEND_FILES),FILE_BUFFER_SIZE);
       //  mCompassBuffer  = new BufferedWriter(new FileWriter(createUniqueFile(compassDirectory,GENERATE_HOURLY),APPEND_FILES),FILE_BUFFER_SIZE);
         mGyroBuffer     = new BufferedWriter(new FileWriter(createUniqueFile(gyroDirectory,GENERATE_HOURLY),APPEND_FILES),FILE_BUFFER_SIZE);
        } catch (IOException e) {return false;}
        return true;
    }*/

   /* private static void closeLogFiles() {
        if(mAccelBuffer   != null){ try {mAccelBuffer.close();mAccelBuffer = null;}     catch (IOException e) {} }
        if(mBeaconBuffer  != null){ try {mBeaconBuffer.close();mBeaconBuffer = null;}   catch (IOException e) {} }
      //  if(mCompassBuffer != null){ try {mCompassBuffer.close();mCompassBuffer = null;} catch (IOException e) {} }
        if(mGyroBuffer    != null){ try {mGyroBuffer.close();mGyroBuffer = null;}       catch (IOException e) {} }
    }*/

    /*private static Buff resetFiles(BufferedWriter buffer,File DIR){
        closeLogFiles(buffer);
        return writeNewFiles(DIR);
    }*/

    private static boolean closeAccel(){
        if(mAccelBuffer == null)
            return true;
        try{
            mAccelBuffer.close();
            mAccelBuffer = null;
            return true;
        }catch (Exception e) {
            return false;
        }
    }

    private static boolean closeGyro(){
        if(mGyroBuffer == null)
            return true;
        try{
            mGyroBuffer.close();
            mGyroBuffer = null;
            return true;
        }catch (Exception e) {
            return false;
        }
    }
    private static boolean closeHeart(){
        if(mHeartBuffer== null)
            return true;
        try{
            mHeartBuffer.close();
            mHeartBuffer = null;
            return true;
        }catch (Exception e) {
            return false;
        }
    }
    private static boolean closeBeacon(){
        if(mBeaconBuffer == null)
            return true;
        try{
            mBeaconBuffer.close();
            mBeaconBuffer = null;
            return true;
        }catch (Exception e) {
            return false;
        }
    }


    private static BufferedWriter writeNewFiles(File DIR) {
        BufferedWriter buffer = null;
        try {
            if(DIR.isDirectory() || DIR.mkdirs())
                buffer = new BufferedWriter(new FileWriter(createUniqueFile(DIR,GENERATE_HOURLY),APPEND_FILES),FILE_BUFFER_SIZE);
        } catch (IOException e) {return null;}
        return buffer;
    }
     /*   if(mAccelBuffer   != null){ try {mAccelBuffer.close();mAccelBuffer = null;}     catch (IOException e) {} }
        if(mBeaconBuffer  != null){ try {mBeaconBuffer.close();mBeaconBuffer = null;}   catch (IOException e) {} }
        //  if(mCompassBuffer != null){ try {mCompassBuffer.close();mCompassBuffer = null;} catch (IOException e) {} }
        if(mGyroBuffer    != null){ try {mGyroBuffer.close();mGyroBuffer = null;}       catch (IOException e) {} }
    }*/


   /* private static void resetAccel(){
        if(mAccelBuffer    != null){
            try {
                mAccelBuffer.close();
                mAccelBuffer    = new BufferedWriter(new FileWriter(createUniqueFile(accelDirectory,GENERATE_HOURLY),APPEND_FILES),FILE_BUFFER_SIZE);
            }    catch (Exception e) {} }
    }

    private static void resetBeacon(){
        if(mBeaconBuffer  != null){
            try {
                mBeaconBuffer.close();
                mBeaconBuffer   = new BufferedWriter(new FileWriter(createUniqueFile(beaconDirectory,GENERATE_HOURLY),APPEND_FILES),FILE_BUFFER_SIZE);
            }    catch (Exception e) {} }
    }

    private static void resetCompass(){
        if(mCompassBuffer  != null){
            try {
                mCompassBuffer.close();
                mCompassBuffer  = new BufferedWriter(new FileWriter(createUniqueFile(compassDirectory,GENERATE_HOURLY),APPEND_FILES),FILE_BUFFER_SIZE);
            }    catch (Exception e) {} }
    }

    private static void resetGyro(){
        if(mGyroBuffer    != null){
            try {
                mGyroBuffer.close();
                mGyroBuffer     = new BufferedWriter(new FileWriter(createUniqueFile(gyroDirectory,GENERATE_HOURLY),APPEND_FILES),FILE_BUFFER_SIZE);
            }    catch (Exception e) {} }
    }*/


    /*
    Creates a new file within the srcDirectory ensuring that is it unqiuely named with the
    following naming system :: MAC_YYYY-MM-DD-HH.runN.format.csv. Here MAC is the Bluetooth MAC
    address, used to uniquely identify the watch. runN specifies the run iteration number for the
    current hour.
    */
    public static File createUniqueFile(File srcDirectory,boolean appendLast) {

        final String TimeStamp = nameFormat.format(new Date(System.currentTimeMillis()));

        String extension = srcDirectory.getPath();

        if(extension.contains("Gyro"))
            extension = ".gyro.csv";
        else if (extension.contains("Accel"))
            extension = ".accel.csv";
        else if (extension.contains("heart"))
            extension = ".heart.csv";
        else if (extension.contains("Beacon"))
            extension = ".beacon.csv";
        else
            extension = ".csv";

        if(appendLast)
            return new File(srcDirectory, Build.SERIAL + TimeStamp + extension);

        // get current run number
        final int numberFiles = getListFileswithExtension(srcDirectory,TimeStamp).size();
        final int fileNumber = appendLast ? numberFiles : (numberFiles + 1);
        final String runN = ".run" + ((numberFiles > 0) ? String.valueOf(fileNumber) : "1" );

        return new File(srcDirectory,Build.SERIAL + TimeStamp + runN + extension);
    }

    /* Returns a list of files which contains a specific extension */
    public static List<File> getListFileswithExtension(File parentDir,String extension) {
        ArrayList<File> inFiles = new ArrayList<File>();
        File[] files = parentDir.listFiles();
        for (File file : files) {
            if (file.isDirectory())
                inFiles.addAll(getListFileswithExtension(file, extension));
            else if (file.getPath().contains(extension))
                inFiles.add(file);
        }
        return inFiles;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No implementation necessary
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
