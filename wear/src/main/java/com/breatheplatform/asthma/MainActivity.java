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
 */

package com.breatheplatform.asthma;

import android.app.Activity;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.view.GestureDetectorCompat;
import android.support.wearable.view.DismissOverlayView;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;

import com.breatheplatform.common.UploadService;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationListener;


import org.json.JSONObject;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity

        implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {


    GoogleApiClient googleClient;

    private static final String TAG = "MainActivity";

    private static final int NOTIFICATION_ID = 1;
    private static final int NOTIFICATION_REQUEST_CODE = 1;
    private static final int NUM_SECONDS = 5;
    private static final int GARBAGE_SENSOR_ID = 2752;

    private static double best_long;
    private static double best_lat;
    private static double best_accuracy;

    private GestureDetectorCompat mGestureDetector;
    private DismissOverlayView mDismissOverlayView;
    private Button btnSend;

    private UploadService uploader;
    //private WatchListener watchlistener;



    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.main_activity);
        // Build a new GoogleApiClient
        googleClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mDismissOverlayView = (DismissOverlayView) findViewById(R.id.dismiss_overlay);
        mDismissOverlayView.setIntroText(R.string.intro_text);
        mDismissOverlayView.showIntroIfNecessary();
        mGestureDetector = new GestureDetectorCompat(this, new LongPressListener());

        btnSend = (Button) findViewById(R.id.send_button);
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //sendDataToServer();
                testSendToServer();
            }

        });

    }
    public void sendDataToServer() {

        JSONObject temp= new JSONObject();//).getJson();

        Log.d("sendDataToServer",temp.toString());
        uploader.postJsonToServer(temp);
    }
    /*
    Measurement
    value	float	False
    timestamp	integer	False
    timezone	string	True
    subject_id	integer	True
    sensor_id	integer	False
    extra_data
     */
    public void testSendToServer() {

        JSONObject temp= new JSONObject();//).getJson();
        try {
            temp.put("value", 5);
            temp.put("timestamp", new Date());
            temp.put("timezone", 1);
            temp.put("subject_id",5);
            temp.put("sensor_id", GARBAGE_SENSOR_ID);
            temp.put("lat",best_lat);
            temp.put("long",best_long);
            temp.put("accuracy",best_accuracy);

            //temp.put("extra_data", 5);

            uploader.postJsonToServer(temp);
        } catch (Exception e) {
            Log.d("exception in testSend",e.toString());
        }
        Log.d("testSendToServer",temp.toString());

    }

    @Override
    protected void onStart() {
        super.onStart();
        googleClient.connect();
    }

    @Override
    public void onLocationChanged(Location location) {

        // Display the latitude and longitude in the UI
        //mTextView.setText("Latitude:  " + String.valueOf(location.getLatitude()) +
          //      "\nLongitude:  " + String.valueOf(location.getLongitude()));
        best_lat = location.getLatitude();
        best_long = location.getLongitude();
        best_accuracy = location.getAccuracy();

        Log.d("onLocationChanged = Latitude",String.valueOf(best_lat));
        Log.d("onLocationChanged = Longitude", String.valueOf(best_long));
    }

    // Register as a listener when connected
    @Override
    public void onConnected(Bundle connectionHint) {

        // Create the LocationRequest object
        LocationRequest locationRequest = LocationRequest.create();
        // Use high accuracy
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        // Set the update interval to 2 seconds
        locationRequest.setInterval(TimeUnit.SECONDS.toMillis(2));
        // Set the fastest update interval to 2 seconds
        locationRequest.setFastestInterval(TimeUnit.SECONDS.toMillis(2));
        // Set the minimum displacement
        locationRequest.setSmallestDisplacement(2);

        // Register listener using the LocationRequest object
        LocationServices.FusedLocationApi.requestLocationUpdates(googleClient, locationRequest, this);
    }

    // Disconnect from Google Play Services when the Activity stops
    @Override
    protected void onStop() {

        if (googleClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(googleClient, this);
            googleClient.disconnect();
        }
        super.onStop();
    }


    // Placeholders for required connection callbacks
    @Override
    public void onConnectionSuspended(int cause) { }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) { }



    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return mGestureDetector.onTouchEvent(event) || super.dispatchTouchEvent(event);
    }

    private class LongPressListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public void onLongPress(MotionEvent event) {
            mDismissOverlayView.show();
        }
    }

    /**
     * Handles the button to launch a notification.
     */


    /*
    public void showNotification(View view) {
        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_title))
                .setSmallIcon(R.mipmap.ic_launcher)
                .addAction(R.mipmap.ic_launcher,
                        getText(R.string.action_launch_activity),
                        PendingIntent.getActivity(this, NOTIFICATION_REQUEST_CODE,
                                new Intent(this, GridExampleActivity.class),
                                PendingIntent.FLAG_UPDATE_CURRENT))
                .build();
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
        finish();
    }
    */


    /**
     * Handles the button press to finish this activity and take the user back to the Home.
     */
    public void onFinishActivity(View view) {
        setResult(RESULT_OK);
        finish();
    }




    private void scroll(final int scrollDirection) {
        final ScrollView scrollView = (ScrollView) findViewById(R.id.scroll);
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(scrollDirection);
            }
        });
    }
}
