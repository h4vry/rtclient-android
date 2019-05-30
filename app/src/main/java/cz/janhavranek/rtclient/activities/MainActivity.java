package cz.janhavranek.rtclient.activities;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import cz.janhavranek.rtclient.adapters.TraceListAdapter;
import cz.janhavranek.rtclient.runnables.DataReceiver;
import cz.janhavranek.rtclient.runnables.Parser;
import cz.janhavranek.rtclient.R;
import cz.janhavranek.rtclient.models.Trace;

import static cz.janhavranek.rtclient.Constants.PROXMARK_PRODUCT_ID;
import static cz.janhavranek.rtclient.Constants.PROXMARK_VENDOR_ID;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = MainActivity.class.getSimpleName();

    private static String ACTION_USB_PERMISSION = "cz.janhavranek.rtclient.USB_PERMISSION";

    private RecyclerView rvTracelist;
    private RecyclerView.Adapter adapter;
    private RecyclerView.LayoutManager layoutManager;

    private DataReceiver receiver;
    private Parser parser;
    private boolean snooping = false;

    private boolean autoscroll = true;

    private final List<Trace> traceList = new LinkedList<>();

    private BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getAction() != null &&
                    intent.getAction().equals(ACTION_USB_PERMISSION)) {
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    Log.i(TAG, "USB device permission granted");
                    // permision granted
                    snoop();
                } else {
                    Log.i(TAG, "USB device permission refused");
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        registerReceiver(usbPermissionReceiver, new IntentFilter(ACTION_USB_PERMISSION));

        rvTracelist = findViewById(R.id.rv_traceview);
        rvTracelist.setHasFixedSize(true);

        layoutManager = new LinearLayoutManager(this);
        rvTracelist.setLayoutManager(layoutManager);

        adapter = new TraceListAdapter(this, traceList);
        rvTracelist.setAdapter(adapter);
    }

    @Override
    protected void onStop() {
        super.onStop();

        try {
            unregisterReceiver(usbPermissionReceiver);
        } catch (IllegalArgumentException e) {
            Log.i(TAG, "onStop: Receiver already unregistered");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        // set default value of autoscroll to menu item
        MenuItem autoscrollItem = menu.findItem(R.id.menu_toggle_autoscroll);
        autoscrollItem.setChecked(autoscroll);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_start_snoop:
                if (!snooping) {
                    snoop();
                } else {
                    Toast.makeText(this, R.string.warn_snoop_in_progress, Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.menu_clear_list:
                traceList.clear();
                adapter.notifyDataSetChanged();
                break;
            case R.id.menu_toggle_autoscroll:
                autoscroll = !autoscroll;
                item.setChecked(autoscroll);
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    public void logTrace(Trace trace) {
        traceList.add(trace);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                adapter.notifyDataSetChanged();
                if (autoscroll) {
                    rvTracelist.scrollToPosition(traceList.size()-1);
                }
            }
        });
    }

    public void setSnooping(boolean snooping) {
        this.snooping = snooping;
    }

    public void snoop() {

        try {
            UsbSerialPort serial = connectToProxmark();

            if (serial == null) return;

            PipedInputStream in = new PipedInputStream();
            PipedOutputStream out = new PipedOutputStream(in);

            receiver = new DataReceiver(out, serial);
            parser = new Parser(in, receiver, this);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Threads are stopped by a signal from proxmark
        new Thread(receiver).start();
        new Thread(parser).start();

        Toast.makeText(this, getString(R.string.info_snoop_started), Toast.LENGTH_SHORT).show();
        snooping = true;
    }

    private UsbSerialPort connectToProxmark() throws IOException {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);

        UsbDevice proxmark = null;
        Map<String, UsbDevice> devices = manager.getDeviceList();
        for (UsbDevice d : devices.values()) {
            if (d.getVendorId() == PROXMARK_VENDOR_ID &&
                    d.getProductId() == PROXMARK_PRODUCT_ID) {
                Log.d(TAG, "connectToProxmark: found proxmark");
                proxmark = d;
            }
        }

        if (proxmark == null) {
            Toast.makeText(this, getString(R.string.err_proxmark_not_connected), Toast.LENGTH_SHORT).show();
            Log.d(TAG, "connectToProxmark: proxmark not found");
            return null;
        }

        if (!manager.hasPermission(proxmark)) {
            PendingIntent pi = PendingIntent.getBroadcast(this, 0,
                    new Intent(ACTION_USB_PERMISSION), 0);
            manager.requestPermission(proxmark, pi);
            return null;
        }

        Log.d(TAG, "connectToProxmark: Permission already granted");

        return openSerialPort(proxmark);
    }

    private UsbSerialPort openSerialPort(UsbDevice device) throws IOException {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);

        ProbeTable probeTable = new ProbeTable();
        probeTable.addProduct(PROXMARK_VENDOR_ID, PROXMARK_PRODUCT_ID, CdcAcmSerialDriver.class);
        UsbSerialProber prober = new UsbSerialProber(probeTable);

        UsbDeviceConnection connection = manager.openDevice(device);
        UsbSerialDriver driver = prober.probeDevice(device);

        if (connection == null) {
            Log.d(TAG, "openSerialPort: Connection is null, probably needs permission");
            return null;
        }

        if (driver == null) {
            Log.d(TAG, "openSerialPort: Driver is null");
            return null;
        }

        UsbSerialPort port = driver.getPorts().get(0);
        port.open(connection);
        port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

        return port;
    }

}
