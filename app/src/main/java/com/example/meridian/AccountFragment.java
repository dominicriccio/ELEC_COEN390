package com.example.meridian;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class AccountFragment extends Fragment {

    private static final String TAG = "AccountFragment";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FrameLayout rootContainer;
    private View loggedInView;
    private View loggedOutView;
    private RecyclerView reportsRecyclerView;
    private ReportsAdapter reportsAdapter;
    private final List<PotholeReport> userReports = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Create a single root container for the fragment's lifecycle
        rootContainer = new FrameLayout(requireContext());

        // Inflate the views but do not attach them to any parent yet
        loggedInView = inflater.inflate(R.layout.fragment_account_logged_in, rootContainer, false);
        loggedOutView = inflater.inflate(R.layout.fragment_account_logged_out, rootContainer, false);

        reportsRecyclerView = loggedInView.findViewById(R.id.rv_reports);
        reportsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        reportsAdapter = new ReportsAdapter(userReports, report -> {
            ReportFragment dialog = new ReportFragment();

            Bundle args = new Bundle();
            args.putString("id", report.id);
            args.putString("status", report.status);
            args.putString("severity", report.severity);
            args.putDouble("latitude", report.getLatitude());
            args.putDouble("longitude", report.getLongitude());
            args.putString("timestamp", report.timestamp != null ? report.timestamp.toDate().toString() : "");
            dialog.setArguments(args);

            dialog.show(getParentFragmentManager(), "reportDetails");
        });
        reportsRecyclerView.setAdapter(reportsAdapter);

        // Setup the interactive elements for each view
        setupLoggedInView(loggedInView);
        setupLoggedOutView(loggedOutView);

        // Return the container that will manage the views
        return rootContainer;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateView(); // Refresh state each time fragment resumes
    }

    private void updateView() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        rootContainer.removeAllViews(); // Prevent view stacking

        if (currentUser != null) {
            rootContainer.addView(loggedInView);
            loadUserDetails(currentUser.getUid());
            loadUserReports(currentUser.getUid());
        } else {
            rootContainer.addView(loggedOutView);
        }
    }

    private void loadUserDetails(String userId) {
        if (userId == null || userId.isEmpty()) {
            Log.e(TAG, "loadUserDetails called with null/empty userId.");
            return;
        }

        db.collection("users").document(userId).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        TextView tvFullName = loggedInView.findViewById(R.id.tv_user_full_name);
                        TextView tvAddress = loggedInView.findViewById(R.id.tv_user_address);
                        TextView tvVehicle = loggedInView.findViewById(R.id.tv_user_vehicle);
                        TextView tvMemberSince = loggedInView.findViewById(R.id.tv_member_since);
                        Chip adminBadge = loggedInView.findViewById(R.id.chip_admin_badge);


                        String name = document.getString("name");
                        String surname = document.getString("surname");
                        String address = document.getString("address");
                        String role = document.getString("role");
                        String vehicle = document.getString("vehicle_type");

                        tvFullName.setText("Name: " + (name != null ? name : "") + " " + (surname != null ? surname : ""));
                        tvAddress.setText("Address: " + (address != null ? address : "Not Provided"));

                        if (tvVehicle!= null) {
                            tvVehicle.setText("Vehicle: " + (vehicle != null ? vehicle : "Not Provided"));
                        }

                        if ("admin".equals(role)) {
                            adminBadge.setVisibility(View.VISIBLE); // Show the badge for admins
                        } else {
                            adminBadge.setVisibility(View.GONE);  // Hide it for everyone else
                        }

                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null && user.getMetadata() != null) {
                            long creationTimestamp = user.getMetadata().getCreationTimestamp();
                            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
                            tvMemberSince.setText("Member Since: " + sdf.format(new Date(creationTimestamp)));
                        }
                    } else {
                        Log.w(TAG, "No user document found for UID: " + userId);
                        Toast.makeText(getContext(), "Could not find user details.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch user details for UID: " + userId, e);
                    Toast.makeText(getContext(), "Error loading details.", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadUserReports(String userId) {
        db.collection("potholes")
                .whereEqualTo("detectedBy", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    userReports.clear();
                    if (queryDocumentSnapshots.isEmpty()) {
                        Log.d(TAG, "No reports found for user: " + userId);
                    } else {
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            String id = document.getId();
                            String status = document.getString("status");
                            Timestamp timestamp = document.getTimestamp("timestamp");
                            String severity = document.getString("severity");
                            GeoPoint location = document.getGeoPoint("location");

                            userReports.add(new PotholeReport(id, status, severity, timestamp, location));

                            Log.d(TAG, "Loaded pothole " + id + " at " +
                                    (location != null ? location.getLatitude() + ", " + location.getLongitude() : "null"));
                        }
                    }
                    reportsAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading user reports", e);
                    Toast.makeText(getContext(), "Failed to load reports.", Toast.LENGTH_SHORT).show();
                });
    }

    private void setupLoggedOutView(View view) {
        Button btnLogin = view.findViewById(R.id.btn_go_to_login);
        Button btnRegister = view.findViewById(R.id.btn_go_to_register);
        btnLogin.setOnClickListener(v -> startActivity(new Intent(getActivity(), LoginActivity.class)));
        btnRegister.setOnClickListener(v -> startActivity(new Intent(getActivity(), RegisterActivity.class)));
    }

    private void setupLoggedInView(View view) {
        Button btnLogout = view.findViewById(R.id.btn_logout);
        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            Toast.makeText(getContext(), "Logged out", Toast.LENGTH_SHORT).show();
            updateView();
        });
        //settings
        View btnEditProfile = view.findViewById(R.id.btn_edit_profile);
        if (btnEditProfile != null) {
            btnEditProfile.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), SettingsActivity.class);
                startActivity(intent);
            });
        }
    }

    private static class PotholeReport {
        String id;
        String status;
        String severity;
        Timestamp timestamp;
        GeoPoint location;

        PotholeReport(String id, String status, String severity, Timestamp timestamp, GeoPoint location) {
            this.id = id;
            this.status = status;
            this.severity = severity;
            this.timestamp = timestamp;
            this.location = location;
        }

        double getLatitude() {
            return location != null ? location.getLatitude() : 0.0;
        }

        double getLongitude() {
            return location != null ? location.getLongitude() : 0.0;
        }
    }

    private static class ReportViewHolder extends RecyclerView.ViewHolder {
        TextView tvPotholeId;
        TextView tvReportStatus;

        public ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPotholeId = itemView.findViewById(R.id.tv_pothole_id);
            tvReportStatus = itemView.findViewById(R.id.tv_report_status);
        }
    }

    private static class ReportsAdapter extends RecyclerView.Adapter<ReportViewHolder> {

        public interface OnReportClickListener {
            void onReportClick(PotholeReport report);
        }

        private final List<PotholeReport> reports;
        private final OnReportClickListener listener;

        ReportsAdapter(List<PotholeReport> reports, OnReportClickListener listener) {
            this.reports = reports;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.report_item, parent, false);
            return new ReportViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
            PotholeReport report = reports.get(position);

            holder.tvPotholeId.setText("Pothole ID: " + report.id);

            long daysAgo = 0;
            if (report.timestamp != null) {
                long diffInMillis = new Date().getTime() - report.timestamp.toDate().getTime();
                daysAgo = TimeUnit.DAYS.convert(diffInMillis, TimeUnit.MILLISECONDS);
            }

            holder.tvReportStatus.setText(
                    String.format(Locale.getDefault(), "Status: %s (%dd)", report.status, daysAgo)
            );

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onReportClick(report);
            });
        }

        @Override
        public int getItemCount() {
            return reports.size();
        }

    }
}
