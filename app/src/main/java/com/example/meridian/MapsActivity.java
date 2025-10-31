package com.example.meridian;

import androidx.activity.OnBackPressedCallback;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.example.meridian.databinding.ActivityMapsBinding;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.QueryDocumentSnapshot;


public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ActivityMapsBinding binding;
    private FloatingActionButton fabBack;
    FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        fabBack = findViewById(R.id.fab_back);

        // Set the click listener for the button
        fabBack.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                // Trigger the exact same logic as the system back press
                getOnBackPressedDispatcher().onBackPressed();
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Close the activity
                finish();
                // Apply reverse animation
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }
        });

    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        db.collection("potholes")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        GeoPoint geoPoint = doc.getGeoPoint("location");
                        String severity = doc.getString("severity");
                        String status = doc.getString("status");
                        String id = doc.getId();

                        if (geoPoint != null) {
                            LatLng potholeLocation = new LatLng(geoPoint.getLatitude(), geoPoint.getLongitude());

                            // Choose color based on severity
                            float markerColor;
                            if ("Severe".equalsIgnoreCase(severity)) {
                                markerColor = BitmapDescriptorFactory.HUE_RED;
                            } else if ("Moderate".equalsIgnoreCase(severity)) {
                                markerColor = BitmapDescriptorFactory.HUE_ORANGE;
                            } else {
                                markerColor = BitmapDescriptorFactory.HUE_YELLOW;
                            }

                            // Add marker
                            mMap.addMarker(new MarkerOptions()
                                    .position(potholeLocation)
                                    .title("Pothole (" + severity + ")")
                                    .snippet("Status: " + status)
                                    .icon(BitmapDescriptorFactory.defaultMarker(markerColor)));
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load potholes.", Toast.LENGTH_SHORT).show();
                });

        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        mMap.setBuildingsEnabled(false);
        mMap.setTrafficEnabled(false);
        mMap.setIndoorEnabled(false);
        mMap.getUiSettings().setMapToolbarEnabled(false);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(false);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }

        try {
            boolean success = mMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style));

            if (!success) {
                Log.e("MapStyle", "Style parsing failed.");
            }
        } catch (Resources.NotFoundException e) {
            Log.e("MapStyle", "Can't find style. Error: ", e);
        }


        mMap.setMyLocationEnabled(true);

        // Get current location using FusedLocationProviderClient
        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 16f));
                        mMap.addMarker(new MarkerOptions()
                                .position(currentLocation)
                                .title("You are here"));
                    } else {
                        Toast.makeText(this, "Unable to get current location", Toast.LENGTH_SHORT).show();
                    }
                });
    }

}