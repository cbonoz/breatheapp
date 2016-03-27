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

    private Map<T, DataApi.DataListener> dataListeners = new LinkedHashMap<T, DataApi.DataListener>();
    private Map<T, NodeApi.NodeListener> nodeListeners = new LinkedHashMap<T, NodeApi.NodeListener>();

    public void startReceiving(final Context context, final T target) {
        this.context = context;
        initNodeListener(target);
        initDataListener(target);
    }

    public void stopReceiving(T target) {
        GoogleApiClient apiClient = WearableApis.googleApiClient;
        if(apiClient==null) {
            return;
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

        if (path.equals("/api/risk/get")) {
            final int as_int = Packager.unpack(context, item, int.class);

            handler.post(new Runnable() {
                public void run() {
                    target.onRiskReceived(as_int, node);
                }
            });
        } else if (path.equals("/api/multisensor/add")) {
            final java.lang.String as_java_lang_String = Packager.unpack(context, item, java.lang.String.class);

            handler.post(new Runnable() {
                public void run() {
                    target.onMultiReceived(as_java_lang_String, node);
                }
            });
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
