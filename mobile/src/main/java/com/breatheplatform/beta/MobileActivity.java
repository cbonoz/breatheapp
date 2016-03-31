package com.breatheplatform.beta;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.breatheplatform.beta.encryption.MyEncrypter;
import com.breatheplatform.beta.services.Constants;
import com.breatheplatform.beta.services.DetectedActivitiesIntentService;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.DetectedActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;

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
    protected ActivityDetectionBroadcastReceiver mBroadcastReceiver;

    /**
     * Request code for launching the Intent to resolve Google Play services errors.
     */
    private static final int REQUEST_RESOLVE_ERROR = 1000;
    public static final String MY_PREFS_NAME = "SubjectFile";
    //    public GoogleApiClient mGoogleApiClient;

    public String getCountandIncrement() {
        if (prefs==null) {
            Log.e(TAG, "getCount but prefs is null");
            return "-1";
        }
        String count = prefs.getString("subject", null);
        if (count == null) {
            count = "0";
        }

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

    private static String code = "5555";
    private SharedPreferences prefs = null;
    private String subject;

    public Boolean acceptCredentials(String pw) {
        return code.equals(pw);
    }

    public void setSubjectAndClose(String subject_id) {

        Courier.deliverMessage(this,ClientPaths.SUBJECT_API,subject_id);
        subject = subject_id;

        setTheme(android.R.style.Theme_NoDisplay);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("subject", subject_id);
        editor.commit();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(android.R.style.Theme_NoDisplay);

        prefs = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE);



        subject = prefs.getString("subject", null);
        if (subject == null) {
            setTheme(android.R.style.Theme_DeviceDefault);

            setContentView(R.layout.mobile_activity);

            Button subjectButton = (Button) findViewById(R.id.subjectButton);
            subjectButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    EditText codeText = (EditText) findViewById(R.id.codeText);
                    EditText subjectText = (EditText) findViewById(R.id.subjectText);

                    if (acceptCredentials(codeText.getText().toString())) {
                        setSubjectAndClose(subjectText.getText().toString());
                        Toast.makeText(MobileActivity.this, "Registered Patient",Toast.LENGTH_SHORT).show();

                    } else {
                        Toast.makeText(MobileActivity.this, "Clinician Code Not Valid",Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
        super.onCreate(savedInstanceState);
        Courier.startReceiving(this);

//        mBroadcastReceiver = new ActivityDetectionBroadcastReceiver();

        buildGoogleApiClient();
        Log.d(TAG, "created API client");

        MyEncrypter.createRsaEncrypter(this);


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
        // Register the broadcast receiver that informs this activity of the DetectedActivity
        // object broadcast sent by the intent service.
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver,
                new IntentFilter(Constants.BROADCAST_ACTION));
        finish();
    }

    @Override
    protected void onPause() {
        // Unregister the broadcast receiver that was registered during onResume().
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        super.onPause();
    }


    /**
     * Runs when a GoogleApiClient object successfully connects.
     */
    @Override
    public void onConnected(Bundle connectionHint) {

        Log.i(TAG, "Connected to GoogleApiClient");
        //Request Activity Updates from GoogleAPIClient
//        try {
//            ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
//                    mGoogleApiClient,
//                    Constants.DETECTION_INTERVAL_IN_MILLISECONDS,
//                    getActivityDetectionPendingIntent()
//            ).setResultCallback(this);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            Log.e(TAG, "[Handled] Error Requesting Activity (lack of permission)");
//
//        }
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
        Courier.deliverMessage(this, ClientPaths.SUBJECT_API, subject);
    }

//    @BackgroundThread
//    @ReceiveMessages(ClientPaths.CALENDAR_API)
//    void onCalendarReceived(String data) { // The nodeId parameter is optional
//        //create calendar event (if authenticated)
////        Courier.deliverMessage(this, ClientPaths.SUBJECT_API, subject);
//    }

    public void createToast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }


    private static Boolean writing = true;
    private static Boolean encrypting = false;
    private static Boolean collecting = false;
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
                        createToast(labelDirectory + " created");
                    } else {
                        createToast("Label Memory Full");
                        writing = false;
                    }

                } else {
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
        Toast.makeText(this, "Breathe App onDestroy", Toast.LENGTH_SHORT).show();
//        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);

        if (mGoogleApiClient.isConnected()) {
            ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(
                    mGoogleApiClient,
                    getActivityDetectionPendingIntent()
            ).setResultCallback(this);
        }

        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        } catch (Exception e) {
            Log.d(TAG, "BroadCast Receiver unregistered");
        }
//
        if (mGoogleApiClient.isConnected()) {
            try {
                ActivityRecognition.ActivityRecognitionApi
                        .removeActivityUpdates(mGoogleApiClient, getActivityDetectionPendingIntent());
            } catch (Exception e) {

            }
        }

//        Wearable.DataApi.removeListener(mGoogleApiClient, this);


        mGoogleApiClient.disconnect();

    }


    /**
     * Receiver for intents sent by DetectedActivitiesIntentService via a sendBroadcast().
     * Receives a list of one or more DetectedActivity objects associated with the current state of
     * the device.
     */
    public class ActivityDetectionBroadcastReceiver extends BroadcastReceiver {
        protected static final String TAG = "activity-detection-response-receiver";

        @Override
        public void onReceive(Context context, Intent intent) {
            ArrayList<DetectedActivity> updatedActivities =
                    intent.getParcelableArrayListExtra(Constants.ACTIVITY_EXTRA);

            for (DetectedActivity activity : updatedActivities) {
                Log.d(TAG, activity.getType() + " " + activity.getConfidence());
            }

        }
    }



    public String toJSON(MultiData md){

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("timestamp",md.timestamp);
            jsonBody.put("subject_id", md.subject_id);
            jsonBody.put("key", md.key);
            jsonBody.put("battery",md.battery);
            jsonBody.put("connection", md.connection);
            jsonBody.put("data",md.data);

            return jsonBody.toString();
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return "";
        }
    }


}
