package cz.janhavranek.rtclient.runnables;

import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;
import java.io.PipedOutputStream;
import java.util.Arrays;

import cz.janhavranek.rtclient.Constants;

public class DataReceiver implements Runnable {

    private static final String TAG = DataReceiver.class.getSimpleName();

    private static final int TIMEOUT = 10;

    private PipedOutputStream out;
    private UsbSerialPort serial;
    private boolean run = true;

    public DataReceiver(PipedOutputStream out, UsbSerialPort serial) {
        this.out = out;
        this.serial = serial;
    }

    @Override
    public void run() {
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

        try {
            // send snoop command with param 0x04
            byte[] snoopCmd = new byte[Constants.PROXMARK_USB_CMD_SIZE];
            Arrays.fill(snoopCmd, (byte)0);
            snoopCmd[0] = (byte) 0x83;
            snoopCmd[1] = (byte) 0x03;
            snoopCmd[8] = (byte) 0x04;
            serial.write(snoopCmd, TIMEOUT);

            byte[] buffer = new byte[1024];
            int read;

            while (run) {
                read = serial.read(buffer, 0);
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                serial.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Log.d(TAG, "run: Receiver stopped");
    }

    public void stop() {
        run = false;
    }

}
