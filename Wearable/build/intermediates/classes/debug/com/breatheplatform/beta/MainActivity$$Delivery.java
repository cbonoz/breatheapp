package com.breatheplatform.beta;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

import me.denley.courier.Courier;
import me.denley.courier.Packager;
import me.denley.courier.WearableApis;

import android.os.Handler;
import android.os.Looper;
import android.content.Context;

import static me.denley.courier.WearableApis.NODE;
import static me.denley.courier.WearableApis.DATA;
import static me.denley.courier.WearableApis.MESSAGE;

public class MainActivity$$Delivery<T extends MainActivity> implements Courier.DeliveryBoy<T> {
    private Context context;
    private Handler handler = new Handler(Looper.getMainLooper());

    private Map<T, MessageApi.MessageListener> messageListeners = new LinkedHashMap<T, MessageApi.MessageListener>();
    private Map<T, DataApi.DataListener> dataListeners = new LinkedHashMap<T, DataApi.DataListener>();
    private Map<T, NodeApi.NodeListener> nodeListeners = new LinkedHashMap<T, NodeApi.NodeListener>();

    public void startReceiving(final Context context, final T target) {
        this.context = context;
        initNodeListener(target);
        initMessageListener(target);
        initDataListener(target);
    }

    public void stopReceiving(T target) {
        GoogleApiClient apiClient = WearableApis.googleApiClient;
        if(apiClient==null) {
            return;
        }

        MessageApi.MessageListener ml = messageListeners.remove(target);
        if(ml!=null) {
            WearableApis.getMessageApi().removeListener(apiClient, ml);
        }

        DataApi.DataListener dl = dataListeners.remove(target);
        if(dl!=null) {
            WearableApis.getDataApi().removeListener(apiClient, dl);
        }

        NodeApi.NodeListener nl = nodeListeners.remove(target);
        if(nl!=null) {
            WearableApis.getNodeApi().removeListener(apiClient, nl);
        }

    }

    private void initMessageListener(final T target) {
        final MessageApi.MessageListener ml = new MessageApi.MessageListener() {
            @Override public void onMessageReceived(MessageEvent messageEvent) {
                deliverMessage(target, messageEvent);
            }
        };

        messageListeners.put(target, ml);
        WearableApis.makeWearableApiCall(context, MESSAGE, new WearableApis.WearableApiRunnable() {
            public void run(GoogleApiClient apiClient){
                WearableApis.getMessageApi().addListener(apiClient, ml);
            }
        });
    }

    private void deliverMessage(final T target, final MessageEvent message) {
        final String path = message.getPath();
        final byte[] data = message.getData();
        final String node = message.getSourceNodeId();

        if (path.equals("/api/risk/get")) {
            final int as_int = Packager.unpack(context, data, int.class);

            target.onRiskReceived(as_int);
        } else if (path.equals("/api/subject/add")) {
            final java.lang.String as_java_lang_String = Packager.unpack(context, data, java.lang.String.class);

            target.onSubjectReceived(as_java_lang_String);
        } else if (path.equals("/label")) {
            final java.lang.String as_java_lang_String = Packager.unpack(context, data, java.lang.String.class);

            target.onLabelReceived(as_java_lang_String);
        } else if (path.equals("/api/multisensor/add")) {
            final int as_int = Packager.unpack(context, data, int.class);

            target.onMultiReceived(as_int);
        }
    }

    private void initNodeListener(final T target) {
        final NodeApi.NodeListener nl = new NodeApi.NodeListener() {
            @Override public void onPeerConnected(Node node) {
                deliverRemoteNodes(target);
                initializeData(target);
            }
            @Override public void onPeerDisconnected(Node node) {
                deliverRemoteNodes(target);
            }
        };
        nodeListeners.put(target, nl);
        WearableApis.makeWearableApiCall(context, NODE, new WearableApis.WearableApiRunnable() {
            public void run(GoogleApiClient apiClient){
                WearableApis.getNodeApi().addListener(apiClient, nl);
            }
        });
    }

    private void deliverRemoteNodes(final T target) {
        WearableApis.makeWearableApiCall(context, NODE, new WearableApis.WearableApiRunnable() {
            public void run(GoogleApiClient apiClient){
                final List<Node> nodes = WearableApis.getNodeApi().getConnectedNodes(apiClient).await().getNodes();

            }
        });
    }

    private void initDataListener(final T target) {
        final DataApi.DataListener dl = new DataApi.DataListener(){
            @Override public void onDataChanged(DataEventBuffer dataEvents) {
                for(DataEvent event:dataEvents) {
                    deliverData(target, event.getDataItem());
                }
            }
        };
        dataListeners.put(target, dl);
        WearableApis.makeWearableApiCall(context, DATA, new WearableApis.WearableApiRunnable() {
            public void run(GoogleApiClient apiClient){
                WearableApis.getDataApi().addListener(apiClient, dl);
            }
        });
        initializeData(target);
    }

    private void deliverData(final T target, final DataItem item) {
        final String path = item.getUri().getPath();
        final byte[] data = item.getData();
        final String node = item.getUri().getHost();

        if (path.equals("/activity")) {
            final int as_int = Packager.unpack(context, item, int.class);

            target.onActivityReceived(as_int);
        }
    }

    private void initializeData(final T target) {
        WearableApis.makeWearableApiCall(context, DATA, new WearableApis.WearableApiRunnable() {
            public void run(GoogleApiClient apiClient){
                final DataItemBuffer existingItems = WearableApis.getDataApi().getDataItems(apiClient).await();
                for(DataItem item:existingItems) {
                    deliverData(target, item);
                }
                existingItems.release();
            }
        });
    }

}
