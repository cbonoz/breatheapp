package com.breatheplatform.beta;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.StatFs;
import android.util.Log;
import android.widget.Toast;

import com.breatheplatform.beta.encryption.HybridCrypt;
import com.breatheplatform.beta.services.CalendarService;
import com.breatheplatform.beta.services.MobileUploadService;
import com.breatheplatform.beta.shared.Constants;

import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

import me.denley.courier.BackgroundThread;
import me.denley.courier.Courier;
import me.denley.courier.ReceiveData;
import me.denley.courier.ReceiveMessages;

/**
 * Receives its own events using a listener API designed for foreground activities. Updates a data
 * item every second while it is open. Also allows user to take a photo and send that as an asset
 * to the paired wearable.
 */
public class MobileActivity extends Activity
        /*implements
        DataApi.DataListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener */{

    private static final String TAG = "MobileActivity";

    public static Boolean unregisterUser = false;
    public static Boolean writing = true;


//    protected GoogleApiClient mGoogleApiClient;
//    protected ActivityDetectionBroadcastReceiver activityReceiver;

    /**
     * Request code for launching the Intent to resolve Google Play services errors.
     */
    private static final int REQUEST_RESOLVE_ERROR = 1000;
    public static final String MY_PREFS_NAME = "SubjectFile";
    private static String count = "0";
    //    public GoogleApiClient mGoogleApiClient;


    public static String labelDirectory = null;
    public static File labelFile  = null;// = createFile(sensorDirectory);


    private static Boolean createCalendarEvent = false;

    public void calendarEvent() {
        Intent i = new Intent(this, CalendarService.class);
        startService(i);
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (createCalendarEvent) {
            calendarEvent();
        }

        prefs = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE);

        if (unregisterUser) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("subject", "");
            editor.commit();
            Log.d(TAG, "unregister, id now " + prefs.getString("subject", ""));
            unregisterUser = false;
        }

        subject = prefs.getString("subject", "");
        Log.d(TAG, "subject " + subject);

        //if subject is null, start registration page
        if (subject.equals("")) {
            startActivity(new Intent(MobileActivity.this, RegisterActivity.class));
            finish();
        }

        Courier.startReceiving(this);


        Log.d(TAG, "created API client");

//        MyEncrypter.createRsaEncrypter(this);

//        try {
//            MyEncrypter.lAESKey = MyEncrypter.randomKey(MyEncrypter.AES_KEY_SIZE);
//            MyEncrypter.lRSAKey = MyEncrypter.readKeyWrapped(getResources().openRawResource(R.raw.api_public));
////            MyEncrypter.createRsaEncrypter(this);
//        } catch (Exception e) {
//            encrypting = false;
//        }

//        MyEncrypter.createAes();

        aes = new HybridCrypt(this, subject);
        aesKeyString = aes.getKeyString();
        encKeyString = aes.encryptRSA(aesKeyString);

        Log.d("raw_key", aesKeyString);
        Log.d("enc_key", encKeyString);
//        Log.d(TAG, "Sending subject_id " + subject + " to watch");
//        Courier.deliverMessage(this, Constants.SUBJECT_API,subject);
    }


    public String getCountandIncrement() {
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


    public File nextLabelFile() {
        labelDirectory = ROOT + "/Breathe" + getCountandIncrement() + ".txt";
        Log.d(TAG, "Creating Label File " + labelDirectory);
        return createFile(labelDirectory);
    }

//    private static
    private SharedPreferences prefs = null;
    private String subject = "";


    @Override
    protected void onStart() {
        super.onStart();
//        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
//        mGoogleApiClient.disconnect();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");

        //if subject is null onResume, try registering again.
        if (subject.equals("")) {
            Intent i = new Intent(this, RegisterActivity.class);
            this.startActivity(i);
        }

        // Register the broadcast receiver that informs this activity of the DetectedActivity
        // object broadcast sent by the intent service.
//        LocalBroadcastManager.getInstance(this).registerReceiver(activityReceiver, new IntentFilter(Constants.BROADCAST_ACTION));
//        finish();
    }

    @Override
    protected void onPause() {
        // Unregister the broadcast receiver that was registered during onResume().
//        LocalBroadcastManager.getInstance(this).unregisterReceiver(activityReceiver);
        super.onPause();
    }


    @BackgroundThread
    @ReceiveMessages(Constants.RISK_API)
    void onRiskReceived(String data) { // The nodeId parameter is optional
        Log.d(TAG, "Received message from " + Constants.RISK_API);
        Intent i = new Intent(this, MobileUploadService.class);
        i.putExtra("data",data);
        i.putExtra("url", Constants.RISK_API);
        startService(i);
    }

    @BackgroundThread
    @ReceiveMessages(Constants.SUBJECT_API)
    void onSubjectReceived(String data) { // The nodeId parameter is optional
        //send subject back to watch
        Log.d(TAG, "Received subject_id request from wear");
        Courier.deliverMessage(this, Constants.SUBJECT_API, subject);
    }

    @BackgroundThread
    @ReceiveMessages(Constants.QUESTION_API)
    void onQuestionReceived(String data) { // The nodeId parameter is optional
        //send subject back to watch
        Log.d(TAG, "Received questionnaire request from wear");
        //TODO: Start questionnaire here
    }


    @BackgroundThread
    @ReceiveMessages(Constants.FILE_API)
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
                createToast(info);
                ;
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

    public void createToast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

//    private static final String API_KEY = "I3jmM2DI4YabH8937pRwK7MwrRWaJBgziZTBFEDTpec";


    private HybridCrypt aes;
    private String aesKeyString;
    private String encKeyString;

    private static final File ROOT = android.os.Environment.getExternalStorageDirectory();
    private static final String sensorDirectory = ROOT + "/SensorData.txt";
    private static final File sensorFile = createFile(sensorDirectory);
    
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
    


    @BackgroundThread
    @ReceiveData(Constants.MULTI_API)
    void onMultiReceived(String s) {
    //    void onMultiReceived(PostData pd) {
//        Log.d(TAG, "Received multi data");//+ s);
        if (s.length()==0) {
            Log.e(TAG, "Received null multi string");
            return;
        }



        try {
//            jsonBody.put("key",API_KEY);




//            String[] parts = s.split("^^");
//
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
//                byte[] aesBytes = MyEncrypter.lAESKey.getBytes();
//
//                String lEncryptedKey = Base64.encodeToString(MyEncrypter.RSAEncrypt(aesBytes, aesBytes), 0);
//                String lEncryptedBody = Base64.encodeToString(MyEncrypter.AESEncrypt(sensorData, MyEncrypter.lAESKey), 0);

//                String lEncryptedKey = MyEncrypter.getEncryptedAesKey();
//                String lEncryptedBody = MyEncrypter.encryptAes(subject, sensorData);

//                byte[] key = MyEncrypter.getAes();
//                byte[] lEncryptedKey = MyEncrypter.encryptRSA(this, key);
//                byte[] lEncryptedBody = MyEncrypter.encryptAES(parts[1].getBytes());


                //parts[0] = {"timestamp":1460484850245,"subject_id":"3","key":"I3jmM2DI4YabH8937pRwK7MwrRWaJBgziZTBFEDTpec","battery":99,"connection":"PROXY"

                String encData = aes.encrypt(sensorData);

                jsonBody.put("data", encData);
                jsonBody.put("raw_key", aesKeyString);
                jsonBody.put("enc_key", encKeyString);

                Log.d("encData", encData);
                Log.d("un_data", aes.decrypt(encData));



                
                Log.d("raw_key", aesKeyString);
                Log.d("enc_key", encKeyString);
            }
//            else {
//                data = jsonBody.toString()
//            }

            data = jsonBody.toString();

            try {
                jsonBody = new JSONObject(data);//pd.data);
            } catch(Exception e) {
                e.printStackTrace();
                Log.e(TAG, "Error creating json Object in multi api");
                return;
            }

            Log.d("reconstructed raw_key", jsonBody.getString("raw_key"));


            if (writing) {
                if (Constants.collecting) {
                    if (labelFile != null) {
                        writeDataToFile(sensorData, labelFile, true);
                    } else {
                        Log.e(TAG, "Attempted to write to labelfile when null");
                    }

                } else {
                    //write the first instance of the multi-api post request body (for testing encryption)

                    writeDataToFile(data, sensorFile, false);
                    if (Constants.encrypting)
                        Log.d(TAG, "Wrote to file " + sensorFile.toString());
                    Log.d(TAG, "Writing = false");
                    writing = false;
                }
            }
            Intent i = new Intent(this, MobileUploadService.class);
            i.putExtra("data",data);
            //perhaps add encrypted data bytes here as additional intent parameter
            i.putExtra("url", Constants.MULTI_API);
            startService(i);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Error receiving/processing multi api data");
            return;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Courier.stopReceiving(this);
        Log.d(TAG, "onDestroy called");
//        createToast("Breathe App onDestroy");
    }
}
