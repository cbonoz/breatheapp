package com.breatheplatform.beta;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

/**
 * Receives its own events using a listener API designed for foreground activities. Updates a data
 * item every second while it is open. Also allows user to take a photo and send that as an asset
 * to the paired wearable.
 */
public class MobileActivity extends Activity {

    private static final String TAG = "MobileActivity";

    /**
     * Request code for launching the Intent to resolve Google Play services errors.
     */
    private static final int REQUEST_RESOLVE_ERROR = 1000;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "MobileActivity onCreate Mobile");
    }
}
