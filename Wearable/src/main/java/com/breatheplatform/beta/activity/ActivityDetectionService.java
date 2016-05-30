package com.breatheplatform.beta.activity;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import com.breatheplatform.beta.ClientPaths;
import com.breatheplatform.beta.data.SensorAddService;
import com.breatheplatform.beta.shared.Constants;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.util.List;

/**
 * Created by cbono on 4/5/16.
 * CLASS NO LONGER USED - activity recognition is done on server
 */

public class ActivityDetectionService extends IntentService {

    protected static final String TAG = "ActivityDetectionService";

//    private static final Integer CONFIDENCE_THRESHOLD = 30;

    public ActivityDetectionService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final ActivityRecognitionResult
                activityRecognitionResult =
                ActivityRecognitionResult.extractResult(intent);
        if (activityRecognitionResult == null) {
            return;
        }

        DetectedActivity mostProbableActivity = activityRecognitionResult.getMostProbableActivity();

        Integer activityType = mostProbableActivity.getType();
//        String activityName = activityFromType(activityType);
//        Integer activityConfidence = mostProbableActivity.getConfidence();

        //Get Running or Walking if user is on foot
        if (activityType == DetectedActivity.ON_FOOT) {
            DetectedActivity betterActivity = walkingOrRunning(activityRecognitionResult.getProbableActivities());
            if (betterActivity != null) {
                mostProbableActivity = betterActivity;
                activityType = mostProbableActivity.getType();
            }
        }

        String activityName = activityFromType(activityType);
        Integer activityConfidence = mostProbableActivity.getConfidence();


        //launch question api (questionnaire on phone if transition from still to running
        if (activityName.equals("STILL") && ClientPaths.activityDetail.contains("RUN")) {
            Log.d(TAG, "detected transition from still to running");
//            Courier.deliverMessage(this,Constants.QUESTION_API,"");
        }

        ClientPaths.activityDetail = activityName + ":" + activityConfidence;
        Log.d(TAG, "Activity: " + ClientPaths.activityDetail);

        addSensorData(Constants.ACTIVITY_SENSOR_ID, activityConfidence, System.currentTimeMillis(), new float[]{activityType});

//        sendBroadcast(MainActivity.newBroadcastIntent(probableActivities));
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

    private String activityFromType(int type ) {
        switch(type) {
            case DetectedActivity.IN_VEHICLE:
                return "IN_VEHICLE";
            case DetectedActivity.ON_BICYCLE:
                return "ON_BICYCLE";
            case DetectedActivity.ON_FOOT:
                return "ON_FOOT";
            case DetectedActivity.RUNNING:
                return "RUNNING";
            case DetectedActivity.STILL:
                return "STILL";
            case DetectedActivity.TILTING:
                return "TILTING";
            case DetectedActivity.WALKING:
                return "WALKING";
            case DetectedActivity.UNKNOWN:
                return "UNKNOWN";
            default:
                return "UNEXPECTED";
        }
    }

    //append sensor data
    private void addSensorData(final Integer sensorType, final Integer accuracy, final Long t, final float[] values) {

        Intent i = new Intent(this, SensorAddService.class);
        i.putExtra("sensorType", sensorType);
        i.putExtra("accuracy", accuracy);
        i.putExtra("time", t);
        i.putExtra("values", values);

        startService(i);
    }
}
