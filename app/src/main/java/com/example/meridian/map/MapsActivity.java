package com.example.meridian.map;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.example.meridian.R;
import com.example.meridian.databinding.ActivityMapsBinding;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.maps.android.SphericalUtil;
import com.google.maps.android.heatmaps.HeatmapTileProvider;
import com.google.maps.android.heatmaps.WeightedLatLng;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import okhttp3.*;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String TAG = "MapsActivity";

    private GoogleMap mMap;
    private ActivityMapsBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private final OkHttpClient httpClient = new OkHttpClient();

    private FloatingActionButton fabBack, fabAddClosure, fabConfirmClosure, fabCancelClosure, fabUndoClosure;
    // Closure card views
    private View closureCard;
    private TextView tvClosureTitle, tvClosureEnddate, tvClosureDescription;
    private Button btnClosureModify, btnClosureDelete;

    private boolean isAdmin = false;

    private final List<Marker> potholeMarkers = new ArrayList<>();
    private HeatmapTileProvider heatmapProvider;
    private TileOverlay heatmapOverlay;
    private final List<WeightedLatLng> heatmapPoints = new ArrayList<>();
    private final List<Polyline> closurePolylines = new ArrayList<>();
    private final List<Marker> closureMarkers = new ArrayList<>();
    // Road continuity tracking
    private String lastSnappedPlaceId = null;
    private String lastSnappedRoadName = null;
    private boolean isDrawingClosure = false;
    private Polyline tempClosurePolyline;
    private final List<LatLng> tempClosurePoints = new ArrayList<>();

    private static final float MAX_SNAP_DISTANCE = 25f;

    // ---------------- DATA CLASSES ----------------
    private static class PotholeData {
        String id, status, severity;
        GeoPoint location;
        Timestamp timestamp;
        List<String> followers;

        PotholeData(String i, String st, String sev, GeoPoint loc, Timestamp ts, List<String> f) {
            id = i; status = st; severity = sev; location = loc; timestamp = ts;
            followers = (f != null) ? f : new ArrayList<>();
        }
    }

    private static class RoadClosureData {
        String id, description;
        long endDateMillis;
        List<LatLng> points;

        RoadClosureData(String id, long endDateMillis, String desc, List<LatLng> pts) {
            this.id = id;
            this.endDateMillis = endDateMillis;
            this.description = desc;
            this.points = pts;
        }
    }

    // ======================================================
    //                   LIFECYCLE
    // ======================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        SupportMapFragment mf = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mf != null) mf.getMapAsync(this);

        fabBack = findViewById(R.id.fab_back);
        fabAddClosure = findViewById(R.id.fab_add_closure);
        fabConfirmClosure = findViewById(R.id.fab_confirm_closure);
        fabCancelClosure = findViewById(R.id.fab_cancel_closure);
        fabUndoClosure = findViewById(R.id.fab_undo_closure);
        closureCard = findViewById(R.id.closure_detail_card);

        View closureLayout = findViewById(R.id.closureDetailLayout);

        tvClosureTitle = closureLayout.findViewById(R.id.tv_closure_title);
        tvClosureEnddate = closureLayout.findViewById(R.id.tv_closure_enddate);
        tvClosureDescription = closureLayout.findViewById(R.id.tv_closure_description);

        btnClosureModify = closureLayout.findViewById(R.id.btn_closure_modify);
        btnClosureDelete = closureLayout.findViewById(R.id.btn_closure_delete);



        fabAddClosure.setVisibility(View.GONE);
        fabConfirmClosure.setVisibility(View.GONE);
        fabCancelClosure.setVisibility(View.GONE);
        fabUndoClosure.setVisibility(View.GONE);


        fabBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        fabAddClosure.setOnClickListener(v -> beginClosureDrawingMode());
        fabConfirmClosure.setOnClickListener(v -> showSaveClosureDialog());
        fabCancelClosure.setOnClickListener(v -> cancelClosureDrawing());
        fabUndoClosure.setOnClickListener(v -> undoLastPoint());


        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }
        });

        checkUserRole();
    }

    private void checkUserRole() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && "admin".equals(doc.getString("role"))) {
                        isAdmin = true;
                        fabAddClosure.setVisibility(View.VISIBLE);
                    }
                });
    }

    @Override
    public void onMapReady(GoogleMap gmap) {
        mMap = gmap;

        setupMapUI();
        focusOnUserLocation();
        loadPotholes();
        loadAdminClosures();

        mMap.setOnPolylineClickListener(polyline -> {
            Object tag = polyline.getTag();
            if (!(tag instanceof RoadClosureData)) return;

            RoadClosureData closure = (RoadClosureData) tag;

            // Center of the polyline
            LatLng center = getPolylineCenter(closure.points);

            // Smooth move to center
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(center, 16f));

            showClosureDetailCard(closure);
        });




        mMap.setOnMarkerClickListener(marker -> {
            if (isDrawingClosure) return true;

            Object tag = marker.getTag();
            if (tag instanceof PotholeData) {
                PotholeData potholeData = (PotholeData) tag;
                mMap.animateCamera(CameraUpdateFactory.newLatLng(marker.getPosition()));
                showPotholeDetailCard(potholeData, marker);   // 👈 SHOW CARD HERE
                return true;
            }

            return false;
        });

        mMap.setOnMapClickListener(tap -> {
            if (isDrawingClosure) {
                handleRoadSnappedTap(tap);
                return;
            }

            binding.potholeDetailCard.setVisibility(View.GONE);
            binding.closureDetailCard.setVisibility(View.GONE);
        });


        mMap.setOnCameraMoveListener(() -> {
            float zoom = mMap.getCameraPosition().zoom;
            updateHeatmapAndPinsVisibility(zoom);
        });

        mMap.setOnCameraIdleListener(() -> {
            float zoom = mMap.getCameraPosition().zoom;
            updateHeatmapAndPinsVisibility(zoom);
        });
    }

    private void focusOnUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    1
            );
            return;
        }

        mMap.setMyLocationEnabled(true);
        FusedLocationProviderClient fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(this);

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        LatLng currentLocation =
                                new LatLng(location.getLatitude(), location.getLongitude());
                        mMap.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(currentLocation, 11.5f)
                        );
                    } else {
                        Toast.makeText(this,
                                "Unable to get current location", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setupMapUI() {
        try {
            boolean success = mMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style)
            );
            if (!success) Log.e(TAG, "Style parsing failed.");
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Can't find style. Error: ", e);
        }

        mMap.setBuildingsEnabled(false);
        mMap.getUiSettings().setMapToolbarEnabled(false);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(false);
    }

    private void updateHeatmapAndPinsVisibility(float zoom) {
        if (heatmapOverlay == null) return;

        float fadeStart = 12.5f;
        float fadeEnd = 13.5f;

        float alpha = Math.min(1f, Math.max(0f, (zoom - fadeStart) / (fadeEnd - fadeStart)));

        boolean showHeatmap = alpha < 0.5f;
        heatmapOverlay.setVisible(showHeatmap);

        for (Marker marker : potholeMarkers) {
            marker.setVisible(alpha > 0f);
            marker.setAlpha(alpha);
        }
        // Admin closures stay visible at all zoom levels (for now)
    }

    // ======================================================
    //                ROAD SNAP LOGIC (GOOGLE API)
    // ======================================================
    private void handleRoadSnappedTap(LatLng rawTap) {

        snapToRoad(rawTap, (snappedPoint, placeId, roadName, error) -> {
            if (error != null) {
                Toast.makeText(this, "Road API error", Toast.LENGTH_SHORT).show();
                return;
            }
            if (snappedPoint == null) {
                Toast.makeText(this, "Tap must be on a road", Toast.LENGTH_SHORT).show();
                return;
            }

            // distance validation
            double dist = SphericalUtil.computeDistanceBetween(rawTap, snappedPoint);
            if (dist > MAX_SNAP_DISTANCE) {
                Toast.makeText(this, "Tap too far from a road", Toast.LENGTH_SHORT).show();
                return;
            }

            // OR condition for continuity
            if (lastSnappedPlaceId != null || lastSnappedRoadName != null) {

                boolean matchesPlaceId =
                        placeId != null &&
                                placeId.equals(lastSnappedPlaceId);

                boolean matchesRoadName =
                        roadName != null &&
                                lastSnappedRoadName != null &&
                                roadName.equalsIgnoreCase(lastSnappedRoadName);

                if (!matchesPlaceId && !matchesRoadName) {
                    Toast.makeText(this,
                            "Stay on the same road (intersection allowed)",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
            }


            // Accept segment → update tracking
            lastSnappedPlaceId = placeId;
            lastSnappedRoadName = roadName;

            if (tempClosurePoints.isEmpty()) {
                // First point — just add it
                addPointToCurrentClosure(snappedPoint);
                lastSnappedPlaceId = placeId;
            } else {
                LatLng last = tempClosurePoints.get(tempClosurePoints.size() - 1);

                snapCurveBetween(last, snappedPoint, (curvePoints, e2) -> {
                    if (e2 != null || curvePoints == null) {
                        Toast.makeText(this, "Curve snap failed", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Do NOT duplicate the last point
                    for (int i = 1; i < curvePoints.size(); i++) {
                        tempClosurePoints.add(curvePoints.get(i));
                    }

                    tempClosurePolyline.setPoints(tempClosurePoints);
                    lastSnappedPlaceId = placeId;
                });
            }
        });
    }

    private void undoLastPoint() {
        if(tempClosurePoints.isEmpty()) return;

        tempClosurePoints.remove(tempClosurePoints.size() - 1);

        if (tempClosurePoints.isEmpty()) {
            fabUndoClosure.setVisibility(View.GONE);
            lastSnappedPlaceId = null;

            if (tempClosurePolyline != null) {
                tempClosurePolyline.remove();
                tempClosurePolyline = null;
            }
            return;
        }

        tempClosurePoints.remove(tempClosurePoints.size() - 1);
        tempClosurePolyline.setPoints(tempClosurePoints);
    }

    private void snapToRoad(LatLng tap, RoadSnapCallback cb) {

        String url = "https://roads.googleapis.com/v1/nearestRoads"
                + "?points=" + tap.latitude + "," + tap.longitude
                + "&key=" + getString(R.string.map_api_key);

        Request req = new Request.Builder().url(url).build();

        httpClient.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> cb.onResult(null, null, null, e));
            }

            @Override
            public void onResponse(Call call, Response resp) throws IOException {
                if (!resp.isSuccessful()) {
                    runOnUiThread(() -> cb.onResult(null, null, null, new Exception("API error")));
                    return;
                }

                try {
                    String body = resp.body().string();
                    JSONObject json = new JSONObject(body);
                    JSONArray snapped = json.optJSONArray("snappedPoints");

                    if (snapped == null || snapped.length() == 0) {
                        runOnUiThread(() -> cb.onResult(null, null, null, null));
                        return;
                    }

                    JSONObject first = snapped.getJSONObject(0);
                    JSONObject loc = first.getJSONObject("location");

                    LatLng snappedPt = new LatLng(
                            loc.getDouble("latitude"),
                            loc.getDouble("longitude")
                    );

                    String placeId = first.optString("placeId", "");

                    // CALL PLACE DETAILS TO EXTRACT ROAD NAME
                    fetchRoadName(placeId, (roadName, e2) -> {
                        if (e2 != null) {
                            runOnUiThread(() -> cb.onResult(snappedPt, placeId, null, e2));
                        } else {
                            runOnUiThread(() -> cb.onResult(snappedPt, placeId, roadName, null));
                        }
                    });

                } catch (Exception e) {
                    runOnUiThread(() -> cb.onResult(null, null, null, e));
                }
            }
        });
    }

    private void snapCurveBetween(LatLng from, LatLng to, CurveSnapCallback cb) {
        String url = "https://roads.googleapis.com/v1/snapToRoads"
                + "?interpolate=true"
                + "&path=" + from.latitude + "," + from.longitude
                + "|" + to.latitude + "," + to.longitude
                + "&key=" + getString(R.string.map_api_key);

        Request req = new Request.Builder().url(url).build();

        httpClient.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> cb.onResult(null, e));
            }

            @Override
            public void onResponse(Call call, Response resp) throws IOException {
                if (!resp.isSuccessful()) {
                    runOnUiThread(() -> cb.onResult(null, new Exception("API error")));
                    return;
                }

                try {
                    String body = resp.body().string();
                    JSONObject json = new JSONObject(body);
                    JSONArray snapped = json.optJSONArray("snappedPoints");

                    if (snapped == null || snapped.length() == 0) {
                        runOnUiThread(() -> cb.onResult(null, null));
                        return;
                    }

                    List<LatLng> curve = new ArrayList<>();
                    for (int i = 0; i < snapped.length(); i++) {
                        JSONObject sp = snapped.getJSONObject(i);
                        JSONObject loc = sp.getJSONObject("location");
                        curve.add(new LatLng(
                                loc.getDouble("latitude"),
                                loc.getDouble("longitude")));
                    }

                    runOnUiThread(() -> cb.onResult(curve, null));

                } catch (Exception e) {
                    runOnUiThread(() -> cb.onResult(null, e));
                }
            }
        });
    }

    interface CurveSnapCallback {
        void onResult(List<LatLng> curvedPoints, Exception error);
    }


    private void fetchRoadName(String placeId, RoadNameCallback cb) {

        if (placeId == null || placeId.isEmpty()) {
            cb.onResult(null, null);
            return;
        }

        String url = "https://maps.googleapis.com/maps/api/place/details/json"
                + "?place_id=" + placeId
                + "&fields=address_components"
                + "&key=" + getString(R.string.map_api_key);

        Request req = new Request.Builder().url(url).build();

        httpClient.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                cb.onResult(null, e);
            }

            @Override
            public void onResponse(Call call, Response resp) throws IOException {
                if (!resp.isSuccessful()) {
                    cb.onResult(null, new Exception("Place Details API error"));
                    return;
                }

                try {
                    String body = resp.body().string();
                    JSONObject json = new JSONObject(body);
                    JSONObject result = json.optJSONObject("result");

                    if (result == null) {
                        cb.onResult(null, null);
                        return;
                    }

                    JSONArray comp = result.optJSONArray("address_components");
                    if (comp == null) {
                        cb.onResult(null, null);
                        return;
                    }

                    // Find "route" component → this is the STREET NAME
                    String roadName = null;
                    for (int i = 0; i < comp.length(); i++) {
                        JSONObject item = comp.getJSONObject(i);
                        JSONArray types = item.optJSONArray("types");
                        if (types != null) {
                            for (int t = 0; t < types.length(); t++) {
                                if (types.getString(t).equals("route")) {
                                    roadName = item.optString("long_name", null);
                                    break;
                                }
                            }
                        }
                    }

                    cb.onResult(roadName, null);

                } catch (Exception e) {
                    cb.onResult(null, e);
                }
            }
        });
    }

    interface RoadNameCallback {
        void onResult(String roadName, Exception error);
    }



    interface RoadSnapCallback {
        void onResult(LatLng snappedPoint, String placeId, String roadName, Exception error);
    }

    // ======================================================
    //          DRAWING MODE: ADD POINTS TO CLOSURE
    // ======================================================
    private void beginClosureDrawingMode() {
        if (!isAdmin) return;

        lastSnappedPlaceId = null;
        lastSnappedRoadName = null;
        isDrawingClosure = true;
        tempClosurePoints.clear();

        if (tempClosurePolyline != null) tempClosurePolyline.remove();

        fabAddClosure.setVisibility(View.GONE);
        fabConfirmClosure.setVisibility(View.VISIBLE);
        fabCancelClosure.setVisibility(View.VISIBLE);

        Toast.makeText(this, "Tap the road to draw a closure", Toast.LENGTH_LONG).show();
    }


    private void addPointToCurrentClosure(LatLng p) {
        tempClosurePoints.add(p);

        if (tempClosurePoints.size() == 1 && fabUndoClosure != null) {
            fabUndoClosure.setVisibility(View.VISIBLE);
        }

        if (tempClosurePolyline == null) {
            tempClosurePolyline = mMap.addPolyline(
                    new PolylineOptions()
                            .addAll(tempClosurePoints)
                            .color(Color.CYAN)
                            .width(12f)
            );
        } else {
            tempClosurePolyline.setPoints(tempClosurePoints);
        }
    }

    private void cancelClosureDrawing() {
        isDrawingClosure = false;
        tempClosurePoints.clear();
        lastSnappedPlaceId = null;
        lastSnappedRoadName = null;

        if (tempClosurePolyline != null) {
            tempClosurePolyline.remove();
            tempClosurePolyline = null;
        }

        fabConfirmClosure.setVisibility(View.GONE);
        fabCancelClosure.setVisibility(View.GONE);
        fabUndoClosure.setVisibility(View.GONE);
        if (isAdmin) fabAddClosure.setVisibility(View.VISIBLE);
    }


    private void showSaveClosureDialog() {
        if (tempClosurePoints.size() < 2) {
            Toast.makeText(this, "Need at least 2 points", Toast.LENGTH_SHORT).show();
            return;
        }

        final Calendar calendar = Calendar.getInstance();

        DatePickerDialog picker = new DatePickerDialog(
                this,
                (view, year, month, day) -> {
                    calendar.set(year, month, day);
                    long endDateMillis = calendar.getTimeInMillis();
                    saveClosureToFirestore(endDateMillis);
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        picker.setTitle("Select expected end date");
        picker.getDatePicker().setMinDate(System.currentTimeMillis());
        picker.show();
    }


    private void saveClosureToFirestore(long endDateMillis) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        List<Map<String,Object>> pts = new ArrayList<>();
        for (LatLng p : tempClosurePoints) {
            Map<String,Object> m = new HashMap<>();
            m.put("lat", p.latitude);
            m.put("lng", p.longitude);
            pts.add(m);
        }

        Map<String,Object> data = new HashMap<>();
        data.put("points", pts);
        data.put("endDate", endDateMillis);
        data.put("description", "Construction road closure");
        data.put("createdAt", Timestamp.now());
        data.put("createdBy", user.getUid());

        db.collection("closures")
                .add(data)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(this, "Closure saved", Toast.LENGTH_SHORT).show();

                    RoadClosureData c = new RoadClosureData(
                            ref.getId(), endDateMillis,
                            "Construction road closure",
                            new ArrayList<>(tempClosurePoints)
                    );

                    addSingleClosureToMap(c);
                    cancelClosureDrawing();
                    loadAdminClosures();

                    notifyUsersNearClosure(c);
                });
    }

    private void notifyUsersNearClosure(RoadClosureData closure) {

        if (closure.points.isEmpty()) return;

        // Midpoint of closure
        LatLng mid = closure.points.get(closure.points.size() / 2);

        db.collection("users").get().addOnSuccessListener(snap -> {

            for (DocumentSnapshot doc : snap) {

                GeoPoint gp = doc.getGeoPoint("addressGeo");
                if (gp == null) continue;

                double userLat = gp.getLatitude();
                double userLng = gp.getLongitude();

                // Compute distance from user’s home to closure midpoint
                double dist = distanceKm(
                        userLat, userLng,
                        mid.latitude, mid.longitude
                );

                if (dist <= 5.0) {   // temporary rule

                    String uid = doc.getId();

                    Map<String, Object> notif = new HashMap<>();
                    notif.put("type", "closure_nearby");
                    notif.put("message", "A road closure near your area was created.");
                    notif.put("closureId", closure.id);
                    notif.put("distanceKm", dist);
                    notif.put("timestamp", com.google.firebase.Timestamp.now());

                    db.collection("users")
                            .document(uid)
                            .collection("notifications")
                            .add(notif);
                }
            }
        });
    }



    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371; // Earth radius km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a =
                Math.sin(dLat/2) * Math.sin(dLat/2) +
                        Math.cos(Math.toRadians(lat1)) *
                                Math.cos(Math.toRadians(lat2)) *
                                Math.sin(dLon/2) * Math.sin(dLon/2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }




    // ======================================================
    //              LOAD EXISTING CLOSURES
    // ======================================================
    private void loadAdminClosures() {
        db.collection("closures").get()
                .addOnSuccessListener(snap -> {
                    clearClosures();

                    for (QueryDocumentSnapshot doc : snap) {
                        List<Map<String,Object>> pm = (List<Map<String,Object>>) doc.get("points");
                        if (pm == null) continue;

                        List<LatLng> pts = new ArrayList<>();
                        for (Map<String,Object> m : pm) {
                            pts.add(new LatLng(
                                    ((Number)m.get("lat")).doubleValue(),
                                    ((Number)m.get("lng")).doubleValue()
                            ));
                        }

                        addSingleClosureToMap(new RoadClosureData(
                                doc.getId(),
                                doc.getLong("endDate") != null ? doc.getLong("endDate") : 0L,
                                doc.getString("description"),
                                pts
                        ));
                    }
                });
    }

    private void showClosureDetailCard(RoadClosureData closure) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        tvClosureTitle.setText("Road Closure");
        tvClosureEnddate.setText("Until: " + sdf.format(new Date(closure.endDateMillis)));
        tvClosureDescription.setText(closure.description != null ? closure.description : "No description");

        if (isAdmin) {
            btnClosureModify.setVisibility(View.VISIBLE);
            btnClosureDelete.setVisibility(View.VISIBLE);

            btnClosureModify.setOnClickListener(v -> openDatePickerForClosureUpdate(closure.id));
            btnClosureDelete.setOnClickListener(v -> deleteClosureFromFirestore(closure.id));
        } else {
            btnClosureModify.setVisibility(View.GONE);
            btnClosureDelete.setVisibility(View.GONE);
        }

        closureCard.setVisibility(View.VISIBLE);
    }

    private LatLng getPolylineCenter(List<LatLng> pts) {
        double lat = 0, lng = 0;
        for (LatLng p : pts) {
            lat += p.latitude;
            lng += p.longitude;
        }
        return new LatLng(lat / pts.size(), lng / pts.size());
    }


    private void deleteClosureFromFirestore(String closureId) {
        db.collection("closures").document(closureId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Closure deleted", Toast.LENGTH_SHORT).show();
                    loadAdminClosures();     // Refresh closures on map
                    binding.closureDetailCard.setVisibility(View.GONE);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to delete closure", Toast.LENGTH_SHORT).show());
    }

    private void openDatePickerForClosureUpdate(String closureId) {
        Calendar calendar = Calendar.getInstance();

        DatePickerDialog picker = new DatePickerDialog(
                this,
                (view, year, month, day) -> {
                    calendar.set(year, month, day);
                    long newEndDateMillis = calendar.getTimeInMillis();

                    db.collection("closures").document(closureId)
                            .update("endDate", newEndDateMillis)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this,
                                        "End date updated", Toast.LENGTH_SHORT).show();
                                loadAdminClosures();     // Refresh
                                binding.closureDetailCard.setVisibility(View.GONE);
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this,
                                            "Failed to update date", Toast.LENGTH_SHORT).show());
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        // Prevent picking past dates
        picker.getDatePicker().setMinDate(System.currentTimeMillis());
        picker.setTitle("Select new end date");
        picker.show();
    }

    private void addSingleClosureToMap(RoadClosureData c) {
        if (c.points.isEmpty()) return;

        Polyline poly = mMap.addPolyline(new PolylineOptions()
                .addAll(c.points)
                .width(14f)
                .color(Color.parseColor("#FFA500"))  // ORANGE
                .clickable(true)
        );

        poly.setTag(c);
        closurePolylines.add(poly);
    }


    private void clearClosures() {
        for (Polyline p : closurePolylines) p.remove();
        for (Marker m : closureMarkers) m.remove();
        closurePolylines.clear();
        closureMarkers.clear();
    }

    private int colorForSeverity(String s) {
        if (s == null) return Color.CYAN;
        s = s.toLowerCase(Locale.ROOT);
        if (s.contains("major")) return Color.RED;
        if (s.contains("mod")) return Color.parseColor("#FF8800");
        if (s.contains("minor")) return Color.YELLOW;
        return Color.CYAN;
    }

    // ======================================================
    //                     Potholes (unchanged)
    // ======================================================
    private void loadPotholes() {
        db.collection("potholes").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        List<String> followers = (List<String>) doc.get("followers");

                        PotholeData potholeData = new PotholeData(
                                doc.getId(),
                                doc.getString("status"),
                                doc.getString("severity"),
                                doc.getGeoPoint("location"),
                                doc.getTimestamp("timestamp"),
                                followers
                        );

                        if (potholeData.location != null) {
                            LatLng potholeLocation = new LatLng(
                                    potholeData.location.getLatitude(),
                                    potholeData.location.getLongitude()
                            );

                            float markerColor;
                            double weight;

                            if ("Severe".equalsIgnoreCase(potholeData.severity)) {
                                markerColor = BitmapDescriptorFactory.HUE_RED;
                                weight = 3.0;
                            } else if ("Moderate".equalsIgnoreCase(potholeData.severity)) {
                                markerColor = BitmapDescriptorFactory.HUE_ORANGE;
                                weight = 2.0;
                            } else {
                                markerColor = BitmapDescriptorFactory.HUE_YELLOW;
                                weight = 1.0;
                            }

                            heatmapPoints.add(new WeightedLatLng(potholeLocation, weight));

                            Marker marker = mMap.addMarker(new MarkerOptions()
                                    .position(potholeLocation)
                                    .icon(BitmapDescriptorFactory.defaultMarker(markerColor)));
                            if (marker != null) {
                                marker.setTag(potholeData);
                                marker.setVisible(false); // start hidden
                                potholeMarkers.add(marker);
                            }
                        }
                    }

                    if (!heatmapPoints.isEmpty()) {
                        if (heatmapProvider == null) {
                            int[] colors = {
                                    Color.rgb(255, 255, 102),   // Yellow
                                    Color.rgb(255, 165, 0),     // Orange
                                    Color.rgb(255, 69, 0),      // Red-Orange
                                    Color.rgb(178, 34, 34)      // Dark Red
                            };
                            float[] startPoints = {0.2f, 0.5f, 0.7f, 1.0f};

                            com.google.maps.android.heatmaps.Gradient gradient =
                                    new com.google.maps.android.heatmaps.Gradient(colors, startPoints);

                            heatmapProvider = new HeatmapTileProvider.Builder()
                                    .weightedData(heatmapPoints)
                                    .gradient(gradient)
                                    .radius(40)
                                    .build();

                            heatmapOverlay = mMap.addTileOverlay(
                                    new TileOverlayOptions().tileProvider(heatmapProvider));
                            if (heatmapOverlay != null) {
                                heatmapOverlay.setVisible(true);
                            }
                        } else {
                            heatmapProvider.setWeightedData(heatmapPoints);
                            if (heatmapOverlay != null) {
                                heatmapOverlay.clearTileCache();
                            }
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load potholes.", Toast.LENGTH_SHORT).show());
    }

    private void showAdminOptionsDialog(final String potholeId, final Marker marker) {
        final CharSequence[] options = {"Change Status", "Delete Report", "Cancel"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Admin Options");
        builder.setItems(options, (dialog, item) -> {
            if (options[item].equals("Change Status")) {
                showStatusChangeDialog(potholeId, marker);
            } else if (options[item].equals("Delete Report")) {
                marker.remove();
                binding.potholeDetailCard.setVisibility(View.GONE);
                deletePotholeFromFirestore(potholeId);
            } else {
                dialog.dismiss();
            }
        });
        builder.show();
    }

    private void showPotholeDetailCard(PotholeData potholeData, Marker marker) {
        binding.infoWindowLayout.tvTitle.setText(
                "Pothole - " + potholeData.id.substring(0, Math.min(6, potholeData.id.length()))
        );
        binding.infoWindowLayout.tvStatus.setText("Status: " + potholeData.status);
        binding.infoWindowLayout.tvSeverity.setText("Severity: " + potholeData.severity);
        binding.infoWindowLayout.tvCoordinates.setText(
                String.format(Locale.getDefault(), "Coordinates: %.4f, %.4f",
                        potholeData.location.getLatitude(),
                        potholeData.location.getLongitude())
        );

        if (potholeData.timestamp != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("M/d/yyyy", Locale.getDefault());
            binding.infoWindowLayout.tvDate.setText("Date: " + sdf.format(potholeData.timestamp.toDate()));
        } else {
            binding.infoWindowLayout.tvDate.setText("Date: Not available");
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        ImageView starIcon = binding.infoWindowLayout.ivStar;

        binding.infoWindowLayout.btnModify.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        binding.infoWindowLayout.btnModify.setOnClickListener(v ->
                showAdminOptionsDialog(potholeData.id, marker)
        );

        if (currentUser == null) {
            starIcon.setVisibility(View.GONE);
        } else {
            starIcon.setVisibility(View.VISIBLE);
            if (potholeData.followers.contains(currentUser.getUid())) {
                starIcon.setImageResource(R.drawable.ic_star_filled);
            } else {
                db.collection("potholes").document(potholeData.id).get()
                        .addOnSuccessListener(doc -> {
                            List<String> followers = (List<String>) doc.get("followers");
                            if (followers != null && followers.contains(currentUser.getUid())) {
                                starIcon.setImageResource(R.drawable.ic_star_filled);
                                potholeData.followers = followers;
                            } else {
                                starIcon.setImageResource(R.drawable.ic_star_border);
                            }
                        });
            }

            starIcon.setOnClickListener(v -> {
                if (currentUser.getUid() != null) {
                    toggleFollowPothole(potholeData, currentUser.getUid(), starIcon, marker);
                }
            });

            binding.potholeDetailCard.setVisibility(View.VISIBLE);
        }
    }
    private void toggleFollowPothole(PotholeData potholeData,
                                     String userId,
                                     ImageView starIcon,
                                     Marker marker) {
        boolean isCurrentlyFollowing = potholeData.followers.contains(userId);

        if (isCurrentlyFollowing) {
            db.collection("potholes").document(potholeData.id)
                    .update("followers", FieldValue.arrayRemove(userId))
                    .addOnSuccessListener(aVoid -> {
                        potholeData.followers.remove(userId);
                        marker.setTag(potholeData);
                        starIcon.setImageResource(R.drawable.ic_star_border);
                        Toast.makeText(this, "Unfollowed", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Failed to unfollow", Toast.LENGTH_SHORT).show());
        } else {
            db.collection("potholes").document(potholeData.id)
                    .update("followers", FieldValue.arrayUnion(userId))
                    .addOnSuccessListener(aVoid -> {
                        potholeData.followers.add(userId);
                        marker.setTag(potholeData);
                        starIcon.setImageResource(R.drawable.ic_star_filled);
                        Toast.makeText(this, "Following", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Failed to follow", Toast.LENGTH_SHORT).show());
        }
    }

    private void showStatusChangeDialog(final String potholeId, final Marker marker) {
        final CharSequence[] statuses = {"Reported", "In Progress", "Repaired"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select New Status");
        builder.setItems(statuses, (dialog, which) -> {
            String newStatus = statuses[which].toString();
            updatePotholeStatusInFirestore(potholeId, newStatus, marker);
        });
        builder.show();
    }

    private void updatePotholeStatusInFirestore(String potholeId,
                                                String newStatus,
                                                Marker marker) {

        db.collection("potholes")
                .document(potholeId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) return;

                    String oldStatus = snapshot.getString("status");

                    // Only notify if the status ACTUALLY changed
                    if (oldStatus != null && oldStatus.equals(newStatus)) {
                        return;
                    }

                    // Now update
                    snapshot.getReference()
                            .update("status", newStatus)
                            .addOnSuccessListener(aVoid -> {

                                Toast.makeText(this,
                                        "Status updated to " + newStatus, Toast.LENGTH_SHORT).show();

                                PotholeData data = (PotholeData) marker.getTag();

                                if (data != null) {
                                    data.status = newStatus;
                                    marker.setTag(data);
                                    binding.infoWindowLayout.tvStatus.setText("Status: " + newStatus);
                                }

                                // NOW safe to notify followers
                                notifyPotholeFollowers(potholeId, newStatus);
                            });
                });
    }


    private void notifyPotholeFollowers(String potholeId, String newStatus) {

        db.collection("potholes").document(potholeId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;

                    List<String> followers = (List<String>) doc.get("followers");
                    if (followers == null || followers.isEmpty()) return;

                    for (String userId : followers) {
                        Map<String, Object> notif = new HashMap<>();
                        notif.put("type", "pothole_update");
                        notif.put("potholeId", potholeId);
                        notif.put("newStatus", newStatus);
                        notif.put("timestamp", com.google.firebase.Timestamp.now());

                        db.collection("users")
                                .document(userId)
                                .collection("notifications")
                                .add(notif);
                    }
                });
    }

    private void deletePotholeFromFirestore(String potholeId) {
        db.collection("potholes").document(potholeId)
                .delete()
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(this,
                                "Pothole report deleted", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Failed to delete from database", Toast.LENGTH_SHORT).show());
    }
}
