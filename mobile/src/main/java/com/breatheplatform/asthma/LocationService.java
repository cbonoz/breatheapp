package com.breatheplatform.asthma;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.breatheplatform.common.ClientPaths;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

/*
Currently location being managed through mainactivity, can use this class as a background service should we want to move away from that style -
the location is shared between services by using the ClientPaths Global class object within the App.

 */

public class LocationService extends Service
        implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private static final String TAG = "LocationService";


    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    PendingIntent pendingIntent;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Location onCreate");


        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        // Create the LocationRequest object
        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10 * 1000)        // 10 seconds, in milliseconds
                .setFastestInterval(1 * 1000); // 1 second, in milliseconds
    }


    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, pendingIntent);
            mGoogleApiClient.disconnect();
        }
    }


    private void handleNewLocation(Location location) {
        Log.d(TAG, location.toString());

        ClientPaths.currentLocation = location;
    }

    @Override
    public void onConnected(Bundle bundle) {
        Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (location == null) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, pendingIntent);
        }
        else {
            handleNewLocation(location);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        /*
         * Google Play services can resolve some errors it detects.
         * If the error has a resolution, try sending an Intent to
         * start a Google Play services activity that can resolve
         * error.
         */
        if (!connectionResult.hasResolution()) {

            /*
             * If no resolution is available, display a dialog to the
             * user with the error.
             */
            Log.i(TAG, "Location services connection failed with code " + connectionResult.getErrorCode());
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        handleNewLocation(location);
    }



//
//    public static final String BROADCAST_ACTION = "Hello World";
//    private static final int TWO_MINUTES = 1000 * 60 * 2;
//    public LocationManager locationManager;
//    public MyLocationListener listener;
//    public Location previousBestLocation = null;
//
//    Intent intent;
//    int counter = 0;
//
//    @Override
//    public void onCreate()
//    {
//        super.onCreate();
//        intent = new Intent(BROADCAST_ACTION);
//    }
//
//    @Override
//    public void onStart(Intent intent, int startId)
//    {
//        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
//        listener = new MyLocationListener();
//        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 4000, 0, listener);
//        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 4000, 0, listener);
//    }
//
    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }
//
//    protected boolean isBetterLocation(Location location) {
//        if (ClientPaths.currentLocation == null) {
//            // A new location is always better than no location
//            return true;
//        }
//
//        // Check whether the new location fix is newer or older
//        long timeDelta = location.getTime() - ClientPaths.currentLocation.getTime();
//        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
//        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
//        boolean isNewer = timeDelta > 0;
//
//        // If it's been more than two minutes since the current location, use the new location
//        // because the user has likely moved
//        if (isSignificantlyNewer) {
//            return true;
//            // If the new location is more than two minutes older, it must be worse
//        } else if (isSignificantlyOlder) {
//            return false;
//        }
//
//        // Check whether the new location fix is more or less accurate
//        int accuracyDelta = (int) (location.getAccuracy() - ClientPaths.currentLocation.getAccuracy());
//        boolean isLessAccurate = accuracyDelta > 0;
//        boolean isMoreAccurate = accuracyDelta < 0;
//        boolean isSignificantlyLessAccurate = accuracyDelta > 200;
//
//        // Check if the old and new location are from the same provider
//        boolean isFromSameProvider = isSameProvider(location.getProvider(),
//                ClientPaths.currentLocation.getProvider());
//
//        // Determine location quality using a combination of timeliness and accuracy
//        if (isMoreAccurate) {
//            return true;
//        } else if (isNewer && !isLessAccurate) {
//            return true;
//        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
//            return true;
//        }
//        return false;
//    }
//
//
//
//    /** Checks whether two providers are the same */
//    private boolean isSameProvider(String provider1, String provider2) {
//        if (provider1 == null) {
//            return provider2 == null;
//        }
//        return provider1.equals(provider2);
//    }
//
//
//
//    @Override
//    public void onDestroy() {
//        // handler.removeCallbacks(sendUpdatesToUI);
//        super.onDestroy();
//        Log.d(TAG, "LocationService Destroy");
//        locationManager.removeUpdates(listener);
//    }
//
//    public static Thread performOnBackgroundThread(final Runnable runnable) {
//        final Thread t = new Thread() {
//            @Override
//            public void run() {
//                try {
//                    runnable.run();
//                } finally {
//
//                }
//            }
//        };
//        t.start();
//        return t;
//    }
//
//
//
//
//    public class MyLocationListener implements LocationListener
//    {
//
//        public void onLocationChanged(final Location loc)
//        {
//            Log.i("**************************************", "Location changed");
//            if(isBetterLocation(loc)) {
//                loc.getLatitude();
//                loc.getLongitude();
//                intent.putExtra("Latitude", loc.getLatitude());
//                intent.putExtra("Longitude", loc.getLongitude());
//                intent.putExtra("Provider", loc.getProvider());
//                sendBroadcast(intent);
//
//            }
//        }
//
        public void onProviderDisabled(String provider)
        {
            Log.d(TAG, "gps disabled");
        }


        public void onProviderEnabled(String provider)
        {
            Log.d(TAG, "gps enabled");
        }


        public void onStatusChanged(String provider, int status, Bundle extras)
        {

        }

}