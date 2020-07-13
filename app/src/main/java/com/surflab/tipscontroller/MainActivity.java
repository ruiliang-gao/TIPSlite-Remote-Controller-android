package com.surflab.tipscontroller;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
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

    public class TIPSSensor implements SensorEventListener
    {
        private Sensor mRotationVectorSensor;
        public Quaternion mSensorQuat; //original quaternion from the sensor
        public Quaternion mQuat; //quaternion in (w,x,y,z)
        public Quaternion mCalibrateQuat; //for Calibration purpose
        public boolean mCalibrated;
        public int mFlipDown; // 0 = up, 1 = down

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
                if(mPacket == null || mSocket == null)
                    return;
                mPacket.setData(bytes);
                mPacket.setLength(bytes.length);
                try {
                    mSocket.send(mPacket);
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
                        else if(mFlipDown == 1 && Math.abs(mQuat.x2) < 0.08){
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

                    if(mStreamActive){
                        //Wrap up the sensor message in format (id, button, motion, orientation)
                        mMotionStateY = mTouchView.motionY;
                        mMotionStateX = (float)-1.0 * mTouchView.motionX;
                        mTouchView.resetMotionXY();
                        //mStrBuilder.append(String.format(Locale.ENGLISH, "%d, %.1f, %.1f, %.3f, %.3f, %.3f, %.3f", mButtonState, mMotionStateY, mMotionStateX, mQuat.x1, mQuat.x2, mQuat.x3, mQuat.x0));
                        mStrBuilder.append(String.format(Locale.ENGLISH, "%d, %d, %.1f, %.1f, %.3f, %.3f, %.3f, %.3f", mDeviceID, mButtonState, mMotionStateY, mMotionStateX, mQuat.x1, mQuat.x2, mQuat.x3, mQuat.x0));
                        mSensordata = mStrBuilder.toString();
                        //Log.d("TIPS_Motion sensor", " : (" +mSensordata + ")");
                        send();
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
    public static DatagramSocket mSocket = null;
    public static DatagramPacket mPacket = null;
    public TIPSListenerThread mListener;
    private double[] mRot_Vec_Buffer 	= new double[3];
    private EditText mIP_Address;
    private EditText mPort;
    private Vibrator mSysVibrator;
    SharedPreferences mPrefs;

    class TIPSListenerThread extends Thread{
        @Override
        public void run() {
            super.run();
            Log.d("TIPS_Controller Listen", " constructed ... ");
            while(true){
                Log.d("TIPS_Controller Keep", " : listening..");
                byte[] buffer = new byte[128];
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                if(!mListeningActive){
                    Log.d("TIPS_Controller ", "Listener Disabled ");
                    return;
                }
                try {
                    mSocket.receive(response);
                } catch (IOException e) {
                    Log.d("TIPS_Controller ", "receive IOException");
                    e.printStackTrace();
                }
                String quote = new String(buffer, 0, response.getLength());
                Log.d("TIPS_Controller got", " :(" +quote + ")");
                if(quote.equals("contact")){
                    //int strength = Integer.parseInt(quote.substring(7));
                    //Log.d("TIPS_Controller ", " Vibrator starts with strength"+strength);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        mSysVibrator.vibrate(VibrationEffect.createOneShot(300, mVibrationStrength/*VibrationEffect.DEFAULT_AMPLITUDE*/));
                    } else { //deprecated in API 26
                        mSysVibrator.vibrate(300);
                    }
                }

            }
        }
    }

    /// Instrument ID & Button state & Motion state
    private int mDeviceID = 2;
    public int mButtonState = 0;
    private float mMotionStateY = 0;
    private float mMotionStateX = 0;
    Button  mStartButton;
    Button mCalibrateButton;
    Button mActionButton;
    ToggleButton mToggleDevice; // for device id: checked = 1, unchecked = 2;
    private TextView mInstructionText;

    private TIPSTouchView mTouchView;

    //check if wifi is connected
    private boolean isOnWifi() {
        ConnectivityManager conman = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        return conman.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnectedOrConnecting();
    }


    /// Streaming status

    private static boolean mStreamActive = false;   //true = streaming
    private static boolean mListeningActive = false;   //true = listening
    private TextView mStreamStatus;

    private boolean startStreaming(){

        boolean isOnWifi = isOnWifi();
        if(!isOnWifi)
        {
            ///AlertDialog
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.warning_wifi).setTitle("Warning");
            builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            AlertDialog mDialog = builder.create();
            mDialog.show();
            return false;
        }

        //check if ip address valid...
        InetAddress server_address = null;
        try {
            server_address = InetAddress.getByName(mIP_Address.getText().toString());
        } catch (UnknownHostException e) {
            CharSequence message = "Invalid IP Address";
            Toast toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
            toast.show();
            return false;
        }

        try {
            mSocket = new DatagramSocket();
            mSocket.setReuseAddress(true);
        } catch (SocketException e) {
            mSocket = null;
            Toast toast = Toast.makeText(this, R.string.error_unknown, Toast.LENGTH_SHORT);
            toast.show();
            return false;
        }

        byte[] buf = new byte[256];
        int port;
        try {
            port = Integer.parseInt(mPort.getText().toString());
            mPacket = new DatagramPacket(buf, buf.length, server_address, port);
        } catch (Exception e) {
            mSocket.close();
            mSocket = null;
            Toast toast = Toast.makeText(this, R.string.error_unknown, Toast.LENGTH_SHORT);
            toast.show();
            return false;
        }
        mListeningActive = true;
        mListener = new TIPSListenerThread();
        mListener.start();

        mIP_Address.setEnabled(false);
        mPort.setEnabled(false);
        mToggleDevice.setEnabled(false);
        //mStreamStatus.setText(getString(R.string.calibrate_status_done));
        mInstructionText.setTextColor(Color.LTGRAY);
        mCalibrateButton.setEnabled(false);
        mVibrationBar.setEnabled(false);
        return true;
    }

    private void stopStreaming() {
        mStreamStatus.setText(getString(R.string.calibrate_status_redo));
        mListeningActive = false;
        mIP_Address.setEnabled(true);
        mPort.setEnabled(true);
        mToggleDevice.setEnabled(true);
        mCalibrateButton.setEnabled(true);
        mVibrationBar.setEnabled(true);
        mInstructionText.setTextColor(Color.DKGRAY);
        if (mSocket != null)
            mSocket.close();
        mSocket = null;
        mPacket = null;
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

    //TODO (should change the thread structure later to move the thread to an non-GUI activity)
        //remove the restriction so that we can run network operations on the main thread
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // Get an instance of the SensorManager
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mRotVec[0]  = (TextView) findViewById(R.id.textX);
        mRotVec[1]  = (TextView) findViewById(R.id.textY);
        mRotVec[2]  = (TextView) findViewById(R.id.textZ);
        mRotVec[3]  = (TextView) findViewById(R.id.textW);

        mTIPSSensor = new TIPSSensor();

        mIP_Address = (EditText) findViewById(R.id.editTextTextIPAddress);
        mPort	    = (EditText) findViewById(R.id.editTextTextPortNum);
        mIP_Address.bringToFront();
        mPort.bringToFront();

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
                    /// store the ipAddr and port# to sharedPref
                    mPrefs = getSharedPreferences("AppInfo", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = mPrefs.edit();
                    editor.putString("ip", mIP_Address.getText().toString());
                    editor.putString("port", mPort.getText().toString());
                    editor.apply();
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
                mCalibrateButton.setText(R.string.button_recalibrate);
                mStreamStatus.setText(R.string.calibrate_status_done);
                Log.d(TAG, "calibrated...");
            }
        });

        mToggleDevice = (ToggleButton) findViewById(R.id.toggleButton);
        mToggleDevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                Log.d(TAG, "toogle button status : "+ mToggleDevice.isChecked());
                if(mToggleDevice.isChecked()){
                    mDeviceID = 1;
                }
                else
                    mDeviceID = 2;
            }
        });


        mTouchView = (TIPSTouchView) findViewById(R.id.touch_view);
        mSysVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        /// retrieve the saved INFO from mPrefs
        mPrefs = getSharedPreferences("AppInfo", Context.MODE_PRIVATE);
        String ip = mPrefs.getString("ip","");
        String port = mPrefs.getString("port","");
        mVibrationStrength = mPrefs.getInt("vibration_str", 20);
        mVibrationBar.setProgress(mVibrationStrength);
        if(! ip.isEmpty()) mIP_Address.setText(ip);
        if(! port.isEmpty())  mPort.setText(port);

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
        Log.d(TAG, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(mStreamActive) {
            //stopStreaming();
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