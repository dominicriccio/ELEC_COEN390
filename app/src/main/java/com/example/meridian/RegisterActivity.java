package com.example.meridian;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText etName, etSurname, etAddress, etEmail, etPassword, etConfirmPassword;
    private Button btnRegister, btnBack;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();

        // Get references to all EditText fields
        etName = findViewById(R.id.et_register_name);
        etSurname = findViewById(R.id.et_register_surname);
        etAddress = findViewById(R.id.et_register_address); // Get reference to the new field
        etEmail = findViewById(R.id.et_register_email);
        etPassword = findViewById(R.id.et_register_password);
        etConfirmPassword = findViewById(R.id.et_register_confirm_password);
        btnRegister = findViewById(R.id.btn_register);
        progressBar = findViewById(R.id.register_progress_bar);
        btnBack = findViewById(R.id.btn_register_back);
        btnBack.setOnClickListener(v -> finish());
        btnRegister.setOnClickListener(v -> registerUser());
    }


    private void registerUser() {
        // Get text from all fields
        String name = etName.getText().toString().trim();
        String surname = etSurname.getText().toString().trim();
        String address = etAddress.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        // Validation checks...
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(surname) || TextUtils.isEmpty(address) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    // Hide progress bar regardless of outcome
                    progressBar.setVisibility(View.GONE);

                    if (task.isSuccessful()) {
                        Log.d("RegisterActivity", "createUserWithEmail:success");
                        FirebaseUser user = mAuth.getCurrentUser();

                        if (user != null) {
                            String userId = user.getUid();

                            Map<String, Object> userMap = new HashMap<>();
                            userMap.put("name", name);
                            userMap.put("surname", surname);
                            userMap.put("address", address);
                            userMap.put("email", email);

                            // **THE FIX**: Use your existing FirestoreManager.getUsersCollection()
                            FirestoreManager.getUsersCollection().document(userId)
                                    .set(userMap)
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d("RegisterActivity", "SUCCESS: User profile created for UID: " + userId);
                                        Toast.makeText(RegisterActivity.this, "Registration successful.", Toast.LENGTH_SHORT).show();
                                        finish(); // Go back to the previous screen
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e("RegisterActivity", "FAILURE: Error saving user details.", e);
                                        Toast.makeText(RegisterActivity.this, "Auth successful, but failed to save details.", Toast.LENGTH_SHORT).show();
                                    });
                        }
                    } else {
                        Log.w("RegisterActivity", "createUserWithEmail:failure", task.getException());
                        Toast.makeText(RegisterActivity.this, "Authentication failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

}
