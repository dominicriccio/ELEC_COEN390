package com.example.meridian.realtime;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.example.meridian.items.Pothole;
import com.example.meridian.R;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.Priority;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
    private LocationCallback locationCallback;

    private SwitchMaterial gpsToggle;
    private FusedLocationProviderClient fusedLocationClient;
    private Location lastPhoneLocation;

    private Thread readerThread;
    private final AtomicBoolean reading = new AtomicBoolean(false);
    private final StringBuilder lineBuf = new StringBuilder();
    private FloatingActionButton backButton;
    private TextView tvStatus, tvLat, tvLon, tvGz;
    private final Handler ui = new Handler(Looper.getMainLooper());



    private final ArrayList<Pair<Long, Double>> recentAz = new ArrayList<>();


    private FirebaseFirestore db;


    private static final double REPORT_THRESHOLD_AZ = 1.25;
    private static final long MIN_REPORT_INTERVAL_MS = 5_000L;


    private final AtomicLong lastReportTime = new AtomicLong(0);
    private double lastReportLat = Double.NaN;
    private double lastReportLon = Double.NaN;
    private final AtomicBoolean reportingInProgress = new AtomicBoolean(false);

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


        db = FirebaseFirestore.getInstance();

        gpsToggle = findViewById(R.id.toggle_gps_source_report);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SharedPreferences prefs = getSharedPreferences("RealTimeSettings", MODE_PRIVATE);
        boolean usePhoneGps = prefs.getBoolean("usePhoneGps", false);
        gpsToggle.setChecked(usePhoneGps);

        gpsToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = getSharedPreferences("RealTimeSettings", MODE_PRIVATE).edit();
            editor.putBoolean("usePhoneGps", isChecked);
            editor.apply();


            if (isChecked) {
                startPhoneLocationUpdates();
            }
        });

        startPhoneLocationUpdates();

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
            } catch (NumberFormatException ignored) {}
        }


        double azMax60s = 0;
        long now = System.currentTimeMillis();
        if (az != null) recentAz.add(new Pair<>(now, az));

        Iterator<Pair<Long, Double>> iter = recentAz.iterator();
        while (iter.hasNext()) {
            Pair<Long, Double> p = iter.next();
            if (p.first < now - 60_000) iter.remove();
            else if (p.second > azMax60s) azMax60s = p.second;
        }

        final Double fLat = lat;
        final Double fLon = lon;
        final Double fAz = az;
        final Double fAzMax60s = azMax60s;

        ui.post(() -> {
            if (!gpsToggle.isChecked()) {
                if (fLat != null) {
                    tvLat.setText(String.format("Latitude %.6f", fLat));
                } else {
                    tvLat.setText("Latitude : --");
                }
                if (fLon != null) {
                    tvLon.setText(String.format("Longitude %.6f", fLon));
                } else {
                    tvLon.setText("Longitude : --");
                }
            }

            StringBuilder sb = new StringBuilder();
            if (fAz != null) sb.append(String.format("Accel g %.2f", fAz));
            if (fAzMax60s != null && fAzMax60s > 0) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append(String.format("Az Max 60s %.2f", fAzMax60s));
            }

            tvGz.setText(sb.toString());


            tryAutoReportIfNeeded(fAz, fLat, fLon);
        });
    }

    private void tryAutoReportIfNeeded(Double fAz, Double fHardwareLat, Double fHardwareLon) {

        if (fAz == null) return;
        if (reportingInProgress.get()) return;


        if (fAz < REPORT_THRESHOLD_AZ) return;


        long now = System.currentTimeMillis();
        if (now - lastReportTime.get() < MIN_REPORT_INTERVAL_MS) {
            Log.d("RT", "Skipping report: interval not passed");
            return;
        }

        Double reportLat, reportLon;
        String source;


        if (gpsToggle.isChecked()) {
            if (lastPhoneLocation == null) {

                startPhoneLocationUpdates();
                Toast.makeText(this, "Waiting for phone GPS signal...", Toast.LENGTH_SHORT).show();
                return;
            }
            reportLat = lastPhoneLocation.getLatitude();
            reportLon = lastPhoneLocation.getLongitude();
            source = "Phone";
        } else {

            if (fHardwareLat == null || fHardwareLon == null) {
                Log.w(TAG, "Skipping report: Hardware GPS selected but data is null.");
                return;
            }
            reportLat = fHardwareLat;
            reportLon = fHardwareLon;
            source = "Hardware";
        }



        reportingInProgress.set(true);
        lastReportTime.set(System.currentTimeMillis());
        setStatus("Reporting pothole…");
        Log.d(TAG, "Reporting pothole using " + source + " GPS.");

        Pothole pothole = new Pothole(reportLat, reportLon, fAz);


        db.collection("potholes")
                .add(pothole)
                .addOnSuccessListener(docRef -> {
                    String docId = docRef.getId();
                    docRef.update("id", docId);

                    reportingInProgress.set(false);
                    ui.post(() -> {
                        Toast.makeText(RealTimeDataActivity.this,
                                "Pothole auto-reported (severity: " + pothole.getSeverity() + ")",
                                Toast.LENGTH_LONG).show();
                        setStatus("Reported. Monitoring...");
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e("RT", "Failed to write pothole", e);
                    reportingInProgress.set(false);
                    ui.post(() -> {
                        setStatus("Report failed");
                        Toast.makeText(RealTimeDataActivity.this, "Auto-report failed", Toast.LENGTH_SHORT).show();
                    });
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


    private void startPhoneLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission not granted. Phone GPS will be unavailable for reporting.");

            return;
        }


        if (locationCallback == null) {
            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    for (Location location : locationResult.getLocations()) {
                        if (location != null) {
                            lastPhoneLocation = location;
                            Log.d(TAG, "Phone location updated: " + location.getLatitude());

                            if (gpsToggle.isChecked()) {
                                ui.post(() -> {
                                    tvLat.setText(String.format("Latitude %.6f", location.getLatitude()));
                                    tvLon.setText(String.format("Longitude %.6f", location.getLongitude()));
                                });
                            }
                        }
                    }
                }
            };
        }


        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
                .setMinUpdateIntervalMillis(1000)
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

}
