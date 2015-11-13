package com.breatheplatform.common;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

/**
 * Created by cbono on 11/11/15.
 */
/*
Location Service uses a different listener format than that of other sensors.
This is a separate class for gather location based data
through the Fused Location Provider API (as not all watches
have built-in GPS

See
http://developer.android.com/training/location/receive-location-updates.html


 */

public class LocListener implements LocationListener {

    private LocationManager locationManager;
    private Location mCurrentLocation;

    public LocListener(Context ctx) {

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
        Log.d("Location",location.toString());

    }


}



