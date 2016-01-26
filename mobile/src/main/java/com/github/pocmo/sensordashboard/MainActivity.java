package com.github.pocmo.sensordashboard;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.github.pocmo.sensordashboard.bluetooth.HexAsciiHelper;
import com.github.pocmo.sensordashboard.bluetooth.RFduinoService;
import com.github.pocmo.sensordashboard.data.DustData;
import com.github.pocmo.sensordashboard.events.BusProvider;
import com.github.pocmo.sensordashboard.shared.ClientPaths;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONObject;

import java.util.UUID;

public class MainActivity extends AppCompatActivity implements BluetoothAdapter.LeScanCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private RemoteSensorManager remoteSensorManager;


    private static final String TAG = "MainActivity";

    Toolbar mToolbar;

    private ViewPager pager;
    private View emptyState;
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

    private int state;
    private Boolean rfBound = false;

    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    private Boolean connected;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        connected = false;
        receiveBuffer = "";

        setContentView(R.layout.activity_main);

        mToolbar = (Toolbar) findViewById(R.id.my_awesome_toolbar);
        emptyState = findViewById(R.id.empty_state);

        //bluetoothAdapter.startLeScan(new UUID[]{ RFduinoService.UUID_SERVICE },this);

        initToolbar();
        //initViewPager();

        if (!connected) {
            //onStart();
            bluetoothAdapter.startLeScan(new UUID[]{RFduinoService.UUID_SERVICE}, this);

        }

        TextView t = (TextView) findViewById(R.id.subjectText);
        t.setText("SubjectID: " + ClientPaths.getSubjectID());

        remoteSensorManager = RemoteSensorManager.getInstance(this);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        ToggleButton sensorToggleButton = (ToggleButton) findViewById(R.id.sensorToggleButton);
        sensorToggleButton.setChecked(true);

        sensorToggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                //remoteSensorManager will log the change if called.
                if (isChecked) {
                    remoteSensorManager.startMeasurement();
                    registerReceiver(scanModeReceiver, new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));
                    registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
                    registerReceiver(rfduinoReceiver, RFduinoService.getIntentFilter());

                    updateState(bluetoothAdapter.isEnabled() ? STATE_DISCONNECTED : STATE_BLUETOOTH_OFF);
                } else {
                    remoteSensorManager.stopMeasurement();

                    //bluetoothAdapter.stopLeScan(this);

                    unregisterReceiver(scanModeReceiver);
                    unregisterReceiver(bluetoothStateReceiver);
                    unregisterReceiver(rfduinoReceiver);
                }
            }
        });

        //remoteSensorManager.startMeasurement();
        registerReceiver(scanModeReceiver, new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));
        registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        registerReceiver(rfduinoReceiver, RFduinoService.getIntentFilter());

//        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
//        wl = pm.newWakeLock(
//                PowerManager.PARTIAL_WAKE_LOCK,
//                "wlTag");
//        wl.acquire();


        buildGoogleApiClient();
        mGoogleApiClient.connect();

        Log.d(TAG, "MainActivity onCreate");

    }

    /**
     * Builds a GoogleApiClient. Uses the addApi() method to request the LocationServices API.
     */
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        //wl.release();

        Log.d(TAG, "MainActivity onDestroy");

        //BusProvider.getInstance().unregister(this);

        BusProvider.getInstance().unregister(this);
        remoteSensorManager.stopMeasurement();


        bluetoothAdapter.stopLeScan(this);

        unregisterReceiver(scanModeReceiver);
        unregisterReceiver(bluetoothStateReceiver);
        unregisterReceiver(rfduinoReceiver);

        try {
            unbindService(rfduinoServiceConnection);
        } catch (Exception e) {
            Log.e(TAG, "Attempted to unbind when rfduinoService was unbound - handled");
        }

        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
        //dustTask.cleanUp();

    }

    private void initToolbar() {
        setSupportActionBar(mToolbar);

        final ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(false);
            ab.setTitle(R.string.app_name);
            mToolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    switch (item.getItemId()) {
                        case R.id.action_about:
                            startActivity(new Intent(MainActivity.this, AboutActivity.class));
                            return true;
                        case R.id.action_export: //currently just goes to about
                            startActivity(new Intent(MainActivity.this, AboutActivity.class));
                            return true;
                    }

                    return true;
                }
            });
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        //BusProvider.getInstance().register(this);
        //List<Sensor> sensors = RemoteSensorManager.getInstance(this).getSensors();

        TextView t = (TextView) findViewById(R.id.subjectText);
        t.setText("SubjectID: " + ClientPaths.getSubjectID());


        BusProvider.getInstance().register(this);

        remoteSensorManager.startMeasurement();


    }

    @Override
    protected void onPause() {
        super.onPause();


        //remoteSensorManager.stopMeasurement();
    }


    public void ProcessReceivedData(String data) {
        Log.d(TAG, "ProcessReceivedData (Dust) called");
        if (!connected) {
            connected = true; //we received the data
            receiveBuffer = "";
        }

        receiveBuffer = receiveBuffer + data;

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

                    JSONObject jsonDataEntry = new JSONObject();
                    try {
                        jsonDataEntry.put("sensor_type", ClientPaths.DUST_SENSOR_ID);
                        jsonDataEntry.put("sensor_name", "Dust Sensor");

                        jsonDataEntry.put("value", NewData.iValue);
                        jsonDataEntry.put("timestamp", System.currentTimeMillis());
                        jsonDataEntry.put("timezone", ClientPaths.getTimeZone());
                        jsonDataEntry.put("sensor_id", ClientPaths.GARBAGE_SENSOR_ID);//will be changed to actual sensor (sensorType)
                        //jsonDataEntry.put("sensor_id",sensorType);
                        if (ClientPaths.currentLocation != null) {
                            jsonDataEntry.put("lat", ClientPaths.currentLocation.getLatitude());
                            jsonDataEntry.put("long", ClientPaths.currentLocation.getLongitude());
                            jsonDataEntry.put("accuracy", ClientPaths.currentLocation.getAccuracy());

                        } else {
                            jsonDataEntry.put("accuracy", "Location Not Found");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        return;
                    }
                    Log.i(TAG, "Dust Value Received: " + jsonDataEntry.toString());
                    ClientPaths.appendData(jsonDataEntry);
                    ClientPaths.incrementCount();
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


    /**
     * Runs when a GoogleApiClient object successfully connects.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        // Provides a simple way of getting a device's location and is well suited for
        // applications that do not require a fine-grained location and that do not need location
        // updates. Gets the best and most recent location currently available, which may be null
        // in rare cases when a location is not available.
        ClientPaths.currentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
//        if (ClientPaths.currentLocation==null)
//            Toast.makeText(this,"No Location Detected", Toast.LENGTH_LONG).show();
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
        Log.i(TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }
}

