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
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NotificationsFragment extends Fragment {

    private RecyclerView recyclerView;
    private NotificationsAdapter adapter;
    private List<UserNotification> notifications = new ArrayList<>();

    private ListenerRegistration notificationListener;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // =========================================================
    // USER NOTIFICATION MODEL
    // =========================================================
    public static class UserNotification {
        public String id;
        public String type;
        public String message;
        public String potholeId;
        public Timestamp timestamp;

        public UserNotification() {} // Needed for Firestore

        public UserNotification(String id, String type, String message, String potholeId, Timestamp timestamp) {
            this.id = id;
            this.type = type;
            this.message = message;
            this.potholeId = potholeId;
            this.timestamp = timestamp;
        }
    }

    // =========================================================
    // FRAGMENT LIFECYCLE
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (notificationListener != null) {
            notificationListener.remove();
        }
    }

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

                        String type = doc.getString("type");
                        String potholeId = doc.getString("potholeId");
                        String msg = "";

                        if ("pothole_update".equals(type)) {
                            String newStatus = doc.getString("newStatus");
                            msg = "Pothole \"" + potholeId + "\" status updated to: " + newStatus;
                        }

                        Timestamp ts = doc.getTimestamp("timestamp");

                        notifications.add(new UserNotification(
                                doc.getId(),
                                type,
                                msg,
                                potholeId,
                                ts
                        ));
                    }

                    adapter.notifyDataSetChanged();
                });
    }


    // =========================================================
    // RECYCLER VIEW ADAPTER
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

            h.tvTitle.setText("Pothole Update");
            h.tvSubtitle.setText(n.message);

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
