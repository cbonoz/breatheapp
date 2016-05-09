package com.breatheplatform.beta.services;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.StatFs;
import android.util.Log;
import android.widget.Toast;

import com.breatheplatform.beta.RegisterActivity;
import com.breatheplatform.beta.connection.Connectivity;
import com.breatheplatform.beta.encryption.HybridCrypt;
import com.breatheplatform.beta.encryption.RandomString;
import com.breatheplatform.beta.receivers.AlarmReceiver;
import com.breatheplatform.beta.shared.Constants;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Calendar;

import me.denley.courier.Courier;
import me.denley.courier.Packager;

/**
 * Created by cbono on 4/13/16.
 */
public class ListenerServiceFromWear extends WearableListenerService {
    private static final String TAG = "ListenerServiceFromWear";


    private static Boolean unregisterUser = false;
    private static Boolean writeOnce = true;
    private static Integer requestCode = 0;


    private AlarmManager alarmMgr;
    private PendingIntent alarmIntent;
    private PendingIntent spiroIntent;
    private PendingIntent questionIntent;
    private PendingIntent sensorIntent;

    /*
     * Receive the message from wear
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
            case Constants.FILE_API:
                onFileReceived(s);
                break;
        }
    }


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





//    protected GoogleApiClient mGoogleApiClient;
//    protected ActivityDetectionBroadcastReceiver activityReceiver;

    /**
     * Request code for launching the Intent to resolve Google Play services errors.
     */
    private static final int REQUEST_RESOLVE_ERROR = 1000;

    private static String count = "0";
    //    public GoogleApiClient mGoogleApiClient;


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


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ListenerService onCreate called");

        if (writeOnce) {
            sensorFile = createFile(sensorDirectory);
            rawSensorFile = createFile(rawSensorDirectory);
        }

        if (runOnce) {
            runOnce = false;


            Log.d(TAG, "getting preferences");
            prefs = getSharedPreferences(Constants.MY_PREFS_NAME, MODE_PRIVATE);

            if (unregisterUser) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("subject", "");
                editor.commit();
                Log.d(TAG, "unregister, id now " + prefs.getString("subject", ""));
                unregisterUser = false;
            }

            alarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            scheduleAlarms();


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



        }
    }


    public static String getCountandIncrement() {
        if (prefs==null) {
            Log.e(TAG, "getCount but prefs is null");
            return "-1";
        }

        count = prefs.getString("count", "0");

        String newCount = (Integer.parseInt(count) + 1)+"";

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("count", newCount);
        editor.commit();

        return count;
    }


    public static File nextLabelFile() {
        labelDirectory = ROOT + "/Breathe" + getCountandIncrement() + ".txt";
        Log.d(TAG, "Creating Label File " + labelDirectory);
        return createFile(labelDirectory);
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

    void onFileReceived(String data) {

        if (data.equals(Constants.START_WRITE)) {
            labelFile = nextLabelFile();
            Log.d(TAG, "Set new labelfile: " + labelFile.toString());
        } else {
            try {
                StatFs stats = new StatFs("/data");
                int availableBlocks = stats.getAvailableBlocks();
                int blockSizeInBytes = stats.getBlockSize();
                double freeSpaceInBytes = availableBlocks * blockSizeInBytes;
                String info = labelDirectory + " " + labelFile.length()/1000 + "kB";// + freeSpaceInBytes / 1000 + "kB left";
                Log.d(TAG, info);
                showToastInService(info);

            } catch (Exception e) {
                Log.d(TAG, "Error getting label file stats");
            }

            Courier.deliverMessage(this,Constants.LABEL_API,"File " + count + " created");//: " + labelFile.length()/1000 + "kb")
        }
    }

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

            String subject = jsonBody.getString("subject_id");
            Log.d(TAG, "Received multi data - subject " + subject);

            String sensorData = jsonBody.getString("data");
            Log.d(TAG, "sensorData: " + sensorData);

            String data;


            if (Constants.encrypting) {

                Log.d(TAG, "Encrypting Data");

                //parts[0] = {"timestamp":1460484850245,"snregbject_id":"3","key":"I3jmM2DI4YabH8937pRwK7MwrRWaJBgziZTBFEDTpec","battery":99,"connection":"PROXY"

                String encData = aes.encrypt(sensorData);// + DustService.getDustData());

                jsonBody.put("data", encData);
//                jsonBody.put("raw_key", aesKeyString);
                jsonBody.put("enc_key", encKeyString);

                Log.d("encData", encData);
//
            }

//            data = jsonBody.toString();
            data = processAndSerialize(jsonBody);
//            Log.d(TAG, "Received multi request - " + data.length() + " bytes");

            if (Constants.collecting) {
                if (labelFile != null) {
                    writeDataToFile(sensorData, labelFile, true);
                } else {
                    Log.e(TAG, "[Handled] Cancel write, still waiting to update labelfile");
                }
            }

            //write the first instance of the multi-api post request body (for testing encryption)
            if (writeOnce) {
                writeDataToFile(data, sensorFile, false);
                writeDataToFile(s, rawSensorFile,false);
                Log.d(TAG, "writeOnce done -> now false");
                writeOnce = false;
            }
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

//    //this method will vibrate and notify the watch only (to use the spirometer)
//    private void scheduleSpiroNotification(Notification notification, long interval, int id) {
//        Intent notificationIntent = new Intent(this, AlarmReceiver.class);
//        notificationIntent.putExtra(AlarmReceiver.ALARM_ID, id);
//        notificationIntent.putExtra(AlarmReceiver.NOTIFICATION, notification);
//        spiroIntent = PendingIntent.getBroadcast(this, requestCode++, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
//
//        long futureInMillis = SystemClock.elapsedRealtime() + Constants.ONE_MIN_MS;
//        AlarmManager alarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
//        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, futureInMillis, interval, spiroIntent);
//        if (id==Constants.SPIRO_ALARM_ID)
//            Log.d(TAG, "Scheduled spiro alarm at interval " + interval + " ms");
//    }

    //this method will launch the question activity on the phone and also vibrate and notify the watch
    private void scheduleQuestionReminder(int hour, int minute) {

        // Set the alarm to start at approximately 2:00 p.m.
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);

        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.putExtra("alarm-id", Constants.QUESTION_ALARM_ID);
        questionIntent = PendingIntent.getBroadcast(this, requestCode++, intent, 0);

// With setInexactRepeating(), you have to use one of the AlarmManager interval
// constants--in this case, AlarmManager.INTERVAL_DAY.
        alarmMgr.setInexactRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, questionIntent);


        Log.d(TAG, "Scheduled question alarm at time: " + hour + ":" + minute);

    }

    private static final Integer TWO_HOUR_MS = 1000 * 60 * 120;

    private void scheduleSpiroReminder(int startHour, int startMinute) {
        // Set the alarm to start at 8:30 a.m.
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, startHour);
        calendar.set(Calendar.MINUTE, startMinute);

// setRepeating() lets you specify a precise custom interval--in this case,
// 20 minutes.

        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.putExtra("alarm-id", Constants.SPIRO_ALARM_ID);
        spiroIntent = PendingIntent.getBroadcast(this, Constants.SPIRO_ALARM_ID, intent, 0);

        alarmMgr.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(),
                TWO_HOUR_MS, spiroIntent);

        Log.d(TAG, "Scheduled spiro alarm to repeat every " + TWO_HOUR_MS/60000 + " min");

    }

    //units: ms
    private static final Long SPIRO_REMINDER_INTERVAL = AlarmManager.INTERVAL_HOUR*2;

    //Alarms for Questionnaire and Spiro
    //    -7:30am (fixed time)
    //    -3:30pm (fixed time)
    //    -5-7pm (randomized to occur once between these hours)
    //    -7-8pm (randomized to occur once between this hour)
    private void scheduleAlarms() {
        Log.d(TAG, "Scheduling Alarms");
        alarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

//        scheduleSpiroNotification(buildSpiroReminder(), SPIRO_REMINDER_INTERVAL, Constants.SPIRO_ALARM_ID);//AlarmManager.INTERVAL_HOUR*2);
        scheduleSpiroReminder(1,0);

//        scheduleQuestionReminder(12,20);

        scheduleQuestionReminder(7, 30);
        scheduleQuestionReminder(15, 30);
        scheduleQuestionReminder(17, 30);
        scheduleQuestionReminder(19, 30);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();

//        try {
//            if (alarmMgr != null) {
//                alarmMgr.cancel(spiroIntent);
//                alarmMgr.cancel(questionIntent);
////                alarmManager.cancel(sensorIntent);
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }




}
