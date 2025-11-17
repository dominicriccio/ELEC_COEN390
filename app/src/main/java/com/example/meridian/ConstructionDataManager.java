package com.example.meridian;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ConstructionDataManager {

    private static final String TAG = "ConstructionDataManager";
    private static final String CONSTRUCTION_CSV_URL = "https://donnees.montreal.ca/dataset/667342f7-f667-4c3c-9837-65e81312cd8d/resource/cc41b532-f12d-40fb-9f55-eb58c9a2b12b/download/entraves-travaux-en-cours.csv";

    public interface OnDataLoadedListener {
        void onDataLoaded(List<ConstructionSite> constructionSites);
        void onError(Exception e);
    }

    public void fetchConstructionData(OnDataLoadedListener listener) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(CONSTRUCTION_CSV_URL).build();
        Handler mainHandler = new Handler(Looper.getMainLooper());

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> listener.onError(e));
                Log.e(TAG, "Failed to fetch CSV data", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    IOException e = new IOException("Request failed: " + response);
                    mainHandler.post(() -> listener.onError(e));
                    Log.e(TAG, "CSV Response was not successful", e);
                    return;
                }

                try {
                    String csvData = response.body().string();
                    List<ConstructionSite> sites = parseCsvData(csvData);
                    mainHandler.post(() -> listener.onDataLoaded(sites));
                } catch (Exception e) {
                    mainHandler.post(() -> listener.onError(e));
                    Log.e(TAG, "Failed to parse CSV", e);
                }
            }
        });
    }

    private List<ConstructionSite> parseCsvData(String csvData) {
        List<ConstructionSite> sites = new ArrayList<>();
        // Split the data into lines
        String[] lines = csvData.split("\\r?\\n");

        if (lines.length < 2) {
            // Not enough data (less than header + 1 data line)
            return sites;
        }


        String[] headers = lines[0].split(",");
        int latIndex = -1;
        int lonIndex = -1;
        int descIndex = -1;
        int startIndex = -1;
        int endIndex = -1;

        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].trim().toLowerCase();
            if (header.contains("latitude")) {
                latIndex = i;
            } else if (header.contains("longitude")) {
                lonIndex = i;
            } else if (header.contains("occupancy_name")) {
                descIndex = i;
            } else if (header.contains("duration_start_date")) {
                startIndex = i;
            } else if (header.contains("duration_end_date")) { // French for end
                endIndex = i;
            }
        }

        // If we didn't find the essential lat/lon columns, we can't proceed.
        if (latIndex == -1 || lonIndex == -1) {
            Log.e(TAG, "Could not find 'latitude' or 'longitude' columns in CSV header.");
            return sites; // Return empty list
        }

        // 2. Loop through the data lines (start from 1 to skip the header)
        for (int i = 1; i < lines.length; i++) {
            String[] columns = lines[i].split(",");
            try {
                // Make sure the line has enough columns to avoid errors
                if (columns.length > latIndex && columns.length > lonIndex) {
                    double latitude = Double.parseDouble(columns[latIndex]);
                    double longitude = Double.parseDouble(columns[lonIndex]);
                    LatLng location = new LatLng(latitude, longitude);

                    String description = (descIndex != -1 && columns.length > descIndex) ? columns[descIndex] : "No description";

                    String startDate = "N/A";
                    if (startIndex != -1 && columns.length > startIndex && columns[startIndex].length() >= 10) {
                        // Take the first 10 characters, e.g., "2025-01-31"
                        startDate = columns[startIndex].substring(0, 10);
                    }

                    String endDate = "N/A";
                    if (endIndex != -1 && columns.length > endIndex && columns[endIndex].length() >= 10) {
                        endDate = columns[endIndex].substring(0, 10);
                    }

                    sites.add(new ConstructionSite(description, location, startDate, endDate));
                }
            } catch (NumberFormatException e) {
                Log.w(TAG, "Could not parse line " + i + " as a number: " + lines[i]);
                // Continue to the next line
            }
        }

        Log.d(TAG, "Successfully parsed " + sites.size() + " road hindrances from CSV.");
        return sites;
    }
}
