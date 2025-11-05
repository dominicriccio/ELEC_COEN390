package com.example.meridian;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
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

        loadPersonalizedPotholes();
        return view;
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
            potholeList.add(pothole);
            potholeIds.add(doc.getId());
        }
    }

    // -------------------- Adapter --------------------
    private static class PotholeViewHolder {
        TextView id, severity, location, creator, date;
        PotholeViewHolder(View v) {
            id = v.findViewById(R.id.potholeId);
            severity = v.findViewById(R.id.severityText);
            location = v.findViewById(R.id.locationText);
            creator = v.findViewById(R.id.creatorText);
            date = v.findViewById(R.id.dateText);
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
