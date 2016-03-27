package com.breatheplatform.beta;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.breatheplatform.beta.services.ActivityRecognitionService;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.wearable.Wearable;

import me.denley.courier.BackgroundThread;
import me.denley.courier.Courier;
import me.denley.courier.ReceiveData;

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

    /**
     * Request code for launching the Intent to resolve Google Play services errors.
     */
    private static final int REQUEST_RESOLVE_ERROR = 1000;


    //    public GoogleApiClient mGoogleApiClient;
    private int count = 0;

    /**
     * Gets a PendingIntent to be sent for each activity detection.
     */
    private PendingIntent getActivityDetectionPendingIntent() {
        Intent intent = new Intent(this, ActivityRecognitionService.class);

        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // requestActivityUpdates() and removeActivityUpdates().
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Courier.startReceiving(this);



//        mGoogleApiClient = buildClient();
        Log.d(TAG, "created API client");

//        mGoogleApiClient.connect();

        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter("upload-done"));

    }

    private GoogleApiClient buildClient() {
        return new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addApiIfAvailable(ActivityRecognition.API)
//                .addConnectionCallbacks(this)
//                .addOnConnectionFailedListener(this)
                .build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        finish();

    }
//
//    @Override
//    public void onConnected(Bundle bundle) {
//        Log.d(TAG, "mobile wear api connected");
//        Wearable.DataApi.addListener(mGoogleApiClient, this);
//
//        Toast.makeText(this, "Breathe App onCreate", Toast.LENGTH_SHORT).show();
//
//
//        //Request Activity Updates from GoogleAPIClient
//        try {
//            ActivityRecognition.ActivityRecognitionApi
//                    .requestActivityUpdates(mGoogleApiClient, 10000, getActivityDetectionPendingIntent())
//                    .setResultCallback(new ResultCallback<Status>() {
//
//                        @Override
//                        public void onResult(Status status) {
//                            if (status.getStatus().isSuccess()) {
//
//                                Log.d(TAG, "Successfully requested activity updates");
//
//                            } else {
//                                Log.e(TAG, "Failed in requesting acitivity updates, "
//                                        + "status code: "
//                                        + status.getStatusCode() + ", message: " + status
//                                        .getStatusMessage());
//                            }
//                        }
//                    });
//        } catch (Exception e) {
//            e.printStackTrace();
//            Log.e(TAG, "[Handled] Error Requesting location service (lack of permission)");
//
//        }
//
//    }
//
//    @Override
//    public  void onConnectionSuspended(int i) {
//        Log.d(TAG, "onConnectionSuspended called");
//    }
//
//    @Override
//    public void onConnectionFailed(ConnectionResult connectionResult) {
//        Log.d(TAG, "onConnectionFailed called");
//    }


//
//    @Override
//    public void onDataChanged(DataEventBuffer dataEvents) {
//        for (DataEvent event : dataEvents) {
//            if (event.getType() == DataEvent.TYPE_CHANGED) {
//                // DataItem changed
//                DataItem item = event.getDataItem();
//                DataMap dm= DataMapItem.fromDataItem(item).getDataMap();
//                String data = dm.getString("data");
//
//                String url = item.getUri().getPath();
//
//                Log.d(TAG, "mobile weardata " + url + ": " + data);
//
//
////
//                //create post request from message parameters
//                Intent i = new Intent(this, MobileUploadService.class);
//                i.putExtra("data",data);
//                i.putExtra("url",url);
//                startService(i);
//
//            } else if (event.getType() == DataEvent.TYPE_DELETED) {
//                // DataItem deleted
//            }
//        }
//    }




//    public class ActivityDetectionBroadcastReceiver extends BroadcastReceiver {
//        protected static final String TAG = "activity-detection-response-receiver";
//
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            String act  = intent.getStringExtra("activity");
//            int confidence = intent.getIntExtra("confidence",-1);
//
//            Log.d(TAG, "activity detected: " + act + " " + confidence);
//
//        }
//    }


    // Our handler for received Intents. This will be called whenever an Intent
// with an action named "upload-done" is broadcasted.
    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent

            String urlCase = intent.getStringExtra("url");
//            Log.d("local receiver", urlCase);

//            PutDataMapRequest putDataMapReq = PutDataMapRequest.create(urlCase);
//            DataMap dm = putDataMapReq.getDataMap();

            switch (urlCase) {
                case ClientPaths.RISK_API:
                    int riskValue = intent.getIntExtra("risk", ClientPaths.NO_VALUE);
                    Courier.deliverMessage(MobileActivity.this,ClientPaths.RISK_API, riskValue);
//                    dm.putInt("risk", riskValue);
                    break;
                case ClientPaths.MULTI_API:
                    String response = intent.getStringExtra("response");
                    Courier.deliverMessage(MobileActivity.this,ClientPaths.RISK_API, response);
//                    dm.putString("response",response);
                    break;
            }


//            PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
//            com.google.android.gms.common.api.PendingResult<DataApi.DataItemResult> pendingResult =
//                    Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq);
        }
    };



//    @BackgroundThread
    @ReceiveData(ClientPaths.RISK_API)
    void onRiskReceived(String data, String nodeId) { // The nodeId parameter is optional
        Log.d(TAG, data);
    }



    @BackgroundThread
    @ReceiveData(ClientPaths.MULTI_API)
    void onMultiReceived(String data, String nodeId) { // The nodeId parameter is optional
        Log.d(TAG, data);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Courier.stopReceiving(this);
        Toast.makeText(this, "Breathe App onDestroy", Toast.LENGTH_SHORT).show();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
//
//        if (mGoogleApiClient.isConnected()) {
//            ActivityRecognition.ActivityRecognitionApi
//                    .removeActivityUpdates(mGoogleApiClient, getActivityDetectionPendingIntent());
//        }
//
//        Wearable.DataApi.removeListener(mGoogleApiClient, this);
//        mGoogleApiClient.disconnect();

    }


}
