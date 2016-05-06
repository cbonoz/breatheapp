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
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Switch;
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
    private TextView activeView;
    private ImageView smileView;
    private ImageView heartImage;

    private RelativeLayout mRectBackground;
    private RelativeLayout mRoundBackground;


    private WatchViewStub stub;

    private SharedPreferences prefs;

    //Used for Spirometer Bluetooth Connection state
    private BTSocket spiroConn;
    private Boolean sensorToggled = false;


    private void requestSubjectAndUpdateUI() {
        ClientPaths.subjectId = prefs.getString("subject", "");

        //check if SUBJECT ID is "" or null
        if (ClientPaths.subjectId.equals("")) {
            Courier.deliverMessage(this, Constants.SUBJECT_API, "");
        } else {
            Log.i(TAG, "Requested Subject, but already have it.");
            updateSubjectUI();
        }
    }

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



//    BroadcastReceiver call_method = new BroadcastReceiver() {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            String action_name = intent.getAction();
//            if (action_name.equals("call_method")) {
//                //launch question api on phone
//                Courier.deliverMessage(MainActivity.this, Constants.QUESTION_API,"");
//            }
//        }
//    };

    private Notification buildQuestionReminder(long reminderTime) {
        // This is what you are going to set a pending intent which will start once
        // notification is pressed. Hopes you know how to add notification bar.
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction("android.intent.action.MAIN");
        notificationIntent.addCategory("android.intent.category.LAUNCHER");
        PendingIntent viewPendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);


//        registerReceiver(call_method, new IntentFilter("call_method"));


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


    private AlarmManager alarmManager;



    public void onCreate(Bundle b) {
        super.onCreate(b);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSigMotionSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);

        prefs = getSharedPreferences(Constants.MY_PREFS_NAME, MODE_PRIVATE);
        spiroConn = new BTSocket(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"), this);
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
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                if (!isAmbient()) {
                    Log.d(TAG, "Layout Inflated - not ambient");
                    mRectBackground = (RelativeLayout) findViewById(R.id.rect_layout);
                    mRoundBackground = (RelativeLayout) findViewById(R.id.round_layout);


                    setupOnLayoutInflated();
//                    printAvailableSensors();


                } else {
                    Log.d(TAG, "Layout Inflated - ambient");
                }
            }
        });



    }

    // called after layout has been inflated
    private void setupOnLayoutInflated() {
        Log.d(TAG, "MainActivity setupOnLayoutInflated");
//        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        LocalBroadcastManager.getInstance(this).registerReceiver(mLastReceiver,
                new IntentFilter(Constants.LAST_SENSOR_EVENT));

        LocalBroadcastManager.getInstance(this).registerReceiver(mHeartReceiver,
                new IntentFilter(Constants.HEART_EVENT));


        dateText = (TextView) findViewById(R.id.dateText);
        subjectText = (TextView) findViewById(R.id.subjectText);
        activeView = (TextView) findViewById(R.id.activeView);
        heartImage = (ImageView) findViewById(R.id.heartImage);
        smileView = (ImageView) findViewById(R.id.smileView);
        riskText = (TextView) findViewById(R.id.riskText);
        heartText = (TextView) findViewById(R.id.heartText);
        lastSensorText = (TextView) findViewById(R.id.lastSensorText);

        //http://stackoverflow.com/questions/5442183/using-the-animated-circle-in-an-imageview-while-loading-stuff
        loadingPanel = (RelativeLayout) findViewById(R.id.loadingPanel);
        spiroToggleButton = (ToggleButton) findViewById(R.id.spiroToggleButton);

        spiroToggleButton.setChecked(false);

        loadingPanel.setVisibility(View.GONE);

        spiroToggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
                Log.d(TAG, "startSpiro - show loading");
                loadingPanel.setVisibility(View.VISIBLE);
                spiroToggleButton.setVisibility(View.GONE);
                new BluetoothTask().execute(Constants.SPIRO_SENSOR_ID);
            } else {
                Log.i(TAG, "stopSpiro");
                spiroConn.closeConn();
            }
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
//                        addSensorData(Constants.TERMINATE_SENSOR_ID, null, null, null);
                        Courier.deliverMessage(MainActivity.this, Constants.FILE_API, Constants.END_WRITE);
                        Log.d(TAG, "sensorToggle Not Checked");
                    }
                }
            });
        } else {
            sensorSwitch.setVisibility(View.GONE);
            startMeasurement(this);
        }
//        sensorSwitch.setVisibility(View.GONE);
//        startMeasurement(this);
        requestSubjectAndUpdateUI();
        updateRiskUI(lastRiskValue);
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

    //Used for managing tasks while awake
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
    @ReceiveMessages(Constants.REMINDER_API)
    void onReminderReceived(String message) {

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
        Courier.stopReceiving(this);

//        try {
//            taskHandler.removeCallbacks(riskTask);
//        } catch (Exception e) {
//            Log.e(TAG, "Risk Timer off");
//        }

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
    private static int updateN = 1;
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

        loadingPanel = (RelativeLayout) findViewById(R.id.loadingPanel);
        spiroToggleButton = (ToggleButton) findViewById(R.id.spiroToggleButton);

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

//    public int pefToRisk(float p) {
//        if (p<150)
//            return HIGH_RISK;
//        else if (p<250)
//            return MED_RISK;
//        else
//            return LOW_RISK;
//    }


    //Sensor Controllers Below
    private SensorManager mSensorManager = null;
    private Sensor mSigMotionSensor;

    private final TriggerListener mListener =  new TriggerListener();

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


    private BroadcastReceiver myBatteryReceiver = new BroadcastReceiver(){

        @Override
        public void onReceive(Context arg0, Intent arg1) {
            int bLevel = arg1.getIntExtra("level", 0); // gets the battery level
            ClientPaths.batteryLevel = bLevel;
            Log.i(" Battery Level", ""+bLevel);
            // Here you do something useful with the battery level...
        }
    };




    //Sensor Related
    public void startMeasurement(Context c) {
        if (!sensorToggled) {
            Log.i(TAG, "Start Measurement");
            c.startService(new Intent(c, SensorService.class));
            sensorToggled = true;
            scheduleStopSensor(Constants.SENSOR_ON_TIME, Constants.STOP_ALARM_ID);

            c.registerReceiver(myBatteryReceiver,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        }
    }

    public void stopMeasurement(Context c) {
        Log.i(TAG, "Stop Measurement");
        c.stopService(new Intent(c, SensorService.class));
        sensorToggled = false;

        try {
            c.unregisterReceiver(myBatteryReceiver);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "myBatteryReceiver turned off");
        }

        try {
            mSensorManager.requestTriggerSensor(mListener, mSigMotionSensor);
        } catch (Exception e) {
            Log.d(TAG, "No sig motion sensor");
        }
    }

    private void scheduleStopSensor(Integer futureTime, Integer alarmId) {
//        Intent intent = new Intent(this, SensorAlarm.class);
//        intent.putExtra("alarm_id", alarmId);
//        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, alarmId, intent, 0);
//
//        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
//                SystemClock.elapsedRealtime() + futureTime, pendingIntent);
        taskHandler.postDelayed(stopSensorTask, futureTime);
        Log.d(TAG, "scheduled stop sensor alarm for " + futureTime + "ms");
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
    private BroadcastReceiver mLastReceiver = new BroadcastReceiver() {
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
                    if (spiroConn.findConn(Constants.SPIRO_SENSOR_ID)) {
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
                    spiroConn.beginListen(deviceType);
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

//        @Override
//        protected void onPreExecute() {}


    }



}
