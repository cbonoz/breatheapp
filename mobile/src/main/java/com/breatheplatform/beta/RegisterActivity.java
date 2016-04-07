package com.breatheplatform.beta;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Created by cbono on 4/1/16.
 */
public class RegisterActivity extends Activity {


    private static final String TAG = "RegisterActivity";
    private SharedPreferences prefs = null;
    private static final String CLINIC_CODE = "5555";
    public static final String MY_PREFS_NAME = "SubjectFile";

    private EditText codeText;
    private EditText subjectText;


    public Boolean acceptCredentials(String pw) {
        String code = CLINIC_CODE;
        return code.equals(pw);
    }

    public void setSubjectAndClose(String subject_id) {
        prefs = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("subject", subject_id);
        editor.commit();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.register_activity);

        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        codeText = (EditText) findViewById(R.id.codeText);
        subjectText = (EditText) findViewById(R.id.subjectText);

        Button subjectButton = (Button) findViewById(R.id.subjectButton);
        subjectButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {


                if (acceptCredentials(codeText.getText().toString())) {
                    String subject_id = subjectText.getText().toString();
                    prefs = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("subject", subject_id);
                    editor.commit();

                    Toast.makeText(RegisterActivity.this, "Success - Registered Patient " + subject_id, Toast.LENGTH_LONG).show();

                    Log.d(TAG, "Starting Mobile Activity - subject now " + prefs.getString("subject", ""));

//                    Intent intent = new Intent(getApplicationContext(), MobileActivity.class);
//                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//
//                    startActivity(intent);
//
//                    RegisterActivity.this.finish();

                    startActivity(new Intent(RegisterActivity.this, MobileActivity.class));
                    finish();
                } else {
                    Toast.makeText(RegisterActivity.this, "Clinician Code Not Valid", Toast.LENGTH_SHORT).show();
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

        codeText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(codeText.getWindowToken(), 0);
                }
            }
        });


    }
}
