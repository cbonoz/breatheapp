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
import android.app.AlertDialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.breatheplatform.beta.activity.ActivityDetectionService;
import com.breatheplatform.beta.data.SensorAddService;
import com.breatheplatform.beta.messaging.AlarmReceiver;
import com.breatheplatform.beta.sensors.SensorService;
import com.breatheplatform.beta.shared.Constants;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import me.denley.courier.BackgroundThread;
import me.denley.courier.Courier;
import me.denley.courier.ReceiveMessages;


public class MainActivity extends WearableActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        LocationListener,  ResultCallback<Status>
        //,DataApi.DataListener, MessageApi.MessageListener,NodeApi.NodeListener
{
    private static final SimpleDateFormat AMBIENT_DATE_FORMAT =
            new SimpleDateFormat("HH:mm", Locale.US);

    private static final String TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 1;


    private GoogleApiClient mGoogleApiClient;

    private static final int MIN_BATTERY = 5;

    private static final int RISK_TASK_PERIOD=10000; //10 seconds


    BluetoothAdapter bluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;

    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;

    volatile boolean stopWorker;



    private static final int NAME_REQUEST_CODE = 1;
    private static final int ID_REQUEST_CODE = 2;
    private static final int LOW_RISK = 2;
    private static final int MED_RISK = 1;
    private static final int HIGH_RISK = 0;



    private ToggleButton spiroToggleButton;

    private Boolean dustConnected = false;



    private TextView lastSensorView=null;
    private TextView mClockView;
    private TextView riskText;

    private TextView subjectView;
    private ImageView smileView;

    private static int lastRiskValue = LOW_RISK;//ActivityConstants.NO_VALUE;

    private RelativeLayout mRectBackground;
    private RelativeLayout mRoundBackground;

//    private DeviceClient client=null;

    private static Boolean lowBatteryState = false;

    //get battery level and save it
    public void updateBatteryLevel(){
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = this.registerReceiver(null, ifilter);
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int previousLevel = ClientPaths.batteryLevel;
            ClientPaths.batteryLevel = level;


            if (level <= MIN_BATTERY && !lowBatteryState) {

                Log.d(TAG, "Battery Low - Stopping Activity");
                lowBatteryState = true;
                destroyRoutine();
            } else if (previousLevel<=MIN_BATTERY && lowBatteryState && level > MIN_BATTERY) { //last battery level was low, but not current
                lowBatteryState = false;
                resumeAfterLowBattery();
            }




        } catch (Exception e) {
            Log.e(TAG, "[Handled] Error getting battery level value");

        }
    }


    private long mLastClickTime = 0;

    //Intervals in MS
    private static final Integer ACTIVITY_INTERVAL = 0;//15000;//20000;//ms (0 fastest)
    private static final Integer LOCATION_INTERVAL = 1000 * 60 * 2; //request ever 2 min
    private final int MIN_CLICK_INTERVAL =5000;


    private RelativeLayout progressBar;
    //this function is called during onCreate (if the user has not registered an ID yet, will be called after
//    a valid ID has been registered during the boot up registration process)


    private void setupUI() {
        Log.d(TAG, "MainActivity setupUI");



        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        //http://stackoverflow.com/questions/5442183/using-the-animated-circle-in-an-imageview-while-loading-stuff
//        progressBar = (RelativeLayout) findViewById(R.id.loadingPanel);
//        progressBar.setVisibility(View.GONE);

        //set to 2 minute timeout (for ambient)
        Settings.System.putString(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, "120000");

        LocalBroadcastManager.getInstance(this).registerReceiver(mLastReceiver,
                new IntentFilter(Constants.LAST_SENSOR_EVENT));

        LocalBroadcastManager.getInstance(this).registerReceiver(mHeartReceiver,
                new IntentFilter(Constants.HEART_EVENT));


        lastSensorView = (TextView) findViewById(R.id.lastSensorView);
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
                    }
                    return;
                }

                mLastClickTime = SystemClock.elapsedRealtime();

//                progressBar.setVisibility(View.VISIBLE);
//                progressBar.bringToFront();

                if (isChecked) {
                    Log.d(TAG, "startSpiro");
                    Toast.makeText(MainActivity.this, "Connecting...", Toast.LENGTH_SHORT).show();

                    if (findBT(Constants.SPIRO_SENSOR_ID)) {
                        if (!openSpiro()) {
                            Toast.makeText(MainActivity.this, "Spirometer Paired: Could not connect", Toast.LENGTH_SHORT).show();
                            spiroToggleButton.setChecked(false);
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "Spirometer not Paired", Toast.LENGTH_SHORT).show();
                        spiroToggleButton.setChecked(false);
                    }
                } else {
                    Log.i(TAG, "stopSpiro");
                    closeSpiro();
                    spiroToggleButton.setChecked(false);
                }

//                progressBar.setVisibility(View.GONE);
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
                        startMeasurement();


                        Log.d(TAG, "sensorToggle Checked");
                    } else {
                        stopMeasurement();

                        //trigger a data send event to the mobile device
                        addSensorData(Constants.TERMINATE_SENSOR_ID, null, null, null);

                        Courier.deliverMessage(MainActivity.this, Constants.FILE_API, Constants.END_WRITE);


                        Log.d(TAG, "sensorToggle Not Checked");
                    }
                }
            });
        } else {
            sensorSwitch.setVisibility(View.GONE);
            if (!sensorToggled)// && !Constants.sensorAlarm)
                startMeasurement();
        }


//        updateRiskUI(Constants.NO_VALUE);
        updateRiskUI(lastRiskValue);
        requestSubjectAndUpdateUI();

//        dustRequest();
//        scheduleDustRequest();

        scheduleRiskRequest();


//        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
//        registerReceiver(connReceiver, filter);

        //prevent app screen from dimming - lock app on screen (confirmed)
//        getWindowonDestory().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //end setupUI
    }

    private Boolean sensorToggled = false;

    private void createSpiroNotification() {

        final AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle(R.string.spiro_title);
        alertDialog.setMessage("Please take Spirometer Reading");
        alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                alertDialog.dismiss();
            }
        });

        alertDialog.show();
    }



    private void createDangerNotification() {
        final AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle(R.string.danger_title);
        alertDialog.setMessage("Please Contact your Doctor!");
        alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                alertDialog.dismiss();
            }
        });
        alertDialog.show();
    }

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

    public static final int REQUEST_GOOGLE_PLAY_SERVICES = 1972;

    private void startRegistrationService() {
        GoogleApiAvailability api = GoogleApiAvailability.getInstance();
        int code = api.isGooglePlayServicesAvailable(this);
        if (code == ConnectionResult.SUCCESS) {
            onActivityResult(REQUEST_GOOGLE_PLAY_SERVICES, Activity.RESULT_OK, null);
        } else if (api.isUserResolvableError(code) &&
                api.showErrorDialogFragment(this, code, REQUEST_GOOGLE_PLAY_SERVICES)) {
            // wait for onActivityResult call (see below)
        } else {
            String str = GoogleApiAvailability.getInstance().getErrorString(code);
            Toast.makeText(this, str, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
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

    private void buildApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(MainActivity.this)
                .addApi(LocationServices.API)
                .addApi(ActivityRecognition.API)
//                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    private AlarmManager alarmManager;
    private PendingIntent spiroIntent;
    private PendingIntent questionIntent;
    private PendingIntent sensorIntent;

    //units: ms
    private static final long ONE_MIN_MS = 60000;
    private static final long SPIRO_REMINDER_INTERVAL = ONE_MIN_MS;
    private static final long SENSOR_INTERVAL = 2000;


    private void scheduleSensors(long interval) {
        Intent notificationIntent = new Intent(this, AlarmReceiver.class);
        notificationIntent.putExtra(AlarmReceiver.NOTIFICATION_ID, Constants.SENSOR_ALARM_ID);
//        notificationIntent.putExtra(AlarmReceiver.NOTIFICATION, notification);
        sensorIntent = PendingIntent.getBroadcast(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), interval, sensorIntent);
    }

    private void scheduleNotification(Notification notification, long interval, int id) {
        Intent notificationIntent = new Intent(this, AlarmReceiver.class);
        notificationIntent.putExtra(AlarmReceiver.NOTIFICATION_ID, id);
        notificationIntent.putExtra(AlarmReceiver.NOTIFICATION, notification);
        spiroIntent = PendingIntent.getBroadcast(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
//        alarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE); //alternative is ELAPSED_RTC
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()+ONE_MIN_MS,interval, spiroIntent);
        if (id==Constants.SPIRO_ALARM_ID)
            Log.d(TAG, "Scheduled spiro at interval " + interval + " ms");
    }

    private void scheduleQuestionAtTime(int hour, int minute) {

        // Set the alarm to start at approximately the hour and minute given
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);

        long reminderTime = calendar.getTimeInMillis();

        Notification notification = buildQuestionReminder(reminderTime);


        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.putExtra(AlarmReceiver.NOTIFICATION_ID, Constants.QUESTION_ALARM_ID);
        intent.putExtra(AlarmReceiver.NOTIFICATION, notification);
        questionIntent = PendingIntent.getBroadcast(this, 0, intent, 0);


        // setInexactRepeating() - AlarmManager.INTERVAL_DAY.
        //uncomment when ready
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, reminderTime, AlarmManager.INTERVAL_DAY, questionIntent);

        Log.d(TAG, "Scheduled question at time: " + hour + ":" + minute);

    }



    private Notification buildQuestionReminder(long reminderTime) {

        // This is what you are going to set a pending intent which will start once
        // notification is pressed. Hopes you know how to add notification bar.
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction("android.intent.action.MAIN");
        notificationIntent.addCategory("android.intent.category.LAUNCHER");

        PendingIntent viewPendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        BroadcastReceiver call_method = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action_name = intent.getAction();
                if (action_name.equals("call_method")) {
                    //launch question api on phone
                    Courier.deliverMessage(MainActivity.this, Constants.QUESTION_API,"");
                }
            };
        };

        registerReceiver(call_method, new IntentFilter("call_method"));


        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_spiro)
//                        .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_spiro))
                        .setContentTitle("Question Reminder!")
                        .setContentText("Slide to Answer Questions on Phone->")
                        .setWhen(reminderTime)
                        .setVibrate(new long[] { 1000, 1000})
                        .setContentIntent(viewPendingIntent);
        return builder.build();
    }

    private Notification buildSpiroReminder() {
        Intent viewIntent = new Intent(this, MainActivity.class);
        viewIntent.putExtra("event-id", 0);
        PendingIntent viewPendingIntent =
                PendingIntent.getActivity(this, 0, viewIntent, 0);
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_spiro)
//                        .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_spiro))
                        .setContentTitle("Breathe Reminder!")
                        .setContentText("Time to use Spirometer ->")
                        .setWhen(System.currentTimeMillis())
                        .setContentIntent(viewPendingIntent);
        return builder.build();
    }


    public void onCreate(Bundle b) {
        super.onCreate(b);

        prefs = getSharedPreferences(Constants.MY_PREFS_NAME, MODE_PRIVATE);
        ClientPaths.mainContext = this;

        WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = .25F; //value between 0 and 1
        getWindow().setAttributes(layout);

        Courier.startReceiving(this);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        //register sensor listener service

        setContentView(R.layout.main_activity);

        stub = (WatchViewStub) findViewById(R.id.stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                if (!isAmbient()) {
                    Log.d(TAG, "Layout Inflated - not ambient");
                    mRectBackground = (RelativeLayout) findViewById(R.id.rect_layout);
                    mRoundBackground = (RelativeLayout) findViewById(R.id.round_layout);

                    setupUI();


                } else {
                    Log.d(TAG, "Layout Inflated - ambient");
                }
            }
        });

        //start google play registration service
        startRegistrationService();
        buildApiClient();
        mGoogleApiClient.connect();

//        retrieveDeviceNode(); //set up mobileNode
        setAmbientEnabled();
        updateBatteryLevel();


        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        scheduleAlarms();
    }

    private void resumeAfterLowBattery() {
        Log.d(TAG, "Resume after low battery");
        WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = .25F; //value between 0 and 1
        getWindow().setAttributes(layout);

        Courier.startReceiving(this);
        updateBatteryLevel();
        mGoogleApiClient.connect();



        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        scheduleAlarms();

        exitAmbientRoutine();
    }

    private void scheduleAlarms() {
        Log.d(TAG, "Scheduling Alarms");
        scheduleNotification(buildSpiroReminder(), SPIRO_REMINDER_INTERVAL, Constants.SPIRO_ALARM_ID);//AlarmManager.INTERVAL_HOUR*2);

//        if (Constants.sensorAlarm)
//            scheduleSensors(SENSOR_INTERVAL);

        //Alarms for Questionnaire
        //    -7:30am (fixed time)
        //    -3:30pm (fixed time)
        //    -5-7pm (randomized to occur once between these hours)
        //    -7-8pm (randomized to occur once between this hour)
        scheduleQuestionAtTime(7, 30);
        scheduleQuestionAtTime(15, 30);
        scheduleQuestionAtTime(17, 30);
        scheduleQuestionAtTime(19, 30);
    }

    private Boolean healthDanger = false;

    private void updateSubjectUI() {

        String sub = ClientPaths.subjectId;

        subjectView = (TextView) findViewById(R.id.subjectText);
        if (subjectView != null) {
            String st = "Subject: " + sub;
            subjectView.setText(st);
        } else {
            Log.e(TAG, "Received subject before layout inflated");
        }

        Log.d(TAG, "updated subject UI - " + sub);
    }

    public void updateRiskUI(int value) {
        riskText = (TextView) findViewById(R.id.riskText);
        smileView = (ImageView) findViewById(R.id.smileView);
        String statusString;

        if (riskText == null || smileView == null) {
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
        riskText.setText(statusString);
        Log.d(TAG, "updateRiskUI - " + statusString);
    }

    private void updateConnectivityText(String conn) {
        TextView connText = (TextView) findViewById(R.id.connText);
        connText.setText(conn);
    }

    private Handler taskHandler = new Handler();

    private Runnable riskTask = new Runnable()
    {
        public void run()
        {
            riskRequest();

            taskHandler.postDelayed(this, RISK_TASK_PERIOD);
        }
    };

    private void riskRequest() {

        Log.d(TAG, "Called riskRequest");
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



    private void scheduleRiskRequest() {
        Log.d(TAG, "scheduleRiskRequest");
        taskHandler.postDelayed(riskTask, RISK_TASK_PERIOD);
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
        } catch (NumberFormatException e) {
            Log.e(TAG, "bad onSubjectReceived - received " + sub);
            return;
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
    public void onResult(Status status) {
        if (status.isSuccess()) {

            Log.d(TAG, "Successfully requested activity updates");

        } else {
            Log.e(TAG, "Failed in requesting activity updates, "
                    + "status code: "
                    + status.getStatusCode() + ", message: " + status
                    .getStatusMessage());
        }
    }

    private PendingIntent getActivityDetectionPendingIntent() {
        Intent intent = new Intent(this, ActivityDetectionService.class);
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void destroyRoutine() {

        setContentView(R.layout.black_layout);

//        unregisterReceiver(mBroadcastReceiver);
        try {
            if (alarmManager != null) {
                alarmManager.cancel(spiroIntent);
                alarmManager.cancel(questionIntent);
//                alarmManager.cancel(sensorIntent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mHeartReceiver);
        Courier.stopReceiving(this);

        //LocalBroadcastManager.getInstance(this).unregisterReceiver(mHeartReceiver);
//
        if (mGoogleApiClient.isConnected()) {
            ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(
                    mGoogleApiClient,
                    getActivityDetectionPendingIntent()
            ).setResultCallback(this);
            Log.d(TAG, "Removed activity updates");
        }


        try {
            taskHandler.removeCallbacks(riskTask);
        } catch (Exception e) {
            Log.e(TAG, "Risk Timer off");
        }





        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi
                    .removeLocationUpdates(mGoogleApiClient, this);
        }


        mGoogleApiClient.disconnect();

        stopMeasurement();
        closeSpiro();


        try {


            LocalBroadcastManager.getInstance(this).unregisterReceiver(mLastReceiver);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mHeartReceiver);
        } catch( Exception e) {
            Log.e(TAG, "connReceiver off");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MainActivity onDestroy");
        destroyRoutine();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }
    //
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        requestSubjectAndUpdateUI();
    }


    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
//        LocalBroadcastManager.getInstance(this).unregisterReceiver(activityReceiver);
//        onResume();
    }

    private void updateLastView(String sensorName) {
        if (lastSensorView!=null)
            lastSensorView.setText("Last: " + sensorName + "\nDust Sensor: " + (dustConnected ? "Yes" : "No"));
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
            Float lastHeartRate = intent.getFloatExtra("heartrate", Constants.NO_VALUE);
//            String message = intent.getStringExtra("message");
            Log.d("sensor receiver", "Got heart rate: " + lastHeartRate.intValue());
            updateHeartUI(lastHeartRate.intValue());
        }
    };



    private void updateHeartUI(int lastHeartRate) {
        TextView heartText = (TextView) findViewById(R.id.heartText);

        Log.d(TAG, "Updating heart UI " + lastHeartRate);
        try {
            if (lastHeartRate == Constants.NO_VALUE)
                heartText.setText("--");
            else
                heartText.setText(lastHeartRate + "");
        } catch (Exception e) {
            e.printStackTrace();
//            Log.d(TAG, "Error updating heart UI");
        }
    }

    //Sensor Related
    private void startMeasurement() {
        Log.i(TAG, "Start Measurement");
//        if (!Constants.sensorAlarm)
//            startService(new Intent(getBaseContext(), SensorService.class));
        startService(new Intent(getBaseContext(), SensorService.class));
        sensorToggled = true;
        try {
            ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(mGoogleApiClient, 0, getActivityDetectionPendingIntent()).setResultCallback(MainActivity.this);
            Log.d(TAG, "requested activity updates");
        } catch (Exception e) {
//            e.printStackTrace();
            Log.e(TAG, "connect - activity client not yet connected");
        }

    }


    private void stopMeasurement() {
        Log.i(TAG, "Stop Measurement");
        stopService(new Intent(getBaseContext(), SensorService.class));
        sensorToggled = false;

        try {
            ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(mGoogleApiClient, getActivityDetectionPendingIntent()).setResultCallback(MainActivity.this);
        } catch (Exception e) {
            Log.e(TAG, "disconnect - activity client not yet connected");
        }

    }

//    private GoogleApiClient activityClient;
    /**
     * Runs when a GoogleApiClient object successfully connects.
     * http://stackoverflow.com/questions/27779974/getting-googleapiclient-to-work-with-activity-recognition
     * http://www.sitepoint.com/google-play-services-location-activity-recognition/
     */

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "GoogleApiClient onConnected");
        try {
            final PendingResult<Status>
                    statusPendingResult =
                    ActivityRecognition.ActivityRecognitionApi
                            .requestActivityUpdates(mGoogleApiClient, ACTIVITY_INTERVAL, getActivityDetectionPendingIntent());
            statusPendingResult.setResultCallback(this);
        } catch (Exception e) {
            e.printStackTrace();
        }


        //Request Location Updates from Google API Client
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setNumUpdates(1)
                .setSmallestDisplacement(10) //10m
                .setInterval(LOCATION_INTERVAL)
                .setFastestInterval(LOCATION_INTERVAL);
        try {
            LocationServices.FusedLocationApi
                    .requestLocationUpdates(mGoogleApiClient, locationRequest, this)
                    .setResultCallback(new ResultCallback<Status>() {

                        @Override
                        public void onResult(Status status) {
                            if (status.getStatus().isSuccess()) {

                                Log.d(TAG, "Successfully requested location updates");

                            } else {
                                Log.e(TAG, "Failed in requesting location updates, "
                                        + "status code: "
                                        + status.getStatusCode() + ", message: " + status
                                        .getStatusMessage());
                            }
                        }
                    });
        } catch(SecurityException e) {
            e.printStackTrace();
            Log.e(TAG, "[Handled] Error Requesting location service (lack of permission)");
        }

    }


    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended called");
        try {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(mGoogleApiClient, getActivityDetectionPendingIntent());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.e(TAG, "onConnectionFailed called");
        if (!result.hasResolution()) {
            GoogleApiAvailability.getInstance().getErrorDialog(this, result.getErrorCode(), 0).show();
            return;
        }
        try {
            result.startResolutionForResult(this, REQUEST_GOOGLE_PLAY_SERVICES);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Exception while starting resolution activity", e);
        }
    }

    @Override
    public void onLocationChanged (Location location)
    {
        Log.d(TAG, "onLocationChanged: " + location.getLatitude() + "," + location.getLongitude());
        ClientPaths.currentLocation = location;
    }

    @Override
    public void onEnterAmbient(Bundle ambientDetails) {
        super.onEnterAmbient(ambientDetails);
        Log.d(TAG, "onEnterAmbient - remove task callbacks");
        try {
            taskHandler.removeCallbacks(riskTask);
//            taskHandler.removeCallbacks(dustTask);


            LocalBroadcastManager.getInstance(this).unregisterReceiver(mLastReceiver);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mHeartReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
//        setContentView(R.layout.black_layout);

        setContentView(R.layout.black_layout);
    }

    private void exitAmbientRoutine() {
//        riskRequest();
//        dustRequest();
//        scheduleDustRequest();
        scheduleRiskRequest();


        Log.d(TAG, "Set main layout");
        setContentView(R.layout.main_activity);

        stub = (WatchViewStub) findViewById(R.id.stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                if (!isAmbient()) {
                    Log.d(TAG, "Layout Inflated - not ambient");
                    mRectBackground = (RelativeLayout) findViewById(R.id.rect_layout);
                    mRoundBackground = (RelativeLayout) findViewById(R.id.round_layout);
                    setupUI();
                } else {
                    Log.d(TAG, "Layout Inflated - ambient");
                }
            }
        });
    }


    @Override
    public void onExitAmbient() {
        super.onExitAmbient();
        Log.d(TAG, "onExitAmbient - add task callbacks");
        exitAmbientRoutine();

    }

    @Override
    public void onUpdateAmbient() {
        super.onUpdateAmbient();
        Log.d(TAG, "onUpdateAmbient");

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
            Log.d(TAG, "findBT did not find paired spiro device");
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
//            Log.d(TAG, "findBT did not find paired dustdevice");
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

                                            Log.d(TAG, "Received spiro data: " + data.toString());
                                            try {
                                                Long timestamp = System.currentTimeMillis();
                                                addSensorData(Constants.SPIRO_SENSOR_ID, 3, timestamp, data.toArray());

//                                                Toast.makeText(MainActivity.this, "PEF Received: " + data.pef + "!", Toast.LENGTH_SHORT).show();
                                                Toast.makeText(MainActivity.this, "Data Received!", Toast.LENGTH_SHORT).show();

//                                                //FOR STATIC DEMO: UPDATE IMAGE BASED ON PEF
                                                if (Constants.staticApp) {
                                                    lastRiskValue = pefToRisk(data.getPef());
                                                    if (data.good_test || (lastSpiroTime!=null && (timestamp - lastSpiroTime < ONE_MIN_MS))) {
                                                        updateRiskUI(lastRiskValue);
                                                    } else {
                                                        Toast.makeText(MainActivity.this, R.string.second_reading, Toast.LENGTH_LONG).show();
                                                    }
                                                }  else {
                                                    if (data.good_test || (lastSpiroTime!=null && (timestamp - lastSpiroTime < ONE_MIN_MS))) {
                                                        Toast.makeText(MainActivity.this, R.string.second_reading, Toast.LENGTH_LONG).show();
                                                    }
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
            // Get extra data included in the Intent
//            Log.d("receiver", "Got message: " + sensorId);
            updateLastView(intent.getStringExtra("sensorName"));

        }
    };


}
