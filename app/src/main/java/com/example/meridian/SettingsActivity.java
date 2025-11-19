package com.example.meridian;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.Spinner;
import android.text.TextUtils;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class SettingsActivity extends AppCompatActivity {

    private EditText etName, etSurname, etAddress, etPassword;
    private Spinner vehicleTypeSpinner; private ArrayAdapter<CharSequence> adapter;
    private Button btnSave;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        MaterialToolbar toolbar = findViewById(R.id.edit_profile_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Link UI elements
        etName = findViewById(R.id.et_edit_name);
        etSurname = findViewById(R.id.et_edit_surname);
        etAddress = findViewById(R.id.et_edit_address);
        etPassword = findViewById(R.id.et_edit_password);
        vehicleTypeSpinner = findViewById(R.id.vehicleType);
        btnSave = findViewById(R.id.btn_save_profile);

        adapter = ArrayAdapter.createFromResource(
                this,
                R.array.vehicle_type_array,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        vehicleTypeSpinner.setAdapter(adapter);

        loadUserInfo(); // Load user info from Firestore

        btnSave.setOnClickListener(v -> saveUserInfo());

    }

    private void loadUserInfo() {
        String userId = auth.getCurrentUser().getUid();
        DocumentReference userRef = db.collection("users").document(userId);

        userRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                etName.setText(documentSnapshot.getString("name"));
                etSurname.setText(documentSnapshot.getString("surname"));
                etAddress.setText(documentSnapshot.getString("address"));
                // Password won’t be loaded (for security reasons)

                String currentVehicleType = documentSnapshot.getString("vehicle_type");
                if (currentVehicleType != null) {
                    int spinnerPosition = adapter.getPosition(currentVehicleType);
                    vehicleTypeSpinner.setSelection(spinnerPosition);
                }
            }
        }).addOnFailureListener(e ->
                Toast.makeText(this, "Failed to load info", Toast.LENGTH_SHORT).show());
    }

    private void saveUserInfo() {
        String userId = auth.getCurrentUser().getUid();
        DocumentReference userRef = db.collection("users").document(userId);

        String selectedVehicleType = vehicleTypeSpinner.getSelectedItem().toString();
        if (selectedVehicleType.equals(getResources().getStringArray(R.array.vehicle_type_array)[0])) {
            Toast.makeText(this,"Please select a valid vehicle type", Toast.LENGTH_SHORT).show();
            return;
        }
        
        userRef.update(
                "name", etName.getText().toString(),
                "surname", etSurname.getText().toString(),
                "address", etAddress.getText().toString(),
                "vehicle_type", selectedVehicleType
        ).addOnSuccessListener(aVoid ->
                Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
        ).addOnFailureListener(e ->
                Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show());
    }
}
