package com.breatheplatform.beta.mobile.shared;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;


/**
 * Created by cbono on 11/10/15.
 *
 * UploadService provides an interface for uploading Sensor Data to the GEO_MEASUREMENT API
 * on the breatheplatform website (currently supports single and multi-datum requests
 * Will spawn a new background thread for completing the send request via postJsonToServer
 *
 *  Pushed file writing to the background process thread to sensor service can be freed to focus on doing sensor related tasks
 */

public class UploadTask extends AsyncTask<String, Void, String> {

    private static final String TAG = UploadTask.class.getSimpleName();

    //define endpoints for server
    //target url will be URL + SENSOR

    private static final String CHARSET_UTF8 = "UTF-8";


    private URL url;
    OutputStreamWriter wr;
    BufferedReader rd;

    //private BufferedWriter sensorBuffer;
    private Boolean writing;
    private static String urlString;
    private static Context context;

    //private TimeZone tz;


    //loading a json object will create a large amount of temporary data overhead if the app is not
    // connected to the internet and the sensors are running. Going to use a file approach, where the
    // server will process the files


    public UploadTask(String urlName, Context c) {
        context = c;
        writing = false;
        urlString = urlName;

        try {
            url = new URL(urlString);// URL(MULTI_FULL_API);
        } catch (Exception e) {
            Log.e(TAG, "Error creating URL");
            e.printStackTrace();
        }
//
//        if (writing) {
//            sensorBuffer = writeNewFile(filePath);
//
//            if (sensorBuffer == null) {
//                Log.e(TAG, "Sensor buffer was not able to be opened");
//                Log.i(TAG, "Buffer writing OFF");
//                writing = false;
//            } else {
//                Log.i(TAG, "Buffer writing ON");
//            }
//        }


        //super("UploadService");
    }

    @Override
    protected String doInBackground(String... strings) {
        int statusCode = 0;
        InputStream is=null;
        OutputStream os=null;
        HttpURLConnection conn=null;
        String data = strings[0];
        String result = null;

        Log.i(url.toString(), "NOW Sending: "+data);

        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(10000 /*milliseconds*/);
            conn.setConnectTimeout(15000 /* milliseconds */);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(data.getBytes().length);
            conn.setRequestProperty("connection", "close"); // disables Keep Alive
            //conn.setChunkedStreamingMode(0);

            //make some HTTP header nicety
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");

            //open
            conn.connect();

            //setup send
            os = new BufferedOutputStream(conn.getOutputStream());

            os.write(data.getBytes());
            //clean up
            os.flush();

            os.close();

            statusCode = conn.getResponseCode();


            StringBuffer sb = new StringBuffer();


            try {
                is = new BufferedInputStream(conn.getInputStream());
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                String inputLine = "";
                while ((inputLine = br.readLine()) != null) {
                    sb.append(inputLine);
                }
                result = sb.toString();
            }    catch (Exception e) {
                Log.i(TAG, "Error reading InputStream");
                result = null;
            }
            finally {
                if (is != null) {
                    try {
                        is.close();
                    }
                    catch (Exception e) {
                        Log.i(TAG, "Error closing InputStream");
                        e.printStackTrace();
                    }
                }
            }

            Log.i(TAG, "Response: " + result);
            if (urlString.equals(ClientPaths.SUBJECT_API)) {
                Log.i(TAG, "Detected response was from SUBJECT_API");

                try {
                    String jsonString = result.substring(result.indexOf("{"),result.indexOf("}")+1);
                    final JSONObject resJson = new JSONObject(jsonString);
                    int newID = Integer.parseInt(resJson.getString("subject_id"));
                    Log.i(TAG, "Setting new SubjectID: " + newID);
                    ClientPaths.setSubjectID(newID);
                    return "Registered " + newID;
                }
                catch (Exception e) {
                    e.printStackTrace();
                    return statusCode + ": Server Error";
                }
            }


        }catch (Exception e) {
            Log.e(TAG, "Error in ASync Post Request: " + urlString);
            e.printStackTrace();
            return statusCode + ": Server Error";


        } finally {
            Log.i(TAG,"STATUSCODE: " + statusCode);
            if (conn!=null)
                conn.disconnect();


            //write data to file should unsuccessful post occur
//            if (writing && statusCode != 200) {
//                Log.d(TAG, "Writing sensor datum to file");
//                try {
//
//                    sensorBuffer.write(data);
//
//                } catch (Exception e) {
//                    //failed to write data
//                    Log.e(TAG, e.toString());
//                    e.printStackTrace();
//                    cleanUp();
//                }
//                writing = false;
//            }

        }

        return statusCode+"";
    }

    @Override
    protected void onPostExecute(String result){
        if (context!=null) {
            Toast toast = Toast.makeText(context, result, Toast.LENGTH_SHORT);
            toast.show();
        }
    }


    private BufferedWriter writeNewFile(String filePath) {

        BufferedWriter writer = null;


        try {
            Log.i(TAG, "Attempting to open file descriptor: " + filePath);// + File.separator + fileName);

            File file = new File(filePath);

            FileWriter fileWriter = new FileWriter(file);

            writer = new BufferedWriter(fileWriter);


        } catch (Exception e) {
            Log.e("error: writeNewFile",e.toString());
            e.printStackTrace();
            writing = false;
            return null;
        }

        Log.i(TAG, "OPENED " + filePath);
        return writer;

    }


    public void cleanUp() {
//        if (sensorBuffer != null) {
//            try {
//                sensorBuffer.flush();
//                sensorBuffer.close();
//
//            } catch (Exception e) {
//                Log.e(TAG, "Error closing sensorBuffer");
//                e.printStackTrace();
//            }
//        }

        Log.d(TAG, "cleanUp called");
    }

}