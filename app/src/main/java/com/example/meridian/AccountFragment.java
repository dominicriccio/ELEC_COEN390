package com.example.meridian;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log; // FIX: Use the correct android.util.Log
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

import com.google.firebase.firestore.Query;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
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
        reportsAdapter = new ReportsAdapter(userReports);
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
        // This is the ideal place to update the view based on authentication state
        updateView();
    }

    private void updateView() {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        // Always start by clearing the container to prevent view stacking
        rootContainer.removeAllViews();

        if (currentUser != null) {
            // User is logged in, add the correct view
            rootContainer.addView(loggedInView);
            // And load their specific data
            loadUserDetails(currentUser.getUid());
            loadUserReports(currentUser.getUid());
        } else {
            // User is logged out, add the other view
            rootContainer.addView(loggedOutView);
        }
    }

    private void loadUserDetails(String userId) {
        // Sanity check for the user ID
        if (userId == null || userId.isEmpty()) {
            Log.e(TAG, "loadUserDetails was called with a null or empty userId.");
            return;
        }

        db.collection("users").document(userId).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        Log.d(TAG, "User document found for UID: " + userId);
                        // Find the TextViews within the currently attached loggedInView
                        TextView tvFullName = loggedInView.findViewById(R.id.tv_user_full_name);
                        TextView tvAddress = loggedInView.findViewById(R.id.tv_user_address);
                        TextView tvMemberSince = loggedInView.findViewById(R.id.tv_member_since);

                        // Safely extract data
                        String name = document.getString("name");
                        String surname = document.getString("surname");
                        String address = document.getString("address");

                        // Populate the views
                        tvFullName.setText("Name: " + (name != null ? name : "") + " " + (surname != null ? surname : ""));
                        tvAddress.setText("Address: " + (address != null ? address : "Not Provided"));

                        // Get creation date from auth metadata
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
                .orderBy("timestamp", Query.Direction.DESCENDING) // Show newest first
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
                            userReports.add(new PotholeReport(id, status, timestamp));
                        }
                    }
                    reportsAdapter.notifyDataSetChanged(); // Refresh the list
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
            updateView(); // Refresh the UI to show the logged-out state
        });
        //settings
        View btnEditProfile = view.findViewById(R.id.btn_edit_profile);
        if (btnEditProfile != null) {
            btnEditProfile.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), EditProfileActivity.class);
                startActivity(intent);
            });
        }
    }

    private static class PotholeReport {
        String id;
        String status;
        Timestamp timestamp;

        PotholeReport(String id, String status, Timestamp timestamp) {
            this.id = id;
            this.status = status;
            this.timestamp = timestamp;
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

    private class ReportsAdapter extends RecyclerView.Adapter<ReportViewHolder> {
        private final List<PotholeReport> reports;

        ReportsAdapter(List<PotholeReport> reports) {
            this.reports = reports;
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
        }

        @Override
        public int getItemCount() {
            return reports.size();
        }
    }
}
