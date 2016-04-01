package com.breatheplatform.beta;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.StatFs;
import android.util.Log;
import android.widget.Toast;

import com.breatheplatform.beta.encryption.MyEncrypter;
import com.breatheplatform.beta.services.DetectedActivitiesIntentService;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;

import org.json.JSONObject;

import java.io.File;

import me.denley.courier.BackgroundThread;
import me.denley.courier.Courier;
import me.denley.courier.ReceiveData;
import me.denley.courier.ReceiveMessages;

/**
 * Receives its own events using a listener API designed for foreground activities. Updates a data
 * item every second while it is open. Also allows user to take a photo and send that as an asset
 * to the paired wearable.
 */
public class MobileActivity extends Activity implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, ResultCallback<Status>
        /*implements
        DataApi.DataListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener */{

    private static final String TAG = "MobileActivity";


    protected GoogleApiClient mGoogleApiClient;
//    protected ActivityDetectionBroadcastReceiver activityReceiver;

    /**
     * Request code for launching the Intent to resolve Google Play services errors.
     */
    private static final int REQUEST_RESOLVE_ERROR = 1000;
    public static final String MY_PREFS_NAME = "SubjectFile";
    private static String count = "0";
    //    public GoogleApiClient mGoogleApiClient;

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

    public static String labelDirectory = null;
    public static File labelFile = null;// = createFile(sensorDirectory);

    public File nextLabelFile() {
        labelDirectory = ClientPaths.ROOT + "/Breathe" + getCountandIncrement() + ".txt";
        Log.d(TAG, "Creating Label File " + labelDirectory);
        return ClientPaths.createFile(labelDirectory);
    }

    /**
     * Gets a PendingIntent to be sent for each activity detection.
     */
    private PendingIntent getActivityDetectionPendingIntent() {
        Intent intent = new Intent(this, DetectedActivitiesIntentService.class);

        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // requestActivityUpdates() and removeActivityUpdates().
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

//    private static
    private SharedPreferences prefs = null;
    private String subject = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE);


        subject = prefs.getString("subject", "");


        Log.d(TAG, "subject " + subject);

        //if subject is null, start registration page
        if (subject.equals("")) {
            Intent i = new Intent(this, RegisterActivity.class);
            this.startActivity(i);
        }

        Courier.startReceiving(this);

        buildGoogleApiClient();
        Log.d(TAG, "created API client");

        MyEncrypter.createRsaEncrypter(this);
        Log.d(TAG, "Sending subject_id " + subject + " to watch");
        Courier.deliverMessage(this,ClientPaths.SUBJECT_API,subject);
    }

    /**
     * Builds a GoogleApiClient. Uses the {@code #addApi} method to request the
     * ActivityRecognition API.
     */
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(ActivityRecognition.API)
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
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

    /**
     * Runs when a GoogleApiClient object successfully connects.
     */
    @Override
    public void onConnected(Bundle connectionHint) {

        Log.i(TAG, "Connected to GoogleApiClient");
    }

    @Override
    public void onResult(Status status) {
        if (status.getStatus().isSuccess()) {

            Log.d(TAG, "Successfully requested activity updates");

        } else {
            Log.e(TAG, "Failed in requesting activity updates, "
                    + "status code: "
                    + status.getStatusCode() + ", message: " + status
                    .getStatusMessage());
        }
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


//
//    // Our handler for received Intents. This will be called whenever an Intent
//// with an action named "upload-done" is broadcasted.
//    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            // Get extra data included in the Intent
//
//            String urlCase = intent.getStringExtra("url");
////            Log.d("local receiver", urlCase);
//
////            PutDataMapRequest putDataMapReq = PutDataMapRequest.create(urlCase);
////            DataMap dm = putDataMapReq.getDataMap();
//
//            switch (urlCase) {
//                case ClientPaths.RISK_API:
//                    int riskValue = intent.getIntExtra("risk", ClientPaths.NO_VALUE);
//                    Courier.deliverMessage(MobileActivity.this,ClientPaths.RISK_API, riskValue);
////                    dm.putInt("risk", riskValue);
//                    break;
//                case ClientPaths.MULTI_API:
//                    String response = intent.getStringExtra("response");
//                    Courier.deliverMessage(MobileActivity.this,ClientPaths.MULTI_API, response);
////                    dm.putString("response",response);
//                    break;
//            }
//
//
////            PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
////            com.google.android.gms.common.api.PendingResult<DataApi.DataItemResult> pendingResult =
////                    Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq);
//        }
//    };



    @BackgroundThread
    @ReceiveMessages(ClientPaths.RISK_API)
    void onRiskReceived(String data) { // The nodeId parameter is optional
        Log.d(TAG, "Received message from " + ClientPaths.RISK_API);
        Intent i = new Intent(this, MobileUploadService.class);
        i.putExtra("data",data);
        i.putExtra("url",ClientPaths.RISK_API);
        startService(i);
    }

    @BackgroundThread
    @ReceiveMessages(ClientPaths.SUBJECT_API)
    void onSubjectReceived(String data) { // The nodeId parameter is optional
        //send subject back to watch
        Log.d(TAG, "Received subject_id request from wear");
        Courier.deliverMessage(this, ClientPaths.SUBJECT_API, subject);
    }

//    @BackgroundThread
//    @ReceiveMessages(ClientPaths.CALENDAR_API)
//    void onCalendarReceived(String data) { // The nodeId parameter is optional
//        //create calendar event (if authenticated)
////        Courier.deliverMessage(this, ClientPaths.SUBJECT_API, subject);
//    }

    public void createToast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }


    private static Boolean writing = true;
    private static Boolean encrypting = false;
    private static Boolean collecting = true;

    private static final String API_KEY = "I3jmM2DI4YabH8937pRwK7MwrRWaJBgziZTBFEDTpec";//"GWTgVdeNeVwsGqQHHhChfiPgDxxgXJzLoxUD0R64Gns";

    @BackgroundThread
    @ReceiveData(ClientPaths.MULTI_API)
    void onMultiReceived(String s) { // The nodeId parameter is optional
    //    void onMultiReceived(PostData pd) {
//        Log.d(TAG, "Received multi " + s);
        if (s.length()==0) {
            Log.e(TAG, "Received null multi string");
            return;
        }
        Log.d(TAG, "Received multi data");

        JSONObject jsonBody;
        try {
            jsonBody = new JSONObject(s);//pd.data);
        } catch(Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Error creating json Object in multi api");
            return;
        }

        try {
//            jsonBody.put("key",API_KEY);

            String subject = jsonBody.getInt("subject_id")+"";
            Log.d(TAG, "parsed subject: " + subject);
            String sensorData = jsonBody.getString("data");

            if (encrypting) {
                String encSensorData = MyEncrypter.encryptAes(subject, sensorData);//data.getString("data"));
                Log.d(TAG, "encData: " + encSensorData);
                jsonBody.put("data", encSensorData);

                jsonBody.put("raw_key", MyEncrypter.getAesKey());
                jsonBody.put("data_key", MyEncrypter.getEncryptedAesKey());

                Log.d("data_key", jsonBody.getString("data_key"));
                Log.d("raw_key", jsonBody.getString("raw_key"));
            }

            String data = jsonBody.toString();

            if (writing) {
                if (collecting) {
                    labelFile = nextLabelFile();
                    Boolean result = ClientPaths.writeDataToFile(sensorData, labelFile, false);
                    if (result) {
                        try {
                            StatFs stats = new StatFs("/data");
                            int availableBlocks = stats.getAvailableBlocks();
                            int blockSizeInBytes = stats.getBlockSize();
                            double freeSpaceInBytes = availableBlocks * blockSizeInBytes;
                            String info = labelDirectory + " - " + freeSpaceInBytes / 1000 + "kB left";
                            Log.d(TAG, info);
                            createToast(info);
                            Courier.deliverMessage(this,ClientPaths.LABEL_API,count);
                        } catch (Exception e) {
                            Log.d(TAG, "Error getting label file stats");
                        }

                    } else {
                        createToast("Label Memory Full");
                        writing = false;
                    }

                } else {
                    //write the first instance of the multi-api post request body (for testing encryption)
                    ClientPaths.writeDataToFile(data, ClientPaths.sensorFile, false);
                    writing = false;
                }
            }


            Intent i = new Intent(this, MobileUploadService.class);
            i.putExtra("data",data);
            i.putExtra("url",ClientPaths.MULTI_API);
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
        createToast("Breathe App onDestroy");
//        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);

//        if (mGoogleApiClient.isConnected()) {
//            ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(
//                    mGoogleApiClient,
//                    getActivityDetectionPendingIntent()
//            ).setResultCallback(this);
//        }

//        try {
//            LocalBroadcastManager.getInstance(this).unregisterReceiver(activityReceiver);
//        } catch (Exception e) {
//            Log.d(TAG, "BroadCast Receiver unregistered");
//        }
//
        if (mGoogleApiClient.isConnected()) {
            try {
                ActivityRecognition.ActivityRecognitionApi
                        .removeActivityUpdates(mGoogleApiClient, getActivityDetectionPendingIntent());
            } catch (Exception e) {

            }
        }
        mGoogleApiClient.disconnect();
    }


    /**
     * Receiver for intents sent by DetectedActivitiesIntentService via a sendBroadcast().
     * Receives a list of one or more DetectedActivity objects associated with the current state of
     * the device.
     */
//    public class ActivityDetectionBroadcastReceiver extends BroadcastReceiver {
//        protected static final String TAG = "activity-detection-response-receiver";
//
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            ArrayList<DetectedActivity> updatedActivities =
//                    intent.getParcelableArrayListExtra(Constants.ACTIVITY_EXTRA);
//
//            for (DetectedActivity activity : updatedActivities) {
//                Log.d(TAG, activity.getType() + " " + activity.getConfidence());
//            }
//
//        }
//    }
//

}
