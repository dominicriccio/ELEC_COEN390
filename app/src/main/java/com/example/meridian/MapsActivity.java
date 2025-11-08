package com.example.meridian;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.example.meridian.databinding.ActivityMapsBinding;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.maps.android.heatmaps.HeatmapTileProvider;
import com.google.maps.android.heatmaps.WeightedLatLng;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ActivityMapsBinding binding;
    private FloatingActionButton fabBack;
    FirebaseFirestore db = FirebaseFirestore.getInstance();
    private FirebaseAuth mAuth;
    private boolean isAdmin = false;
    private List<Marker> potholeMarkers = new ArrayList<>();
    private HeatmapTileProvider heatmapProvider;
    private TileOverlay heatmapOverlay;
    private final List<WeightedLatLng> heatmapPoints = new ArrayList<>();

    // A simple data class to hold all pothole information
    private static class PotholeData {
        String id;
        String status;
        String severity;
        GeoPoint location;
        Timestamp timestamp;
        List<String> followers;

        PotholeData(String id, String status, String severity, GeoPoint location, Timestamp timestamp, List<String> followers) {
            this.id = id;
            this.status = status;
            this.severity = severity;
            this.location = location;
            this.timestamp = timestamp;
            if (this.followers != null) {
                this.followers = followers;
            }
            else {
                this.followers = new ArrayList<>();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        mAuth = FirebaseAuth.getInstance();
        checkUserRole();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        fabBack = findViewById(R.id.fab_back);
        fabBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }
        });
    }

    private void checkUserRole() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            db.collection("users").document(currentUser.getUid()).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String role = documentSnapshot.getString("role");
                            isAdmin = "admin".equals(role);
                        }
                    })
                    .addOnFailureListener(e -> Log.e("MapsActivity", "Failed to check user role", e));
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        loadPotholes();
        setupMapUI();
        focusOnUserLocation();

        mMap.setOnMarkerClickListener(marker -> {
            PotholeData potholeData = (PotholeData) marker.getTag();
            if (potholeData != null) {
                mMap.animateCamera(CameraUpdateFactory.newLatLng(marker.getPosition()));
                showPotholeDetailCard(potholeData, marker);
            }
            return true;
        });

        mMap.setOnMapClickListener(latLng -> {
            // Use the binding to access the card
            if (binding.potholeDetailCard.getVisibility() == View.VISIBLE) {
                binding.potholeDetailCard.setVisibility(View.GONE);
            }
        });

        mMap.setOnCameraIdleListener(() -> {
            float zoom = mMap.getCameraPosition().zoom;
            if (heatmapOverlay != null) {
                if (zoom < 14f) { // Zoomed out → show heatmap, hide pins
                    heatmapOverlay.setVisible(true);
                    for (Marker marker : potholeMarkers) marker.setVisible(false);
                } else { // Zoomed in → show pins, hide heatmap
                    heatmapOverlay.setVisible(false);
                    for (Marker marker : potholeMarkers) marker.setVisible(true);
                }
            }
        });
    }

    private void showPotholeDetailCard(PotholeData potholeData, Marker marker) {
        binding.infoWindowLayout.tvTitle.setText("Pothole - " + potholeData.id.substring(0, Math.min(6, potholeData.id.length())));
        binding.infoWindowLayout.tvStatus.setText("Status: " + potholeData.status);
        binding.infoWindowLayout.tvSeverity.setText("Severity: " + potholeData.severity);
        binding.infoWindowLayout.tvCoordinates.setText(String.format(Locale.getDefault(), "Coordinates: %.4f, %.4f",
                potholeData.location.getLatitude(), potholeData.location.getLongitude()));

        if (potholeData.timestamp != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("M/d/yyyy", Locale.getDefault());
            binding.infoWindowLayout.tvDate.setText("Date: " + sdf.format(potholeData.timestamp.toDate()));
        } else {
            binding.infoWindowLayout.tvDate.setText("Date: Not available");
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        ImageView starIcon = binding.infoWindowLayout.ivStar;

        binding.infoWindowLayout.btnModify.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        binding.infoWindowLayout.btnModify.setOnClickListener(v -> {
            showAdminOptionsDialog(potholeData.id, marker);
        });

        if (currentUser == null) {
            starIcon.setVisibility(View.GONE);
        } else {
            starIcon.setVisibility(View.VISIBLE);
            if (potholeMarkers != null && potholeData.followers.contains(currentUser.getUid())) {
                starIcon.setImageResource(R.drawable.ic_star_filled); // A filled star icon
            } else {
                db.collection("potholes").document(potholeData.id).get()
                        .addOnSuccessListener(doc -> {
                            List<String> followers = (List<String>) doc.get("followers");
                            if (followers != null && followers.contains(currentUser.getUid())) {
                                starIcon.setImageResource(R.drawable.ic_star_filled);
                                potholeData.followers = followers; // refresh local cache
                            } else {
                                starIcon.setImageResource(R.drawable.ic_star_border);
                            }
                        });
            }

            // The click listener now toggles the follow state
            starIcon.setOnClickListener(v -> {
                if (currentUser.getUid() != null) {
                    toggleFollowPothole(potholeData, currentUser.getUid(), starIcon, marker);
                }
            });

            binding.potholeDetailCard.setVisibility(View.VISIBLE);
        }
    }

    private void toggleFollowPothole(PotholeData potholeData, String userId, ImageView starIcon, Marker marker) {
        boolean isCurrentlyFollowing = potholeData.followers.contains(userId);

        if (isCurrentlyFollowing) {
            db.collection("potholes").document(potholeData.id)
                    .update("followers", FieldValue.arrayRemove(userId))
                    .addOnSuccessListener(aVoid -> {
                        potholeData.followers.remove(userId);
                        marker.setTag(potholeData);
                        starIcon.setImageResource(R.drawable.ic_star_border);
                        Toast.makeText(this, "Unfollowed", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to unfollow", Toast.LENGTH_SHORT).show());
        } else {
            db.collection("potholes").document(potholeData.id)
                    .update("followers", FieldValue.arrayUnion(userId))
                    .addOnSuccessListener(aVoid -> {
                        potholeData.followers.add(userId);
                        marker.setTag(potholeData);
                        starIcon.setImageResource(R.drawable.ic_star_filled);
                        Toast.makeText(this, "Following", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to follow", Toast.LENGTH_SHORT).show());
        }
    }


    private void loadPotholes() {
        db.collection("potholes").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        List<String> followers = (List<String>) doc.get("followers");

                        PotholeData potholeData = new PotholeData(
                                doc.getId(),
                                doc.getString("status"),
                                doc.getString("severity"),
                                doc.getGeoPoint("location"),
                                doc.getTimestamp("timestamp"),
                                followers
                        );

                        if (potholeData.location != null) {
                            LatLng potholeLocation = new LatLng(potholeData.location.getLatitude(), potholeData.location.getLongitude());
                            float markerColor;
                            double weight;

                            if ("Severe".equalsIgnoreCase(potholeData.severity)) {
                                markerColor = BitmapDescriptorFactory.HUE_RED;
                                weight = 3.0;
                            } else if ("Moderate".equalsIgnoreCase(potholeData.severity)) {
                                markerColor = BitmapDescriptorFactory.HUE_ORANGE;
                                weight = 2.0;
                            } else {
                                markerColor = BitmapDescriptorFactory.HUE_YELLOW;
                                weight = 1.0;
                            }

                            heatmapPoints.add(new WeightedLatLng(potholeLocation, weight));

                            Marker marker = mMap.addMarker(new MarkerOptions()
                                    .position(potholeLocation)
                                    .icon(BitmapDescriptorFactory.defaultMarker(markerColor)));
                            marker.setTag(potholeData);
                            marker.setVisible(false); // hide by default
                            potholeMarkers.add(marker);
                        }
                    }

                    if (!heatmapPoints.isEmpty()) {
                        if (heatmapProvider == null) {

                            // --- Custom Heatmap Gradient ---
                            // Yellow → Orange → Red → Dark Red
                            int[] colors = {
                                    android.graphics.Color.rgb(255, 255, 102),   // Yellow
                                    android.graphics.Color.rgb(255, 165, 0),     // Orange
                                    android.graphics.Color.rgb(255, 69, 0),      // Red-Orange
                                    android.graphics.Color.rgb(178, 34, 34)      // Dark Red
                            };

                            // Intensity points for the gradient (0 → 1)
                            float[] startPoints = {
                                    0.2f, 0.5f, 0.7f, 1.0f
                            };

                            // Create custom gradient
                            com.google.maps.android.heatmaps.Gradient gradient =
                                    new com.google.maps.android.heatmaps.Gradient(colors, startPoints);

                            // --- Build provider using custom gradient ---
                            heatmapProvider = new HeatmapTileProvider.Builder()
                                    .weightedData(heatmapPoints)
                                    .gradient(gradient)
                                    .radius(40)
                                    .build();

                            heatmapOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(heatmapProvider));
                            heatmapOverlay.setVisible(true);

                        } else {
                            heatmapProvider.setWeightedData(heatmapPoints);
                            heatmapOverlay.clearTileCache();
                        }
                    }

                    mMap.setOnCameraMoveListener(() -> {
                        float zoom = mMap.getCameraPosition().zoom;
                        updateHeatmapAndPinsVisibility(zoom);
                    });

                    mMap.setOnCameraIdleListener(() -> {
                        float zoom = mMap.getCameraPosition().zoom;
                        updateHeatmapAndPinsVisibility(zoom);
                    });

                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load potholes.", Toast.LENGTH_SHORT).show());
    }

    private void updateHeatmapAndPinsVisibility(float zoom) {
        if (heatmapOverlay == null) return;

        // You can tweak this cutoff zoom level
        float cutoffZoom = 13f;

        boolean showPins = zoom >= cutoffZoom;

        // Heatmap visible when zoomed out
        heatmapOverlay.setVisible(!showPins);

        // Show/hide pins based on zoom level
        for (Marker marker : potholeMarkers) {
            marker.setVisible(showPins);
        }
    }


    private void setupMapUI() {
        try {
            boolean success = mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style));
            if (!success) Log.e("MapStyle", "Style parsing failed.");
        } catch (Resources.NotFoundException e) {
            Log.e("MapStyle", "Can't find style. Error: ", e);
        }

        mMap.setBuildingsEnabled(false);
        mMap.getUiSettings().setMapToolbarEnabled(false);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(false);
    }

    private void focusOnUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }

        mMap.setMyLocationEnabled(true);
        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 16f));
                    } else {
                        Toast.makeText(this, "Unable to get current location", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showAdminOptionsDialog(final String potholeId, final Marker marker) {
        final CharSequence[] options = {"Change Status", "Delete Report", "Cancel"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Admin Options");
        builder.setItems(options, (dialog, item) -> {
            if (options[item].equals("Change Status")) {
                showStatusChangeDialog(potholeId, marker);
            } else if (options[item].equals("Delete Report")) {
                marker.remove();
                // Use binding to hide the card
                binding.potholeDetailCard.setVisibility(View.GONE);
                deletePotholeFromFirestore(potholeId);
            } else {
                dialog.dismiss();
            }
        });
        builder.show();
    }

    private void showStatusChangeDialog(final String potholeId, final Marker marker) {
        final CharSequence[] statuses = {"Reported", "In Progress", "Repaired"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select New Status");
        builder.setItems(statuses, (dialog, which) -> {
            String newStatus = statuses[which].toString();
            updatePotholeStatusInFirestore(potholeId, newStatus, marker);
        });
        builder.show();
    }

    private void updatePotholeStatusInFirestore(String potholeId, String newStatus, Marker marker) {
        db.collection("potholes").document(potholeId)
                .update("status", newStatus)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(MapsActivity.this, "Status updated to " + newStatus, Toast.LENGTH_SHORT).show();
                    PotholeData data = (PotholeData) marker.getTag();
                    if (data != null) {
                        data.status = newStatus;
                        marker.setTag(data);
                        // Use binding to update the text
                        binding.infoWindowLayout.tvStatus.setText("Status: " + newStatus);
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(MapsActivity.this, "Failed to update status", Toast.LENGTH_SHORT).show());
    }

    private void deletePotholeFromFirestore(String potholeId) {
        db.collection("potholes").document(potholeId)
                .delete()
                .addOnSuccessListener(aVoid -> Toast.makeText(MapsActivity.this, "Pothole report deleted", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(MapsActivity.this, "Failed to delete from database", Toast.LENGTH_SHORT).show());
    }
}
