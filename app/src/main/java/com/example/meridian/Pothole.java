package com.example.meridian;
import java.util.UUID;
import java.util.Date;


public class Pothole {

    private String severity;
    private float latitude;
    private float longitude;
    private final String id;
    private Date dateReported;
    private PotholeStatus status;


    public Pothole(){
        this.id = UUID.randomUUID().toString(); // Assign a random ID by default
        this.dateReported = new Date(); // Set date reported to current time (on creation)
        this.status = PotholeStatus.REPORTED; // Set status to reported by default

    };

    public Pothole(String severity, float latitude, float longitude) {
        this.severity = severity;
        this.latitude = latitude;
        this.longitude = longitude;
        this.id = UUID.randomUUID().toString(); // Assign a random ID
        this.dateReported = new Date(); // Set reported date to current
        this.status = PotholeStatus.REPORTED; // Set status to reported by default
    }



    // -------------------SETTERS-------------------
    // Note that we don't want a setter for the id and dateReported as it is generated automatically

    public void setLatitude(float latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(float longitude) {
        this.longitude = longitude;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public void setStatus(PotholeStatus status) {
        this.status = status;
    }



    // -------------------GETTERS-------------------

    public float getLatitude() {
        return latitude;
    }

    public float getLongitude() {
        return longitude;
    }

    public String getSeverity() {
        return severity;
    }
    public String getId() {
        return id;
    }

    public Date getDateReported() {
        return dateReported;
    }

    public PotholeStatus getStatus() {
        return status;
    }



    //Possible pothole status
    enum PotholeStatus {
        REPORTED, // Initial status
        REPAIR_SCHEDULED, // If pothole has been scheduled for repair
        REPAIRED, // If pothole was repaired
        INVALID; // For reports that were not actual potholes
    }
}


