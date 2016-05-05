package com.breatheplatform.beta;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.breatheplatform.beta.services.MobileUploadService;
import com.breatheplatform.beta.shared.Constants;

import org.json.JSONObject;

import me.denley.courier.BackgroundThread;
import me.denley.courier.Courier;
import me.denley.courier.ReceiveMessages;

/**
 * Created by cbono on 4/1/16.
 */
public class RegisterActivity extends Activity {


    private static final String TAG = "RegisterActivity";
    private SharedPreferences prefs = null;
//    private static final String CLINIC_CODE = "5555";

    private EditText clinicText;
    private EditText subjectText;
    private Handler timeOutHandler;
    private Runnable timeOutTask = new Runnable() {
        public void run() {
            Toast.makeText(RegisterActivity.this, "Check connection to Wearable and try again", Toast.LENGTH_SHORT).show();
        }
    };

    // handler for received Intents for the "my-event" event
    private BroadcastReceiver mRegisterReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Extract data included in the Intent

            Boolean success = intent.getBooleanExtra("success",false);
            String subject = intent.getStringExtra("subject_id");
            if (subject==null)
                subject="";
            Log.d("mRegisterReceiver", "success,subject " + success.toString()+","+subject);


            if (success) {
                Courier.startReceiving(RegisterActivity.this);
                Courier.deliverMessage(RegisterActivity.this, Constants.SUBJECT_API, subject);
                timeOutHandler = new Handler();
                timeOutHandler.postDelayed(timeOutTask, 5000);
            } else
                Toast.makeText(RegisterActivity.this, "Clinician Email / Subject Pair Not Valid", Toast.LENGTH_SHORT).show();
        }
    };


    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "RegisterActivity onDestroy");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mRegisterReceiver);
    }

    @BackgroundThread
    @ReceiveMessages(Constants.REGISTERED_API)
    void onSubjectAcknowledged(String subject) {
        timeOutHandler.removeCallbacks(timeOutTask);
        Courier.stopReceiving(RegisterActivity.this);
        saveSubjectAndClose(subject);

    }

    private void saveSubjectAndClose(String subject) {
        prefs = getSharedPreferences(Constants.MY_PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("subject", subject);
        editor.commit();

        Toast.makeText(RegisterActivity.this, "Success - Registered Patient " + subject, Toast.LENGTH_LONG).show();
        Log.d(TAG, "Registration successful, closing activity. Subject now " + prefs.getString("subject", ""));

        RegisterActivity.this.finish();
    }

    private String createRegisterRequest(String clinicEmail, String subject_id) {

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("timestamp",System.currentTimeMillis());
            jsonObject.put("email", clinicEmail);
            jsonObject.put("subject_id", subject_id);
            jsonObject.put("key", Constants.API_KEY);

            return jsonObject.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void createRegisterIntent(String data, String sub) {
        Intent i = new Intent(this, MobileUploadService.class);
        i.putExtra("url", Constants.REG_CHECK_API);
        i.putExtra("data", data);
        i.putExtra("subject_id",sub);
        startService(i);
        Log.d(TAG, "started register intent");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register_activity);

        LocalBroadcastManager.getInstance(this).registerReceiver(mRegisterReceiver,
                new IntentFilter(Constants.REGISTER_EVENT));

        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        clinicText = (EditText) findViewById(R.id.clinicText);
        subjectText = (EditText) findViewById(R.id.subjectText);

//        Courier.deliverMessage(RegisterActivity.this, Constants.SUBJECT_API, "2");
//        saveSubjectAndClose("2");

        Button subjectButton = (Button) findViewById(R.id.subjectButton);
        subjectButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                String sub = subjectText.getText().toString();
                String clinicEmail = clinicText.getText().toString();

                String data = createRegisterRequest(clinicEmail, sub);
                Log.d(TAG, "onClick data: " + data);

                if (data != null) {
                   createRegisterIntent(data, sub);
                } else
                    Log.e(TAG, "data is null - Error creating register request");
            }
        });

        //minimize the subject and code keyboard views on deselect
        subjectText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(subjectText.getWindowToken(), 0);
                }
            }
        });

        clinicText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(clinicText.getWindowToken(), 0);
                }
            }
        });


    }
}
