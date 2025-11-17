package com.example.meridian;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Registration data (kept throughout steps)
    String firstName;
    String lastName;
    String email;
    String password;
    String role = "user";
    String addressLine;
    String city;
    String postalCode;

    double addressLat = -1;
    double addressLng = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.register_fragment_container, new NameStepFragment())
                    .commit();
        }
    }

    /* ----------------------- STEP CALLBACKS ----------------------- */

    public void onNameStepCompleted(@NonNull String first, @NonNull String last) {
        firstName = first.trim();
        lastName = last.trim();

        if (TextUtils.isEmpty(firstName) || TextUtils.isEmpty(lastName)) {
            Toast.makeText(this, "Please enter your first and last name.", Toast.LENGTH_SHORT).show();
            return;
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.register_fragment_container, new EmailStepFragment())
                .addToBackStack(null)
                .commit();
    }

    public void onEmailStepCompleted(@NonNull String emailInput) {
        email = emailInput.trim();

        if (TextUtils.isEmpty(email) ||
                !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email.", Toast.LENGTH_SHORT).show();
            return;
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.register_fragment_container, new PasswordStepFragment())
                .addToBackStack(null)
                .commit();
    }

    public void onPasswordStepCompleted(@NonNull String pass, @NonNull String confirmPass, @NonNull String selectedRole) {

        if (TextUtils.isEmpty(pass) || pass.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!pass.equals(confirmPass)) {
            Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show();
            return;
        }

        password = pass;
        role = selectedRole;   // <<▼ SAVE ROLE (user or admin)

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.register_fragment_container, new AddressStepFragment())
                .addToBackStack(null)
                .commit();
    }


    /**
     * AddressStepFragment sends text address AND geocoded coordinates.
     */
    public void onAddressStepCompleted(String line, String ct, String postal,
                                       double lat, double lng) {

        addressLine = line.trim();
        city = ct.trim();
        postalCode = postal.trim();
        addressLat = lat;
        addressLng = lng;

        if (TextUtils.isEmpty(addressLine) ||
                TextUtils.isEmpty(city) ||
                TextUtils.isEmpty(postalCode)) {
            Toast.makeText(this, "Please fill in your full address.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (lat == -1 || lng == -1) {
            Toast.makeText(this, "Address could not be validated.", Toast.LENGTH_LONG).show();
            return;
        }

        String fullAddress = addressLine + ", " + city + " " + postalCode;

        ReviewStepFragment reviewFragment = ReviewStepFragment.newInstance(
                firstName,
                lastName,
                email,
                fullAddress
        );

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.register_fragment_container, reviewFragment)
                .addToBackStack(null)
                .commit();
    }

    /* ----------------------- FINAL SUBMIT ------------------------ */

    public void onSubmitRegistration() {

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {

                    if (!task.isSuccessful()) {
                        Toast.makeText(this,
                                "Registration failed: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        Toast.makeText(this,
                                "Unexpected error: user is null.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    String fullAddress = addressLine + ", " + city + " " + postalCode;

                    // SAVE USER DATA INCLUDING GEOPOINT
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("name", firstName);
                    userData.put("surname", lastName);
                    userData.put("email", email);
                    userData.put("address", fullAddress);
                    userData.put("role", role);

                    // Add GeoPoint
                    userData.put("addressGeo", new GeoPoint(addressLat, addressLng));

                    db.collection("users")
                            .document(user.getUid())
                            .set(userData)
                            .addOnSuccessListener(aVoid -> {

                                // EMAIL VERIFICATION
                                user.sendEmailVerification()
                                        .addOnSuccessListener(unused -> {
                                            Toast.makeText(this,
                                                    "Account created! Verify your email before logging in.",
                                                    Toast.LENGTH_LONG).show();
                                            finish();
                                        })
                                        .addOnFailureListener(e ->
                                                Toast.makeText(this,
                                                        "Account created but verification email failed.",
                                                        Toast.LENGTH_LONG).show());
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this,
                                            "Failed to save user: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show());
                });
    }

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
        } else {
            super.onBackPressed();
        }
    }
}
