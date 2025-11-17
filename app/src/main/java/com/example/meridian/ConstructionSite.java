package com.example.meridian;

import com.google.android.gms.maps.model.LatLng;

public class ConstructionSite {
    final String description;
    final LatLng location;
    final String startDate;
    final String endDate;

    public ConstructionSite(String description, LatLng location, String startDate, String endDate) {
        this.description = description;
        this.location = location;
        this.startDate = startDate;
        this.endDate = endDate;
    }
}