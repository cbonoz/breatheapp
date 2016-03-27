package com.breatheplatform.beta.messaging;

import android.net.Uri;
import android.util.Log;

import com.breatheplatform.beta.ClientPaths;
import com.breatheplatform.beta.MainActivity;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by cbono on 2/6/16.
 */

public class WearReceiverService extends WearableListenerService {
    private static final String TAG = "WearReceiverService";

    private DeviceClient client;

    @Override
    public void onCreate() {
        super.onCreate();

        client = DeviceClient.getInstance(this);
    }


    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {


        for (DataEvent dataEvent : dataEvents) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                DataItem dataItem = dataEvent.getDataItem();
                Uri uri = dataItem.getUri();
                String path = uri.getPath();
                Log.d(TAG, "wear apiclient onDataChanged " + path );
                DataMap dm = DataMapItem.fromDataItem(dataItem).getDataMap();

                switch(path) {
                    case ClientPaths.RISK_API:
                        int newRisk = dm.getInt("risk");
                        Log.d(TAG, "onDataChanged newRisk " + newRisk);
                        if (ClientPaths.mainContext!=null) {
                            Log.d(TAG, "maincontext found - updating UI");
                            ((MainActivity) ClientPaths.mainContext).updateRiskUI(newRisk);
                        }

                        break;
                    case ClientPaths.MULTI_API:
                        String response = dm.getString("response");
                        Log.d(TAG, "onDataChanged Data Upload " + response);

                        break;
                    default:
                        Log.e(TAG, "Unidentified dataChanged path: " + path);
                        break;
                }
            }
        }
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, "Wear Received message: " + messageEvent.getPath());

    }
}

