package com.breatheplatform.beta;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
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

        Button subjectButton = (Button) findViewById(R.id.subjectButton);
        subjectButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                EditText codeText = (EditText) findViewById(R.id.codeText);
                EditText subjectText = (EditText) findViewById(R.id.subjectText);

                if (acceptCredentials(codeText.getText().toString())) {
                    String subject_id = subjectText.getText().toString();
                    prefs = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("subject", subject_id);
                    editor.commit();

                    Toast.makeText(RegisterActivity.this, "Registered Patient", Toast.LENGTH_SHORT).show();

                    Log.d(TAG, "Starting Mobile Activity - subject now " + prefs.getString("subject", ""));

                    Intent intent = new Intent(getApplicationContext(), MobileActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                    startActivity(intent);

                    RegisterActivity.this.finish();
                } else {
                    Toast.makeText(RegisterActivity.this, "Clinician Code Not Valid", Toast.LENGTH_SHORT).show();
                }
            }
        });

    }
}
