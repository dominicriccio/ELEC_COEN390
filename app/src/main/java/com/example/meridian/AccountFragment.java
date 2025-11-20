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
        rootContainer = new FrameLayout(requireContext());

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

        setupLoggedInView(loggedInView);
        setupLoggedOutView(loggedOutView);

        return rootContainer;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateView();
    }

    private void updateView() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        rootContainer.removeAllViews();

        if (currentUser != null) {
            rootContainer.addView(loggedInView);
            loadUserDetails(currentUser.getUid());
            loadUserReports(currentUser.getUid());
        } else {
            rootContainer.addView(loggedOutView);
        }
    }

    // --------------------------------------------------------------------
    // LOAD USER DETAILS (FIXED FOR NEW FIRESTORE ADDRESS FORMAT)
    // --------------------------------------------------------------------
    private void loadUserDetails(String userId) {

        db.collection("users").document(userId).get()
                .addOnSuccessListener(document -> {

                    if (!document.exists()) return;

                    TextView tvFullName = loggedInView.findViewById(R.id.tv_user_full_name);
                    TextView tvAddress = loggedInView.findViewById(R.id.tv_user_address);
                    TextView tvMemberSince = loggedInView.findViewById(R.id.tv_member_since);
                    Chip adminBadge = loggedInView.findViewById(R.id.chip_admin_badge);

                    String name = document.getString("name");
                    String surname = document.getString("surname");
                    tvFullName.setText("Name: " + name + " " + surname);

                    // ----------- FIX: READ ONLY NEW FIRESTORE FIELDS -----------
                    String address = document.getString("address");
                    if (address == null || address.isEmpty()) {
                        tvAddress.setText("Address: Not Provided");
                    } else {
                        tvAddress.setText("Address: " + address);
                    }

                    // ADMIN BADGE
                    String role = document.getString("role");
                    if ("admin".equals(role)) adminBadge.setVisibility(View.VISIBLE);
                    else adminBadge.setVisibility(View.GONE);

                    // MEMBER SINCE
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null && user.getMetadata() != null) {
                        long created = user.getMetadata().getCreationTimestamp();
                        String formatted = new SimpleDateFormat("MM/dd/yyyy", Locale.CANADA)
                                .format(new Date(created));
                        tvMemberSince.setText("Member Since: " + formatted);
                    }
                })
                .addOnFailureListener(err ->
                        Log.e(TAG, "Error reading user profile", err));
    }

    // --------------------------------------------------------------------
    // LOAD USER REPORTS
    // --------------------------------------------------------------------
    private void loadUserReports(String userId) {

        db.collection("potholes")
                .whereEqualTo("detectedBy", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(result -> {

                    userReports.clear();

                    for (QueryDocumentSnapshot doc : result) {

                        String id = doc.getId();
                        String status = doc.getString("status");
                        Timestamp timestamp = doc.getTimestamp("timestamp");
                        String severity = doc.getString("severity");
                        GeoPoint location = doc.getGeoPoint("location");

                        userReports.add(new PotholeReport(id, status, severity, timestamp, location));
                    }

                    reportsAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Failed to load reports.", Toast.LENGTH_SHORT).show());
    }

    // --------------------------------------------------------------------
    // LOGGED OUT VIEW SETUP
    // --------------------------------------------------------------------
    private void setupLoggedOutView(View view) {
        Button btnLogin = view.findViewById(R.id.btn_go_to_login);
        Button btnRegister = view.findViewById(R.id.btn_go_to_register);

        btnLogin.setOnClickListener(v -> startActivity(new Intent(getActivity(), LoginActivity.class)));
        btnRegister.setOnClickListener(v -> startActivity(new Intent(getActivity(), RegisterActivity.class)));
    }

    // --------------------------------------------------------------------
    // LOGGED IN VIEW SETUP
    // --------------------------------------------------------------------
    private void setupLoggedInView(View view) {

        Button btnLogout = view.findViewById(R.id.btn_logout);
        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            Toast.makeText(getContext(), "Logged out", Toast.LENGTH_SHORT).show();
            updateView();
        });

        View btnEditProfile = view.findViewById(R.id.btn_edit_profile);
        btnEditProfile.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), SettingsActivity.class)));
    }

    // --------------------------------------------------------------------
    // REPORT MODELS
    // --------------------------------------------------------------------
    private static class PotholeReport {
        String id, status, severity;
        Timestamp timestamp;
        GeoPoint location;

        PotholeReport(String id, String status, String severity,
                      Timestamp timestamp, GeoPoint location) {
            this.id = id;
            this.status = status;
            this.severity = severity;
            this.timestamp = timestamp;
            this.location = location;
        }

        double getLatitude() { return location != null ? location.getLatitude() : 0.0; }
        double getLongitude() { return location != null ? location.getLongitude() : 0.0; }
    }

    private static class ReportViewHolder extends RecyclerView.ViewHolder {
        TextView tvPotholeId, tvReportStatus;

        public ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPotholeId = itemView.findViewById(R.id.tv_pothole_id);
            tvReportStatus = itemView.findViewById(R.id.tv_report_status);
        }
    }

    private static class ReportsAdapter extends RecyclerView.Adapter<ReportViewHolder> {

        interface OnReportClickListener { void onReportClick(PotholeReport report); }

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
                long diff = new Date().getTime() - report.timestamp.toDate().getTime();
                daysAgo = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS);
            }

            holder.tvReportStatus.setText(String.format(Locale.getDefault(),
                    "Status: %s (%dd)", report.status, daysAgo));

            holder.itemView.setOnClickListener(v -> listener.onReportClick(report));
        }

        @Override
        public int getItemCount() { return reports.size(); }
    }
}
