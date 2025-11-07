package com.example.meridian;

import static java.lang.Math.abs;
import android.content.Context;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import android.content.Intent;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.GeoPoint;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.core.util.Pair;

public class RealTimeDataActivity extends AppCompatActivity {

    private static final String DEVICE_MAC_ADDRESS = "40:F5:20:57:9C:1E";
    private static final String DEVICE_NAME_MATCH = "Meridian Pothole Tracker";
    private static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter btAdapter;
    private BluetoothSocket btSocket;
    private InputStream inStr;
    private OutputStream outStr;

    private Thread readerThread;
    private final AtomicBoolean reading = new AtomicBoolean(false);
    private final StringBuilder lineBuf = new StringBuilder();
    private FloatingActionButton backButton;
    private TextView tvStatus, tvLat, tvLon, tvGz;
    private final Handler ui = new Handler(Looper.getMainLooper());

    // Stores az values for last 60 seconds
    private final ArrayList<Pair<Long, Double>> recentAz = new ArrayList<>();

    private final ActivityResultLauncher<String> btConnectPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) connectToPairedDevice();
                else {
                    Toast.makeText(this, "Bluetooth permission required", Toast.LENGTH_LONG).show();
                    setStatus("Permission required");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_real_time_data);

        tvStatus = findViewById(R.id.tvStatus);
        tvLat = findViewById(R.id.tvLat);
        tvLon = findViewById(R.id.tvLon);
        tvGz  = findViewById(R.id.tvGz);
        setStatus("Not connected");

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show();
            setStatus("Bluetooth not supported");
            finish();
            return;
        }
        if (!btAdapter.isEnabled()) {
            Toast.makeText(this, "Enable Bluetooth and reopen the app", Toast.LENGTH_LONG).show();
            setStatus("Bluetooth disabled");
            finish();
            return;
        }

        if (hasBtConnectPermission()) {
            connectToPairedDevice();
        } else {
            setStatus("Requesting permission…");
            requestBtConnectPermission();
        }

        backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }

    private boolean hasBtConnectPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBtConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            btConnectPermLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT);
        }
    }

    private void connectToPairedDevice() {
        new Thread(() -> {
            try {
                if (!hasBtConnectPermission()) {
                    ui.post(() -> setStatus("Permission required"));
                    return;
                }

                BluetoothDevice device = pickPairedDeviceSafe();
                if (device == null) {
                    ui.post(() -> setStatus("No paired device found"));
                    return;
                }

                try {
                    if (btAdapter.isDiscovering()) btAdapter.cancelDiscovery();
                } catch (SecurityException ignored) {}

                ui.post(() -> setStatus("Connecting to " + safeName(device) + "…"));

                btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                btSocket.connect();

                inStr  = btSocket.getInputStream();
                outStr = btSocket.getOutputStream();

                // Optional: send handshake if device expects it
                try {
                    outStr.write("START\n".getBytes());
                    outStr.flush();
                } catch (Exception e) {
                    Log.e("BT", "Failed to send handshake", e);
                }

                ui.post(() -> {
                    Toast.makeText(this, "Connected to " + safeName(device), Toast.LENGTH_SHORT).show();
                    setStatus("Connected");
                });

                startReader();

            } catch (SecurityException se) {
                ui.post(() -> setStatus("Permission denied"));
            } catch (Exception e) {
                ui.post(() -> setStatus("Connection failed"));
                Log.e("BT", "Connection exception", e);
                closeQuietly();
            }
        }).start();
    }

    @SuppressLint("MissingPermission")
    private BluetoothDevice pickPairedDeviceSafe() {
        if (!hasBtConnectPermission() || btAdapter == null) return null;

        Set<BluetoothDevice> bonded;
        try { bonded = btAdapter.getBondedDevices(); } catch (SecurityException se) { return null; }
        if (bonded == null || bonded.isEmpty()) return null;

        if (!TextUtils.isEmpty(DEVICE_MAC_ADDRESS)) {
            for (BluetoothDevice d : bonded)
                if (eqAddr(d.getAddress(), DEVICE_MAC_ADDRESS)) return d;
        }
        if (!TextUtils.isEmpty(DEVICE_NAME_MATCH)) {
            String needle = DEVICE_NAME_MATCH.toLowerCase();
            for (BluetoothDevice d : bonded) {
                try {
                    String name = d.getName();
                    if (name != null && name.toLowerCase().contains(needle)) return d;
                } catch (SecurityException ignored) {}
            }
        }
        return bonded.iterator().next();
    }

    private boolean eqAddr(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    private String safeName(BluetoothDevice d) {
        try {
            String n = (d == null) ? null : d.getName();
            return (n == null || n.isEmpty()) ? (d != null ? d.getAddress() : "device") : n;
        } catch (SecurityException se) {
            return (d != null ? d.getAddress() : "device");
        }
    }

    private void startReader() {
        if (inStr == null) return;
        reading.set(true);
        ui.post(() -> setStatus("Reading…"));

        readerThread = new Thread(() -> {
            byte[] buf = new byte[512];
            try {
                while (reading.get() && btSocket != null && btSocket.isConnected()) {
                    try {
                        int n = inStr.read(buf);
                        if (n > 0) {
                            appendAndProcess(new String(buf, 0, n));
                        } else if (n == -1) {
                            Log.w("BT", "Stream closed by device");
                            break;
                        } else {
                            Thread.sleep(50);
                        }
                    } catch (Exception e) {
                        Log.e("BT", "Reader exception", e);
                        break;
                    }
                }
            } finally {
                reading.set(false);
                closeQuietly();
                ui.post(() -> setStatus("Disconnected"));
            }
        }, "BT-Reader");
        readerThread.start();
    }

    private void appendAndProcess(String chunk) {
        synchronized (lineBuf) {
            lineBuf.append(chunk);
            int nl;
            while ((nl = lineBuf.indexOf("\n")) >= 0) {
                String line = lineBuf.substring(0, nl).trim();
                lineBuf.delete(0, nl + 1);
                handleLine(line);
            }
        }
    }

    private void handleLine(String line) {
        if (line.isEmpty()) return;

        String[] parts = line.split(",");
        Double lat = null, lon = null, az = null;

        for (String p : parts) {
            String[] kv = p.split("=", 2);
            if (kv.length != 2) continue;
            String key = kv[0].trim().toLowerCase();
            String val = kv[1].trim();
            try {
                switch (key) {
                    case "lat": lat = Double.valueOf(val); break;
                    case "lon": lon = Double.valueOf(val); break;
                    case "az":  az  = Double.valueOf(val); break;
                }

                if(abs(az) > 2){
                    Pothole pothole = new Pothole(lat, lon, az);
                    FirestoreManager.addPotholeReport(pothole.getLocation(), pothole.getSeverity(), pothole.getDetectedBy());
                    Toast.makeText(this, "Report sent (Hardware Report)", Toast.LENGTH_SHORT).show();
                }

            } catch (NumberFormatException ignored) {}
        }

        // Track max az over last 60 seconds
        double azMax60s = 0;
        long now = System.currentTimeMillis();
        if (az != null) recentAz.add(new Pair<>(now, az));

        Iterator<Pair<Long, Double>> iter = recentAz.iterator();
        while (iter.hasNext()) {
            Pair<Long, Double> p = iter.next();
            if (p.first < now - 60_000) iter.remove(); // remove older than 60s
            else if (p.second > azMax60s) azMax60s = p.second;
        }

        final Double fLat = lat;
        final Double fLon = lon;
        final Double fAz = az;
        final Double fAzMax60s = azMax60s;

        ui.post(() -> {
            if (fLat != null) tvLat.setText(String.format("Latitude %.6f", fLat));
            if (fLon != null) tvLon.setText(String.format("Longitude %.6f", fLon));

            StringBuilder sb = new StringBuilder();
            if (fAz != null) sb.append(String.format("Accel g %.2f", fAz));
            if (fAzMax60s != null && fAzMax60s > 0) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append(String.format("Az Max 60s %.2f", fAzMax60s));
            }

            tvGz.setText(sb.toString());
        });
    }

    private void closeQuietly() {
        try { if (inStr != null) inStr.close(); } catch (Exception ignored) {}
        try { if (outStr != null) outStr.close(); } catch (Exception ignored) {}
        try { if (btSocket != null) btSocket.close(); } catch (Exception ignored) {}
        inStr = null; outStr = null; btSocket = null;
    }

    private void setStatus(String s) {
        if (tvStatus != null) tvStatus.setText("Status: " + s);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        reading.set(false);
        if (readerThread != null) {
            try { readerThread.interrupt(); } catch (Exception ignored) {}
        }
        closeQuietly();
    }
}
