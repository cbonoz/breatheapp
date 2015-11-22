package com.breatheplatform.common;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.TimeZone;

/**
 * Created by cbono on 11/10/15.
 */

public class UploadService extends IntentService {

    private static final String TAG = UploadService.class.getSimpleName();

        //define endpoints for server
        //target url will be URL + SENSOR

    private static final String BASE_URL = "http://www.breatheplatform.com";
    private static final String MEAS_API = "/api/measurement/add";
    private static final String GEO_MEAS_API = "/api/geomeasurement/add";

    //not good practice, will hide this key later
    private static final String API_KEY = "GWTgVdeNeVwsGqQHHhChfiPgDxxgXJzLoxUD0R64Gns";
    private static final String TEMP_EXT = "";//"temperature";
    private static final String SENSOR_DATA_FILE = "sensordata.txt";
    private static int UPLOAD_FREQUENCY = 300;//upload every 5 minutes


    private TimeZone tz;

    private Boolean upload;

    private SecurityUtils securityUtils;


    private int getSubjectId() {
        return -1; //will return a valid subject Id later
    }
    private final int SUBJECT_ID = getSubjectId();

    //loading a json object will create a large amount of temporary data overhead if the app is not
    // connected to the internet and the sensors are running. Going to use a file approach, where the
    // server will process the files
    private JSONObject jsonObj;

    public UploadService() {

        super("UploadService");
        upload=true;
        jsonObj = new JSONObject();

        //service for encryption of the data as it is written and uploaded
        securityUtils = new SecurityUtils();
        tz= new TimeZone() {
            @Override
            public int getOffset(int era, int year, int month, int day, int dayOfWeek, int timeOfDayMillis) {
                return 0;
            }

            @Override
            public int getRawOffset() {
                return 0;
            }

            @Override
            public boolean inDaylightTime(Date time) {
                return false;
            }

            @Override
            public void setRawOffset(int offsetMillis) {

            }

            @Override
            public boolean useDaylightTime() {
                return false;
            }
        };


    }




    @Override
    protected void onHandleIntent(Intent intent) {

        jsonObj = new JSONObject();
        String jsonSendString = jsonObj.toString();
        Log.d("sendJson called", jsonSendString);
        try {
            String targetUrl = BASE_URL + GEO_MEAS_API;
            URL url = new URL(targetUrl);
            Log.d("sending to",targetUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");


            OutputStream os = conn.getOutputStream();
            os.write(jsonSendString.getBytes());
            os.flush();

            if (conn.getResponseCode() != HttpURLConnection.HTTP_CREATED) {
                throw new RuntimeException("Failed : HTTP error code : "
                        + conn.getResponseCode());
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(
                    (conn.getInputStream())));

            String output;
            System.out.println("Output from Server .... \n");
            Log.d("output received", "begin stream");


            String line;
            while ((line = br.readLine()) != null) {
                Log.d("line",line);
            }
            br.close();
            conn.disconnect();


        } catch (Exception e) {

            e.printStackTrace();

        }

    }



    public void toggleUploading() {
        upload=!upload;

    }

    public Boolean isUploading() {
        return upload;
    }


    /*
    private void uploadEvent(Event e) {
        RequestParams params = new RequestParams();
        // Required
        params.put("SubjectId", e.getSubjectId());
        params.put("Type", e.getType());
        params.put("StartTime", e.getStartTime().substring(0, 23));
        params.put("TimeZone", e.getStartTime().substring(23, 28));
        params.put("LocalId", "" + e.getId());
        params.put("Key", Constants.KEY);
        if (e.getNotes() != null)
            params.put("Notes", "" + e.getNotes());

        postEventToAppEngine(params);
    }
    */
    private void sendJsonToServer(final String jsonSendString) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d("sendJson called", jsonSendString);

                try {
                    String targetUrl = BASE_URL + GEO_MEAS_API;
                    URL url = new URL(targetUrl);
                    Log.d("sending to",targetUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setDoOutput(true);
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");


                    OutputStream os = conn.getOutputStream();
                    os.write(jsonSendString.getBytes());
                    os.flush();

                    if (conn.getResponseCode() != HttpURLConnection.HTTP_CREATED) {
                        throw new RuntimeException("Failed : HTTP error code : "
                                + conn.getResponseCode());
                    }

                    BufferedReader br = new BufferedReader(new InputStreamReader(
                            (conn.getInputStream())));

                    String output;
                    System.out.println("Output from Server .... \n");
                    Log.d("output received", "begin stream");


                    String line;
                    while ((line = br.readLine()) != null) {
                        Log.d("line",line);
                    }
                    br.close();
                    conn.disconnect();


                } catch (Exception e) {

                    e.printStackTrace();

                }

            }
        });
        thread.start();
    }

    public void postDataToServer(String data) {
        JSONObject jsonObj = new JSONObject();
        try {
            jsonObj.put("key", API_KEY);
            jsonObj.put("subject_id", SUBJECT_ID);
            jsonObj.put("data",data);
            String input = jsonObj.toString();
            sendJsonToServer(input);
        } catch (Exception e) {

            Log.d("postData Failed",e.toString());
        }


    }


    public void postJsonToServer(JSONObject jsonObj) {
        try {
            jsonObj.put("key", API_KEY);
            //jsonObj.put("username",5);
            jsonObj.put("timezone", tz.getDefault());
            String input = jsonObj.toString();
            sendJsonToServer(input);
        } catch (Exception e) {
            //e.printStackTrace();
            Log.d("postJson Failed",e.toString());
        }
    }



}
