package com.breatheplatform.beta.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.breatheplatform.beta.R;
import com.breatheplatform.beta.data.SensorAddService;
import com.breatheplatform.beta.data.TestData;
import com.breatheplatform.beta.shared.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

/**
 * Created by cbono on 5/5/16.
 * Used for manually initiated bluetooth connections
 */
public class BTSocket {
    private static final String TAG = "BTSocket";
    private static final Integer BUFFER_SIZE = 1024*2;


    BluetoothAdapter bluetoothAdapter;
    BluetoothSocket mmSocket = null;
    Boolean stopWorker;
    BluetoothDevice mmDevice = null;
    InputStream mmInputStream = null;
    Context context;
    UUID uuid;

    private Integer mIndex=0;
    private float[] values = new float[]{0,0,0};

    private Integer readBufferPosition;
    private byte[] readBuffer = new byte[BUFFER_SIZE];
    private Thread workerThread = null;

    private TestData data;
    private Integer deviceType;

    public BTSocket(Integer type, UUID u, Context c) {
        mmDevice = null;
        context = c;
        uuid = u;
        deviceType = type;
        stopWorker = false;
    }

    public Boolean openConn()
    {
        try {
//            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
            mmSocket.connect();
            mmInputStream = mmSocket.getInputStream();

        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "[Handled] openConn unsuccessful");
            return false;
        }
        Log.d(TAG, "Bluetooth Opened");

        return true;
    }

    public void closeConn() {
        mmDevice = null;
        stopWorker = true;

        try {
            if (workerThread != null)
                workerThread.join();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Error joining workerThread");
        }
        try {
            if (mmInputStream != null)
                mmInputStream.close();
        } catch (Exception e) {
            Log.e(TAG, "Error closing mmInputStream");
        }

        try {
            mmSocket.close();
        } catch (Exception e) {
            Log.e(TAG, "Error closing mmSocket");
        }

        Log.d(TAG, "conn bluetooth closed");
    }



    public void beginListen() {

        if (deviceType == Constants.SPIRO_SENSOR_ID)
            beginSpiroListen();
        else if (deviceType == Constants.AIRBEAM_SENSOR_ID)
            beginBeamListen();
    }



    private void beginBeamListen() {
        Log.d(TAG, "beginBeamListen");

        readBufferPosition = 0;
        stopWorker = false;


        //listener worker thread
        workerThread = new Thread(new Runnable()
        {
            public void run()
            {
                Boolean readActive = true;
                StringBuilder beamData = new StringBuilder();
//                StringBuilder s = new StringBuilder();

                while(!Thread.currentThread().isInterrupted() && !stopWorker)
                {

                    try
                    {
                        int bytesAvailable = mmInputStream.available();
                        if(bytesAvailable > 0) {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
//                            Log.d(TAG, "AirBeam bytesAvailable: " + bytesAvailable + " " + packetBytes.toString());

                            for(int i=0;i<bytesAvailable;i++) {
//                                byte b = packetBytes[i];
                                char c = (char) packetBytes[i];
//                                s.append(c);

                                if (c == ';') //end of number
                                    readActive=false;

                                if (readActive) //add digit to number
                                    beamData.append(c);

                                if(c == '\n') { //end of data row

                                    final String data = beamData.toString();

                                    if (data.contains("BC")) {
                                        mIndex=0;
                                        readActive=true;
                                        beamData.setLength(0);
                                        continue;
                                    }

                                    try {
                                        values[mIndex] = Float.parseFloat(data); //(float) (Math.round(Float.parseFloat(data)*100d)/100d);

                                        switch (mIndex) {
                                            case 0: //PARTICLE MATTER (PM)
                                            case 1: //TEMP (F)
                                                mIndex++;
                                                break;
                                            case 2: //HUMIDITY (RH)
                                                addSensorData(Constants.AIRBEAM_SENSOR_ID, Constants.NO_VALUE, System.currentTimeMillis(), values);
                                            default:
                                                mIndex = 0;
                                                break;
                                        }

                                    } catch (Exception e) {
                                        //erase record and start over if parse error
                                        e.printStackTrace();
                                        Log.e(TAG, "[Handled] Error processing " + data + ", at mIndex=" + mIndex);
                                        mIndex=0;

                                    }

                                    readActive=true;
                                    beamData.setLength(0);

                                }

//                                if (s.length()>5000) {
//                                    Log.d(TAG, "data: " + s.toString());
//                                    s.setLength(0);
//                                }

                            }

                        }

                    }
                    catch (IOException ex)
                    {
                        Log.e(TAG, "IO exception in BT run");
                        stopWorker = true;
                    }
                }


            }
        });
        workerThread.start();

    }

    private void beginSpiroListen()
    {
        Log.d(TAG, "beginSpiroListen");
        final Handler handler = new Handler();

        readBufferPosition = 0;
//        readBuffer = new byte[BUFFER_SIZE];
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
                                    Log.d(TAG, "string spiroData: " + new String(encodedBytes));
                                    data = new TestData(encodedBytes);
                                    readBufferPosition = 0;

                                    try {
                                        handler.post(new Runnable() {
                                            public void run() {
//                                                Log.d(TAG, "Parsed spiro data: " + data.toString());
                                                try {
                                                    Long timestamp = System.currentTimeMillis();
                                                    addSensorData(Constants.SPIRO_SENSOR_ID, 3, timestamp, data.toArray());
                                                    Toast.makeText(context, "Data Received!", Toast.LENGTH_SHORT).show();
//
                                                } catch (Exception e) {
                                                    Toast.makeText(context, R.string.bad_reading, Toast.LENGTH_SHORT).show();
                                                }
                                            }
                                        });
                                        break;
                                    } catch (Exception e) {
                                        stopWorker = true;
                                    }
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

    //Bluetooth socket connection - only check paired devices
    public Boolean findConn() {

        if (mmDevice != null) {
            return true;
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        final String deviceString;

        if(bluetoothAdapter == null) {
            Log.e(TAG, "No bluetooth adapter available");
            return false;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        switch (deviceType) {
            case Constants.SPIRO_SENSOR_ID:
                deviceString = "ASMA_";
                break;
            case Constants.AIRBEAM_SENSOR_ID:
                deviceString = "AirBeam-";
                break;
            default:
                Log.e(TAG, "findConn with invalid type " + deviceType);
                return false;
        }

        if (pairedDevices.size() > 0) {
            String deviceName;
            for (BluetoothDevice device : pairedDevices) {
                deviceName = device.getName();
                try {
                    if (deviceName.contains(deviceString)) {
                        mmDevice = device;
                        Log.d("yjcode", "Detected device: " + deviceName);
                        return true;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Handled exception in device name");

                }
            }
        }

        Log.d(TAG, "findConn did not find paired device matching " + deviceString);
        return false;
    }

    private void addSensorData(final Integer sensorType, final Integer accuracy, final Long t, final float[] values) {

        Intent i = new Intent(context, SensorAddService.class);
        i.putExtra("sensorType", sensorType);
        i.putExtra("accuracy", accuracy);
        i.putExtra("time",t);
        i.putExtra("values",values);

        context.startService(i);
    }


}
