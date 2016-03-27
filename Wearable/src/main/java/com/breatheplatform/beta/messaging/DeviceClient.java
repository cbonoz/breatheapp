package com.breatheplatform.beta.messaging;

/**
 * Created by cbono on 2/6/16.
 */

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.breatheplatform.beta.MainActivity;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;



public class DeviceClient {
    private static final String TAG = "DeviceClient";
    private static final int CLIENT_CONNECTION_TIMEOUT = 15000;

    public static DeviceClient instance;

    public static DeviceClient getInstance(Context context) {
        if (instance == null) {
            instance = new DeviceClient(context.getApplicationContext());
        }
        return instance;
    }

    private Context context;
    private GoogleApiClient googleApiClient;
    private ExecutorService executorService;


    public DeviceClient(Context context) {
        this.context = context;

        googleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        Log.d(TAG, "onConnected: " + connectionHint);
                        // Now you can use the Data Layer API
//
//                        List<Node> connectedNodes =
//                                Wearable.NodeApi.getConnectedNodes(googleApiClient).await().getNodes();
//                        Log.d(TAG, "connectedNodes: " + connectedNodes.toString());

                    }
                    @Override
                    public void onConnectionSuspended(int cause) {
                        Log.d(TAG, "onConnectionSuspended: " + cause);
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.d(TAG, "onConnectionFailed: " + result);
                    }
                })
                        // Request access only to the Wearable API
                .addApi(Wearable.API)
                .build();

        executorService = Executors.newCachedThreadPool();

    }


    public void sendPostRequest(final String data, final String apiUrl) {
        Log.d(TAG, "Sending " + apiUrl + "request");
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                sendPostRequestInBackground(data, apiUrl);
            }
        });
    }

    private void sendPostRequestInBackground(final String data, final String url) {
        PutDataMapRequest dataMap = PutDataMapRequest.create(url);

        dataMap.getDataMap().putString("data", data);

        PutDataRequest putDataRequest = dataMap.asPutDataRequest();

        send(putDataRequest);
    }


    private boolean validateConnection() {
        if (googleApiClient.isConnected()) {
            return true;
        }

        ConnectionResult result = googleApiClient.blockingConnect(CLIENT_CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS);

        return result.isSuccess();
    }

    private void send(PutDataRequest putDataRequest) {
        boolean res = validateConnection();
        debugMsg("Valid connection: " + res);
        if (res) {
            Wearable.DataApi.putDataItem(googleApiClient, putDataRequest).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                @Override
                public void onResult(DataApi.DataItemResult dataItemResult) {
                    Log.d(TAG, "Sent sensor data: " + dataItemResult.getStatus().isSuccess());
                }
            });
        }
    }

    public void debugMsg(final String msg) {
        if(context!=null) {
            ((MainActivity) context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, msg);
                }
            });
        }
    }


}
