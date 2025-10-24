package com.example.meridian;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FeedFragment extends Fragment {

    private static final String TAG = "FeedFragment";
    private FirebaseFirestore db;
    private ListView listView;
    private final List<Pothole> potholeList = new ArrayList<>();
    private PotholeAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_feed, container, false);

        listView = view.findViewById(R.id.listView);
        db = FirebaseFirestore.getInstance();

        adapter = new PotholeAdapter(potholeList);
        listView.setAdapter(adapter);

        loadAllPotholes();

        return view;
    }

    /**
     * Loads all potholes from the Firestore database.
     */
    private void loadAllPotholes() {
        db.collection("potholes")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    potholeList.clear();

                    for (DocumentSnapshot doc : querySnapshot) {
                        try {
                            // Assuming your Pothole class has a no-arg constructor
                            // and proper Firestore mapping annotations or fields
                            Pothole pothole = doc.toObject(Pothole.class);
                            if (pothole != null) {
                                // optionally store Firestore document ID
                                pothole.setId(doc.getId());
                                potholeList.add(pothole);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error converting document to Pothole", e);
                        }
                    }

                    adapter.notifyDataSetChanged();

                    if (potholeList.isEmpty()) {
                        Toast.makeText(requireContext(), "No potholes found.", Toast.LENGTH_SHORT).show();
                    }

                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching potholes", e);
                    Toast.makeText(requireContext(), "Failed to load potholes.", Toast.LENGTH_SHORT).show();
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
