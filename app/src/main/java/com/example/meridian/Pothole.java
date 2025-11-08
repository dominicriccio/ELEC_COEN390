package com.example.meridian;

import static java.lang.Math.abs;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a pothole report in Firestore.
 * Fully compatible with Firestore automatic data mapping.
 */
public class Pothole {

    private String id;           // Firestore document ID (set manually)
    private String severity;     // "Low", "Medium", "High"
    private String status;       // e.g., "Reported", "Under Review"
    private String detectedBy;   // userId or "AppUser"
    private Timestamp timestamp; // Firestore timestamp
    private GeoPoint location;   // GPS coordinates
    private List<String> followers;

    // Required public no-argument constructor for Firestore
    public Pothole() { this.followers = new ArrayList<>(); }

    public Pothole(String id, String severity, String status,
                   String detectedBy, Timestamp timestamp, GeoPoint location) {
        this.id = id;
        this.severity = severity;
        this.status = status;
        this.detectedBy = detectedBy;
        this.timestamp = timestamp;
        this.location = location;
    }

    public Pothole(double lat, double lon, double az) {
        this.id = null; // ID will be set by Firestore

        double a = Math.abs(az);
        if (a >= 4) {
            this.severity = "Severe";
        } else if (a < 4 && a >= 2) {
            this.severity = "Moderate";
        } else if (a < 2 && a >= 1.25) {
            this.severity = "Minor";
        } else {
            this.severity = "Negligible";
        }

        this.status = "Reported";
        this.detectedBy = "AppUser";
        this.timestamp = Timestamp.now();
        this.location = new GeoPoint(lat, lon);
    }


    // -----------------------
    // Firestore field mapping
    // -----------------------

    public String getId() {
        return id;
    }

    // This setter is used when the document ID is added manually in FeedFragment
    public void setId(String id) {
        this.id = id;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDetectedBy() {
        return detectedBy;
    }

    public void setDetectedBy(String detectedBy) {
        this.detectedBy = detectedBy;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    public GeoPoint getLocation() {
        return location;
    }

    public void setLocation(GeoPoint location) {
        this.location = location;
    }

    public List<String> getFollowers() {
        return followers != null ? followers : new ArrayList<>();
    }

    public void setFollowers(List<String> followers) { this.followers = followers; }

    // -----------------------
    // Convenience methods
    // -----------------------

    public String getFormattedDate() {
        if (timestamp == null) return "Unknown";
        java.text.SimpleDateFormat sdf =
                new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault());
        return sdf.format(timestamp.toDate());
    }

    public String getFormattedLocation() {
        if (location == null)
            return "Unknown";
        return String.format(java.util.Locale.getDefault(),
                "%.5f, %.5f", location.getLatitude(), location.getLongitude());
    }

    @Override
    public String toString() {
        return "Pothole{" +
                "id='" + id + '\'' +
                ", severity='" + severity + '\'' +
                ", status='" + status + '\'' +
                ", detectedBy='" + detectedBy + '\'' +
                ", timestamp=" + timestamp +
                ", location=" + location +
                '}';
    }
}
