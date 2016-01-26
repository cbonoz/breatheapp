package com.github.pocmo.sensordashboard;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.util.List;

/**
 * Created by cbono on 12/15/15.
 *
 * TODO: Link with SensorReceiver Service to include most probable activity
 */
public class ActivityService extends IntentService {

    private static String TAG = "ActivityService";
    private static Integer currentBestType;
    private static Integer currentConfidence;


    public ActivityService() {
        // Use the TAG to name the worker thread.
        super(TAG);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }



    /**
     * Called when a new activity detection update is available.
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "onHandleIntent");


        // If the intent contains an update
        if (ActivityRecognitionResult.hasResult(intent)) {

            // Get the update
            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);

//            // Log the update
//            logActivityRecognitionResult(result);

            // Get the most probable activity from the list of activities in the update
            DetectedActivity mostProbableActivity = result.getMostProbableActivity();

            // Get the confidence percentage for the most probable activity
            currentConfidence = mostProbableActivity.getConfidence();

            // Get the type of activity
            currentBestType = mostProbableActivity.getType();
            //currentBestActivity = getNameFromType(currentBestType);

            mostProbableActivity.getVersionCode();

            Log.d(TAG, "activity: " + getNameFromType(currentBestType));

            if (currentConfidence >= 50) {
                String mode = getNameFromType(currentBestType);

                if (currentBestType == DetectedActivity.ON_FOOT) {
                    DetectedActivity betterActivity = walkingOrRunning(result.getProbableActivities());

                    if (null != betterActivity)
                        mode = getNameFromType(betterActivity.getType());
                }

                sendNotification(mode);
            }
        }
    }

    /**
     * Post a notification to the user. The notification prompts the user to click it to
     * open the device's GPS settings
     */
    private void sendNotification(String mode) {
//
//        // Create a notification builder that's compatible with platforms >= version 4
//        NotificationCompat.Builder builder =
//                new NotificationCompat.Builder(getApplicationContext());
//
//        // Set the title, text, and icon
//        builder.setContentTitle(getString(R.string.app_name))
//                .setContentText(getString(R.string.turn_on_GPS))
//                .setSmallIcon(R.drawable.ic_notification)
//
//                        // Get the Intent that starts the Location settings panel
//                .setContentIntent(getContentIntent());
//
//        // Get an instance of the Notification Manager
//        NotificationManager notifyManager = (NotificationManager)
//                getSystemService(Context.NOTIFICATION_SERVICE);
//
//        // Build the notification and post it
//        notifyManager.notify(0, builder.build());
    }
    /**
     * Get a content Intent for the notification
     *
     * @return A PendingIntent that starts the device's Location Settings panel.
     */
    private PendingIntent getContentIntent() {

        // Set the Intent action to open Location Settings
        Intent gpsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);

        // Create a PendingIntent to start an Activity
        return PendingIntent.getActivity(getApplicationContext(), 0, gpsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Tests to see if the activity has changed
     *
     * @param currentType The current activity type
     * @return true if the user's current activity is different from the previous most probable
     * activity; otherwise, false.
     */
    private boolean activityChanged(int currentType) {
        return currentBestType == currentType;
    }

    /**
     * Determine if an activity means that the user is moving.
     *
     * @param type The type of activity the user is doing (see DetectedActivity constants)
     * @return true if the user seems to be moving from one location to another, otherwise false
     */
    private boolean isMoving(int type) {
        switch (type) {
            // These types mean that the user is probably not moving
            case DetectedActivity.STILL :
            case DetectedActivity.TILTING :
            case DetectedActivity.UNKNOWN :
                return false;
            default:
                return true;
        }
    }

    private DetectedActivity walkingOrRunning(List<DetectedActivity> probableActivities) {
        DetectedActivity myActivity = null;
        int confidence = 0;
        for (DetectedActivity activity : probableActivities) {
            if (activity.getType() != DetectedActivity.RUNNING && activity.getType() != DetectedActivity.WALKING)
                continue;

            if (activity.getConfidence() > confidence)
                myActivity = activity;
        }

        return myActivity;
    }

    /**
     * Map detected activity types to strings
     *
     * @param activityType The detected activity type
     * @return A user-readable name for the type
     */
    private String getNameFromType(int activityType) {
        switch (activityType) {
            case DetectedActivity.IN_VEHICLE:
                return "in_vehicle";
            case DetectedActivity.ON_BICYCLE:
                return "in_bicycle";
            case DetectedActivity.RUNNING:
                return "running";
            case DetectedActivity.WALKING:
                return "walking";
            case DetectedActivity.ON_FOOT:
                return "on_foot";
            case DetectedActivity.STILL:
                return "still";
            case DetectedActivity.TILTING:
                return "tilting";
            default:
                return "unknown";
        }
    }
}
