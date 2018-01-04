package org.chmoder.atwdpc;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Calendar;
import java.util.HashMap;

public class FindDeviceActivity extends AppCompatActivity {
    private static final String TAG = "FindDeviceActivity";
    private static final int MY_PERMISSIONS_REQUEST_READ_CONTACTS = 1;
    ChmoderBLEManager btm;
    /**
     * The Handler that gets information back from the BluetoothManager
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HandlerConstants.OPEN_CREATE_WIFI_DIALOG:
                    createSetupWIFIDialog();
                    break;
                case HandlerConstants.IS_CONNECTING_TO_WIFI:
                    Integer checkSSIDStartTime = msg.getData().getInt("checkSSIDStartTime");
                    TextView textView = (TextView)findViewById(R.id.status_text);

                    if(Calendar.getInstance().get(Calendar.SECOND) - checkSSIDStartTime < 15) {
                        btm.sendCommandReadSSIDCharacteristic();
                        textView.setText("checking if device is connected");
                    } else {
                        textView.setText("failed to authenticate WIFI");
                    }
                    break;
                case HandlerConstants.IS_CONNECTED_TO_WIFI:
                    Intent intent = getBaseContext().getPackageManager().getLaunchIntentForPackage(getBaseContext().getPackageName());
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    break;
            }
        }
    };
    private int permissionRetryCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_find_device);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        checkPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(btm != null) {
            btm.close();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_READ_CONTACTS: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Permission granted.");
                    startScan();
                    // permission was granted, yay! Do the
                } else {
                    Log.d(TAG, "Permission denied.");
                    showPermissionsExplanations();
                    permissionRetryCount++;
                }
            }
        }
    }

    public void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {

                Log.d(TAG, "SHOW EXPLANATION");
                showPermissionsExplanations();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_READ_CONTACTS);
            }
        } else {
            startScan();
        }
    }

    public void showPermissionsExplanations() {
        TextView textView = (TextView)findViewById(R.id.status_text);

        if(permissionRetryCount < 3) {
            textView.setText("Location permission is required for Bluetooth.");
        } else {
            textView.setText("Location permission is required for Bluetooth.  Please reinstall the app and grant Location permission.");
        }

        Button retryButton = (Button)findViewById(R.id.retry_permissions);
        retryButton.setVisibility(View.VISIBLE);
        retryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ActivityCompat.requestPermissions(FindDeviceActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_READ_CONTACTS);
            }
        });
    }

    public void startScan() {
        btm = new ChmoderBLEManager(this, mHandler);
    }

    public void createSetupWIFIDialog() {
        WIFIManager wifiManager = new WIFIManager(getApplicationContext());
        String thisDeviceSSID = wifiManager.getSSID();

        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.fragment_wifisetup, null);
        EditText ssidnameEditText = (EditText) dialogView.findViewById(R.id.ssidName);
        ssidnameEditText.setText(thisDeviceSSID.replaceAll("\"", ""));


        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setMessage("Set Up WIFI")
                .setTitle("Set Up Android Thing")
                .setView(dialogView)
                .setPositiveButton("connect", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        String ssidName = ((EditText) ((AlertDialog) dialog).findViewById(R.id.ssidName)).getText().toString();
                        String ssidPassword = ((EditText) ((AlertDialog) dialog).findViewById(R.id.ssidPassword)).getText().toString();
                        Log.d(TAG, ssidName);
                        Log.d(TAG, ssidPassword);

                        HashMap<String, String> command = (HashMap<String, String>) btm.newCommand("setup_wifi");
                        command.put("ssidName", ssidName);
                        command.put("ssidPassword", ssidPassword);
                        command.put("uid", "some UID");
                        btm.sendCommandSetupWIFICharacteristic(command);

                    }
                })
                .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        //
                    }
                });

        AlertDialog dialog = builder.create();
        dialog.show();
    }
}
