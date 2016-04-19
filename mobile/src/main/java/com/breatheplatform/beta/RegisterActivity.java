package com.breatheplatform.beta;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
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

import me.denley.courier.Courier;

/**
 * Created by cbono on 4/1/16.
 */
public class RegisterActivity extends Activity {


    private static final String TAG = "RegisterActivity";
    private SharedPreferences prefs = null;
//    private static final String CLINIC_CODE = "5555";

    private EditText clinicText;
    private EditText subjectText;

    // handler for received Intents for the "my-event" event
    private BroadcastReceiver mRegisterReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Extract data included in the Intent

            String sub = intent.getStringExtra("response");

            Log.d("receiver", "Got register response, subject " + sub);

            if (!sub.equals(""))
                saveSubjectAndClose(sub);
            else
                Toast.makeText(RegisterActivity.this, "Clinician Code / Subject Not Valid", Toast.LENGTH_SHORT).show();
        }
    };


    private void saveSubjectAndClose(String subject) {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mRegisterReceiver);

        prefs = getSharedPreferences(Constants.MY_PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("subject", subject);
        editor.commit();

        RegisterActivity.this.finish();

        Courier.deliverMessage(RegisterActivity.this, Constants.SUBJECT_API, subject);
        Toast.makeText(RegisterActivity.this, "Success - Registered Patient " + subject, Toast.LENGTH_LONG).show();
        Log.d(TAG, "Starting Mobile Activity - subject now " + prefs.getString("subject", ""));
//                    startActivity(new Intent(RegisterActivity.this, MobileActivity.class));
    }

    private String createRegisterRequest(String clinicEmail, String subject_id) {

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("timestamp",System.currentTimeMillis());
            jsonObject.put("clinician_email", clinicEmail);
            jsonObject.put("subject_id", subject_id);
            jsonObject.put("api_key", Constants.API_KEY);

            return jsonObject.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LocalBroadcastManager.getInstance(this).registerReceiver(mRegisterReceiver,
                new IntentFilter(Constants.REGISTER_EVENT));

        setContentView(R.layout.register_activity);

        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        clinicText = (EditText) findViewById(R.id.clinicText);
        subjectText = (EditText) findViewById(R.id.subjectText);

        Button subjectButton = (Button) findViewById(R.id.subjectButton);
        subjectButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                String sub = subjectText.getText().toString();
                String clinicEmail = clinicText.getText().toString();

                String data = createRegisterRequest(clinicEmail,sub);

                if (data == null) {
                    Log.e(TAG, "data is null - Error creating register request");

                } else {
                    Intent i = new Intent(RegisterActivity.this, MobileUploadService.class);
                    i.putExtra("url", Constants.REGISTER_API);
                    i.putExtra("data", data);
                    startService(i);
                }


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
