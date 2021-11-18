package com.surflab.tipscontroller;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    ///newly added Bluetooth components
    private BluetoothAdapter mAdapter = null;
    private BluetoothSocket socketConnection = null;
    private TIPSBluetoothClient mConnectedThread = null;
    //MATCHED IN SPP ENGINE
    private static final UUID BT_MODULE_UUID = UUID.fromString("263beec5-a7fe-443a-a9ee-9bdfc5fc17a3");
    private static final String TAG_BT = "Bluetooth";

    //The BroadcastReceiver that listens for bluetooth broadcasts
    private final BroadcastReceiver _BTReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, @NotNull Intent intent)
        {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            String deviceName = device.getName();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                Log.d(TAG_BT, "ACTION_FOUND BT: " + deviceName);
            }
            else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                Log.d(TAG_BT, "ACTION_ACL_CONNECTED BT: " + deviceName);
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.d(TAG_BT, "ACTION_DISCOVERY_FINISHED BT: " + deviceName);
            }
            else if (BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(action)) {
                Log.d(TAG_BT, "ACTION_ACL_DISCONNECT_REQUESTED BT: " + deviceName);
            }
            else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                Log.d(TAG_BT, "ACTION_ACL_DISCONNECTED BT: " + deviceName);
            }
        }
    };

    private void ConnectToDeviceByAddress(String InAddress)
    {
        Log.d(TAG_BT, " - ConnectToDeviceByAddress: " + InAddress);

        BluetoothDevice device = mAdapter.getRemoteDevice(InAddress);

        Log.d(TAG_BT, " - address: " + InAddress);
        Log.d(TAG_BT, " - GUID: " + BT_MODULE_UUID);

        if(mConnectedThread != null)
        {
            mConnectedThread.stop();
            mConnectedThread = null;
        }
        if(socketConnection != null)
        {
            try
            {
                socketConnection.close();
                socketConnection = null;
            } catch (IOException e) {
            }
        }

        try {
            socketConnection = createBluetoothSocket(device);
            Log.d(TAG_BT, " - created socket: " + socketConnection);
        } catch (IOException e) {
            Log.d(TAG_BT, " - SOCKET FAILED");
            return;
        }
        try {
            Log.d(TAG_BT, " - called connect: " + socketConnection);
            socketConnection.connect();
        } catch (IOException e) {
            Log.d(TAG_BT, " - CONNECT FAILED");
            try {
                socketConnection.close();
            } catch (IOException e2) {
                //insert code to deal with this
            }
            return;
        }
        mConnectedThread = new TIPSBluetoothClient(socketConnection, null);
        mConnectedThread.start();
        Log.d(TAG_BT, " - CONNECTING");
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        return device.createRfcommSocketToServiceRecord(BT_MODULE_UUID);
    }

    /// TIPS Sensor components
    private SensorManager mSensorManager;
    private TIPSSensor mTIPSSensor;
    private String mSensordata;
    private SeekBar mVibrationBar;
    private int mVibrationStrength = 20;
    StringBuilder mStrBuilder = new StringBuilder(256);
    String TAG_TIPS = "TIPS ";

    public static TextView[] mRotVec = new TextView [4];
    private double[] mRotVecBuffer = new double[3];

    private RemoteTunnel sofaServerTunnel;
    private int skipSendMax = 0; // a const var which determines how many sends to skip
    private EditText skipSendEditText;

    public class TIPSSensor implements SensorEventListener
    {
        private Sensor mRotationVectorSensor;
        public Quaternion mSensorQuat; //original quaternion from the sensor
        public Quaternion mQuat; //quaternion in (w,x,y,z)
        public Quaternion mCalibrateQuat; //for Calibration purpose
        public boolean mCalibrated;
        public int mFlipDown; // 0 = up, 1 = down
        private int curSkipSend = 0; // used to limit the amount of data sent through the TCP conn.

        public TIPSSensor()
        {
            mRotationVectorSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            mQuat = new Quaternion(1.0, 0.0, 0.0, 0.0);
            mCalibrateQuat = new Quaternion(1.0, 0.0, 0.0, 0.0);
            mSensorQuat = new Quaternion(1.0, 0.0, 0.0, 0.0);
            mCalibrated = false;
            mFlipDown = 0; //init to be facing up
        }

        public void start()
        {
            // enable our sensor when the activity is resumed, ask for 20 ms (50Hz) updates.
            try {
                mSensorManager.registerListener(this, mRotationVectorSensor, 20000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void stop()
        {
            // make sure to turn our sensor off when the activity is paused
            mSensorManager.unregisterListener(this);
        }

        public void sendJsonBluetooth(){
            if(mConnectedThread != null){
                JSONObject jsonObj = new JSONObject();
                try {
                    jsonObj.put("data", mSensordata);
                    //String response = ...
                } catch ( JSONException e) {
                    e.printStackTrace();
                }
                mConnectedThread.write(jsonObj.toString());

                //check buzz messages and handle vibration
                if(mConnectedThread.buzzReceived){
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        mSysVibrator.vibrate(VibrationEffect.createOneShot(200, mVibrationStrength/*VibrationEffect.DEFAULT_AMPLITUDE*/));
                    } else { //deprecated in API 26
                        mSysVibrator.vibrate(200);
                    }
                    mConnectedThread.buzzReceived = false;
                }
            }

        }

        public void send(){
            byte bytes [];
            try {
                bytes = mSensordata.getBytes("UTF-8");
                String response = sofaServerTunnel.sendArr(bytes);

                // response indicates if we vibrate
                if(response.equals("contact")){
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        mSysVibrator.vibrate(VibrationEffect.createOneShot(300, mVibrationStrength/*VibrationEffect.DEFAULT_AMPLITUDE*/));
                    } else { //deprecated in API 26
                        mSysVibrator.vibrate(300);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void onSensorChanged(SensorEvent event)
        {
            mStrBuilder.setLength(0);
            switch(event.sensor.getType())
            {
                case Sensor.TYPE_ROTATION_VECTOR:
                    // data format as quat XYZW
                    // values[0]: x*sin(&#952/2)
                    // values[1]: y*sin(&#952/2)
                    // values[2]: z*sin(&#952/2)
                    // values[3]: cos(&#952/2)
                    // values[4]: estimated heading Accuracy (in radians) (-1 if unavailable)
                    mSensorQuat = new Quaternion(event.values[3], event.values[0], event.values[1], event.values[2]);
                    if(mCalibrated) {
                        mQuat = mCalibrateQuat.times(mSensorQuat);
                        /// Gesture detection： flipping down
                        if(mFlipDown == 0 && Math.abs(mQuat.x2) > 0.92){
                            mFlipDown = 1;
                            //Log.d("TIPS_Motion sensor", "Device flipped down");
                            if (!mStreamActive)
                                mStartButton.performClick();
                        }
                        else if(mFlipDown == 1 && Math.abs(mQuat.x2) < 0.1){
                            mFlipDown = 0;
                            //Log.d("TIPS_Motion sensor", "Device flipped up");
                            if (mStreamActive)
                                mStartButton.performClick();
                        }
                    }
                    else
                        mQuat = mSensorQuat;
                    if(mRotVec[0] != null)
                    {
                        // commented out as no need to display them at all.
//                        mRotVec[0].setText(String.format(Locale.ENGLISH,"%.3f", mQuat.x1));
//                        mRotVec[1].setText(String.format(Locale.ENGLISH,"%.3f", mQuat.x2));
//                        mRotVec[2].setText(String.format(Locale.ENGLISH,"%.3f", mQuat.x3));
//                        mRotVec[3].setText(String.format(Locale.ENGLISH,"%.3f", mQuat.x0));
                    }
                    //start streaming the sensor data
                    if(mStreamActive && mTouchView.isOnTouch) {
                        //Wrap up the sensor message in format (id, button, motion, orientation)
                        mMotionStateY = (float)mTouchView.motionY;
                        mMotionStateX = (float)mTouchView.motionX * (-1);
                        //mTouchView.resetMotionXY();
                        //mStrBuilder.append(String.format(Locale.ENGLISH, "%d, %.1f, %.1f, %.3f, %.3f, %.3f, %.3f", mButtonState, mMotionStateY, mMotionStateX, mQuat.x1, mQuat.x2, mQuat.x3, mQuat.x0));
                        mStrBuilder.append(String.format(Locale.ENGLISH, "%d, %d, %.3f, %.3f, %.3f, %.3f, %.3f, %.3f", mDeviceID, mButtonState, mMotionStateY, mMotionStateX, mQuat.x1, mQuat.x2, mQuat.x3, mQuat.x0));
                        mSensordata = mStrBuilder.toString();
//                        Log.d(TAG_TIPS, " : (" +mSensordata + ")");

                        // base case where skip value is 0 (also handles edge case where input is negative)
                        if (skipSendMax < 1) {
                            //send();
                            sendJsonBluetooth();
                            if(mButtonState == 3)
                                mButtonState = 0;//reset the calibrate button event
                        }
                        // when curSkipSend is reset to 0, actually send the next data update
                        else if (curSkipSend == 0) {
                            sendJsonBluetooth();//send();
                            curSkipSend++;
                            if(mButtonState == 3)
                                mButtonState = 0;//reset the calibrate button event
                        }
                        // if we have reached the predetermined limit, reset the counter
                        else if (curSkipSend >= skipSendMax) {
                            curSkipSend = 0;
                        }
                        // increment the counter while we have not yet hit the limit of send()'s to skip
                        else {
                            curSkipSend++;
                        }

                    }
                    break;

                default: {}
            }

        }

        public void onAccuracyChanged(Sensor arg0, int arg1)
        {
        }

    }

    /// UDP Client Components
    private double[] mRot_Vec_Buffer 	= new double[3];
    private Vibrator mSysVibrator;
    SharedPreferences mPrefs;

    /// Instrument ID & Button state & Motion state
    private int mDeviceID = 1;
    public int mButtonState = 0;
    private float mMotionStateY = 0;
    private float mMotionStateX = 0;
    private Spinner BTlist;
    Button  mStartButton;
    Button mCalibrateButton;
    Button mJoinButton;
    Button mJoinBackupButton;
    ToggleButton mToggleDevice; // for device id: checked = 1, unchecked = 2;
    private TextView mInstructionText;
    private TIPSTouchView mTouchView;
    private ImageView mInstructionImage;
    private ImageView mCenterImage;
    private ImageButton mQuestionButton;
    /// Streaming status
    private static boolean mStreamActive = false;   //true = streaming
    private TextView mStreamStatus;

    private boolean startStreaming(){

        //mToggleDevice.setEnabled(false);
        //mStreamStatus.setText(getString(R.string.calibrate_status_done));
        mInstructionText.setTextColor(Color.LTGRAY);
        mCalibrateButton.setEnabled(false);
        mVibrationBar.setEnabled(false);

        // checks edittext value and extracts skip number (edittext is set to only accept numbers)
        String skipStr = skipSendEditText.getText().toString();
        skipSendMax = Integer.parseInt(skipStr);

        return true;
    }

    private void stopStreaming() {
        mStreamStatus.setText(getString(R.string.calibrate_status_redo));
        //mToggleDevice.setEnabled(true);
        mCalibrateButton.setEnabled(true);
        mVibrationBar.setEnabled(true);
        mInstructionText.setTextColor(Color.DKGRAY);
    }

    /// to check and request permission.
    private static final int INTERNET_PERMISSION_CODE = 100;
    private static final int VIBRATE_PERMISSION_CODE = 101;
    private static final int ACCESS_NETWORK_STATE_PERMISSION_CODE = 102;
    private static final int ACCESS_WIFI_STATE_PERMISSION_CODE = 103;

    public void checkPermission(String permission, int requestCode)
    {
        if (ContextCompat.checkSelfPermission(MainActivity.this, permission)
                == PackageManager.PERMISSION_DENIED) {
            // Requesting the permission
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[] { permission },
                    requestCode);
        }
        else {
            Toast.makeText(MainActivity.this,
                    "Internet and Vibrate Permission granted",
                    Toast.LENGTH_SHORT)
                    .show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        android.permission.VIBRATE
//        android.permission.INTERNET
//        android.permission.ACCESS_NETWORK_STATE
//        android.permission.ACCESS_WIFI_STATE
        checkPermission(Manifest.permission.VIBRATE, VIBRATE_PERMISSION_CODE);
        checkPermission(Manifest.permission.ACCESS_NETWORK_STATE, ACCESS_NETWORK_STATE_PERMISSION_CODE);
        checkPermission(Manifest.permission.ACCESS_WIFI_STATE, ACCESS_WIFI_STATE_PERMISSION_CODE);
        //checkPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION, BLUETOOTH_PERMISSION_CODE);
        //Keep the screen always on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //TODO (should change the thread structure later to move the thread to an non-GUI activity)
        //remove the restriction so that we can run network operations on the main thread
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        skipSendEditText = (EditText)findViewById(R.id.skip_send_delay);
        skipSendEditText.bringToFront();

        // Get an instance of the SensorManager
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mRotVec[0]  = (TextView) findViewById(R.id.textX);
        mRotVec[1]  = (TextView) findViewById(R.id.textY);
        mRotVec[2]  = (TextView) findViewById(R.id.textZ);
        mRotVec[3]  = (TextView) findViewById(R.id.textW);

        mTIPSSensor = new TIPSSensor();

        mStreamStatus = findViewById(R.id.textViewStreamState);
        mStreamStatus.setTextColor(Color.RED);
        mStreamStatus.setText(R.string.calibrate_status_init);
        mInstructionText = findViewById(R.id.textInstruction);
        mInstructionText.setTextColor(Color.DKGRAY);
        mStartButton = findViewById(R.id.start_button);
        mJoinButton = findViewById(R.id.join_button);
        mJoinBackupButton = findViewById(R.id.join_backup_button);
        mVibrationBar = findViewById(R.id.seekBarVibration);
        mJoinButton.bringToFront();
        mVibrationBar.bringToFront();
        mVibrationBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mVibrationStrength = progress;
                ///update the saved info
                mPrefs = getSharedPreferences("AppInfo", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = mPrefs.edit();
                editor.putInt("vibration_str", progress);
                editor.apply();
                Log.d(TAG_TIPS, "onProgressChanged: "+mVibrationStrength);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        mStartButton.setVisibility(View.GONE);/// removed it since we are using gesture based
        mStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mStreamActive) {
                    boolean started = startStreaming();
                    if(started)
                    {
                        mStreamActive = true;
                        mStartButton.setText(R.string.button_stop);
                        mCenterImage.setVisibility(View.INVISIBLE);
                    }
                }
                else{
                    mStreamActive = false;
                    mStartButton.setText(R.string.button_start);
                    stopStreaming();
                }
            }
        });

        //Join Surflab PC Server -> replaced by bluetooth connect function for now
//        mJoinButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                mJoinButton.setText(R.string.button_joining);
//                mJoinButton.setEnabled(false);
//                //SETUP CONNECTION
//                // gets secrets to perform remote tunneling process
//                String username = getResources().getString(R.string.remoteTunnelUsername);
//                String password = getResources().getString(R.string.remoteTunnelPassword);
//                String devKey = getResources().getString(R.string.remoteTunnelDeveloperKey);
//                String serverID = getResources().getString(R.string.remoteTunnelServerID);
//                // tunnels into remote server network
//                sofaServerTunnel = new RemoteTunnel(username, password, devKey, serverID);
//                mJoinButton.setText(R.string.button_connected);
//                mJoinBackupButton.setEnabled(false);
//            }
//        });

        //Join Surflab Backup PC Server -- been set hidden as currently not used at all
        mJoinBackupButton.setVisibility(View.GONE);/// removed it since the button is confusing
        mJoinBackupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mJoinBackupButton.setText(R.string.button_joining);
                mJoinBackupButton.setEnabled(false);
                //SETUP CONNECTION
                // gets secrets to perform remote tunneling process
                String username = getResources().getString(R.string.remoteTunnelUsername);
                String password = getResources().getString(R.string.remoteTunnelPassword);
                String devKey = getResources().getString(R.string.remoteTunnelDeveloperKey);
                String serverID = getResources().getString(R.string.remoteBackupServerID);
                // tunnels into remote server network
                sofaServerTunnel = new RemoteTunnel(username, password, devKey, serverID);
                mJoinBackupButton.setText(R.string.button_connected);
                mJoinButton.setEnabled(false);
            }
        });

        //TIPSlite instruction image
        mInstructionImage = findViewById(R.id.instructionImage);
        //TIPSlite center TouchMe image
        mCenterImage = findViewById(R.id.centerImage);
        mCenterImage.setVisibility(View.INVISIBLE);

        //Calibrate Button
        mCalibrateButton = findViewById(R.id.calibrate_button);
        mCalibrateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTIPSSensor.mCalibrateQuat = mTIPSSensor.mSensorQuat.inverse();
                mTIPSSensor.mCalibrated = true;
                mButtonState = 3;
                mCalibrateButton.setText(R.string.button_recalibrate);
                mStreamStatus.setTextColor(Color.rgb(120,180,0));
                mStreamStatus.setText(R.string.calibrate_status_done);
                mInstructionImage.setVisibility(View.INVISIBLE);
                mCenterImage.setVisibility(View.VISIBLE);
                mCalibrateButton.setEnabled(false);
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mCalibrateButton.setEnabled(true);
                    }
                },2000);
                Log.d(TAG_TIPS, "calibrated...");

            }
        });

        //TIPSlite instruction button -> brings the image when clicked
        mQuestionButton = findViewById(R.id.questionButton);
        mQuestionButton.bringToFront();
        mQuestionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mInstructionImage.getVisibility() == View.VISIBLE)
                    mInstructionImage.setVisibility(View.INVISIBLE);
                else if(mInstructionImage.getVisibility() == View.INVISIBLE)
                    mInstructionImage.setVisibility(View.VISIBLE);
            }
        });
        mCalibrateButton.bringToFront();
        mTouchView = (TIPSTouchView) findViewById(R.id.touch_view);
        mSysVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        /// retrieve the saved INFO from mPrefs
        mPrefs = getSharedPreferences("AppInfo", Context.MODE_PRIVATE);
        mVibrationStrength = mPrefs.getInt("vibration_str", 20);
        mVibrationBar.setProgress(mVibrationStrength);

        ////Bluetooth
        //not sure what this filter is used for -- copied from SPP
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        this.registerReceiver(_BTReceiver, filter);
        //Add the bluetooth drop down list
        List<String> BTNames = new ArrayList<String>();
        BTlist = findViewById(R.id.spinner_bt);
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mAdapter == null)
        {
            BTNames.add("Your Device does not support bluetooth");
        }
        else if (!mAdapter.isEnabled())
        {
            BTNames.add("Switch on bluetooth and reopen this app");
            //Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            //startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        else {
            //BTNames.add("Choose Your Device Here");
            Set<BluetoothDevice> pairedDevices = mAdapter.getBondedDevices();

            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                if (device.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.COMPUTER) {
                    String deviceName = device.getName();
                    String deviceHardwareAddress = device.getAddress(); // MAC address

                    BTNames.add(deviceName + ":" + deviceHardwareAddress);
                    Log.d(TAG_BT, "Device Name: " + deviceName);
                    Log.d(TAG_BT, "Device Addr: " + deviceHardwareAddress);
                    Log.d(TAG_BT, "Device Class ID: " + device.getBluetoothClass().getMajorDeviceClass());
                }
            }

            /// retrieve the saved BT text from mPrefs
            mPrefs = getSharedPreferences("AppInfo", Context.MODE_PRIVATE);
            String savedBT = mPrefs.getString("bluetooth_text", "Choose Your Device Here");
            if(BTNames.contains(savedBT)) {
                BTNames.remove(savedBT);
                BTNames.add(0, savedBT);
            }
            else
                BTNames.add(0, "Choose Your Device Here");
        }
        ArrayAdapter<String> btDataAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item,
                BTNames);
        // attaching data adapter to spinner
        BTlist.setAdapter(btDataAdapter);
        //Bluetooth Connect
        mJoinButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mJoinButton.setText(R.string.button_joining);
                mJoinButton.setEnabled(false);
                String BT_text = BTlist.getSelectedItem().toString();

                ///update the saved info
                mPrefs = getSharedPreferences("AppInfo", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = mPrefs.edit();
                editor.putString("bluetooth_text", BT_text);
                editor.apply();
                Log.d(TAG_BT, "Saved Bluetooth text:" + BT_text);

                Log.d(TAG_BT, "************************");
                Log.d(TAG_BT, "************************");
                Log.d(TAG_BT, "************************");
                Log.d(TAG_BT, "Bluetooth CONNECT TO: " + BT_text);

                final String BT_address = BT_text.substring(BT_text.length() - 17);

                ConnectToDeviceByAddress(BT_address);
                mJoinButton.setText(R.string.button_connected);
                mJoinBackupButton.setEnabled(false);
            }
        });

    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)){
            Log.d(TAG_TIPS, "Volume Down...");
            mButtonState = 2;
        }
        return true;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)){
            Log.d(TAG_TIPS, "Volume Down released...");
            mButtonState = 0;
        }
        return true;
    }


    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mTIPSSensor.start();
        Log.d(TAG_TIPS, "onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mStreamActive) {
            stopStreaming();
        }
        Log.d(TAG_TIPS, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(mStreamActive) {
            stopStreaming();
        }
        Log.d(TAG_TIPS, "onStop");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG_TIPS, "onRestart");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG_TIPS, "onDestroy");
    }
}