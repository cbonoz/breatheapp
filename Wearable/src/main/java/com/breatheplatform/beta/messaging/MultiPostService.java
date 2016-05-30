package com.breatheplatform.beta.messaging;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import com.breatheplatform.beta.ClientPaths;
import com.breatheplatform.beta.shared.Constants;

import org.json.JSONObject;

import me.denley.courier.Courier;

/**
 * Created by cbono on 5/30/16.
 * IntentService for sending formatting sensorData to the mobile for server upload
 */
public class MultiPostService  extends IntentService {
    private static final String TAG = "MultiPostService";

    private static final JSONObject jsonBody = new JSONObject();

    //default constructor
    public MultiPostService() {
        super("MultiPostService");
    }

    //expects newline separated sensor data point values stored in the "data" String extra field
    @Override
    protected void onHandleIntent(Intent intent) {

        String sensorData = intent.getStringExtra("data");
        if (sensorData == null) {
            Log.e(TAG, "Received null sensorData");
            return;
        }

        Log.d(TAG, "createDataPostRequest");


        try {
            if (ClientPaths.subject.equals("")) {
                Log.e(TAG, "No Subject detected - blocking multi post");
                return;
            }

            jsonBody.put("timestamp", System.currentTimeMillis());
            jsonBody.put("subject_id", ClientPaths.subject);
            jsonBody.put("key", Constants.API_KEY);
            jsonBody.put("battery", ClientPaths.batteryLevel);
            jsonBody.put("testing", Constants.TESTING);
            jsonBody.put("data", sensorData);

            String data = jsonBody.toString();

            //deliver data to the mobile device
            Courier.deliverData(this, Constants.MULTI_API, data);
            Log.d(TAG, "Courier sent multiapi data " + data.length());


        } catch (Exception e) {
            Log.e(TAG, "[Handled] Error requesting multi post request");
            e.printStackTrace();
        }
    }
}
