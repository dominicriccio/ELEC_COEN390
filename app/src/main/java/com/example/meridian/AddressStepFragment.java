package com.example.meridian;

import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class AddressStepFragment extends Fragment {

    private EditText etAddressLine, etCity, etPostalCode;
    private Button btnNext;
    Button fabBack;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_register_address, container, false);

        etAddressLine = view.findViewById(R.id.et_address_line);
        etCity = view.findViewById(R.id.et_city);
        etPostalCode = view.findViewById(R.id.et_postal_code);
        btnNext = view.findViewById(R.id.btn_next_address);
        fabBack = view.findViewById(R.id.fab_back);
        fabBack.setOnClickListener(v -> {

            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });

        btnNext.setOnClickListener(v -> validateAndGeocode());

        return view;
    }

    private void validateAndGeocode() {
        String line = etAddressLine.getText().toString().trim();
        String city = etCity.getText().toString().trim();
        String postal = etPostalCode.getText().toString().trim();

        if (TextUtils.isEmpty(line) || TextUtils.isEmpty(city) || TextUtils.isEmpty(postal)) {
            Toast.makeText(getContext(), "Please fill all address fields", Toast.LENGTH_SHORT).show();
            return;
        }

        String full = line + ", " + city + " " + postal;

        Geocoder geocoder = new Geocoder(getContext(), Locale.getDefault());

        try {
            List<Address> results = geocoder.getFromLocationName(full, 1);

            if (results == null || results.isEmpty()) {
                Toast.makeText(getContext(), "Could not validate address", Toast.LENGTH_SHORT).show();
                return;
            }

            Address address = results.get(0);
            double lat = address.getLatitude();
            double lng = address.getLongitude();

            ((RegisterActivity) requireActivity())
                    .onAddressStepCompleted(line, city, postal, lat, lng);

        } catch (IOException e) {
            Toast.makeText(getContext(), "Geocoding failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
