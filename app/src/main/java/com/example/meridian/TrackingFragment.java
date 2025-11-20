package com.example.meridian;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class TrackingFragment extends Fragment {

    private static final String TAG = "TrackingFragment";
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListView listView;
    private Spinner vehicleFilterSpinner;
    private LinearLayout filterContainer;  private boolean isAdmin = false;
    private final List<Pothole> potholeList = new ArrayList<>();
    private final Set<String> potholeIds = new HashSet<>();
    private PotholeAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_tracking, container, false);

        listView = view.findViewById(R.id.listView);
        vehicleFilterSpinner = view.findViewById(R.id.spinner_vehicle_filter);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        adapter = new PotholeAdapter(potholeList);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, itemView, position, id) -> {
            Pothole p = potholeList.get(position);
            if (p == null) return;

            ReportFragment dialog = new ReportFragment();
            Bundle args = new Bundle();
            args.putString("id", p.getId());
            args.putString("status", p.getStatus());
            args.putString("severity", p.getSeverity());
            args.putDouble("latitude", p.getLocation() != null ? p.getLocation().getLatitude() : 0d);
            args.putDouble("longitude", p.getLocation() != null ? p.getLocation().getLongitude() : 0d);
            args.putLong("timestampMillis", p.getTimestamp() != null ? p.getTimestamp().toDate().getTime() : 0L);
            dialog.setArguments(args);

            dialog.show(getParentFragmentManager(), "reportDetails");
        });

        Button realTimeBtn = view.findViewById(R.id.realtimeButton);
        realTimeBtn.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), RealTimeDataActivity.class);
            startActivity(intent);
        });

        checkUserRoleAndSetupUI();

        //loadPersonalizedPotholes();
        return view;
    }

    private void checkUserRoleAndSetupUI() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Please log in to see tracked potholes.", Toast.LENGTH_LONG).show();
            return; }

        db.collection("users").document(currentUser.getUid()).get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                String role = documentSnapshot.getString("role");
                if (role != null && role.equals("admin")) {
                    isAdmin = true;
                    setupAdminFilter();
                }
                else {
                    isAdmin = false;
                    if (vehicleFilterSpinner != null) {
                        vehicleFilterSpinner.setVisibility(View.GONE);
                    }
                    loadPersonalizedPotholes();

                }
            }
        });
    }

    private void setupAdminFilter() {
        if (vehicleFilterSpinner == null) {
            loadAllPotholes(null);
            return;
        }
        vehicleFilterSpinner.setVisibility(View.VISIBLE);

        String [] filterOptions = {"All vehicles", "Bicycle", "Sedan", "SUV", "Pickup Truck" };

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, filterOptions);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        vehicleFilterSpinner.setAdapter(spinnerAdapter);

        vehicleFilterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = filterOptions[position];
                if (selected.equals("All vehicles") ){
                    loadAllPotholes(null);
                }
                else{
                    loadAllPotholes(selected);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void loadAllPotholes(@Nullable String vehicleTypeFilter) {
        com.google.firebase.firestore.Query query = db.collection("potholes");
        if (vehicleTypeFilter != null) {
            query = query.whereEqualTo("vehicleType", vehicleTypeFilter);
        }

        query.get().addOnSuccessListener(queryDocumentSnapshots -> {
            potholeList.clear();
            potholeIds.clear();

            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                addUniquePothole(doc);
            }

            potholeList.sort((a,b) -> {
                if (a.getTimestamp() == null || b.getTimestamp() == null) return 0;
                return b.getTimestamp().compareTo(a.getTimestamp());
            });

            adapter.notifyDataSetChanged();

            if (potholeList.isEmpty()) {
                Toast.makeText(requireContext(), "No potholes found.", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error loading all potholes", e);
            Toast.makeText(requireContext(), "Failed to load potholes.", Toast.LENGTH_SHORT).show();
        });
    }


    private void loadPersonalizedPotholes() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Please log in to see tracked potholes.", Toast.LENGTH_LONG).show();
            potholeList.clear();
            adapter.notifyDataSetChanged();
            return;
        }

        String userId = currentUser.getUid();

        var reportedTask = db.collection("potholes")
                .whereEqualTo("detectedBy", userId)
                .get();

        var followedTask = db.collection("potholes")
                .whereArrayContains("followers", userId)
                .get();

        Tasks.whenAllSuccess(reportedTask, followedTask).addOnSuccessListener(results -> {
            potholeList.clear();
            potholeIds.clear();

            QuerySnapshot reported = (QuerySnapshot) results.get(0);
            QuerySnapshot followed = (QuerySnapshot) results.get(1);

            for (DocumentSnapshot doc : reported) addUniquePothole(doc);
            for (DocumentSnapshot doc : followed) addUniquePothole(doc);

            potholeList.sort((a, b) -> {
                if (a.getTimestamp() == null || b.getTimestamp() == null) return 0;
                return b.getTimestamp().compareTo(a.getTimestamp());
            });

            adapter.notifyDataSetChanged();

            if (potholeList.isEmpty()) {
                Toast.makeText(requireContext(), "You are not tracking any potholes.", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error fetching potholes", e);
            Toast.makeText(requireContext(), "Failed to load tracked potholes.", Toast.LENGTH_SHORT).show();
        });
    }

    private void addUniquePothole(DocumentSnapshot doc) {
        if (potholeIds.contains(doc.getId())) return;
        Pothole pothole = doc.toObject(Pothole.class);
        if (pothole != null) {
            pothole.setId(doc.getId());
            if ((pothole.getVehicleType() == null) && doc.contains("vehicle_type")) {
                pothole.setVehicleType(doc.getString("vehicle_type"));
            }
            potholeList.add(pothole);
            potholeIds.add(doc.getId());
        }
    }

    // -------------------- Adapter --------------------
    private static class PotholeViewHolder {
        TextView id, severity, location, creator, date, vehicle;
        PotholeViewHolder(View v) {
            id = v.findViewById(R.id.potholeId);
            severity = v.findViewById(R.id.severityText);
            location = v.findViewById(R.id.locationText);
            creator = v.findViewById(R.id.creatorText);
            date = v.findViewById(R.id.dateText);
            vehicle = v.findViewById(R.id.vehicleText);
        }
    }

    private class PotholeAdapter extends android.widget.BaseAdapter {
        private final List<Pothole> potholes;
        PotholeAdapter(List<Pothole> list) { this.potholes = list; }

        @Override public int getCount() { return potholes.size(); }
        @Override public Object getItem(int pos) { return potholes.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @NonNull
        @Override
        public View getView(int pos, View convertView, @NonNull ViewGroup parent) {
            PotholeViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.pothole_item, parent, false);
                holder = new PotholeViewHolder(convertView);
                convertView.setTag(holder);
            } else holder = (PotholeViewHolder) convertView.getTag();

            Pothole p = potholes.get(pos);

            holder.id.setText("Pothole ID: " + (p.getId() != null ? p.getId() : "N/A"));
            holder.severity.setText("Severity: " + (p.getSeverity() != null ? p.getSeverity() : "Unknown"));

            if (p.getLocation() != null) {
                holder.location.setText(String.format(Locale.getDefault(),
                        "Location: %.5f, %.5f",
                        p.getLocation().getLatitude(),
                        p.getLocation().getLongitude()));
            } else holder.location.setText("Location: Unknown");

            holder.creator.setText("Reported by: " +
                    (p.getDetectedBy() != null ? p.getDetectedBy() : "Anonymous"));

            Timestamp ts = p.getTimestamp();
            if (ts != null) {
                long daysAgo = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - ts.toDate().getTime());
                holder.date.setText("Reported " + daysAgo + " day(s) ago");
            } else holder.date.setText("Date: Unknown");

            return convertView;
        }
    }
}
