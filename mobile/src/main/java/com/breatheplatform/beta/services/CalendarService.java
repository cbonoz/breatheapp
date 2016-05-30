package com.breatheplatform.beta.services;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract.Calendars;
import android.util.Log;

import java.util.Calendar;
import java.util.Date;

/**
 * Created by cbono on 4/6/16.
 * CalendarService for creating events in the user's calendar for various Breathe Activities
 */
public class CalendarService extends IntentService {

    protected static final String TAG = "CalendarService";

    // Projection array. Creating indices for this array instead of doing
// dynamic lookups improves performance.
    public static final String[] EVENT_PROJECTION = new String[] {
            Calendars._ID,                           // 0
            Calendars.ACCOUNT_NAME,                  // 1
            Calendars.CALENDAR_DISPLAY_NAME,         // 2
            Calendars.OWNER_ACCOUNT                  // 3
    };

    // The indices for the projection array above.
    private static final int PROJECTION_ID_INDEX = 0;
    private static final int PROJECTION_ACCOUNT_NAME_INDEX = 1;
    private static final int PROJECTION_DISPLAY_NAME_INDEX = 2;
    private static final int PROJECTION_OWNER_ACCOUNT_INDEX = 3;

    /**
     * This constructor is required, and calls the super IntentService(String)
     * constructor with the name for a worker thread.
     */
    public CalendarService() {
        // Use the TAG to name the worker thread.
        super(TAG);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    /**
     * Handles incoming intents.
     * @param intent The Intent is provided (inside a PendingIntent) when requestActivityUpdates()
     *               is called.
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "Starting");

        Cursor cur = null;
        ContentResolver cr = getContentResolver();
        Uri uri = Calendars.CONTENT_URI;
        String selection = "((" + Calendars.ACCOUNT_NAME + " = ?) AND ("
                + Calendars.ACCOUNT_TYPE + " = ?) AND ("
                + Calendars.OWNER_ACCOUNT + " = ?))";
        String[] selectionArgs = new String[] {"cking0130@gmail.com", "com.google",
                "cking0130@gmail.com"};
// Submit the query and get a Cursor object back.
        cur = cr.query(uri, EVENT_PROJECTION, selection, selectionArgs, null);

        while (cur.moveToNext()) {
            long calID = 0;
            String displayName = null;
            String accountName = null;
            String ownerName = null;

            // Get the field values
            calID = cur.getLong(PROJECTION_ID_INDEX);
            displayName = cur.getString(PROJECTION_DISPLAY_NAME_INDEX);
            accountName = cur.getString(PROJECTION_ACCOUNT_NAME_INDEX);
            ownerName = cur.getString(PROJECTION_OWNER_ACCOUNT_INDEX);

            Log.d(TAG, displayName + " " + ownerName + " " + calID);
        }

        cur.close();

        Calendar startTime = Calendar.getInstance();
        startTime.set(2015,04,01,00,00);

        Calendar endTime= Calendar.getInstance();
        endTime.set(2016, 04, 05, 00, 00);

        long stTime = startTime.getTimeInMillis();
        long enTime = endTime.getTimeInMillis();



        Cursor cursors = getContentResolver().query(uri, new String[]{ "_id", "title", "description", "dtstart", "dtend", "eventLocation" },
                null,null, null);



        cursors.moveToFirst();

        String[] CalNames = new String[cursors.getCount()];
        int[] CalIds = new int[cursors.getCount()];
        for (int i = 0; i < CalNames.length; i++) {
            CalIds[i] = cursors.getInt(0);
            CalNames[i] = "Event" + cursors.getInt(0) + ": \nTitle: " + cursors.getString(1) + "\nDescription: " + cursors.getString(2) + "\nStart Date: " + new Date(cursors.getLong(3)) + "\nEnd Date : " + new Date(cursors.getLong(4)) + "\nLocation : " + cursors.getString(5);
            Log.d(TAG, CalNames[i]);

            Date mDate = new Date(cursors.getLong(3));
            Date nDate = new Date(cursors.getLong(4));

            long mTime = mDate.getTime();
            long lTime = nDate.getTime();
            if (stTime <= mTime && enTime >= lTime) {
                String eid = cursors.getString(0);

                int eID = Integer.parseInt(eid);

                String desc = cursors.getString(2);
                String title = cursors.getString(1);
            }
        }

        cursors.close();


    }

}
