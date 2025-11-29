package com.example.meridian.firebase;

import android.util.Log;

import com.example.meridian.items.Pothole;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class FirestoreManager {
    private static final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final String USERS_COLLECTION = "users";

    private FirestoreManager() {}

    public void addPothole(Pothole pothole) {
        db.collection("potholes").add(pothole);
    }

    public static FirebaseFirestore getDb() {
        return db;
    }

    public static CollectionReference getUsersCollection() { return db.collection(USERS_COLLECTION); }


    public static void addPotholeReport(GeoPoint location, String severity, String detectedBy) {
        Map<String, Object> pothole = new HashMap<>();
        pothole.put("location", location);
        pothole.put("severity", severity != null ? severity : "Unknown");
        pothole.put("status", "Reported");
        pothole.put("timestamp", new Timestamp(new Date()));
        pothole.put("detectedBy", detectedBy != null ? detectedBy : "AppUser");

        db.collection("potholes")
                .add(pothole)
                .addOnSuccessListener(ref ->
                        Log.d("FirestoreManager", "Pothole added successfully: " + ref.getId()))
                .addOnFailureListener(e ->
                        Log.e("FirestoreManager", "Error adding pothole: ", e));
    }

}
