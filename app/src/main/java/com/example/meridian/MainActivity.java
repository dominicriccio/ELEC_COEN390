package com.example.meridian;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;

import com.example.meridian.databinding.ActivityMainBinding;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;


import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final String TAG = "MainActivity";

    private float x1, x2, y1, y2;
    private GoogleMap mMap;
    private ActivityMainBinding binding;
    private String savedAddress;
    private FusedLocationProviderClient fusedLocationClient;
    private String selectedSeverity = "Medium"; // Default
    private Location currentLocation;
    private LatLng selectedLocation;  // comes from the map search

    private FirebaseFirestore db = FirestoreManager.getDb();

    private final ActivityResultLauncher<Intent> startAutocomplete = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Intent intent = result.getData();
                    if (intent != null) {
                        Place place = Autocomplete.getPlaceFromIntent(intent);
                        handlePlaceSelected(place);
                    }
                } else if (result.getResultCode() == RESULT_CANCELED) {
                    Log.i(TAG, "User canceled autocomplete");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initializePlacesApi();
        setupCustomSearch();
        setupMapFragment();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        RadioGroup severityGroup = findViewById(R.id.severityGroup);
        Button submitButton = findViewById(R.id.reportButton);

// Handle severity selection
        severityGroup.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton selectedButton = findViewById(checkedId);
            selectedSeverity = selectedButton.getText().toString();
        });


// Handle submit
        submitButton.setOnClickListener(v -> submitPotholeReport());
    }

    private void submitPotholeReport() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }

        // Prefer user-selected location first
        if (selectedLocation != null) {
            GeoPoint geoPoint = new GeoPoint(selectedLocation.latitude, selectedLocation.longitude);
            FirestoreManager.addPotholeReport(geoPoint, selectedSeverity, "AppUser");
            Toast.makeText(this, "Report sent (selected location)", Toast.LENGTH_SHORT).show();
        }
        // Otherwise, fallback to GPS location
        else {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            GeoPoint geoPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
                            FirestoreManager.addPotholeReport(geoPoint, selectedSeverity, "AppUser");
                            Toast.makeText(this, "Report sent (current location)", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Unable to get current location", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }


    private void initializePlacesApi() {
        if (Places.isInitialized()) return;

        try {
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(
                    getPackageName(), PackageManager.GET_META_DATA);
            String apiKey = appInfo.metaData.getString("com.google.android.geo.API_KEY");

            if (apiKey == null) {
                Log.e(TAG, "Google API key not found in AndroidManifest.xml");
                Toast.makeText(this, "Error: Missing API Key", Toast.LENGTH_LONG).show();
                return;
            }

            Places.initialize(getApplicationContext(), apiKey);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to load meta-data: " + e.getMessage());
            Toast.makeText(this, "Error initializing Places API", Toast.LENGTH_LONG).show();
        }
    }

    private void setupCustomSearch() {
        CardView searchBar = binding.addressSearchBar;
        searchBar.setOnClickListener(v -> {
            java.util.List<Place.Field> fields = Arrays.asList(
                    Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG);

            Intent intent = new Autocomplete.IntentBuilder(
                    AutocompleteActivityMode.OVERLAY, fields)
                    .build(this);
            startAutocomplete.launch(intent);
        });
    }

    private void handlePlaceSelected(Place place) {
        savedAddress = place.getAddress();
        LatLng location = place.getLatLng();

        String message = "Address selected: " + (savedAddress != null ? savedAddress : "Unknown");
        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
        hideKeyboard();

        if (mMap != null && location != null) {
            mMap.clear();
            mMap.addMarker(new MarkerOptions().position(location).title(place.getName()));
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 15));
        }

    }

    private void setupMapFragment() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);

        if (mapFragment == null) {
            mapFragment = SupportMapFragment.newInstance();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.mapFragment, mapFragment)
                    .commit();
        }

        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Enable location layer if permissions granted
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }

        mMap.setMyLocationEnabled(true);

        // Get current location using FusedLocationProviderClient
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        currentLocation = location; // store for later use
                        LatLng userLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15));
                        mMap.addMarker(new MarkerOptions().position(userLatLng).title("Your current location"));
                    }
                });

        // When the user taps the map, update the marker and save the selected location
        mMap.setOnMapClickListener(latLng -> {
            mMap.clear(); // remove old marker
            mMap.addMarker(new MarkerOptions().position(latLng).title("Selected Location"));
            selectedLocation = latLng; // store this for Firestore
            Toast.makeText(this, "Location selected!", Toast.LENGTH_SHORT).show();
        });
    }


    @Override
    public boolean onTouchEvent(MotionEvent touchEvent) {
        switch (touchEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                x1 = touchEvent.getX();
                y1 = touchEvent.getY();
                break;
            case MotionEvent.ACTION_UP:
                x2 = touchEvent.getX();
                y2 = touchEvent.getY();
                float deltaX = x2 - x1;
                if (deltaX > 200) {
                    Intent i = new Intent(MainActivity.this, MapsActivity.class);
                    startActivity(i);
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                    return true;
                }
                break;
        }
        return super.onTouchEvent(touchEvent);
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}
