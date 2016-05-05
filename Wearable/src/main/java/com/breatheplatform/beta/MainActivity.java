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
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
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
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.breatheplatform.beta.data.SensorAddService;
import com.breatheplatform.beta.sensors.AlarmReceiver;
import com.breatheplatform.beta.sensors.SensorService;
import com.breatheplatform.beta.shared.Constants;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import me.denley.courier.BackgroundThread;
import me.denley.courier.Courier;
import me.denley.courier.ReceiveMessages;


public class MainActivity extends WearableActivity
        //,DataApi.DataListener, MessageApi.MessageListener,NodeApi.NodeListener
{
    private static final SimpleDateFormat AMBIENT_DATE_FORMAT =
            new SimpleDateFormat("hh:mm aa", Locale.US);

    private static final String TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 1;

    private static final int RISK_TASK_PERIOD=20000; //20 seconds

    BluetoothAdapter bluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;

    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;

    volatile boolean stopWorker;

    private final TriggerListener mListener = new TriggerListener();

    private static final int NAME_REQUEST_CODE = 1;
    private static final int ID_REQUEST_CODE = 2;
    private static final int LOW_RISK = 2;
    private static final int MED_RISK = 1;
    private static final int HIGH_RISK = 0;

    private ToggleButton spiroToggleButton;

    private TextView lastSensorText =null;
    private TextView dateText;
    private TextView riskText;
    private TextView heartText;
    private TextView subjectText;
    private TextView activeView;
    private ProgressBar spinnerBar;
    private ImageView smileView;
    private ImageView heartImage;

    private static int lastRiskValue = LOW_RISK;//ActivityConstants.NO_VALUE;

    private RelativeLayout mRectBackground;
    private RelativeLayout mRoundBackground;

    private long mLastClickTime = 0;

    //units: ms
    private static final long SPIRO_REMINDER_INTERVAL = Constants.ONE_MIN_MS*10;
//    private static final long SENSOR_INTERVAL = 2000;

    //Intervals in MS
    private final int MIN_CLICK_INTERVAL = 5000;

    //this function is called during onCreate (if the user has not registered an ID yet, will be called after
//    a valid ID has been registered during the boot up registration process)
    private void setupUI() {
        Log.d(TAG, "MainActivity setupUI");
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        //http://stackoverflow.com/questions/5442183/using-the-animated-circle-in-an-imageview-while-loading-stuff
//        progressBar = (RelativeLayout) findViewById(R.id.loadingPanel);
//        progressBar.setVisibility(View.GONE);

        //set to 2 minute timeout (for ambient)
//        Settings.System.putString(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, "120000");

        LocalBroadcastManager.getInstance(this).registerReceiver(mLastReceiver,
                new IntentFilter(Constants.LAST_SENSOR_EVENT));

        LocalBroadcastManager.getInstance(this).registerReceiver(mHeartReceiver,
                new IntentFilter(Constants.HEART_EVENT));

        spinnerBar = (ProgressBar) findViewById(R.id.spinnerBar);
        dateText = (TextView) findViewById(R.id.dateText);
        subjectText = (TextView) findViewById(R.id.subjectText);
        activeView = (TextView) findViewById(R.id.activeView);
        heartImage = (ImageView) findViewById(R.id.heartImage);
        smileView = (ImageView) findViewById(R.id.smileView);
        riskText = (TextView) findViewById(R.id.riskText);
        heartText = (TextView) findViewById(R.id.heartText);
        lastSensorText = (TextView) findViewById(R.id.lastSensorText);
        spiroToggleButton = (ToggleButton) findViewById(R.id.spiroToggleButton);

        spiroToggleButton.setChecked(false);

        if (mmSocket!=null) {
            if (mmSocket.isConnected())
                spiroToggleButton.setChecked(true);

        }

        spiroToggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.d(TAG, "onCheckChanged");
                if (SystemClock.elapsedRealtime() - mLastClickTime < MIN_CLICK_INTERVAL) {
                    if (isChecked) {
                        spiroToggleButton.setChecked(false);
                        Log.d(TAG, "Spiro Button blocked - currently connecting");
                        Toast.makeText(MainActivity.this, "Wait 5 seconds", Toast.LENGTH_SHORT).show();

                    }
                    return;
                }

                spinnerBar.setVisibility(View.VISIBLE);
                spinnerBar.bringToFront();

                mLastClickTime = SystemClock.elapsedRealtime();

                if (isChecked) {
                    Log.d(TAG, "startSpiro");
                    Toast.makeText(MainActivity.this, "Connecting...", Toast.LENGTH_SHORT).show();

                    if (findBT(Constants.SPIRO_SENSOR_ID)) {
                        if (!openSpiro()) {
                            Toast.makeText(MainActivity.this, "Spirometer not on", Toast.LENGTH_SHORT).show();
                            spiroToggleButton.setChecked(false);
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "Spirometer not paired", Toast.LENGTH_SHORT).show();
                        spiroToggleButton.setChecked(false);
                    }
                } else {
                    Log.i(TAG, "stopSpiro");
                    closeSpiro();
                    spiroToggleButton.setChecked(false);
                }

                spinnerBar.setVisibility(View.GONE);

            }
        });


        Switch sensorSwitch = (Switch) findViewById(R.id.sensorSwitch);
        if (Constants.collecting) {

            sensorSwitch.setChecked(sensorToggled);


            sensorSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        Courier.deliverMessage(MainActivity.this, Constants.FILE_API, Constants.START_WRITE);
                        startMeasurement(MainActivity.this);


                        Log.d(TAG, "sensorToggle Checked");
                    } else {
                        stopMeasurement(MainActivity.this);

                        //trigger a data send event to the mobile device
                        addSensorData(Constants.TERMINATE_SENSOR_ID, null, null, null);

                        Courier.deliverMessage(MainActivity.this, Constants.FILE_API, Constants.END_WRITE);


                        Log.d(TAG, "sensorToggle Not Checked");
                    }
                }
            });
        } else {
            sensorSwitch.setVisibility(View.GONE);
            startMeasurement(MainActivity.this);
        }

        updateRiskUI(lastRiskValue);
        requestSubjectAndUpdateUI();

//        scheduleRiskRequest();

    }

    private Boolean sensorToggled = false;


    private SharedPreferences prefs;


    private void requestSubjectAndUpdateUI() {
        //check if SUBJECT ID is "" (null), using "" for serialization purposes via data api
        ClientPaths.subjectId = prefs.getString("subject", "");

        if (ClientPaths.subjectId.equals("")) {
            Courier.deliverMessage(this, Constants.SUBJECT_API, "");
        } else {
            Log.i(TAG, "Requested Subject, but already have it.");
            updateSubjectUI();
        }
    }

    WatchViewStub stub;



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

    private static Integer requestCode = 0;

    private AlarmManager alarmMgr;
    private PendingIntent alarmIntent;
    private PendingIntent spiroIntent;
    private PendingIntent questionIntent;
    private PendingIntent sensorIntent;

//    AlarmReceiver alarmReceiver = new AlarmReceiver();

//    private IntentFilter alarmFilter = new IntentFilter(Constants.ALARM_ACTION);

//    private void scheduleNotification(Notification notification, int delay) {
//
//        Intent notificationIntent = new Intent(this, AlarmReceiver.class);
//        notificationIntent.putExtra(AlarmReceiver.ALARM_ID, 1);
//        notificationIntent.putExtra(AlarmReceiver.NOTIFICATION, notification);
//        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
//
//        long futureInMillis = SystemClock.elapsedRealtime() + delay;
//
//        alarmMgr.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, futureInMillis, pendingIntent);
//    }

    private Notification getNotification(String title, String content) {
        Notification.Builder builder = new Notification.Builder(this);
        builder.setContentTitle(title);
        builder.setContentText(content);
        builder.setSmallIcon(R.drawable.ic_launcher);
        return builder.build();
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

//    private void scheduleSensors(long interval) {
//        Intent notificationIntent = new Intent(Constants.ALARM_ACTION);
//        notificationIntent.putExtra(AlarmReceiver.ALARM_ID, Constants.SENSOR_ALARM_ID);
//        sensorIntent = PendingIntent.getBroadcast(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
//        alarmMgr.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), interval, sensorIntent);
//    }

    private void scheduleSpiroNotification(Notification notification, long interval, int id) {
        Intent notificationIntent = new Intent(this, AlarmReceiver.class);
        notificationIntent.putExtra(AlarmReceiver.ALARM_ID, id);
        notificationIntent.putExtra(AlarmReceiver.NOTIFICATION, notification);
        spiroIntent = PendingIntent.getBroadcast(this, requestCode++, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        long futureInMillis = SystemClock.elapsedRealtime() + Constants.ONE_MIN_MS;
        AlarmManager alarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, futureInMillis, interval, spiroIntent);
        if (id==Constants.SPIRO_ALARM_ID)
            Log.d(TAG, "Scheduled spiro alarm at interval " + interval + " ms");
    }

    private void scheduleQuestionAtTime(int hour, int minute) {

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR, hour);
        calendar.set(Calendar.MINUTE, minute);

        long reminderTime = calendar.getTimeInMillis();
        Notification notification = buildQuestionReminder(reminderTime);

        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.putExtra(AlarmReceiver.ALARM_ID, Constants.QUESTION_ALARM_ID);
        intent.putExtra(AlarmReceiver.NOTIFICATION, notification);
        questionIntent = PendingIntent.getBroadcast(this, requestCode++, intent, 0);

        alarmMgr.setInexactRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, questionIntent);


        Log.d(TAG, "Scheduled question alarm at time: " + hour + ":" + minute);

    }

    BroadcastReceiver call_method = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action_name = intent.getAction();
            if (action_name.equals("call_method")) {
                //launch question api on phone
                Courier.deliverMessage(MainActivity.this, Constants.QUESTION_API,"");
            }
        }
    };

    private Notification buildQuestionReminder(long reminderTime) {
        // This is what you are going to set a pending intent which will start once
        // notification is pressed. Hopes you know how to add notification bar.
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction("android.intent.action.MAIN");
        notificationIntent.addCategory("android.intent.category.LAUNCHER");
        PendingIntent viewPendingIntent = PendingIntent.getActivity(this, requestCode,
                notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);


        registerReceiver(call_method, new IntentFilter("call_method"));


        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_spiro)
//                        .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_spiro))
                        .setContentTitle("Question Reminder!")
                        .setContentText("Slide to Answer Questions on Phone->")
                        .setWhen(reminderTime)
                        .setVibrate(new long[]{1000, 1000})
                        .setContentIntent(viewPendingIntent);
        return builder.build();
    }

    private SensorManager mSensorManager = null;
    private Sensor mSigMotionSensor;
    private Sensor mStepSensor;

    private BroadcastReceiver myBatteryReceiver = new BroadcastReceiver(){

        @Override
        public void onReceive(Context arg0, Intent arg1) {
            int bLevel = arg1.getIntExtra("level", 0); // gets the battery level
            ClientPaths.batteryLevel = bLevel;
            Log.i(" Battery Level", ""+bLevel);
            // Here you do something useful with the battery level...
        }
    };


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

    public void onCreate(Bundle b) {
        super.onCreate(b);
        alarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        prefs = getSharedPreferences(Constants.MY_PREFS_NAME, MODE_PRIVATE);
        ClientPaths.mainContext = this;



//
//        this.registerReceiver(this.myBatteryReceiver,
//                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = .5F; //value between 0 and 1
        getWindow().setAttributes(layout);

        Courier.startReceiving(this);
        setContentView(R.layout.main_activity);

        stub = (WatchViewStub) findViewById(R.id.stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                if (!isAmbient()) {
                    Log.d(TAG, "Layout Inflated - not ambient");
                    mRectBackground = (RelativeLayout) findViewById(R.id.rect_layout);
                    mRoundBackground = (RelativeLayout) findViewById(R.id.round_layout);


                    mSensorManager.requestTriggerSensor(mListener, mSigMotionSensor);
                    setupUI();


                } else {
                    Log.d(TAG, "Layout Inflated - ambient");
                }
            }
        });

        //start google play registration service
        startRegistrationService();
        setAmbientEnabled();
//        updateBatteryLevel();

        //set up the significant motion listener for regulating the sensor service
        mSensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        mSigMotionSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);


//        printAvailableSensors();

        scheduleAlarms();
    }

    //Alarms for Questionnaire and Spiro
    //    -7:30am (fixed time)
    //    -3:30pm (fixed time)
    //    -5-7pm (randomized to occur once between these hours)
    //    -7-8pm (randomized to occur once between this hour)
    private void scheduleAlarms() {
        Log.d(TAG, "Scheduling Alarms");
        alarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);


        scheduleSpiroNotification(buildSpiroReminder(), SPIRO_REMINDER_INTERVAL, Constants.SPIRO_ALARM_ID);//AlarmManager.INTERVAL_HOUR*2);


        scheduleQuestionAtTime(7, 30);
        scheduleQuestionAtTime(15, 30);
        scheduleQuestionAtTime(17, 30);
        scheduleQuestionAtTime(19, 30);
    }

    private void updateSubjectUI() {

        String sub = ClientPaths.subjectId;


        if (subjectText != null) {
            String st = "Subject: " + sub;
            subjectText.setText(st);
        } else {
            Log.e(TAG, "Received subject before layout inflated");
        }

        Log.d(TAG, "updated subject UI - " + sub);
    }

    public void updateRiskUI(int value) {
//        riskText = (TextView) findViewById(R.id.riskText);
//        smileView = (ImageView) findViewById(R.id.smileView);
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
        if (isAmbient())
            riskText.setTextColor(Color.WHITE);

        riskText.setText(statusString);
        Log.d(TAG, "updateRiskUI - " + statusString);
    }


    private Handler taskHandler = new Handler();
//
//    private Runnable riskTask = new Runnable()
//    {
//        public void run()
//        {
//            riskRequest();
//
//            taskHandler.postDelayed(this, RISK_TASK_PERIOD);
//        }
//    };

//    private void scheduleRiskRequest() {
//        Log.d(TAG, "scheduleRiskRequest");
//        taskHandler.postDelayed(riskTask, RISK_TASK_PERIOD);
//    }

    private Runnable stopSensorTask = new Runnable()
    {
        public void run()
        {
            stopMeasurement(MainActivity.this);
        }
    };

    private void riskRequest() {

        Log.d(TAG, "riskRequest");
        try {
            JSONObject jsonBody = new JSONObject();

            jsonBody.put("timestamp",System.currentTimeMillis());
            jsonBody.put("subject_id", ClientPaths.subjectId);
            jsonBody.put("key", Constants.API_KEY);
            jsonBody.put("battery",ClientPaths.batteryLevel);
//            jsonBody.put("connection", ClientPaths.connectionInfo);

            String data = jsonBody.toString();
            Log.d(TAG, "risk post: " + data);

            Courier.deliverMessage(this, Constants.RISK_API, data);

        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "[Handled] Error creating risk post request");
        }
    }



    @BackgroundThread
    @ReceiveMessages(Constants.RISK_API)
    void onRiskReceived(int value) { // The nodeId parameter is optional
        Log.d(TAG, "ReceiveMessage risk: " + value);
        updateRiskUI(value);
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
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("subject", sub);
        editor.commit();
        ClientPaths.subjectId = sub;
        Log.d(TAG, "set subject, id now " + sub);

        Courier.deliverMessage(this, Constants.REGISTERED_API, sub);
        updateSubjectUI();
    }

//    @BackgroundThread
//    @ReceiveMessages(Constants.LABEL_API)
//    void onLabelReceived(String data) { // The nodeId parameter is optional
//        Log.d(TAG, "ReceiveMessage label: " + data);
//        Toast.makeText(this,data, Toast.LENGTH_LONG).show();
//    }

//
//    @BackgroundThread
//    @ReceiveMessages(Constants.MULTI_API)
//    void onMultiReceived(Boolean success) { // The nodeId parameter is optional
//        Log.d(TAG, "ReceiveMessage multi success: " + success.toString());
//        // ...
//    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MainActivity onDestroy");


        try {
            if (alarmMgr != null) {
                alarmMgr.cancel(spiroIntent);
                alarmMgr.cancel(questionIntent);
//                alarmManager.cancel(sensorIntent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Courier.stopReceiving(this);

//        try {
//            taskHandler.removeCallbacks(riskTask);
//        } catch (Exception e) {
//            Log.e(TAG, "Risk Timer off");
//        }

        try {
            stopMeasurement(this);
            closeSpiro();
        } catch (Exception e) {
            e.printStackTrace();
        }


        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mLastReceiver);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mHeartReceiver);
        } catch( Exception e) {
            Log.e(TAG, "connReceiver off");
        }

        try {
//            unregisterReceiver(alarmReceiver);
            unregisterReceiver(call_method);
            this.unregisterReceiver(this.myBatteryReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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

    private void updateLastView(String sensorName) {
        if (lastSensorText !=null)
            lastSensorText.setText("Last: " + sensorName + "\nDust Sensor: " + (ClientPaths.dustConnected ? "Yes" : "No"));
    }

    public void onFinishActivity(View view) {
        setResult(RESULT_OK);
        finish();
    }

//    private Integer lastHeartRate = Constants.NO_VALUE;

    //sensor BroadCast Listener
    private BroadcastReceiver mHeartReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            Integer lastHeartRate = intent.getIntExtra("heartrate", Constants.NO_VALUE);
            updateHeartUI(lastHeartRate);
        }
    };



    private void updateHeartUI(int heartRate) {


        Log.d(TAG, "Updating heart UI " + heartRate);
        try {
            if (heartRate == Constants.NO_VALUE)
                heartText.setText("--");
            else
                heartText.setText(heartRate + "");
        } catch (Exception e) {
            e.printStackTrace();
//            Log.d(TAG, "Error updating heart UI");
        }
    }

    private void scheduleStopSensor(Integer futureTime) {
//        Intent intent = new Intent(this, AlarmReceiver.class);
//        intent.putExtra(AlarmReceiver.ALARM_ID, Constants.STOP_ALARM_ID);
//        alarmIntent = PendingIntent.getBroadcast(this, Constants.STOP_ALARM_ID, intent, 0);
//
//
//
//        alarmMgr.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
//                SystemClock.elapsedRealtime() + futureTime, alarmIntent);
        taskHandler.postDelayed(stopSensorTask, futureTime);
        Log.d(TAG, "scheduled stop sensor alarm for " + futureTime + "ms");
    }

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


    //Sensor Related
    public void startMeasurement(Context c) {
        if (!sensorToggled) {
            Log.i(TAG, "Start Measurement");
            startService(new Intent(c, SensorService.class));
            sensorToggled = true;
            scheduleStopSensor(Constants.SENSOR_ON_TIME);
            registerReceiver(myBatteryReceiver,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        }
    }

    public void stopMeasurement(Context c) {
        Log.i(TAG, "Stop Measurement");
        stopService(new Intent(c, SensorService.class));
        sensorToggled = false;

        try {
            mSensorManager.requestTriggerSensor(mListener, mSigMotionSensor);
        } catch (Exception e) {
            Log.d(TAG, "No sig motion sensor");
        }

        unregisterReceiver(myBatteryReceiver);
    }

    @Override
    public void onEnterAmbient(Bundle ambientDetails) {
        super.onEnterAmbient(ambientDetails);
        Log.d(TAG, "onEnterAmbient - remove task callbacks");
        try {
//            taskHandler.removeCallbacks(riskTask);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mLastReceiver);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mHeartReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }

//        activeView.setVisibility(View.VISIBLE);
        dateText.setVisibility(View.VISIBLE);
        riskText.setTextColor(Color.WHITE);

        heartImage.setVisibility(View.GONE);
        lastSensorText.setVisibility(View.GONE);
        spiroToggleButton.setVisibility(View.GONE);
        smileView.setVisibility(View.GONE);
        heartText.setVisibility(View.GONE);
        subjectText.setVisibility(View.GONE);

        updateN = 1;


//        setContentView(R.layout.black_layout); //null background
    }
    private int updateN = 1;
    private static final int N = 2;

    @Override
    public void onUpdateAmbient() {
        super.onUpdateAmbient();
        Log.d(TAG, "onUpdateAmbient");
        Log.d(TAG, "updateN: " + updateN);
        riskRequest();


        if (updateN == N) {
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
        Log.d(TAG, "onExitAmbient - add task callbacks");
//        scheduleRiskRequest();


        riskText.setTextColor(Color.GREEN);
        activeView.setVisibility(View.GONE);
        dateText.setVisibility((View.GONE));


        subjectText.setVisibility(View.VISIBLE);
        smileView.setVisibility(View.VISIBLE);
        heartImage.setVisibility(View.VISIBLE);
        heartText.setVisibility(View.VISIBLE);
        lastSensorText.setVisibility(View.VISIBLE);
        spiroToggleButton.setVisibility(View.VISIBLE);

        //register receivers
        LocalBroadcastManager.getInstance(this).registerReceiver(mLastReceiver,
                new IntentFilter(Constants.LAST_SENSOR_EVENT));

        LocalBroadcastManager.getInstance(this).registerReceiver(mHeartReceiver,
                new IntentFilter(Constants.HEART_EVENT));


        Log.d(TAG, "Set main layout");

        riskRequest();
        startMeasurement(this);

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
            Log.d(TAG, "findDust did not find paired spiro device");
        }
//        else if (type == Constants.DUST_SENSOR_ID){
//            dustDevice = null;
//            if (pairedDevices.size() > 0) {
//                String deviceName;
//                for (BluetoothDevice device : pairedDevices) {
//                    deviceName = device.getName();
//
//                    if (deviceName.contains(Constants.DUST_BT_NAME)) {
//                        dustDevice = device;
//                        Log.d("yjcode", "Detected RFduino device: " + deviceName + " " + dustDevice.getAddress());
//                        //add connection for RF duino here as well
//                        return true;
//                    }
//                }
//            }
//            Log.d(TAG, "findDust did not find paired dustdevice");
//
//        }



        return false;

    }


    public Boolean openSpiro()
    {
        try {
            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID

            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
            mmSocket.connect();
            mmInputStream = mmSocket.getInputStream();
            beginSpiroListen();

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

    private static Long lastSpiroTime = null;
//    private static Boolean lastSpiroGood = false;


    public int pefToRisk(float p) {
        if (p<150)
            return HIGH_RISK;
        else if (p<250)
            return MED_RISK;
        else
            return LOW_RISK;
    }

    public void beginSpiroListen()
    {
        Log.d(TAG, "beginSpiroListen");
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
                            String spiroData = packetBytes.toString();
                            Log.d(TAG, "string spiroData: " + spiroData);
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

                                            Log.d(TAG, "Parsed spiro data: " + data.toString());
                                            try {
                                                Long timestamp = System.currentTimeMillis();
                                                addSensorData(Constants.SPIRO_SENSOR_ID, 3, timestamp, data.toArray());

//                                                Toast.makeText(MainActivity.this, "PEF Received: " + data.pef + "!", Toast.LENGTH_SHORT).show();
                                                Toast.makeText(MainActivity.this, "Data Received!", Toast.LENGTH_SHORT).show();

//                                                //FOR STATIC DEMO: UPDATE IMAGE BASED ON PEF
//                                                if (Constants.staticApp) {
//                                                    lastRiskValue = pefToRisk(data.getPef());
//                                                    if (data.good_test || (lastSpiroTime!=null && (timestamp - lastSpiroTime < ONE_MIN_MS))) {
//                                                        updateRiskUI(lastRiskValue);
//                                                    } else {
//                                                        Toast.makeText(MainActivity.this, R.string.second_reading, Toast.LENGTH_LONG).show();
//                                                    }
//                                                }  else {
//                                                    if (data.good_test || (lastSpiroTime!=null && (timestamp - lastSpiroTime < ONE_MIN_MS))) {
//                                                        Toast.makeText(MainActivity.this, R.string.second_reading, Toast.LENGTH_LONG).show();
//                                                    }
//                                                }
                                                if (Constants.staticApp) {
                                                    lastRiskValue = pefToRisk(data.getPef());
                                                    updateRiskUI(lastRiskValue);
                                                }
                                                lastSpiroTime = timestamp;
//                                                lastSpiroGood = data.good_test;
                                                //END STATIC DEMO SECTION
                                            } catch (Exception e) {
                                                Toast.makeText(MainActivity.this, R.string.bad_reading, Toast.LENGTH_SHORT).show();
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
//            Log.d("receiver", "Got message: " + sensorId);
            updateLastView(intent.getStringExtra("sensorName"));

        }
    };



}
