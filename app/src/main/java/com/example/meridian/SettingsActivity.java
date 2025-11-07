package com.example.meridian;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class SettingsActivity extends AppCompatActivity {

    private EditText etName, etSurname, etAddress, etPassword;
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
        btnSave = findViewById(R.id.btn_save_profile);

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
            }
        }).addOnFailureListener(e ->
                Toast.makeText(this, "Failed to load info", Toast.LENGTH_SHORT).show());
    }

    private void saveUserInfo() {
        String userId = auth.getCurrentUser().getUid();
        DocumentReference userRef = db.collection("users").document(userId);

        userRef.update(
                "name", etName.getText().toString(),
                "surname", etSurname.getText().toString(),
                "address", etAddress.getText().toString()
        ).addOnSuccessListener(aVoid ->
                Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
        ).addOnFailureListener(e ->
                Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show());
    }
}
