package com.breatheplatform.beta.services;

import android.app.IntentService;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.breatheplatform.beta.ClientPaths;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

/**
 * Created by cbono on 3/25/16.
 */
public class ActivityRecognitionService extends IntentService {
    private static final String TAG = "ActivityRecognitionService";


    public ActivityRecognitionService() {
        super("ActivityRecognitionService");
//        Toast.makeText(this, "here", Toast.LENGTH_SHORT).show();
        // TODO Auto-generated constructor stub
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "ActivityRecognitionService intent called");

        // TODO Auto-generated method stub

//        Toast.makeText(this, "here2", Toast.LENGTH_SHORT).show();
        ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
        DetectedActivity activity = result.getMostProbableActivity();

        String act = getNameFromType(activity.getType());
        int conf = activity.getConfidence();
        Log.d("current activity",act);

//        ClientPaths.activityName = act;
//        ClientPaths.activityConfidence = activity.getConfidence();
//        Toast.makeText(this, getNameFromType(type), Toast.LENGTH_SHORT).show();
        Intent localIntent = new Intent(ClientPaths.BROADCAST_ACTION);
        localIntent.putExtra("activity", act);
        localIntent.putExtra("confidence", conf);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);

    }

    private String getNameFromType(int activityType) {
        switch(activityType) {
            case DetectedActivity.IN_VEHICLE:
                return "in_vehicle";
            case DetectedActivity.ON_BICYCLE:
                return "on_bicycle";
            case DetectedActivity.ON_FOOT:
                return "on_foot";
            case DetectedActivity.STILL:
                return "still";
            case DetectedActivity.UNKNOWN:
                return "unknown";
            case DetectedActivity.TILTING:
                return "tilting";
        }
        return "unknown";
    }
}

