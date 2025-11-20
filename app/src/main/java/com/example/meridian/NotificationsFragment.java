package com.example.meridian;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NotificationsFragment extends Fragment {

    private RecyclerView recyclerView;
    private NotificationsAdapter adapter;
    private List<UserNotification> notifications = new ArrayList<>();

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // =========================================================
    //  MODEL
    // =========================================================
    public static class UserNotification {
        public String id;
        public String type;
        public String potholeId;
        public String newStatus;     // for potholes
        public Double distanceKm;    // for closures
        public Timestamp timestamp;

        public UserNotification() {}
    }

    // =========================================================
    //  LIFECYCLE
    // =========================================================
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_notifications, container, false);

        recyclerView = view.findViewById(R.id.rv_notifications);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new NotificationsAdapter(notifications);
        recyclerView.setAdapter(adapter);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        loadNotificationsOnce();

        return view;
    }

    // =========================================================
    //  LOAD NOTIFICATIONS FROM FIRESTORE ONCE
    // =========================================================
    private void loadNotificationsOnce() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        db.collection("users")
                .document(user.getUid())
                .collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snap -> {

                    notifications.clear();

                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        UserNotification n = new UserNotification();

                        n.id = doc.getId();
                        n.type = doc.getString("type");
                        n.potholeId = doc.getString("potholeId");
                        n.newStatus = doc.getString("newStatus");
                        n.distanceKm = doc.getDouble("distanceKm");
                        n.timestamp = doc.getTimestamp("timestamp");

                        notifications.add(n);
                    }

                    adapter.notifyDataSetChanged();
                });
    }

    // =========================================================
    //  ADAPTER
    // =========================================================
    public class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.ViewHolder> {

        private final List<UserNotification> list;

        public NotificationsAdapter(List<UserNotification> list) {
            this.list = list;
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvSubtitle, tvDate;
            public ViewHolder(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.notification_title);
                tvSubtitle = v.findViewById(R.id.notification_subtitle);
                tvDate = v.findViewById(R.id.notification_date);
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_notification, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
            UserNotification n = list.get(pos);

            // Title + message logic
            if ("pothole_update".equals(n.type)) {
                h.tvTitle.setText("Pothole Update");
                h.tvSubtitle.setText("Status changed to: " + n.newStatus);
            }
            else if ("closure_nearby".equals(n.type)) {
                h.tvTitle.setText("Road Closure Near You");
                if (n.distanceKm != null) {
                    h.tvSubtitle.setText(
                            "A closure was detected " +
                                    String.format("%.1f", n.distanceKm) +
                                    " km from your home."
                    );
                } else {
                    h.tvSubtitle.setText("A nearby closure was detected.");
                }
            }
            else {
                h.tvTitle.setText("Notification");
                h.tvSubtitle.setText("");
            }

            // Timestamp
            if (n.timestamp != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
                h.tvDate.setText(sdf.format(n.timestamp.toDate()));
            } else {
                h.tvDate.setText("");
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }
    }
}
