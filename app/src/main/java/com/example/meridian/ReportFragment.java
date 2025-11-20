package com.example.meridian;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReportFragment extends DialogFragment implements OnMapReadyCallback {

    private MapView mapView;
    private GoogleMap googleMap;
    private double latitude, longitude;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_report, container, false);

        TextView tvDetails = view.findViewById(R.id.tvDetails);
        Button btnClose = view.findViewById(R.id.btnClose);
        mapView = view.findViewById(R.id.mapView);

        String id = "N/A";
        String status = "Unknown";
        String severity = "Unknown";
        String formattedDate = "Unknown";
        String vehicleType = "Unknown";

        if (getArguments() != null) {
            id = getArguments().getString("id", "N/A");
            status = getArguments().getString("status", "Unknown");
            severity = getArguments().getString("severity", "Unknown");

            vehicleType = getArguments().getString("vehicle_type");
            if (vehicleType ==null || vehicleType.isEmpty() || vehicleType.equals("null")) {
                vehicleType = "Unknown"; //defensive check .
            }


            // ✅ Support BOTH long timestamp and string timestamp
            long tsMillis = getArguments().getLong("timestampMillis", 0L);
            String timestampString = getArguments().getString("timestamp", null);

            if (tsMillis > 0) {
                Date date = new Date(tsMillis);
                formattedDate = new SimpleDateFormat("EEEE, MMM dd yyyy", Locale.getDefault()).format(date);
            } else if (timestampString != null && !timestampString.isEmpty()) {
                try {
                    // Parse the default Firestore date string format
                    SimpleDateFormat parser = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH);
                    Date parsed = parser.parse(timestampString);
                    if (parsed != null) {
                        formattedDate = new SimpleDateFormat("EEEE, MMM dd yyyy", Locale.getDefault()).format(parsed);
                    }
                } catch (ParseException e) {
                    formattedDate = timestampString; // fallback
                }
            }
            latitude = getArguments().getDouble("latitude", 0d);
            longitude = getArguments().getDouble("longitude", 0d);
        }

        tvDetails.setText(String.format(
                Locale.getDefault(),
                "ID: %s\nStatus: %s\nSeverity: %s\nVehicle: %s\nReported on: %s",
                id, status, severity,vehicleType, formattedDate
        ));

        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        btnClose.setOnClickListener(v -> dismiss());
        return view;
    }

    @Override
    public void onMapReady(@NonNull GoogleMap gMap) {
        googleMap = gMap;
        LatLng potholeLocation = new LatLng(latitude, longitude);
        googleMap.addMarker(new MarkerOptions().position(potholeLocation).title("Pothole Location"));
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(potholeLocation, 16f));
        googleMap.getUiSettings().setAllGesturesEnabled(false);
    }

    @Override public void onResume() { super.onResume(); mapView.onResume(); }
    @Override public void onPause() { super.onPause(); mapView.onPause(); }
    @Override public void onDestroy() { super.onDestroy(); mapView.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            int width = (int) (requireContext().getResources().getDisplayMetrics().widthPixels * 0.9);
            getDialog().getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }
}
