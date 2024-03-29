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

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.breatheplatform.beta.bluetooth.BTSocket;
import com.breatheplatform.beta.sensors.SensorService;
import com.breatheplatform.beta.shared.Constants;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import me.denley.courier.BackgroundThread;
import me.denley.courier.Courier;
import me.denley.courier.ReceiveMessages;


public class MainActivity extends WearableActivity
        //,DataApi.DataListener, MessageApi.MessageListener,NodeApi.NodeListener
{
    private static final String TAG = "MainActivity";

    private static final SimpleDateFormat AMBIENT_DATE_FORMAT =
            new SimpleDateFormat("hh:mm aa", Locale.US);

    //Risk value constants
    private static final int RISK_TASK_PERIOD=20000; //20 seconds

    private static final int LOW_RISK = 2;
    private static final int MED_RISK = 1;
    private static final int HIGH_RISK = 0;

    private static int lastRiskValue = LOW_RISK;//ActivityConstants.NO_VALUE;

    //UI elements
    private ToggleButton spiroToggleButton;
    private RelativeLayout loadingPanel;
    private TextView lastSensorText;
    private TextView dateText;
    private TextView riskText;
    private TextView heartText;
    private TextView subjectText;
    private ImageView smileView;
    private ImageView heartImage;

    //app status UI elements
    private ImageView dustChecked;
    private ImageView airChecked;
    private ImageView webChecked;

    private TextView dustText;
    private TextView airText;
    private TextView webText;

    private RelativeLayout mRectBackground;
    private RelativeLayout mRoundBackground;

    private WatchViewStub stub;

    private SharedPreferences prefs;

    //Used for Spirometer Bluetooth Connection state
    private BTSocket spiroConn;
    //state for sensors high or low
    private Boolean sensorToggled = false;

    private AlarmManager alarmManager;
    private Vibrator v;
    private WearAlarmReceiver wearAlarmReceiver;
    private static final IntentFilter wearFilter =  new IntentFilter(Constants.WEAR_ALARM_ACTION);
    private PendingIntent sensorPI;

    //Sensor Controllers Below
    private SensorManager mSensorManager = null;
    private Sensor mSigMotionSensor;

    //Used for non-fixed rate (motion-based) sensor sampling
    private final TriggerListener mListener =  new TriggerListener();


    //request the subject from the phone if not present in the watch preferences
    private void requestSubjectAndUpdateUI() {
        ClientPaths.subject = prefs.getString("subject", "");

        //check if SUBJECT ID is "" or null
        if (ClientPaths.subject.equals("")) {
            Courier.deliverMessage(this, Constants.SUBJECT_API, "");
        } else {
            Log.i(TAG, "Requested Subject, but already have it.");
            updateSubjectUI();
        }
    }

    //if googleapi not available, start the registration service
    private void startRegistrationService() {
        GoogleApiAvailability api = GoogleApiAvailability.getInstance();
        int code = api.isGooglePlayServicesAvailable(this);
        if (code == ConnectionResult.SUCCESS) {
            onActivityResult(Constants.REQUEST_GOOGLE_PLAY_SERVICES, Activity.RESULT_OK, null);
        } else if (api.isUserResolvableError(code) &&
                api.showErrorDialogFragment(this, code, Constants.REQUEST_GOOGLE_PLAY_SERVICES)) {
            // wait for onActivityResult call (see below)
        } else {
            String str = GoogleApiAvailability.getInstance().getErrorString(code);
            Toast.makeText(this, str, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case Constants.REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode == Activity.RESULT_OK) {
//                    Intent i = new Intent(this, RegistrationService.class);
//                    startService(i); // OK, init GCM
                    Log.d(TAG, "Google play services available!");

                } else {
                    Log.e(TAG, "Google play services  NOT available!");
                }
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
        Log.d(TAG, "on api client availability result");

    }


    private Notification buildSpiroReminder() {
        Intent viewIntent = new Intent(this, MainActivity.class);
        viewIntent.putExtra("event-id", 0);
        PendingIntent viewPendingIntent = PendingIntent.getActivity(this, 0, viewIntent, 0);

        Notification.Builder builder = new Notification.Builder(this)
//                        .setSmallIcon(R.drawable.ic_spiro)
                        .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_spiro))
                        .setContentTitle("Breathe Reminder!")
                        .setVibrate(new long[]{500})
                        .setContentText("Time to use Spirometer ->")
                        .setWhen(System.currentTimeMillis())
                        .setShowWhen(true)
                        .setContentIntent(viewPendingIntent);
        return builder.build();
    }

    private Notification buildQuestionReminder(long reminderTime) {
        // This is what you are going to set a pending intent which will start once
        // notification is pressed. Hopes you know how to add notification bar.
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction("android.intent.action.MAIN");
        notificationIntent.addCategory("android.intent.category.LAUNCHER");
        PendingIntent viewPendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_spiro)
//                        .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_spiro))
                        .setContentTitle("Question Reminder!")
                        .setContentText("Slide to Answer Questions on Phone->")
                        .setWhen(reminderTime)
                        .setVibrate(new long[]{500})
                        .setContentIntent(viewPendingIntent);
        return builder.build();
    }


    //onCreate method gets called when the activity is created (setup code here)
    public void onCreate(Bundle b) {
        super.onCreate(b);

        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        wearAlarmReceiver = new WearAlarmReceiver();

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSigMotionSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);

        v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        prefs = getSharedPreferences(Constants.MY_PREFS_NAME, MODE_PRIVATE);
        spiroConn = new BTSocket(Constants.SPIRO_SENSOR_ID, UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"), this);
        ClientPaths.mainContext = this;

        WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = .5F; //value between 0 and 1
        getWindow().setAttributes(layout);

        Courier.startReceiving(this);
        setContentView(R.layout.main_activity);

        //start google play registration service
        startRegistrationService();
        setAmbientEnabled();

        stub = (WatchViewStub) findViewById(R.id.stub);

        // called after layout has been inflated on the watch (post UI inflate callback)
        //determines what xml layout view to inflate on the watch
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                if (!isAmbient()) {
                    Log.d(TAG, "Layout Inflated - not ambient");
                    mRectBackground = (RelativeLayout) findViewById(R.id.rect_layout);
                    mRoundBackground = (RelativeLayout) findViewById(R.id.round_layout);
                    setupOnLayoutInflated();
                } else {
                    Log.d(TAG, "Layout Inflated - ambient");
                }
            }
        });
    }

    // this function initializes the UI components and starts the sensors
    private void setupOnLayoutInflated() {
        Log.d(TAG, "MainActivity setupOnLayoutInflated");
//        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        LocalBroadcastManager.getInstance(this).registerReceiver(mLastReceiver,
                new IntentFilter(Constants.LAST_SENSOR_EVENT));

        LocalBroadcastManager.getInstance(this).registerReceiver(mHeartReceiver,
                new IntentFilter(Constants.HEART_EVENT));


        dateText = (TextView) findViewById(R.id.dateText);
        subjectText = (TextView) findViewById(R.id.subjectText);
        heartImage = (ImageView) findViewById(R.id.heartImage);
        smileView = (ImageView) findViewById(R.id.smileView);
        riskText = (TextView) findViewById(R.id.riskText);
        heartText = (TextView) findViewById(R.id.heartText);
        lastSensorText = (TextView) findViewById(R.id.lastSensorText);

        dustChecked = (ImageView) findViewById(R.id.dustCheckView);
        airChecked = (ImageView) findViewById(R.id.airCheckView);
        webChecked = (ImageView) findViewById(R.id.webCheckView);

        dustText = (TextView) findViewById(R.id.dustText);
        airText = (TextView) findViewById(R.id.airText);
        webText = (TextView) findViewById(R.id.webText);

        dustChecked.setImageResource(R.drawable.check_16);
        airChecked.setImageResource(R.drawable.check_16);
        webChecked.setImageResource(R.drawable.check_16);

        //http://stackoverflow.com/questions/5442183/using-the-animated-circle-in-an-imageview-while-loading-stuff
        loadingPanel = (RelativeLayout) findViewById(R.id.loadingPanel);
        spiroToggleButton = (ToggleButton) findViewById(R.id.spiroToggleButton);

        spiroToggleButton.setChecked(false);
        loadingPanel.setVisibility(View.INVISIBLE);

        spiroToggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    Log.d(TAG, "startSpiro - show loading");
                    loadingPanel.setVisibility(View.VISIBLE);
                    spiroToggleButton.setVisibility(View.INVISIBLE);
                    new BluetoothTask().execute(Constants.SPIRO_SENSOR_ID);
                } else {
                    Log.i(TAG, "stopSpiro");
                    spiroConn.closeConn();
                }
            }
        });


        if (Constants.fixedSensorRate) {
            registerReceiver(wearAlarmReceiver, wearFilter);
            Intent intent = new Intent(Constants.WEAR_ALARM_ACTION);
            intent.putExtra("alarm-id", Constants.START_ALARM_ID);
            sensorPI = PendingIntent.getBroadcast(this, Constants.START_ALARM_ID, intent, 0);
        }

        //startMeasurement schedules a corresponding stop event for the sensors
        startMeasurement(this);

        requestSubjectAndUpdateUI();
        updateRiskUI(lastRiskValue);
        updateStatusUI();

        if (!scheduled) {
            scheduleAlarms();
            scheduled = true;
        }


    }

    //update the subject number text on the watch
    private void updateSubjectUI() {
        if (subjectText != null) {
            String st = "Subject: " +  ClientPaths.subject;
            subjectText.setText(st);
        }
        Log.d(TAG, "updated subject UI - " +  ClientPaths.subject);
    }

    //update the risk ui text and color on the watch
    public void updateRiskUI(int value) {

        String statusString;
        if (riskText == null || smileView == null) {
            Log.e(TAG, "riskText or smileView is null");
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
//                if (lastRiskValue!=MED_RISK)//handle risk transition message
//                    Toast.makeText(MainActivity.this, "Risk Warning - Please use Spirometer", Toast.LENGTH_SHORT).show();
                break;
            case HIGH_RISK:
                smileView.setImageResource(R.drawable.frowny_face);
                statusString = "Risk: High";
                riskText.setTextColor(Color.RED);
                if (lastRiskValue!=HIGH_RISK) {//handle risk transition message
                    // Vibrate for 500 milliseconds
                    v.vibrate(500);
                    Toast.makeText(MainActivity.this, "Risk Warning - Use Spirometer", Toast.LENGTH_SHORT).show();
                }
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


    private void updateStatusUI() {
        Log.i("updateStatus", "dust, air, web: "
                + ClientPaths.dustConnected + " " + ClientPaths.airConnected + " " + ClientPaths.webConnection);
        airChecked.setVisibility(ClientPaths.airConnected ? View.VISIBLE : View.INVISIBLE);
        dustChecked.setVisibility(ClientPaths.dustConnected ? View.VISIBLE : View.INVISIBLE);
        webChecked.setVisibility(ClientPaths.webConnection.equals("None") ? View.INVISIBLE : View.VISIBLE);
    }

    //Used for managing tasks while the watch is awake
    private Handler taskHandler = new Handler();

    //runnable for stopping the sensors after SENSOR_ON_TIME (scheduled in startMeasurement)
    private Runnable stopSensorTask = new Runnable()
    {
        public void run()
        {
            stopMeasurement(MainActivity.this);
        }
    };

    //method for creating a risk request to send to the mobile to forward to the server
    private void riskRequest() {

        Log.d(TAG, "riskRequest");
        try {
            JSONObject jsonBody = new JSONObject();

            jsonBody.put("timestamp",System.currentTimeMillis());
            jsonBody.put("subject_id", ClientPaths.subject);
            jsonBody.put("key", Constants.API_KEY);
            jsonBody.put("battery",ClientPaths.batteryLevel);

            String data = jsonBody.toString();
            Log.d(TAG, "risk post: " + data);

            Courier.deliverMessage(this, Constants.RISK_API, data);

        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "[Handled] Error creating risk post request");
        }
    }

    @BackgroundThread
    @ReceiveMessages(Constants.REMINDER_API)
    void onReminderReceived(String message) {
        Log.d(TAG, "Reminder message received: " + message);

        Notification spiroNotification = buildSpiroReminder();
        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(this);

        notificationManager.notify(Constants.SPIRO_ALARM_ID, spiroNotification);
        Log.d(TAG, "created spiro notification");
    }


    @BackgroundThread
    @ReceiveMessages(Constants.RISK_API)
    void onRiskReceived(String riskConnString) { // The nodeId parameter is optional
        //response data here has riskvalue bundled with connection info
        String[] res = riskConnString.split(",");
        Log.d(TAG, "ReceiveMessage risk: " + riskConnString);
        updateRiskUI(Integer.parseInt(res[0]));
        ClientPaths.webConnection = res[1];
    }

    @BackgroundThread
    @ReceiveMessages(Constants.SUBJECT_API)
    void onSubjectReceived(String sub) { // The nodeId parameter is optional
        if (sub.equals("")) {
            Log.e(TAG, "Received blank Subject from message");
            return;
        }

        Log.d(TAG, "ReceiveMessage subject: " + sub);
        try{
            int num = Integer.parseInt(sub);
            // is an integer!
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "[Handled] Error parsing: " + sub + " to number");
            return;
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("subject", sub);
        editor.apply();
        ClientPaths.subject = sub;
        Log.d(TAG, "set subject, id now " + sub);

        Courier.deliverMessage(this, Constants.REGISTERED_API, sub);
        updateSubjectUI();
    }

//    @BackgroundThread
//    @ReceiveMessages(Constants.MULTI_API)
//    void onMultiReceived(Boolean success) { // The nodeId parameter is optional
//        Log.d(TAG, "ReceiveMessage multi success: " + success.toString());
//        if (!isAmbient())
//            riskRequest();
//    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        requestSubjectAndUpdateUI();
        riskRequest();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MainActivity onDestroy");

        if (Constants.fixedSensorRate)
            cancelRepeatedSensors();

        Courier.stopReceiving(this);

        try {
            stopMeasurement(this);
            spiroConn.closeConn();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mLastReceiver);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mHeartReceiver);
        } catch( Exception e) {
            Log.e(TAG, "connReceiver off");
        }
    }

    //precondition that lastSensorText != null
    private void updateLastView(String sensorName) {
//        lastSensorText.setText("Last: " + sensorName + "\nDust Sensor: " + (ClientPaths.dustConnected ? "Yes" : "No"));
        lastSensorText.setText("Last: " + sensorName);

    }

    public void onFinishActivity(View view) {
        setResult(RESULT_OK);
        finish();
    }

    //sensor BroadCast Listener
    private BroadcastReceiver mHeartReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            updateHeartUI(intent.getIntExtra("heart", Constants.NO_VALUE));
        }
    };

    private static Integer NO_HEART_COUNT = 0;
    private static Integer lastHeartRate = Constants.NO_VALUE;

    private void updateHeartUI(int heartRate) {

        Log.d(TAG, "updateHeartUI: " + heartRate);
        try {
            //logic for resetting displayed heart rate (if no recent value in last three updates)
            if (heartRate == Constants.NO_VALUE) {
                NO_HEART_COUNT++;
                if (NO_HEART_COUNT > 2) {
                    NO_HEART_COUNT = 0;
                    heartText.setText("");
                } else if (lastHeartRate!=Constants.NO_VALUE) {
                        heartText.setText(lastHeartRate + "");
                }
            } else {
                heartText.setText(heartRate + "");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Error updating heart UI");
        }

        lastHeartRate = heartRate;
    }

    @Override
    public void onEnterAmbient(Bundle ambientDetails) {
        super.onEnterAmbient(ambientDetails);
        Log.d(TAG, "onEnterAmbient");// - remove task callbacks");
        try {
//            taskHandler.removeCallbacks(riskTask);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mLastReceiver);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mHeartReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Set appropriate visibility of UI elements for ambient mode

        dateText.setVisibility(View.VISIBLE);

        heartImage.setVisibility(View.GONE);
        lastSensorText.setVisibility(View.GONE);
        spiroToggleButton.setVisibility(View.INVISIBLE); //invisible for relative layout purposes
        smileView.setVisibility(View.GONE);
        heartText.setVisibility(View.GONE);
        subjectText.setVisibility(View.GONE);

        webChecked.setVisibility(View.GONE);
        airChecked.setVisibility(View.GONE);
        dustChecked.setVisibility(View.GONE);

        webText.setVisibility(View.GONE);
        airText.setVisibility(View.GONE);
        dustText.setVisibility(View.GONE);



        Calendar c = Calendar.getInstance();
        String strDate = AMBIENT_DATE_FORMAT.format(c.getTime());
        dateText.setText(strDate);
        updateN = 1;

        Courier.stopReceiving(this);
    }
    private static int updateN = 1;

    //lowest sampling period is 10s every 3 min (this is in the case of ZERO activity
    //any kind of signification motion (standing up, normal arm movement during walking, etc.
    // will trigger a faster walking period
    private static final int AMBIENT_SENSOR_PERIOD = 2;

    @Override
    public void onUpdateAmbient() {
        super.onUpdateAmbient();
        Log.d(TAG, "onUpdateAmbient");
        Log.d(TAG, "updateN: " + updateN);
        riskRequest();

        if (updateN == AMBIENT_SENSOR_PERIOD) {
            //reset heart value
//            updateHeartUI(Constants.NO_VALUE);
            if (!Constants.fixedSensorRate)
                startMeasurement(this);
            updateN = 1;
        } else {
            updateN++;
        }

        Calendar c = Calendar.getInstance();
        String strDate = AMBIENT_DATE_FORMAT.format(c.getTime());
        dateText.setText(strDate);
    }


    @Override
    public void onExitAmbient() {
        super.onExitAmbient();
        Log.d(TAG, "onExitAmbient");

        dateText.setVisibility((View.GONE));
        subjectText.setVisibility(View.VISIBLE);
        smileView.setVisibility(View.VISIBLE);
        heartImage.setVisibility(View.VISIBLE);
        heartText.setVisibility(View.VISIBLE);
        lastSensorText.setVisibility(View.VISIBLE);
        spiroToggleButton.setVisibility(View.VISIBLE);


        webText.setVisibility(View.VISIBLE);
        airText.setVisibility(View.VISIBLE);
        dustText.setVisibility(View.VISIBLE);

        //sets visibility or invisibility of checkmark depending on connection
        updateStatusUI();

        //register receivers
        LocalBroadcastManager.getInstance(this).registerReceiver(mLastReceiver,
                new IntentFilter(Constants.LAST_SENSOR_EVENT));

        LocalBroadcastManager.getInstance(this).registerReceiver(mHeartReceiver,
                new IntentFilter(Constants.HEART_EVENT));

        if (!Constants.fixedSensorRate)
            startMeasurement(this);

        Courier.startReceiving(this);

        riskRequest();

    }

    //for static app
//    public int pefToRisk(float p) {
//        if (p<150)
//            return HIGH_RISK;
//        else if (p<250)
//            return MED_RISK;
//        else
//            return LOW_RISK;
//    }

    class TriggerListener extends TriggerEventListener {
        public void onTrigger(TriggerEvent event) {
            Log.i("TriggerListener", "Name:" + event.sensor.getName());
            Log.i("TriggerListener", "Type:" + event.sensor.getType());
            startMeasurement(MainActivity.this);

            // As it is a one shot sensor, it will be canceled automatically.
            // SensorManager.requestTriggerSensor(this, mSigMotion); needs to
            // be called again, if needed.
        }
    }

//
    private final BroadcastReceiver myBatteryReceiver = new BroadcastReceiver(){

        @Override
        public void onReceive(Context arg0, Intent arg1) {
            int bLevel = arg1.getIntExtra("level", 0); // gets the battery level
            ClientPaths.batteryLevel = bLevel;
            Log.i(" Battery Level", ""+bLevel);
            // Here you do something useful with the battery level...
        }
    };

//    private final BroadcastReceiver myBatteryReceiver = new BatteryReceiver();



    //Sensor Related
    public void startMeasurement(Context c) {
        if (!sensorToggled) {


            c.startService(new Intent(c, SensorService.class));
            sensorToggled = true;
//            scheduleStopSensor(Constants.SENSOR_ON_TIME, Constants.STOP_ALARM_ID);
            taskHandler.postDelayed(stopSensorTask, Constants.SENSOR_ON_TIME);
            c.registerReceiver(myBatteryReceiver,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            Log.i(TAG, "Start Measurement, scheduled stop for " + Constants.SENSOR_ON_TIME);

        }
    }

    public void stopMeasurement(Context c) {
        if (sensorToggled) {
            Log.i(TAG, "Stop Measurement");
            c.stopService(new Intent(c, SensorService.class));

            try {
                c.unregisterReceiver(myBatteryReceiver);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "myBatteryReceiver turned off");
            }

            if (Constants.fixedSensorRate) {
                scheduleSensors(Constants.SENSOR_OFF_TIME);
            } else {
                //set the trigger task
                Intent intent = new Intent(Constants.WEAR_ALARM_ACTION);
                intent.putExtra("alarm-id", Constants.TRIGGER_ALARM_ID);
                PendingIntent pi = PendingIntent.getBroadcast(this, Constants.TRIGGER_ALARM_ID, intent, 0);
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(System.currentTimeMillis());

                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + Constants.TRIGGER_DELAY, pi);

                Log.d(TAG, "trigger alarm set for " + Constants.TRIGGER_DELAY + "ms");
//                taskHandler.postDelayed(triggerTask, Constants.TRIGGER_DELAY);
            }
            sensorToggled = false;
        }
    }

    private void printAvailableSensors() {
        if (mSensorManager != null) {
            Log.d(TAG, "print sensors:");
            List<Sensor> deviceSensors = mSensorManager.getSensorList(Sensor.TYPE_ALL);
            for(int i=0; i<deviceSensors.size(); i++) {
                Log.d(TAG, "Sensor found:" + deviceSensors.get(i).toString() + ", isWakeUp: " + deviceSensors.get(i).isWakeUpSensor());
            }

        } else {
            Log.e("printAvailableSensors", "[Handled] no print, mSensorManager null");
        }
    }

//    used to update the last sensor received text
    private final BroadcastReceiver mLastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateLastView(intent.getStringExtra("sensorName"));
        }
    };

    //Manual Bluetooth Connection Request (used for spirometer currently)
    private class BluetoothTask extends AsyncTask<Integer, Integer, String> {

        private int deviceType;

        @Override
        protected String doInBackground(Integer... params) {
            deviceType = params[0];
            Log.d("bluetoothtask", "deviceType: " + deviceType);
            switch (deviceType) {
                case Constants.SPIRO_SENSOR_ID:
                    if (spiroConn.findConn()) {
                        if (spiroConn.openConn()) {
                            return "CONNECTED";
                        } else {
                            return "NOT_ON";
                        }
                    } else {
                        return "NOT_PAIRED";
                    }

                case Constants.AIRBEAM_SENSOR_ID:
                default:
                    Log.e(TAG, "Unexpected Type: "  + deviceType);
                    return "TRY_AGAIN";
            }
        }

        @Override
        protected void onPostExecute(String result) {
            Log.d(TAG, "bluetooth result: " + result);
            switch(result) {
                case "NOT_ON":
                    Toast.makeText(MainActivity.this, "Device not on", Toast.LENGTH_SHORT).show();
                    spiroToggleButton.setChecked(false);
                    break;
                case "NOT_PAIRED":
                    Toast.makeText(MainActivity.this, "Device not paired", Toast.LENGTH_SHORT).show();
                    spiroToggleButton.setChecked(false);
                    break;
                case "CONNECTED":
                    spiroConn.beginListen();
                    Log.d(TAG, "Spirometer connected");
                    Toast.makeText(MainActivity.this, "Connected!", Toast.LENGTH_SHORT).show();
                    break;
                default:
                    Toast.makeText(MainActivity.this, "Try Connecting Again", Toast.LENGTH_SHORT).show();
                    spiroToggleButton.setChecked(false);
                    break;
            }
            spiroToggleButton.setVisibility(View.VISIBLE);
            loadingPanel.setVisibility(View.GONE);
        }
    }

    //ALARM scheduling logic below

    private static Boolean scheduled = false;
    private static ArrayList<Integer> activeAlarms = new ArrayList<Integer>();

    private static int alarmCode = Constants.ALARM_CODE_START; //starting alarm code

   //schedule all the alarms (used in onCreate)
    private void scheduleAlarms() {
        final int[] qHours = new int[]{7,15,17,19};
        for (int t : qHours)
            scheduleQuestionReminder(t,30);

        final int[] sHours = new int[]{11,18};
        for (int t : sHours)
            scheduleSpiroReminder(t,0);
    }

    //increment the alarm codes so they don't overlap
    private Integer getNextAlarmCode() {
        alarmCode++;
        if (alarmCode>Constants.RC_LIMIT)
            alarmCode = Constants.ALARM_CODE_START;

        Log.d(TAG, "Next requestCode: " + alarmCode);
        return alarmCode;
    }

    //create the pending intent for the given alarmId
    private PendingIntent createAlarmPI(Integer alarmId) {
        Intent intent = new Intent(Constants.WEAR_ALARM_ACTION);
        intent.putExtra("alarm-id", alarmId);
        int ac = getNextAlarmCode(); //get next Alarm Code
        activeAlarms.add(ac);
        return PendingIntent.getBroadcast(this, ac, intent, 0);
    }

    //method to schedule a spirometer reminder alarm trigger
    private void scheduleSpiroReminder(int hour, int minute) {
        Calendar calendar = Calendar.getInstance();

        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);

//        Intent intent = new Intent(Constants.WEAR_ALARM_ACTION);
//        intent.putExtra("alarm-id", Constants.SPIRO_ALARM_ID);
//        int ac = Constants.SPIRO_ALARM_ID;
//        PendingIntent pi = PendingIntent.getBroadcast(this, ac, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        PendingIntent pi = createAlarmPI(Constants.SPIRO_ALARM_ID);

        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, pi);

        Log.d(TAG, "Scheduled spiro alarm at time: " + hour + ":" + minute);
    }

    //method to schedule a mobile questionnaire alarm trigger
    private void scheduleQuestionReminder(int hour, int minute) {
        Calendar calendar = Calendar.getInstance();

        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, hour); // For 1 PM or 2 PM
        calendar.set(Calendar.MINUTE, minute);


        PendingIntent pi = createAlarmPI(Constants.QUESTION_ALARM_ID);
        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, pi);

        Log.d(TAG, "Scheduled question alarm at time: " + hour + ":" + minute);
    }

    //cancel all the scheduled reminder alarms (excludes sensor alarms)
    private void cancelAlarms() {
        Log.d(TAG, "Cancelling Alarms");
        for (Integer i : activeAlarms) {
            try {
                Intent intent = new Intent(Constants.WEAR_ALARM_ACTION);
                PendingIntent pi = PendingIntent.getBroadcast(this, i, intent, 0);
                alarmManager.cancel(pi);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "[Handled] Error cancelling alarms");
            }
        }
        activeAlarms.clear();
    }

    //schedule alarm for controlling the sensor service class launch
    private void scheduleSensors(Integer futureTime) {

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());

        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + futureTime, sensorPI);
        Log.d(TAG, "scheduleSensors, for " + futureTime);
    }

    //cancel the alarms for the repeated sensor alarms
    private void cancelRepeatedSensors() {
        Log.d(TAG, "cancelRepeatedSensors");
        unregisterReceiver(wearAlarmReceiver);
        try {
            alarmManager.cancel(sensorPI);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "[Handled] Could not cancer sensorPI");
        }
    }


    private class WearAlarmReceiver extends BroadcastReceiver {
        private final String TAG = "WearAlarmReceiver";
        @Override
        public void onReceive(Context context, Intent intent) {
            Integer alarmId = intent.getIntExtra("alarm-id", Constants.NO_VALUE);
            Log.d(TAG, "Received alarm " + alarmId);

            if (ClientPaths.subject.equals("")) {
                Log.e(TAG, "Blocking alarm, no subject yet registered on device");
                return;
            }

            switch (alarmId) {
                case Constants.START_ALARM_ID: //alarm 6
                    Log.d(TAG, "Sensor alarm called");
                    startMeasurement(MainActivity.this);
                    break;
                case Constants.QUESTION_ALARM_ID:
                    Log.d(TAG, "Wear question alarm called");
                    v.vibrate(500);
                    Toast.makeText(context, "Question Time!", Toast.LENGTH_LONG).show();
                    //send message to phone to start questionnaire
                    Courier.deliverMessage(context, Constants.QUESTION_API,"");
                    break;
                case Constants.SPIRO_ALARM_ID:
                    Log.d(TAG, "Spiro question alarm called");
                    v.vibrate(500);
                    Toast.makeText(context, "Spirometer Time!", Toast.LENGTH_LONG).show();
                    break;
                default:
                    Log.e(TAG, "Unexpected Alarm, id: " + alarmId);
                    break;

//                    Notification spiroNotification = buildSpiroReminder();
//                    NotificationManagerCompat notificationManager =
//                            NotificationManagerCompat.from(MainActivity.this);
//
//                    notificationManager.notify(Constants.SPIRO_ALARM_ID, spiroNotification);
            }
        }
    }




}
