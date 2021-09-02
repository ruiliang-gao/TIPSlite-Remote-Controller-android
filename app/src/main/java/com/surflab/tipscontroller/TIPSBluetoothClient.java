package com.surflab.tipscontroller;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.SystemClock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public class TIPSBluetoothClient implements Runnable {
    private final BluetoothSocket mmSocket;
    private final InputStream mmInStream;
    private final OutputStream mmOutStream;
    public boolean buzzReceived = false; //true if received 'buzz' sent from SOFA

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread worker;

    private static final byte[] startMessage = new byte[] { 0, 1, 2, 3 };
    private static final byte[] endMessage = new byte[] { 3, 2, 1, 0 };

    public void start() {
        worker = new Thread(this);
        worker.start();
    }

    public void stop() {
        running.set(false);
    }

    public TIPSBluetoothClient(BluetoothSocket socket, Handler handler) {
        mmSocket = socket;
        InputStream tmpIn = null;
        OutputStream tmpOut = null;

        // Get the input and output streams, using temp objects because
        // member streams are final
        try {
            tmpIn = socket.getInputStream();
            tmpOut = socket.getOutputStream();
        } catch (IOException e) { }

        mmInStream = tmpIn;
        mmOutStream = tmpOut;
    }

    @Override
    public void run() {
        running.set(true);
        byte[] buffer = new byte[1024];  // buffer store for the stream
        int bytes; // bytes returned from read()
        // Keep listening to the InputStream until an exception occurs
        while (running.get()) {
            try {
                // Read from the InputStream
                bytes = mmInStream.available();
                //Log.d("TIPS bluetooth client ","check " + bytes);
                if(bytes != 0)
                {

                    SystemClock.sleep(10); //pause and wait for rest of data. Adjust this depending on your sending speed.
                    bytes = mmInStream.available(); // how many bytes are ready to be read?
                    bytes = mmInStream.read(buffer, 0, bytes); // record how many bytes we actually read
                    //Log.d("TIPS bluetooth client ","received "+ bytes);
                    buzzReceived = true;

                }
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }
    }

    /* Call this from the main activity to send data to the remote device */
    public void write(String input)
    {
        byte[] bytes = input.getBytes();           //converts entered String into bytes
        try {
            mmOutStream.write(startMessage);
            mmOutStream.write(bytes);
            mmOutStream.write(endMessage);
        } catch (IOException e) { }
    }

    /* Call this from the main activity to shutdown the connection */
    public void cancel()
    {
        try
        {
            mmSocket.close();
        } catch (IOException e) { }
    }
}
