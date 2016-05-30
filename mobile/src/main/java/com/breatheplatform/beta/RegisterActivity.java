package com.breatheplatform.beta;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
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
 * RegisterActivity
 * Created by cbono on 4/1/16.
 * Breathe Registration page logic
 *  Launches automatically if no user detected in the preferences (checked in the main listener service)
 *  Can also be launched directly from the "Breathe Registration" App Icon on the mobile device
 *
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

    // handler for received Intents for the "register" action event
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

    //ensure that the wearable has received the new user id before closing/saving
    @BackgroundThread
    @ReceiveMessages(Constants.REGISTERED_API)
    void onSubjectAcknowledged(String subject) {
        timeOutHandler.removeCallbacks(timeOutTask);
        Courier.stopReceiving(RegisterActivity.this);
        saveSubjectAndClose(subject);

    }

    //save the registered user in preferences and close
    private void saveSubjectAndClose(String subject) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("subject", subject);
        editor.apply();

        Toast.makeText(RegisterActivity.this, "Success - Registered Patient " + subject, Toast.LENGTH_LONG).show();
        Log.d(TAG, "Registration successful, closing activity. Subject now " + subject);//prefs.getString("subject", ""));

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

    private static String subject = "";

    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which){
                case DialogInterface.BUTTON_POSITIVE:
                    //Yes button clicked
                    setupOnLayoutInflated();
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    //No button clicked
                    Toast.makeText(RegisterActivity.this, "Keeping User id: " + subject, Toast.LENGTH_SHORT).show();
                    finish();
                    break;
            }
        }
    };

    //inflate the UI components and set listeners once the activity page loaded
    private void setupOnLayoutInflated() {
        setContentView(R.layout.register_activity);

        LocalBroadcastManager.getInstance(this).registerReceiver(mRegisterReceiver,
                new IntentFilter(Constants.REGISTER_EVENT));

        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        clinicText = (EditText) findViewById(R.id.clinicText);
        subjectText = (EditText) findViewById(R.id.subjectText);

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(Constants.MY_PREFS_NAME, MODE_PRIVATE);
        subject = prefs.getString("subject", "");

        //if subject exists, ask user if they want to re-register before loading the registration page
        if (!subject.equals("")) {
            Toast.makeText(this, "", Toast.LENGTH_LONG).show();
//            finish();
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Already registered with User id " + subject + ". Register Again?").setPositiveButton("Yes", dialogClickListener)
                    .setNegativeButton("No", dialogClickListener).show();
        } else {
            //subject is empty - start registration immediately
//            Toast.makeText(this, "Enter your credentials", Toast.LENGTH_LONG).show();
            setupOnLayoutInflated();

        }

    }
}
