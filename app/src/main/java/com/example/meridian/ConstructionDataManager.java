package com.example.meridian;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ConstructionDataManager {

    private static final String TAG = "ConstructionDataManager";

    // URL for the main hindrances file (provides dates and site ID)
    private static final String HINDRANCES_URL = "https://donnees.montreal.ca/dataset/667342f7-f667-4c3c-9837-65e81312cd8d/resource/cc41b532-f12d-40fb-9f55-eb58c9a2b12b/download/entraves-travaux-en-cours.csv";

    // URL for the impacts/segments file (provides 'type' for filtering and street descriptions)
    private static final String IMPACTS_URL = "https://donnees.montreal.ca/dataset/667342f7-f667-4c3c-9837-65e81312cd8d/resource/a2bc8014-488c-495d-941b-e7ae1999d1bd/download/impacts-entraves-travaux-en-cours.csv";

    public interface OnDataLoadedListener {
        void onDataLoaded(List<MergedConstructionSite> sites);
        void onError(Exception e);
    }

    // This class remains the same
    public static class MergedConstructionSite {
        public final String siteId;
        public final String description;
        public final String startDate;
        public final String endDate;

        public MergedConstructionSite(String siteId, String description, String startDate, String endDate) {
            this.siteId = siteId;
            this.description = description;
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }

    // Helper class for the first CSV (now just dates)
    private static class HindranceDateInfo {
        String startDate;
        String endDate;

        HindranceDateInfo(String startDate, String endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }

    // Helper class for the second CSV (now has description and blocking status)
    private static class ImpactInfo {
        String siteId;
        String description;
        boolean isBlocked;

        ImpactInfo(String siteId, String description, boolean isBlocked) {
            this.siteId = siteId;
            this.description = description;
            this.isBlocked = isBlocked;
        }
    }


    public void fetchConstructionData(OnDataLoadedListener listener) {
        OkHttpClient client = new OkHttpClient();
        Handler mainHandler = new Handler(Looper.getMainLooper());

        final CountDownLatch latch = new CountDownLatch(2);

        // Map to hold date info from the first file (Key: siteId, Value: HindranceDateInfo)
        final Map<String, HindranceDateInfo> dateInfos = new HashMap<>();
        // List to hold impact info from the second file
        final List<ImpactInfo> impactInfos = new ArrayList<>();

        final List<Exception> errors = new ArrayList<>();

        // --- Request 1: Fetch Hindrances (for dates) ---
        Request hindrancesRequest = new Request.Builder().url(HINDRANCES_URL).build();
        client.newCall(hindrancesRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                errors.add(e);
                latch.countDown();
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        dateInfos.putAll(parseHindranceData(body.string()));
                    } else {
                        errors.add(new IOException("Hindrance data request failed: " + response));
                    }
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    latch.countDown();
                }
            }
        });

        // --- Request 2: Fetch Impacts (for descriptions and filtering) ---
        Request impactsRequest = new Request.Builder().url(IMPACTS_URL).build();
        client.newCall(impactsRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                errors.add(e);
                latch.countDown();
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        impactInfos.addAll(parseImpactData(body.string()));
                    } else {
                        errors.add(new IOException("Impact data request failed: " + response));
                    }
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    latch.countDown();
                }
            }
        });

        // --- Merge data after both requests are done ---
        new Thread(() -> {
            try {
                latch.await();
                if (!errors.isEmpty()) {
                    mainHandler.post(() -> listener.onError(errors.get(0)));
                    return;
                }

                List<MergedConstructionSite> mergedSites = new ArrayList<>();
                // Iterate through the impacts, as this is the file we filter on
                for (ImpactInfo impact : impactInfos) {
                    // ** FILTERING LOGIC: Only include if it's blocked **
                    if (impact.isBlocked) {
                        // Find the corresponding date info
                        HindranceDateInfo dateInfo = dateInfos.get(impact.siteId);
                        if (dateInfo != null) {
                            mergedSites.add(new MergedConstructionSite(
                                    impact.siteId,
                                    impact.description,
                                    dateInfo.startDate,
                                    dateInfo.endDate
                            ));
                        }
                    }
                }

                Log.d(TAG, "Successfully merged " + mergedSites.size() + " blocked road sites.");
                mainHandler.post(() -> listener.onDataLoaded(mergedSites));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                mainHandler.post(() -> listener.onError(e));
            }
        }).start();
    }

    // Parses the first CSV (entraves-travaux-en-cours.csv) for DATES
    private Map<String, HindranceDateInfo> parseHindranceData(String csvData) {
        Map<String, HindranceDateInfo> dateInfos = new HashMap<>();
        String[] lines = csvData.split("\\r?\\n");
        if (lines.length < 2) return dateInfos;

        String[] headers = lines[0].split(",");
        int idIndex = -1, startIndex = -1, endIndex = -1;
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].trim().toLowerCase().replace("\"", "");
            // --- FIX: Use correct column names for the first file ---
            if (header.equals("id")) idIndex = i;
            if (header.equals("duration_start_date")) startIndex = i;
            if (header.equals("duration_end_date")) endIndex = i;
        }

        if (idIndex == -1) {
            Log.e(TAG, "Hindrance CSV missing 'id' column.");
            return dateInfos;
        }

        for (int i = 1; i < lines.length; i++) {
            String[] columns = lines[i].split(",");
            try {
                if (columns.length > idIndex) {
                    String siteId = columns[idIndex].replace("\"", "");

                    String startDate = "N/A";
                    // --- FIX: Correctly parse the date format YYYY-MM-DD ---
                    if (startIndex != -1 && columns.length > startIndex && columns[startIndex].length() >= 10) {
                        startDate = columns[startIndex].substring(0, 10).replace("\"", "");
                    }

                    String endDate = "N/A";
                    // --- FIX: Correctly parse the date format YYYY-MM-DD ---
                    if (endIndex != -1 && columns.length > endIndex && columns[endIndex].length() >= 10) {
                        endDate = columns[endIndex].substring(0, 10).replace("\"", "");
                    }
                    dateInfos.put(siteId, new HindranceDateInfo(startDate, endDate));
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not parse hindrance line: " + lines[i]);
            }
        }
        return dateInfos;
    }

    // Parses the second CSV (impacts-entraves-travaux-en-cours.csv) for DESCRIPTIONS and FILTERING
    private List<ImpactInfo> parseImpactData(String csvData) {
        List<ImpactInfo> impactInfos = new ArrayList<>();
        String[] lines = csvData.split("\\r?\\n");
        if (lines.length < 2) return impactInfos;

        String[] headers = lines[0].split(",");
        int idIndex = -1, roadIndex = -1, fromIndex = -1, toIndex = -1, typeIndex = -1;
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].trim().toLowerCase().replace("\"", "");
            // --- FIX: Use correct column names for the second file ---
            if (header.equals("id_request")) idIndex = i;
            if (header.equals("name")) roadIndex = i;
            if (header.equals("fromname")) fromIndex = i;
            if (header.equals("toname")) toIndex = i;
            if (header.equals("streetimpacttype")) typeIndex = i;
        }

        if (idIndex == -1 || roadIndex == -1 || fromIndex == -1 || toIndex == -1 || typeIndex == -1) {
            Log.e(TAG, "Impacts CSV missing required columns (id_request, name, fromname, toname, streetimpacttype)");
            return impactInfos;
        }

        for (int i = 1; i < lines.length; i++) {
            String[] columns = lines[i].split(",");
            try {
                if (columns.length > Math.max(Math.max(idIndex, roadIndex), Math.max(fromIndex, toIndex))) {
                    String siteId = columns[idIndex].replace("\"", "");
                    String roadName = columns[roadIndex].replace("\"", "");
                    String fromStreet = columns[fromIndex].replace("\"", "");
                    String toStreet = columns[toIndex].replace("\"", "");
                    String type = columns[typeIndex].replace("\"", "");

                    // ** FILTERING LOGIC IS NOW HERE **
                    // --- FIX: Handle the special character in "Rue barrée" ---
                    boolean isBlocked = "Rue barrée".equalsIgnoreCase(type) || "Rue barrÃ©e".equalsIgnoreCase(type);

                    // Construct the description string
                    String description = roadName + " entre " + fromStreet + " et " + toStreet;
                    impactInfos.add(new ImpactInfo(siteId, description, isBlocked));
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not parse impact line: " + lines[i]);
            }
        }
        return impactInfos;
    }
}
