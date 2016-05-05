package com.breatheplatform.beta.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Created by cbono on 3/18/16.
 */

public class BluetoothConnection extends Thread {
//    private Context context=null;
    private BluetoothSocket mmSocket;
    private InputStream mmInputStream=null;
    private OutputStream mmOutputStream=null;

    private BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    byte[] buffer;

    // Unique UUID for this application, you may use different
//    private final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static final UUID MY_UUID = UUID.fromString("00002221-0000-1000-8000-00805f9b34fb");
    private static final String TAG = "BluetoothConnection";
//    public final static UUID MY_UUID = BluetoothHelper.sixteenBitUuid(0x2220);

    public BluetoothConnection(BluetoothDevice device, Context c) {
        BluetoothSocket tmp = null;
//        context = c;
        Log.d(TAG, "device: " + device.toString());



        try {
            bluetoothAdapter.cancelDiscovery();
            mmSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            Method m = device.getClass().getMethod("createRfcommSocket", int.class);
            mmSocket = (BluetoothSocket) m.invoke(device, 1);
//            mmSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            mmSocket.connect();
            mmInputStream = mmSocket.getInputStream();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Error connecting BT");
        }

    }

    public void run() {
        Log.d(TAG, "Bluetooth Connection started - run");
        buffer = new byte[1024];
        int readBufferPosition = 0;

        // Keep listening to the InputStream while connected
        while (true) {

            try
            {
                int bytesAvailable = mmInputStream.available();
                if(bytesAvailable > 0)
                {
                    byte[] packetBytes = new byte[bytesAvailable];
                    mmInputStream.read(packetBytes);
                    Log.d(TAG, "bytesAvailable: " + bytesAvailable + " " + packetBytes.toString());
                }
            }
            catch (IOException ex)
            {
                Log.e(TAG, "IO exception in BT run");
            }


        }
    }

    public void write(byte[] buffer) {
        try {
            //write the data to socket stream
            mmOutputStream.write(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void cancel() {
        Log.d(TAG, "Closed dust connection");

        try {
            mmSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



}
