package com.example.meridian;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolylineOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class ClosureFragment extends DialogFragment implements OnMapReadyCallback {

    private MapView mapView;
    private GoogleMap googleMap;

    private ArrayList<Double> latList;
    private ArrayList<Double> lngList;
    private String description;
    private long endDate;
    private String closureId;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View view = inflater.inflate(R.layout.fragment_closure, container, false);

        TextView tvDetails = view.findViewById(R.id.tvClosureDetails);
        mapView = view.findViewById(R.id.mapViewClosure);

        // -------------------------
        // Extract arguments
        // -------------------------
        Bundle args = getArguments();
        if (args != null) {
            closureId = args.getString("id", "N/A");
            description = args.getString("description", "Road closure");
            endDate = args.getLong("endDate", 0);

            latList = (ArrayList<Double>) args.getSerializable("lats");
            lngList = (ArrayList<Double>) args.getSerializable("lngs");
        }

        // Format date
        String formattedEnd = "Unknown";
        if (endDate > 0) {
            Date d = new Date(endDate);
            formattedEnd = new SimpleDateFormat("EEEE, MMM dd yyyy", Locale.getDefault())
                    .format(d);
        }

        tvDetails.setText(String.format(
                "Closure ID: %s\nDescription: %s\nEnds on: %s",
                closureId, description, formattedEnd
        ));

        // Map setup
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        return view;
    }

    @Override
    public void onMapReady(@NonNull GoogleMap gMap) {
        googleMap = gMap;

        if (latList == null || lngList == null || latList.isEmpty()) return;

        ArrayList<LatLng> pts = new ArrayList<>();
        for (int i = 0; i < latList.size(); i++) {
            pts.add(new LatLng(latList.get(i), lngList.get(i)));
        }

        // Draw polyline
        googleMap.addPolyline(new PolylineOptions()
                .addAll(pts)
                .width(10f)
                .color(0xFFFF0000) // red
        );

        // Move camera to first point
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pts.get(0), 16f));
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
