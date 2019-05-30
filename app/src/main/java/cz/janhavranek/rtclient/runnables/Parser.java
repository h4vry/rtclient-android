package cz.janhavranek.rtclient.runnables;

import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.PipedInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import cz.janhavranek.rtclient.Constants;
import cz.janhavranek.rtclient.activities.MainActivity;
import cz.janhavranek.rtclient.models.Trace;

public class Parser implements Runnable {

    private static final String TAG = Parser.class.getSimpleName();

    private static final int CMD_LEN = 2;

    private PipedInputStream in;
    private DataReceiver receiver;
    private MainActivity mainActivity;

    public Parser(PipedInputStream in, DataReceiver receiver, MainActivity mainActivity) {
        this.in = in;
        this.receiver = receiver;
        this.mainActivity = mainActivity;
    }

    @Override
    public void run() {
        boolean run = true;
        byte[] buffer = new byte[1024];

        while (run) {
            try {
                readBytes(buffer, CMD_LEN);
            } catch (IOException e) {
                e.printStackTrace();
            }

            int cmd = 0;
            cmd |= buffer[0] & 0xff;
            cmd |= (buffer[1] << 8) & 0xff00;

            switch (cmd) {
                case 0x0100: // debug msg
                    try {
                        int len = Constants.PROXMARK_USB_CMD_SIZE-2;
                        readBytes(buffer, len);

                        ByteBuffer bb = ByteBuffer.wrap(buffer, 6, 8);
                        bb.order(ByteOrder.LITTLE_ENDIAN);

                        final long msgLen = bb.getLong();
                        final String msg = new String(buffer, 30, (int) msgLen);

                        Log.d(TAG, "DBG: " + msg);
                        mainActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(mainActivity, msg, Toast.LENGTH_SHORT).show();
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;

                case 0xdead:
                    run = false;
                    break;

                case 0x0318: // realtime trace
                    try {
                        readBytes(buffer, 2);

                        int traceLen = 0;
                        traceLen |= buffer[0];
                        traceLen |= buffer[1] << 8;

                        readBytes(buffer, traceLen);

                        Trace trace = new Trace(buffer, traceLen);
                        mainActivity.logTrace(trace);
                    } catch (final IOException e) {
                        mainActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(mainActivity, "IO error: please reconnect Proxmark3: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                        run = false;
                    }

                    break;
            }
        }

        receiver.stop();
        mainActivity.setSnooping(false);

        Log.d(TAG, "run: Parser stopped");
    }

    private void readBytes(byte[] dest, int len) throws IOException {
        int read = 0;
        while (read < len) {
            read += in.read(dest, read, len-read);
        }
    }
}
