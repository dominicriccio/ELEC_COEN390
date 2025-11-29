package com.example.meridian.items;

import static java.lang.Math.abs;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.GeoPoint;

import java.util.ArrayList;
import java.util.List;


public class Pothole {

    private String id;
    private String severity;
    private String status;
    private String detectedBy;
    private Timestamp timestamp;
    private GeoPoint location;
    private List<String> followers;


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
        this.id = null;

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
        this.detectedBy = "Hardware Device";
        this.timestamp = Timestamp.now();
        this.location = new GeoPoint(lat, lon);
    }

    public String getId() {
        return id;
    }

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
