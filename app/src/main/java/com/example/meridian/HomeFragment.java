package com.example.meridian;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.example.meridian.databinding.FragmentHomeBinding;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import com.google.firebase.Firebase;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class HomeFragment extends Fragment implements OnMapReadyCallback {

    private static final String TAG = "HomeFragment";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db; //Firestore instance
    private FragmentHomeBinding binding; // Use View Binding for the fragment
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LatLng selectedLocation;  // Location from map search or tap
    private String selectedSeverity = "Minor"; // Default severity

    // Launcher for the Places Autocomplete activity
    private final ActivityResultLauncher<Intent> startAutocomplete = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == getActivity().RESULT_OK) {
                    Intent intent = result.getData();
                    if (intent != null) {
                        Place place = Autocomplete.getPlaceFromIntent(intent);
                        handlePlaceSelected(place);
                    }
                } else if (result.getResultCode() == getActivity().RESULT_CANCELED) {
                    Log.i(TAG, "User canceled autocomplete");
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance(); //initialize

        initializePlacesApi();
        setupCustomSearch();
        setupMapFragment();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        // Setup UI listeners
        binding.severityGroup.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton selectedButton = view.findViewById(checkedId);
            selectedSeverity = selectedButton.getText().toString();
        });

        binding.reportButton.setOnClickListener(v -> submitPotholeReport());
    }

    private void submitPotholeReport() {

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if(currentUser == null) {
            Toast.makeText(getContext(), "You must be logged in to submit a report", Toast.LENGTH_SHORT).show();
            return;
        }
        String detectedBy = currentUser.getUid();

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Request permission from the user
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }

        db.collection("users").document(detectedBy).get().addOnSuccessListener(documentSnapshot -> {
            String vehicleType = "unknown";

            if (documentSnapshot.exists()) {
                if (documentSnapshot.contains("vehicle_type")) {
                    vehicleType = documentSnapshot.getString("vehicle_type");
                } else if (documentSnapshot.contains("vehicleType")) {
                    vehicleType = documentSnapshot.getString("vehicleType");
                }
            }
            if (vehicleType == null) vehicleType = "unknown";
            finalizeReport(detectedBy, vehicleType);
        }).addOnFailureListener(e -> {
            Toast.makeText(getContext(), "Failed to fetch user data", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error fetching user data", e);
            finalizeReport(detectedBy, "Unknown");
        });
    }

    private void finalizeReport(String detectedBy, String vehicleType){

        // 1. Prefer location selected on the map
        if (selectedLocation != null) {
            GeoPoint geoPoint = new GeoPoint(selectedLocation.latitude, selectedLocation.longitude);
            uploadToFirestore(geoPoint, selectedSeverity, detectedBy, vehicleType);
            Toast.makeText(getContext(), "Report sent (selected location)", Toast.LENGTH_SHORT).show();
            resetUI();
        }
        // 2. Fallback to current GPS location
        else {

            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                return;
            }
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            GeoPoint geoPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
                            uploadToFirestore(geoPoint, selectedSeverity, detectedBy, vehicleType);
                            Toast.makeText(getContext(), "Report sent (current location)", Toast.LENGTH_SHORT).show();
                            resetUI();
                        } else {
                            Toast.makeText(getContext(), "Unable to get current location. Please select a location on the map.", Toast.LENGTH_LONG).show();
                        }
                    });
        }
    }

    private void uploadToFirestore(GeoPoint location, String severity, String detectedBy, String vehicleType) {
        Map<String, Object> report = new HashMap<>();
        report.put("location", location);
        report.put("severity", severity);
        report.put("detectedBy", detectedBy);
        report.put("status", "Reported");
        report.put("timestamp", FieldValue.serverTimestamp());
        report.put("vehicle_type", vehicleType);

        db.collection("potholes")
                .add(report)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(getContext(), "Report sent!", Toast.LENGTH_SHORT).show();
                    resetUI();
                })
                .addOnFailureListener ( e -> {
                    Toast.makeText(getContext(), "Failed to send report: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error adding document", e);
                });
    }

    private void resetUI() {
        // Clear the map and reset the selected location
        if (mMap != null) {
            mMap.clear();
            // You might want to re-center the map on the user's current location here
        }
        selectedLocation = null;

        // Reset severity selection
        binding.severityGroup.check(R.id.lowSeverity); // Or whatever your default is
    }

    private void initializePlacesApi() {
        if (!isAdded() || Places.isInitialized()) return;

        try {
            ApplicationInfo appInfo = requireContext().getPackageManager().getApplicationInfo(
                    requireContext().getPackageName(), PackageManager.GET_META_DATA);
            String apiKey = appInfo.metaData.getString("com.google.android.geo.API_KEY");
            Places.initialize(requireContext().getApplicationContext(), apiKey);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to load meta-data: " + e.getMessage());
        }
    }

    private void setupCustomSearch() {
        binding.addressSearchBar.setOnClickListener(v -> {
            java.util.List<Place.Field> fields = Arrays.asList(
                    Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG);

            Intent intent = new Autocomplete.IntentBuilder(
                    AutocompleteActivityMode.OVERLAY, fields)
                    .build(requireContext());
            startAutocomplete.launch(intent);
        });
    }

    private void handlePlaceSelected(Place place) {
        LatLng location = place.getLatLng();
        selectedLocation = location; // Save the selected location for reporting

        Toast.makeText(getContext(), "Address selected: " + place.getAddress(), Toast.LENGTH_LONG).show();
        hideKeyboard();

        if (mMap != null && location != null) {
            mMap.clear();
            mMap.addMarker(new MarkerOptions().position(location).title(place.getName()));
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 15));
        }
    }

    private void setupMapFragment() {
        // Use getChildFragmentManager() for fragments inside fragments
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.mapFragment);

        if (mapFragment == null) {
            mapFragment = SupportMapFragment.newInstance();
            getChildFragmentManager().beginTransaction()
                    .add(R.id.mapFragment, mapFragment)
                    .commit();
        }
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }

        mMap.setMyLocationEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                LatLng userLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15));
            }
        });

        mMap.setOnMapClickListener(latLng -> {
            mMap.clear();
            mMap.addMarker(new MarkerOptions().position(latLng).title("Selected Location"));
            selectedLocation = latLng; // Store this for the report
            Toast.makeText(getContext(), "Location selected!", Toast.LENGTH_SHORT).show();
        });

        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        mMap.setBuildingsEnabled(false);
        mMap.setTrafficEnabled(false);
        mMap.setIndoorEnabled(false);
        mMap.getUiSettings().setMapToolbarEnabled(false);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(false);

        try {
            boolean success = mMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style));

            if (!success) {
                Log.e("MapStyle", "Style parsing failed.");
            }
        } catch (Resources.NotFoundException e) {
            Log.e("MapStyle", "Can't find style. Error: ", e);
        }

    }

    private void hideKeyboard() {
        View view = requireActivity().getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Important to avoid memory leaks
    }
}
