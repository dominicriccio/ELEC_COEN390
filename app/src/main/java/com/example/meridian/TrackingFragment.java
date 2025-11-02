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
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.google.android.gms.tasks.Task;

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

        loadPersonalizedPotholes();

        Button realTimeBtn = view.findViewById(R.id.realtimeButton);
        realTimeBtn.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), RealTimeDataActivity.class);
            startActivity(intent);
        });

        return view;
    }

    /**
     * Loads all potholes from the Firestore database.
     */

    private void loadPersonalizedPotholes() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Please log in to see your tracked potholes.", Toast.LENGTH_LONG).show();
            potholeList.clear();
            if (adapter != null) { // Add a null check for safety
                adapter.notifyDataSetChanged();
            }
            return;
        }

        String userId = currentUser.getUid();

        // --- START OF FIX ---
        // Use the correct Task class: com.google.android.gms.tasks.Task

        // Query 1: Potholes reported BY the user
        Task<QuerySnapshot> reportedTask = db.collection("potholes")
                .whereEqualTo("detectedBy", userId)
                .get();

        // Query 2: Potholes followed BY the user
        Task<QuerySnapshot> followedTask = db.collection("potholes")
                .whereArrayContains("followers", userId)
                .get();

        // --- END OF FIX ---


        // Combine the results of both tasks (This part is now correct because the Task types match)
        Tasks.whenAllSuccess(reportedTask, followedTask).addOnSuccessListener(results -> {
            potholeList.clear();
            potholeIds.clear();

            // Process results from the first query (reported)
            QuerySnapshot reportedSnapshots = (QuerySnapshot) results.get(0);
            for (DocumentSnapshot doc : reportedSnapshots) {
                if (!potholeIds.contains(doc.getId())) {
                    Pothole pothole = doc.toObject(Pothole.class);
                    if (pothole != null) {
                        pothole.setId(doc.getId());
                        potholeList.add(pothole);
                        potholeIds.add(doc.getId());
                    }
                }
            }

            // Process results from the second query (followed)
            QuerySnapshot followedSnapshots = (QuerySnapshot) results.get(1);
            for (DocumentSnapshot doc : followedSnapshots) {
                // The Set ensures we don't add duplicates
                if (!potholeIds.contains(doc.getId())) {
                    Pothole pothole = doc.toObject(Pothole.class);
                    if (pothole != null) {
                        pothole.setId(doc.getId());
                        potholeList.add(pothole);
                        potholeIds.add(doc.getId());
                    }
                }
            }

            // Sort the final list by timestamp (most recent first)
            potholeList.sort((p1, p2) -> {
                if (p1.getTimestamp() == null || p2.getTimestamp() == null) return 0;
                return p2.getTimestamp().compareTo(p1.getTimestamp());
            });

            adapter.notifyDataSetChanged();

            if (potholeList.isEmpty()) {
                Toast.makeText(requireContext(), "You are not tracking any potholes.", Toast.LENGTH_SHORT).show();
            }

        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error fetching personalized potholes", e);
            Toast.makeText(requireContext(), "Failed to load tracked potholes.", Toast.LENGTH_SHORT).show();
        });
    }

    // --------------------------------
    // ViewHolder Class
    // --------------------------------
    private static class PotholeViewHolder {
        TextView potholeId, severityText, locationText, creatorText, dateText;

        PotholeViewHolder(View itemView) {
            potholeId = itemView.findViewById(R.id.potholeId);
            severityText = itemView.findViewById(R.id.severityText);
            locationText = itemView.findViewById(R.id.locationText);
            creatorText = itemView.findViewById(R.id.creatorText);
            dateText = itemView.findViewById(R.id.dateText);
        }
    }

    // --------------------------------
    // Adapter Class (inline)
    // --------------------------------
    private class PotholeAdapter extends android.widget.BaseAdapter {
        private final List<Pothole> potholes;

        PotholeAdapter(List<Pothole> potholes) {
            this.potholes = potholes;
        }

        @Override
        public int getCount() {
            return potholes.size();
        }

        @Override
        public Object getItem(int position) {
            return potholes.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            PotholeViewHolder holder;

            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.pothole_item, parent, false);
                holder = new PotholeViewHolder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (PotholeViewHolder) convertView.getTag();
            }

            Pothole pothole = potholes.get(position);

            // Defensive null checks — avoid crashes if a field is missing
            holder.potholeId.setText("Pothole ID: " + (pothole.getId() != null ? pothole.getId() : "N/A"));
            holder.severityText.setText("Severity: " + (pothole.getSeverity() != null ? pothole.getSeverity() : "Unknown"));

            if (pothole.getLocation() != null) {
                double lat = pothole.getLocation().getLatitude();
                double lon = pothole.getLocation().getLongitude();
                holder.locationText.setText(String.format(Locale.getDefault(), "Location: %.5f, %.5f", lat, lon));
            } else {
                holder.locationText.setText("Location: Unknown");
            }

            holder.creatorText.setText("Reported by: " + (pothole.getDetectedBy() != null ? pothole.getDetectedBy() : "Anonymous"));

            Timestamp ts = pothole.getTimestamp();
            if (ts != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
                holder.dateText.setText("Date Created: " + sdf.format(ts.toDate()));
            } else {
                holder.dateText.setText("Date Created: Unknown");
            }

            return convertView;
        }
    }
}
