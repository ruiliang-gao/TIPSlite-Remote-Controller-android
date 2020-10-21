package com.surflab.tipscontroller;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    /// TIPS Sensor components
    private SensorManager mSensorManager;
    private TIPSSensor mTIPSSensor;
    private String mSensordata;
    private SeekBar mVibrationBar;
    private int mVibrationStrength = 20;
    StringBuilder mStrBuilder = new StringBuilder(256);
    String TAG = "TIPSSensor_tag";

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

        public void send(){
            byte bytes [];
            try {
                bytes = mSensordata.getBytes("UTF-8");
                try {
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
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        public void onSensorChanged(SensorEvent event)
        {
            mStrBuilder.setLength(0);
            switch(event.sensor.getType())
            {
                case Sensor.TYPE_ROTATION_VECTOR:
                    // values[0]: x*sin(&#952/2) </li>
                    // values[1]: y*sin(&#952/2) </li>
                    // values[2]: z*sin(&#952/2) </li>
                    // values[3]: cos(&#952/2) </li>
                    // values[4]: estimated heading Accuracy (in radians) (-1 if unavailable)
                    mSensorQuat = new Quaternion(event.values[3], event.values[0], event.values[1], event.values[2]);
                    if(mCalibrated) {
                        mQuat = mCalibrateQuat.times(mSensorQuat);
                        /// Gesture detection
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
                        mRotVec[0].setText(String.format(Locale.ENGLISH,"%.3f", mQuat.x1));
                        mRotVec[1].setText(String.format(Locale.ENGLISH,"%.3f", mQuat.x2));
                        mRotVec[2].setText(String.format(Locale.ENGLISH,"%.3f", mQuat.x3));
                        mRotVec[3].setText(String.format(Locale.ENGLISH,"%.3f", mQuat.x0));
//                        mRotVec[0].setText(String.format(Locale.ENGLISH,"%.3f", event.values[0]));
//                        mRotVec[1].setText(String.format(Locale.ENGLISH,"%.3f", event.values[1]));
//                        mRotVec[2].setText(String.format(Locale.ENGLISH,"%.3f", event.values[2]));
//                        mRotVec[3].setText(String.format(Locale.ENGLISH,"%.3f", event.values[3]));
                    }

                    if(mStreamActive && mTouchView.isOnTouch) {
                        //Wrap up the sensor message in format (id, button, motion, orientation)
                        mMotionStateY = (float)mTouchView.motionY;
                        mMotionStateX = (float)mTouchView.motionX * (-1);
                        //mTouchView.resetMotionXY();
                        //mStrBuilder.append(String.format(Locale.ENGLISH, "%d, %.1f, %.1f, %.3f, %.3f, %.3f, %.3f", mButtonState, mMotionStateY, mMotionStateX, mQuat.x1, mQuat.x2, mQuat.x3, mQuat.x0));
                        mStrBuilder.append(String.format(Locale.ENGLISH, "%d, %d, %.3f, %.3f, %.3f, %.3f, %.3f, %.3f", mDeviceID, mButtonState, mMotionStateY, mMotionStateX, mQuat.x1, mQuat.x2, mQuat.x3, mQuat.x0));
                        mSensordata = mStrBuilder.toString();
                        if(mButtonState == 3) {
                            Log.d(TAG, " : (" +mSensordata + ")");
                            mButtonState = 0;//reset the calibrate button event
                        }
//                        Log.d(TAG, " : (" +mSensordata + ")");

                        // base case where skip value is 0 (also handles edge case where input is negative)
                        if (skipSendMax < 1) {
                            send();
                        }
                        // when curSkipSend is reset to 0, actually send the next data update
                        else if (curSkipSend == 0) {
                            send();
                            curSkipSend++;
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
    Button  mStartButton;
    Button mCalibrateButton;
    Button mActionButton;
    ToggleButton mToggleDevice; // for device id: checked = 1, unchecked = 2;
    private TextView mInstructionText;
    private TIPSTouchView mTouchView;

    /// Streaming status
    private static boolean mStreamActive = false;   //true = streaming
    private TextView mStreamStatus;

    private boolean startStreaming(){

        mToggleDevice.setEnabled(false);
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
        mToggleDevice.setEnabled(true);
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
        checkPermission(Manifest.permission.VIBRATE, VIBRATE_PERMISSION_CODE);
        checkPermission(Manifest.permission.ACCESS_NETWORK_STATE, ACCESS_NETWORK_STATE_PERMISSION_CODE);
        checkPermission(Manifest.permission.ACCESS_WIFI_STATE, ACCESS_WIFI_STATE_PERMISSION_CODE);
        //Keep the screen always on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //TODO (should change the thread structure later to move the thread to an non-GUI activity)
        //remove the restriction so that we can run network operations on the main thread
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        skipSendEditText = (EditText)findViewById(R.id.skip_send_delay);
        skipSendEditText.bringToFront();

        // gets secrets to perform remote tunneling process
        String username = getResources().getString(R.string.remoteTunnelUsername);
        String password = getResources().getString(R.string.remoteTunnelPassword);
        String devKey = getResources().getString(R.string.remoteTunnelDeveloperKey);
        String serverID = getResources().getString(R.string.remoteTunnelServerID);

        // tunnels into remote server network
        sofaServerTunnel = new RemoteTunnel(username, password, devKey, serverID);

        // Get an instance of the SensorManager
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mRotVec[0]  = (TextView) findViewById(R.id.textX);
        mRotVec[1]  = (TextView) findViewById(R.id.textY);
        mRotVec[2]  = (TextView) findViewById(R.id.textZ);
        mRotVec[3]  = (TextView) findViewById(R.id.textW);

        mTIPSSensor = new TIPSSensor();

        mStreamStatus = findViewById(R.id.textViewStreamState);
        mStreamStatus.setText(R.string.calibrate_status_init);
        mInstructionText = findViewById(R.id.textInstruction);
        mInstructionText.setTextColor(Color.DKGRAY);
        mStartButton = findViewById(R.id.start_button);
        mVibrationBar = findViewById(R.id.seekBarVibration);
        mVibrationBar.bringToFront();
        mVibrationBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mVibrationStrength = progress;
                mPrefs = getSharedPreferences("AppInfo", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = mPrefs.edit();
                editor.putInt("vibration_str", progress);
                editor.apply();
                Log.d(TAG, "onProgressChanged: "+mVibrationStrength);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        mStartButton.setVisibility(View.GONE);/// set invisible since we are using gesture based

        mStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mStreamActive) {
                    boolean started = startStreaming();
                    if(started)
                    {
                        mStreamActive = true;
                        mStartButton.setText(R.string.button_stop);
                    }
                }
                else{
                    mStreamActive = false;
                    mStartButton.setText(R.string.button_start);
                    stopStreaming();
                }
            }
        });

        mCalibrateButton = findViewById(R.id.calibrate_button);
        mCalibrateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTIPSSensor.mCalibrateQuat = mTIPSSensor.mSensorQuat.inverse();
                mTIPSSensor.mCalibrated = true;
                mButtonState = 3;
                mCalibrateButton.setText(R.string.button_recalibrate);
                mStreamStatus.setText(R.string.calibrate_status_done);
                Log.d(TAG, "calibrated...");
            }
        });

        //Toggle between device 1 and device 2
//        mToggleDevice = (ToggleButton) findViewById(R.id.toggleButton);
//        mToggleDevice.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if(mToggleDevice.isChecked()){
//                    mDeviceID = 1;
//                }
//                else
//                    mDeviceID = 2;
//            }
//        });


        mTouchView = (TIPSTouchView) findViewById(R.id.touch_view);
        mSysVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        /// retrieve the saved INFO from mPrefs
        mPrefs = getSharedPreferences("AppInfo", Context.MODE_PRIVATE);
        mVibrationStrength = mPrefs.getInt("vibration_str", 20);
        mVibrationBar.setProgress(mVibrationStrength);
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)){
            Log.d(TAG, "Volume Down...");
            mButtonState = 2;
        }
        return true;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)){
            Log.d(TAG, "Volume Down released...");
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
        Log.d(TAG, "onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mStreamActive) {
            stopStreaming();
        }
        Log.d(TAG, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(mStreamActive) {
            stopStreaming();
        }
        Log.d(TAG, "onStop");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}