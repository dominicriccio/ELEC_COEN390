package com.example.meridian.settings;

import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.meridian.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private EditText etName, etSurname;
    private TextView etAddressDisplay;
    private TextInputEditText etPassword, etConfirmPassword;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private String newAddress = null;
    private GeoPoint newAddressGeo = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        MaterialToolbar toolbar = findViewById(R.id.edit_profile_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        etName = findViewById(R.id.et_edit_name);
        etSurname = findViewById(R.id.et_edit_surname);
        etAddressDisplay = findViewById(R.id.et_edit_address_display);
        etPassword = findViewById(R.id.et_edit_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        Button btnSave = findViewById(R.id.btn_save_profile);

        loadUserInfo();

        etAddressDisplay.setOnClickListener(v -> showAddressDialog());
        btnSave.setOnClickListener(v -> saveUserInfo());
    }

    private void loadUserInfo() {
        String userId = auth.getCurrentUser().getUid();
        DocumentReference ref = db.collection("users").document(userId);

        ref.get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                etName.setText(doc.getString("name"));
                etSurname.setText(doc.getString("surname"));

                String addr = doc.getString("address");
                if (addr != null)
                    etAddressDisplay.setText(addr);
            }
        });
    }

    private void showAddressDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final AlertDialog dialog = builder.create();

        dialog.setView(getLayoutInflater().inflate(R.layout.dialog_edit_address, null));

        dialog.setOnShowListener(d -> {
            EditText line = dialog.findViewById(R.id.dialog_address_line);
            EditText city = dialog.findViewById(R.id.dialog_city);
            EditText postal = dialog.findViewById(R.id.dialog_postal);

            Button btnCancel = dialog.findViewById(R.id.btn_cancel);
            Button btnSave = dialog.findViewById(R.id.btn_save_address);

            btnCancel.setOnClickListener(v -> dialog.dismiss());

            btnSave.setOnClickListener(v -> {

                String a = line.getText().toString();
                String c = city.getText().toString();
                String p = postal.getText().toString();

                if (a.isEmpty() || c.isEmpty() || p.isEmpty()) {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                    return;
                }

                String full = a + ", " + c + " " + p;

                Geocoder geocoder = new Geocoder(this, Locale.getDefault());

                try {
                    List<Address> result = geocoder.getFromLocationName(full, 1);

                    if (result == null || result.isEmpty()) {
                        Toast.makeText(this, "Address not found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Address addr = result.get(0);
                    newAddress = addr.getAddressLine(0);
                    newAddressGeo = new GeoPoint(addr.getLatitude(), addr.getLongitude());

                    etAddressDisplay.setText(newAddress);

                    dialog.dismiss();

                } catch (IOException e) {
                    Toast.makeText(this, "Geocoder failed", Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }

    private void saveUserInfo() {
        String userId = auth.getCurrentUser().getUid();
        DocumentReference ref = db.collection("users").document(userId);

        String name = etName.getText().toString();
        String surname = etSurname.getText().toString();
        String pass = etPassword.getText().toString();
        String confirm = etConfirmPassword.getText().toString();

        ref.update("name", name,
                "surname", surname
        );

        if (newAddress != null && newAddressGeo != null) {
            ref.update("address", newAddress,
                    "addressGeo", newAddressGeo
            );
        }

        if (!pass.isEmpty()) {
            if (!pass.equals(confirm)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            auth.getCurrentUser().updatePassword(pass)
                    .addOnSuccessListener(v -> Toast.makeText(this, "Password updated", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(v -> Toast.makeText(this, "Failed to update password", Toast.LENGTH_SHORT).show());
        }

        Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show();

        finish();
    }
}
