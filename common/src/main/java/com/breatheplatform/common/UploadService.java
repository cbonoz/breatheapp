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

/**
 * Created by cbono on 11/10/15.
 */

public class UploadService extends IntentService {

    private static final String TAG = UploadService.class.getSimpleName();

        //define endpoints for server
        //target url will be URL + SENSOR

    private static final String BASE_URL = "https://www.breatheplatform.com/";
    private static final String MEAS_API = "/api/measurement/add";
    private static final String GEO_MEAS_API = "/api/geomeasurement/add";

    //not good practice, will hide this key later
    private static final String API_KEY = "GWTgVdeNeVwsGqQHHhChfiPgDxxgXJzLoxUD0R64Gns";
    private static final String TEMP_EXT = "";//"temperature";
    /*
    private static final String GAE_ADD_MEASUREMENT = "https://smoke-cessation-ky.appspot.com/measurements/add";
    private static final String GAE_ADD_RESPONSE = "https://smoke-cessation-ky.appspot.com/responses/add";
    private static final String GAE_ADD_EVENT = "https://smoke-cessation-ky.appspot.com/events/add";
    private static final String GAE_ADD_ATTACHMENT = "https://smoke-cessation-ky.appspot.com/attachments";
    private static final String GAE_ADD_STATUS = "https://smoke-cessation-ky.appspot.com/heartbeat/add";
    */


    private Boolean upload;

    //loading a json object will create a large amount of temporary data overhead if the app is not
    // connected to the internet and the sensors are running. Going to use a file approach, where the
    // server will process the files
    private JSONObject jsonObj;

    public UploadService() {

        super("UploadService");
        upload=false;
        jsonObj = new JSONObject();


    }


    @Override
    protected void onHandleIntent(Intent intent) {

       return;
    }


    public void toggleUpload() {
        upload=!upload;

    }

    public Boolean isUpload() {
        return upload;
    }



    /*

    private void uploadNewMesaurements() {
        WandaDb db = new WandaDb(this);
        db.open();
        List<Measurement> measurements = db.getNewMeasurements();
        db.close();

        for (Measurement m : measurements) {
            uploadMeasurement(m);
        }
    }

    private void uploadMeasurement(Measurement m) {
        RequestParams params = new RequestParams();

        // Required
        params.put("SubjectId",  m.getSubjectId());
        params.put("SensorId", m.getSensorId());
        params.put("StartTime", m.getStartTime().substring(0, 23));
        params.put("TimeZone", m.getStartTime().substring(23, 28));
        params.put("Value", "" + m.getValue());
        params.put("LocalId", "" + m.getId());
        params.put("Key", Constants.KEY);

        // Optional
        if (m.getEndTime() != null)
            params.put("EndTime", m.getEndTime());
        if (m.getSamplingRate() != null)
            params.put("SamplingRate", m.getSamplingRate());

        postMeasurementToAppEngine(params);
    }

    private void postMeasurementToAppEngine(RequestParams params) {
        Log.d(TAG, "postMeasurementToAppEngine()");

        String result = new SyncHttpClient() {

            @Override
            public String onRequestFailed(Throwable arg0, String arg1) {
                Log.v(TAG, "onRequestFailed()");
                Log.v(TAG, arg1);
                return null;
            }

        }.put(GAE_ADD_MEASUREMENT, params);

        if (result != null && result.contains("LocalId")) {
            Log.v(TAG, "onSuccess()");
            try {
                JSONObject json = new JSONObject(result);
                if (json.has("error")) {
                    Log.e(TAG, json.getString("error"));
                    return;
                }

                long localId = json.getLong("LocalId");
                long id = json.getLong("Id");
                WandaDb db = new WandaDb(UploadService.this);
                db.open();
                db.markMeasurementUploaded(id, localId);
                db.close();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void uploadNewResponses() {
        WandaDb db = new WandaDb(this);
        db.open();
        List<Response> responses = db.getNewResponses();
        db.close();

        for (Response r : responses) {
            uploadResponse(r);
        }
    }

    private void uploadResponse(Response r) {
        RequestParams params = new RequestParams();
        // Required
        params.put("SubjectId", r.getSubjectId());
        params.put("QuestionId", r.getQuestionId());
        params.put("QuestionnaireId", r.getQuestionnaireId());
        params.put("Answer", r.getAnswer());
        params.put("StartTime", r.getStartTime().substring(0, 23));
        params.put("TimeZone", r.getStartTime().substring(23, 28));
        params.put("LocalId", "" + r.getId());
        params.put("Key", Constants.KEY);

        postResponseToAppEngine(params);
    }

    private void postResponseToAppEngine(RequestParams params) {
        Log.d(TAG, "postResponseToAppEngine()");

        String result = new SyncHttpClient() {

            @Override
            public String onRequestFailed(Throwable arg0, String arg1) {
                Log.v(TAG, "onRequestFailed()");
                return null;
            }

        }.put(GAE_ADD_RESPONSE, params);

        if (result != null && result.contains("LocalId")) {
            Log.v(TAG, "onSuccess()");
            try {
                JSONObject json = new JSONObject(result);
                if (json.has("error")) {
                    Log.e(TAG, json.getString("error"));
                    return;
                }

                int id = json.getInt("LocalId");
                WandaDb db = new WandaDb(UploadService.this);
                db.open();
                db.markResponseUploaded(id);
                db.close();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void uploadNewEvents() {
        WandaDb db = new WandaDb(this);
        db.open();
        List<Event> events = db.getNewEvents();
        db.close();

        for (Event e : events) {
            uploadEvent(e);
        }
    }

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

    public void postJsonToServer(JSONObject jsonObj) {
        Log.d("postJsonToServer called", jsonObj.toString());

        try {
            String input = jsonObj.toString();
            URL url = new URL(BASE_URL+GEO_MEAS_API);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");



            OutputStream os = conn.getOutputStream();
            os.write(input.getBytes());
            os.flush();

            if (conn.getResponseCode() != HttpURLConnection.HTTP_CREATED) {
                throw new RuntimeException("Failed : HTTP error code : "
                        + conn.getResponseCode());
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(
                    (conn.getInputStream())));

            String output;
            System.out.println("Output from Server .... \n");
            Log.d("output received","begin stream");
            while ((output = br.readLine()) != null) {
                System.out.println(output);
                Log.d("output",output);
            }

            conn.disconnect();

        } catch (Exception e) {

            e.printStackTrace();

        }
    }
}
