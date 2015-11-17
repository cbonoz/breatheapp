package com.breatheplatform.asthma;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.breatheplatform.asthma.fragments.HomeFragment;
import com.breatheplatform.asthma.fragments.SensorFragment;
import com.breatheplatform.common.BTDustSensor;
import com.breatheplatform.common.UploadService;
import com.breatheplatform.common.User;

import org.json.JSONObject;


/**
 * Created by cbono on 11/6/15.
 */

/*
Single Page (Activity) Asthma App
 - allows for psuedo-global listeners and uploaders that work regardless of 'where' the user is
 (indicated by the specific fragment page the user is currently viewing)
 - User accounts to be implemented, want to have a global account manager that will populate the user id
 whenever a request to send json data is made to the server
 - Sensor Data pulled in from all devices in the main activity, aggregated, and sent in the background
 via UploadService
 - Main Activity connects the sensors and the upload service together.
 On regular intervals


 */
public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private User user;
    private static final int DEFAULT_ID=5;


    private PhoneSensors sensors;
    private BTDustSensor dustSensor;
    private LocationService locationService;
    //private WatchListenerCommon watchlistener;

    private UploadService uploader;
    private JSONObject temp;

    private static final int SEND_FREQUENCY = 60*1000; //60sec interval of send


    //merge JSON data from each of the sensor data sources
    //sensors separated by provider (phone, watch, BT, or network)
    //in order to prevent race conditions
    //submit post request to server using the UploadService
    public void sendSensorData() {
        try {
            temp=new JSONObject();
            temp.put("Data", sensors.getJson());
            //temp.put("Data", btlistener.getJson());
            //temp.put("Data", loclistener.getJson());
            Log.d("sendSensorData",temp.toString());
            uploader.postJsonToServer(temp);
        } catch (Exception e) {
            //resetSensors();
            Log.d("Exception",e.toString());
            return;
        }



    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        sensors =new PhoneSensors(this);
        dustSensor = new BTDustSensor();
        locationService = new LocationService();

        //watchlistener = new WatchListenerCommon(this);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Data Sent to Server", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                sendSensorData();
            }
        });

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        user = new User(DEFAULT_ID);

        uploader = new UploadService();

        temp=new JSONObject();

    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        

        Fragment fragment = new HomeFragment();
        int id = item.getItemId();

        if (id == R.id.nav_home) {
            fragment = new HomeFragment();
        } else if (id == R.id.nav_sensors) {
            fragment = new SensorFragment();
        }
        /* else if (id == R.id.nav_sensorlist) {
            fragment = new SensorListFragment();
        }

        else if (id == R.id.nav_weather) {

        }
        else if (id == R.id.nav_history) {

        } else if (id == R.id.nav_gps) {

        } else if (id == R.id.nav_send) {

        }
        */
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.relativeLayout,fragment).commit();


        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }
}
