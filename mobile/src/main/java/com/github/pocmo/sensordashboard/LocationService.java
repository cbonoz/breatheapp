package com.github.pocmo.sensordashboard;

import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.github.pocmo.sensordashboard.shared.ClientPaths;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.wearable.Wearable;

import java.util.concurrent.TimeUnit;

/**
 * Created by cbono on 12/24/15.
 * http://stackoverflow.com/questions/14478179/background-service-with-location-listener-in-android
 */
public class LocationService extends Service implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private static GoogleApiClient googleClient;

        private static final Integer TWO_MINUTES = 60*2*1000;

    private static final String TAG = "LocationService";

    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }


    @Override
    public void onCreate() {
        Log.i(TAG, "LocationService onCreate");

        googleClient = new GoogleApiClient.Builder(this)
                .addApiIfAvailable(LocationServices.API)
                .addApiIfAvailable(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        googleClient.connect();
    }

//    public LocationService() {
//
//
//    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //uploadTask.cleanUp();

        if (googleClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(googleClient, this);
            googleClient.disconnect();

        }

    }



    // Register as a listener when connected
    @Override
    public void onConnected(Bundle connectionHint) {
        Log.d(TAG, "onConnected");

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




    // Placeholders for required connection callbacks
    @Override
    public void onConnectionSuspended(int cause) {
        Log.e(TAG, "googleClient connection suspended - cause: " + cause);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(TAG, "googleClient failed to connect");
    }


    /****************************/
    /* LOCATION SERVICE METHODS */
    /****************************/

    @Override
    public void onLocationChanged(Location location) {

        // Display the latitude and longitude in the UI
        //mTextView.setText("Latitude:  " + String.valueOf(location.getLatitude()) +
        //      "\nLongitude:  " + String.valueOf(location.getLongitude()));
        Log.d("******************", "New Location Recorded:");
        if(isBetterLocation(location)) {
            ClientPaths.currentLocation = location;

            Log.d("locChange = Latitude", String.valueOf(ClientPaths.currentLocation.getLatitude()));
            Log.d("locChange = Longitude", String.valueOf(ClientPaths.currentLocation.getLongitude()));
        }
    }


    protected boolean isBetterLocation(Location location) {
        if (ClientPaths.currentLocation == null) {
            ClientPaths.currentLocation = location;
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - ClientPaths.currentLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - ClientPaths.currentLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                ClientPaths.currentLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }



    /** Checks whether two providers are the same */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }

}
