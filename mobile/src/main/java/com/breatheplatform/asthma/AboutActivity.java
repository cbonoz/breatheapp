package com.breatheplatform.asthma;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.breatheplatform.common.ClientPaths;
import com.breatheplatform.common.UploadTask;

import org.json.JSONObject;


public class AboutActivity extends Activity
{

    private static final String TAG = "AboutActivity";


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        if (savedInstanceState == null)
        {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, new AboutFragment())
                    .commit();
        }
        Log.i(TAG, "Called " + TAG + " onCreate");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

    }



    public class AboutFragment extends Fragment
    {


        public AboutFragment()
        {

        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState)
        {
            View rootView = inflater.inflate(R.layout.fragment_about, container, false);

//            ((TextView) rootView.findViewById(R.id.oss_content)).setText(Html.fromHtml(getResources().getString(R.string.about_open_source_content)));
//            ((TextView) rootView.findViewById(R.id.oss_content)).setMovementMethod(LinkMovementMethod.getInstance());
//
//            ((TextView) rootView.findViewById(R.id.about_github_content)).setText(Html.fromHtml(getResources().getString(R.string.about_github_content)));
//            ((TextView) rootView.findViewById(R.id.about_github_content)).setMovementMethod(LinkMovementMethod.getInstance());


            ((TextView) rootView.findViewById(R.id.dev_content)).setText(Html.fromHtml(getResources().getString(R.string.about_development_content)));
            ((TextView) rootView.findViewById(R.id.time_zone)).setText(Html.fromHtml("<b>Timezone: "+ClientPaths.getTimeZone() + "</b>"));
            ((TextView) rootView.findViewById(R.id.dev_content)).setMovementMethod(LinkMovementMethod.getInstance());

            final Button subjectButton = (Button) rootView.findViewById(R.id.subjectButton);
            final EditText subjectEditText   = (EditText) rootView.findViewById(R.id.subjectEditText);

            final Button idButton = (Button) rootView.findViewById(R.id.idButton);
            final EditText idEditText   = (EditText) rootView.findViewById(R.id.idEditText);

            idButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String sidString = idEditText.getText().toString();
                    try {
                        Integer sid = Integer.parseInt(sidString);

                        ClientPaths.setSubjectID(sid);
                        Toast.makeText(getActivity(), "Success SID: " + sid,Toast.LENGTH_LONG).show();
                    } catch (Exception e) { //parse error of integer in input field
                        e.printStackTrace();
                        Toast.makeText(getActivity(), "Please Enter Valid SID #",Toast.LENGTH_LONG).show();

                    }
                }
            });

            subjectButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Activity a = getActivity();
                    View view = a.getCurrentFocus();
                    if (view != null) {
                        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                    }
                    //String subName = subjectEditText.getText().toString();
                    //ClientPaths.setSubjectID(Integer.parseInt(subName));



                    JSONObject nameJson = new JSONObject();
                    try {
                        nameJson.put("key", ClientPaths.API_KEY);
                        String subName = subjectEditText.getText().toString();
                        if (subName.equals("")) {
                            return;
                        }

                        nameJson.put("name", subName);
                        UploadTask nameTask = new UploadTask(ClientPaths.SUBJECT_API, a);
                        nameTask.execute(nameJson.toString());
                        nameTask.cleanUp();


                        Log.i(TAG, "Sent " + nameJson.toString() + "\nto server for registration");

                    } catch (Exception e) {
                        e.printStackTrace();

                    }

                }
            });


            try
            {
                PackageInfo pInfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
                ((TextView) rootView.findViewById(R.id.header)).setText(getResources().getString(R.string.app_name) + " v." + pInfo.versionName);
            }
            catch (NameNotFoundException e)
            {
                e.printStackTrace();
            }


            return rootView;
        }
    }
}
