package com.example.meridian;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.meridian.databinding.ActivityMainBinding;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "MainActivity"; // Tag for logging

    private float x1, x2, y1, y2;
    private GoogleMap mMap;
    private ActivityMainBinding binding;
    private String savedAddress;

    private final ActivityResultLauncher<Intent> startAutocomplete = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Intent intent = result.getData();
                    if (intent != null) {
                        Place place = Autocomplete.getPlaceFromIntent(intent);
                        handlePlaceSelected(place); // Call a method to handle the result
                    }
                } else if (result.getResultCode() == RESULT_CANCELED) {
                    // The user canceled the operation.
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
    }

    private void initializePlacesApi() {
        if (Places.isInitialized()) {
            return; // Already initialized
        }

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
            Log.e(TAG, "Failed to load meta-data, NameNotFound: " + e.getMessage());
            Toast.makeText(this, "Error initializing Places API", Toast.LENGTH_LONG).show();
        }
    }

    private void setupCustomSearch() {
        // Find the CardView by its ID from the binding object
        CardView searchBar = binding.addressSearchBar;

        searchBar.setOnClickListener(v -> {
            // Define the fields to return
            java.util.List<Place.Field> fields = Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG);

            // Start the autocomplete intent.
            Intent intent = new Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
                    .build(this);
            startAutocomplete.launch(intent);
        });
    }
    private void handlePlaceSelected(Place place) {
        savedAddress = place.getAddress();
        LatLng location = place.getLatLng();

        String message = "Address selected: " + (savedAddress != null ? savedAddress : "Unknown");
        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
        hideKeyboard(); // This might not be necessary anymore but is good practice

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
                    .add(R.id.mapFragment, mapFragment) // Use .add() to put the fragment in the container
                    .commit();
        }
        // Always call getMapAsync to register the callback.
        mapFragment.getMapAsync(this);
    }


    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        LatLng defaultLocation = new LatLng(40.7128, -74.0060); // Default to New York City
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 15));

        mMap.setOnMapClickListener(latLng -> {
            mMap.clear();
            mMap.addMarker(new MarkerOptions().position(latLng).title("Selected Location"));
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

                // Check for a right swipe that is significant enough
                if (deltaX > 200) { // A swipe threshold of 200 pixels
                    Intent i = new Intent(MainActivity.this, MapsActivity.class);
                    startActivity(i);
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                    return true; // We handled the event
                }
                break;
        }
        return super.onTouchEvent(touchEvent);
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}
