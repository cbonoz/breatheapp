package com.breatheplatform.beta.services;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.breatheplatform.beta.QuestionActivity;
import com.breatheplatform.beta.R;
import com.breatheplatform.beta.RegisterActivity;
import com.breatheplatform.beta.connection.Connectivity;
import com.breatheplatform.beta.encryption.HybridCrypt;
import com.breatheplatform.beta.encryption.RandomString;

import com.breatheplatform.beta.shared.Constants;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Calendar;

import me.denley.courier.Courier;
import me.denley.courier.Packager;

/**
 * Created by cbono on 4/13/16.
 * Main Listener Service that is auto-launched on the Mobile Device
 * Listens to incoming requests (via Courier) from the watch and responds accordingly in the service
 * background of the mobile device
 *
 * Also responsible for launching activities on the mobile device when certain events are triggered
 * (such as the registration or question activities)
 */
public class ListenerServiceFromWear extends WearableListenerService {
    private static final String TAG = "ListenerServiceFromWear";

    private ArrayList<Integer> activeAlarms = new ArrayList<Integer>();

    private static Boolean unregisterUser = true;
    private static Boolean writeOnce = false;
    private static Integer requestCode = 0;

    private AlarmManager alarmManager;

    /*
     * Receive the message from wear
     * Messages are used for smaller size requests from the wearable
     */
    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        String path = messageEvent.getPath();
        String s = "";
        try {
            s = Packager.unpack(this, messageEvent.getData(), String.class);
            Log.d(TAG, "onMessage " + path + ", " + s);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Error unpacking received data on mobile: " + s);
            return;
        }
        switch (path) {
            case Constants.QUESTION_API:
                onQuestionReceived(s);
                break;
            case Constants.SUBJECT_API:
                onSubjectReceived(s);
                break;
            case Constants.RISK_API:
                onRiskReceived(s);
                break;
//            case Constants.FILE_API:
//                onFileReceived(s);
//                break;
        }
    }

    /*
     * Data Receiver (onDataChanged)
     * Listens for the multi API data api request path from the wearable.
     * Encrypts the data sent from the wearable before being sent to server
     */

    @Override public void onDataChanged(DataEventBuffer dataEvents) {
        for (DataEvent event : dataEvents) {

            String path = event.getDataItem().getUri().getPath();
            String pdString = Packager.unpack(this, event.getDataItem().getData(), String.class);
            Log.d(TAG, "onData " + path + ", " + pdString);

            switch (path) {
                case Constants.MULTI_API:
                    onMultiReceived(pdString);
                    break;
            }
        }
    }

    /**
     * Request code for launching the Intent to resolve Google Play services errors.
     */
    private static final int REQUEST_RESOLVE_ERROR = 1000;

    private static String count = "0";

    public static String labelDirectory = null;
    public static File labelFile  = null;// = createFile(sensorDirectory);

    //    private static
    private static SharedPreferences prefs = null;
    private static String subject = "";

    private static Boolean createCalendarEvent = false;

    private static HybridCrypt aes;
    private static String aesKeyString;
    private static String encKeyString;

    private static Boolean runOnce = true;

    private static final File ROOT = android.os.Environment.getExternalStorageDirectory();
    private static final String sensorDirectory = ROOT + "/SensorData.txt";
    private static File sensorFile = null;
    private static final String rawSensorDirectory = ROOT + "/RawSensorData.txt";
    private static File rawSensorFile = null;

    private void startRegisterActivity() {
        Intent i = new Intent();
        i.setClass(this, RegisterActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    public static String lastConnection = "None";

    private void updateConnection() {
        lastConnection = Connectivity.isConnectedFast(this);
        Log.d(TAG, "lastConnection: " + lastConnection);
    }

    private String processAndSerialize(JSONObject jsonObject) {
        try {
            jsonObject.put("connection", lastConnection);
        } catch (Exception e) {
            return null;
        }
        return jsonObject.toString();
    }

    private void runOnceRegistered() {

    }

    //onCreate method for the ListenerService
    //sets up preferences - if no user registered, then launch the reigster activity
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ListenerService onCreate called");

//        if (writeOnce) {
//            sensorFile = createFile(sensorDirectory);
//            rawSensorFile = createFile(rawSensorDirectory);
//        }

        if (runOnce) {
            runOnce = false;

            alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

            Log.d(TAG, "getting preferences");
            prefs = getSharedPreferences(Constants.MY_PREFS_NAME, MODE_PRIVATE);

            subject = prefs.getString("subject", "");
            Log.d(TAG, "subject " + subject);

            //if subject is null, start registration page
            if (subject.equals("")) {
                startRegisterActivity();
            }

            try {

                RandomString randomString = new RandomString(10);

                //use random string to init the new hybridencrypter
                aes = new HybridCrypt(this, randomString.nextString());//subject)
                aesKeyString = aes.getKeyString();
//                encKeyString = aes.encryptRSA(aesKeyString);
                encKeyString = aes.encryptRSA(aesKeyString);//aes.getEncryptedKey();
                Log.d(TAG, "created encryption object");
//                encrypting = false;
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "[Critical] Error creating encryption object");
                aes = null;
            }

            Log.d("raw_key", aesKeyString);
            Log.d("enc_key", encKeyString);

            scheduleAlarms();

        }
    }

    void onRiskReceived(String data) { // The nodeId parameter is optional
        Log.d(TAG, "Received message from " + Constants.RISK_API);
        Intent i = new Intent(this, MobileUploadService.class);
        i.putExtra("data",data);
        i.putExtra("url", Constants.RISK_API);
        startService(i);
    }

    void onSubjectReceived(String data) { // The nodeId parameter is optional
        //send subject back to watch
        Log.d(TAG, "Received subject_id request from wear");
        if (!subject.equals("")) {
            Courier.deliverMessage(this, Constants.SUBJECT_API, subject);
        } else {
            Toast.makeText(this, "Enter your Clinician credentials", Toast.LENGTH_SHORT).show();
            startRegisterActivity();
        }
    }

    void onQuestionReceived(String data) { // The nodeId parameter is optional
        //send subject back to watch
        Log.d(TAG, "Received questionnaire request from wear");
        //TODO: Start questionnaire here
    }

    private void showToastInService(final String sText)
    {
        Handler mHandler = new Handler(getMainLooper());
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), sText, Toast.LENGTH_LONG).show();
            }
        });
    };


//    @BackgroundThread
//    @ReceiveMessages(Constants.CALENDAR_API)
//    void onCalendarReceived(String data) { // The nodeId parameter is optional
//        //create calendar event (if authenticated)
////        Courier.deliverMessage(this, Constants.SUBJECT_API, subject);
//    }

//    public void createToast(String s) {
//        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
//    }

//    private static final String API_KEY = "I3jmM2DI4YabH8937pRwK7MwrRWaJBgziZTBFEDTpec";


    private static File createFile(String fname) {
        Log.d(TAG, "Creating file: " + fname);
        return new File(fname);
    }


    private static boolean writeDataToFile(String data, File file, Boolean append) {
        try {
            BufferedWriter f = new BufferedWriter(new FileWriter(file, append));
            f.write(data);
            f.close();

            Log.d(TAG,file.toString()+ " filelength " + file.length() + "B");
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    //method for processing (forwarding to server)received sensor data from the wearable
    void onMultiReceived(String s) {
        updateConnection();

        if (s == null || s.length()==0) {
            Log.e(TAG, "Received null multi string");
            return;
        }

        try {
            JSONObject jsonBody;
            try {
                jsonBody = new JSONObject(s);//pd.data);
            } catch(Exception e) {
                e.printStackTrace();
                Log.e(TAG, "Error creating json Object in multi api");
                return;
            }

            Log.d(TAG, "Received multi data - subject " + subject);
            String subject = jsonBody.getString("subject_id");


            String sensorData = jsonBody.getString("data");

            String data;


            if (Constants.encrypting) {
                Log.d(TAG, "Encrypting Data");
                String encData = aes.encrypt(sensorData);// + DustService.getDustData());

                jsonBody.put("data", encData);
                jsonBody.put("enc_key", encKeyString);

                Log.d("encData", encData);
            }
            data = processAndSerialize(jsonBody);

            Intent i = new Intent(this, MobileUploadService.class);
            i.putExtra("data",data);
            i.putExtra("url", Constants.MULTI_API);
            startService(i);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Error receiving/processing multi api data");
        }
    }


    /*
    Alarm Services
     */

    private static final Integer TWO_HOUR_MS = 1000 * 60 * 120;



    private PendingIntent createAlarmPI(Integer alarmId) {
        Intent intent = new Intent(this, MobileAlarmReceiver.class);
        intent.putExtra("alarm-id", alarmId);

        return PendingIntent.getBroadcast(this, getNextAlarmId(), intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    //this method will launch the question activity on the phone and also vibrate and notify the watch
    //this method takes a definite time of day in hour and minutes (military time
    // - time zone of the alarm is automatic in terms of the local device time
    private void scheduleQuestionReminder(int hour, int minute) {
        Calendar calendar = Calendar.getInstance();

        calendar.set(Calendar.HOUR_OF_DAY, hour); // For 1 PM or 2 PM
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);

        PendingIntent pi = createAlarmPI(Constants.QUESTION_ALARM_ID);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, pi);

        Log.d(TAG, "Scheduled question alarm at time: " + hour + ":" + minute);
    }

    private void scheduleSpiroReminder(long startTime, Integer interval) {
        PendingIntent pi = createAlarmPI(Constants.SPIRO_ALARM_ID);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, startTime,
                interval, pi);
        Log.d(TAG, "Scheduled spiro alarm to repeat every " + interval / 60000 + " min");
    }

    private void scheduleAlarms() {
        Log.d(TAG, "Scheduling Alarms");
        scheduleSpiroReminder(System.currentTimeMillis()+TWO_HOUR_MS/4, TWO_HOUR_MS);
        scheduleQuestionReminder(7, 30);
        scheduleQuestionReminder(15, 30);
        scheduleQuestionReminder(17, 30);
        scheduleQuestionReminder(19, 30);
    }

    private Integer getNextAlarmId() {
        requestCode++;
        if (requestCode>Constants.RC_LIMIT)
            requestCode = 0;

        activeAlarms.add(requestCode);
        Log.d(TAG, "Next requestCode: " + requestCode);
        return requestCode;
    }

    private void cancelAlarms() {
        Log.d(TAG, "Cancelling Alarms");
        for (Integer i : activeAlarms) {
            try {
                Intent intent = new Intent(this, MobileAlarmReceiver.class);
                PendingIntent pi = PendingIntent.getBroadcast(this, i, intent, 0);
                alarmManager.cancel(pi);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "[Handled] Error cancelling alarms");
            }
        }
        activeAlarms.clear();
    }


    @Override
    public void onDestroy() {
        super.onDestroy();

    }

    //Used if mobile managing the bluetooth connections
//    private void scheduleRepeatedBlueTooth(long interval) {
//        Intent intent = new Intent(this, MobileAlarmReceiver.class);
//        intent.putExtra("alarm-id", Constants.START_BLUETOOTH_ID);
//        PendingIntent pi = PendingIntent.getBroadcast(this, Constants.START_BLUETOOTH_ID, intent, 0);
//
//        Calendar calendar = Calendar.getInstance();
//        calendar.setTimeInMillis(System.currentTimeMillis());
//
//        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(),
//                interval, pi);
//        Log.d(TAG, "scheduleRepeatedSensors, interval: " + interval);
//    }

    private class MobileAlarmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Integer alarmId = intent.getIntExtra("alarmId", Constants.NO_VALUE);
            Log.d(TAG, "Received alarm " + alarmId);
            switch (alarmId) {
                case Constants.START_ALARM_ID:
                    Log.d("WearAlarmReceiver", "started sensors");
//                    startMeasurement(MainActivity.this);
                    break;
                case Constants.TRIGGER_ALARM_ID:
                    try {
//                        mSensorManager.requestTriggerSensor(mListener, mSigMotionSensor);
                        Log.d(TAG, "Set sensor motion trigger");
                    } catch (Exception e) {
                        Log.d(TAG, "No sig motion sensor for trigger");
                    }
                    break;
                case Constants.SPIRO_ALARM_ID:
                    Log.d(TAG, "Spiro alarm called");

                    int mNotificationId = 1;
                    NotificationManager mNotifyMgr = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    mNotifyMgr.notify(mNotificationId, buildSpiroReminder(context));
                    Toast.makeText(context, "Spirometer Time", Toast.LENGTH_LONG).show();
                    Courier.deliverMessage(context, Constants.REMINDER_API, "spiro");
                    break;
                case Constants.QUESTION_ALARM_ID:
                    Log.d(TAG, "Question alarm called");
                    Intent i = new Intent();
                    i.setClass(context, QuestionActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(i);
                    Toast.makeText(context, "Please fill out the Survey", Toast.LENGTH_LONG).show();

                    //TODO: create reminder and activity intent for questionnaire
                    break;
                case Constants.CLOSE_SPIRO_ALARM_ID:
                    Log.d(TAG, "Close spiro alarm called");
                    break;

                default:
                    Log.d(TAG, "Unknown Alarm " + alarmId);
                    break;
            }
        }


        private Notification buildSpiroReminder(Context c) {
//        Intent viewIntent = new Intent(this, MainActivity.class);
//        viewIntent.putExtra("event-id", 0);
//        PendingIntent viewPendingIntent = PendingIntent.getActivity(this, 0, viewIntent, 0);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(c)
//                        .setSmallIcon(R.drawable.ic_spiro)
                    .setLargeIcon(BitmapFactory.decodeResource(c.getResources(), R.drawable.ic_spiro))
                    .setContentTitle("Breathe Reminder!")
                    .setVibrate(new long[]{500})
                    .setContentText("Time to use Spirometer on Watch")
                    .setWhen(System.currentTimeMillis())
                    .setShowWhen(true);
//                .setContentIntent(viewPendingIntent);
            return builder.build();
        }

    }

}
