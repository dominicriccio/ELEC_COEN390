package com.example.meridian;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
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
import java.util.Map;

public class NotificationsFragment extends Fragment {

    private RecyclerView recyclerView;
    private NotificationsAdapter adapter;
    private List<UserNotification> notifications = new ArrayList<>();

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private UserNotification recentlyDeletedNotif;
    private int recentlyDeletedPos;


    // =========================================================
    //  MODEL
    // =========================================================
    public static class UserNotification {
        public String id;
        public String type;        // "pothole_update" or "closure_nearby"
        public String potholeId;   // OR closureId (same field)
        public String newStatus;
        public Double distanceKm;
        public Timestamp timestamp;

        public UserNotification() {}
    }


    // =========================================================
    //  MAIN
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

        ItemTouchHelper.SimpleCallback swipeCallback =
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

                    @Override
                    public boolean onMove(@NonNull RecyclerView recyclerView,
                                          @NonNull RecyclerView.ViewHolder viewHolder,
                                          @NonNull RecyclerView.ViewHolder target) {
                        return false;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                        int pos = viewHolder.getBindingAdapterPosition();
                        UserNotification deletedNotif = notifications.get(pos);

                        // Remove from list + UI immediately
                        notifications.remove(pos);
                        adapter.notifyItemRemoved(pos);

                        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                        if (user == null) return;

                        FirebaseFirestore db = FirebaseFirestore.getInstance();

                        // 🔥 DELETE FROM FIRESTORE
                        db.collection("users")
                                .document(user.getUid())
                                .collection("notifications")
                                .document(deletedNotif.id)
                                .delete()
                                .addOnSuccessListener(a -> {
                                    // Optionally toast if you want
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(getContext(), "Failed to delete", Toast.LENGTH_SHORT).show();
                                });

                        // Optional Undo
                        Snackbar.make(recyclerView, "Notification deleted", Snackbar.LENGTH_LONG)
                                .setAction("Undo", v -> {
                                    // Firestore restore
                                    db.collection("users")
                                            .document(user.getUid())
                                            .collection("notifications")
                                            .document(deletedNotif.id)
                                            .set(deletedNotif);

                                    // Local restore
                                    notifications.add(pos, deletedNotif);
                                    adapter.notifyItemInserted(pos);
                                })
                                .show();
                    }


                    @Override
                    public void onChildDraw(@NonNull Canvas c,
                                            @NonNull RecyclerView recyclerView,
                                            @NonNull RecyclerView.ViewHolder viewHolder,
                                            float dX, float dY,
                                            int actionState, boolean isCurrentlyActive) {

                        super.onChildDraw(c, recyclerView, viewHolder, dX, dY,
                                actionState, isCurrentlyActive);

                        View itemView = viewHolder.itemView;
                        Paint paint = new Paint();

                        // Draw red background
                        paint.setColor(Color.parseColor("#D32F2F")); // red 700
                        float cornerRadius = 40f; // adjust as you like

                        RectF background = new RectF(
                                itemView.getRight() + dX,
                                itemView.getTop(),
                                itemView.getRight(),
                                itemView.getBottom()
                        );
                        c.drawRoundRect(background, cornerRadius, cornerRadius, paint);


                        // Draw trash icon
                        Drawable icon = getResources().getDrawable(R.drawable.ic_delete_white_24);
                        int iconMargin = (itemView.getHeight() - icon.getIntrinsicHeight()) / 2;
                        int iconTop = itemView.getTop() + (itemView.getHeight() - icon.getIntrinsicHeight()) / 2;
                        int iconLeft = itemView.getRight() - iconMargin - icon.getIntrinsicWidth();
                        int iconRight = itemView.getRight() - iconMargin;
                        int iconBottom = iconTop + icon.getIntrinsicHeight();

                        icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                        icon.draw(c);
                    }
                };

        new ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView);


        return view;
    }

    private void showUndoSnackbar() {
        Snackbar snackbar = Snackbar.make(
                requireView(),
                "Notification deleted",
                Snackbar.LENGTH_LONG
        );

        snackbar.setAction("UNDO", v -> {
            notifications.add(recentlyDeletedPos, recentlyDeletedNotif);
            adapter.notifyItemInserted(recentlyDeletedPos);
            recyclerView.scrollToPosition(recentlyDeletedPos);

            recentlyDeletedNotif = null; // don't delete from DB
        });

        snackbar.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar transientBottomBar, int event) {
                if (event != Snackbar.Callback.DISMISS_EVENT_ACTION &&
                        recentlyDeletedNotif != null) {

                    // User did NOT press undo → delete in Firestore
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        db.collection("users")
                                .document(user.getUid())
                                .collection("notifications")
                                .document(recentlyDeletedNotif.id)
                                .delete();
                    }
                }
            }
        });

        snackbar.show();
    }


    // =========================================================
    //  LOAD NOTIFICATIONS
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
                        String potId = doc.getString("potholeId");  // for pothole updates
                        String closureId = doc.getString("closureId"); // for closure notifications

                        n.potholeId = (potId != null) ? potId : closureId;
                        n.newStatus = doc.getString("newStatus");
                        n.distanceKm = doc.getDouble("distanceKm");
                        n.timestamp = doc.getTimestamp("timestamp");

                        notifications.add(n);
                    }

                    adapter.notifyDataSetChanged();
                });
    }

    private void openClosureFromNotification(UserNotification notif) {
        if (notif.potholeId == null) {  // potholeId = closureId here
            Toast.makeText(getContext(), "No closure linked to this notification", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("closures").document(notif.potholeId)
                .get()
                .addOnSuccessListener(doc -> {

                    if (!doc.exists()) {
                        Toast.makeText(getContext(), "Closure no longer exists", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Build closure fragment
                    ClosureFragment f = new ClosureFragment();
                    Bundle args = new Bundle();

                    args.putString("id", doc.getId());
                    args.putString("description", doc.getString("description"));
                    args.putLong("endDate", doc.getLong("endDate") != null ? doc.getLong("endDate") : 0);

                    // Convert list of lat/lng maps into arrays
                    ArrayList<Double> lats = new ArrayList<>();
                    ArrayList<Double> lngs = new ArrayList<>();

                    List<Object> pts = (List<Object>) doc.get("points");
                    if (pts != null) {
                        for (Object o : pts) {
                            if (o instanceof java.util.Map) {
                                Map<String, Object> m = (Map<String, Object>) o;
                                lats.add((Double) m.get("lat"));
                                lngs.add((Double) m.get("lng"));
                            }
                        }
                    }

                    args.putSerializable("lats", lats);
                    args.putSerializable("lngs", lngs);

                    f.setArguments(args);
                    f.show(getParentFragmentManager(), "closure_details");
                });
    }

    private void openPotholeFromNotification(UserNotification notif) {
        if (notif.potholeId == null) {
            Toast.makeText(getContext(), "No pothole linked to this notification", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("potholes").document(notif.potholeId).get()
                .addOnSuccessListener(doc -> {

                    if (!doc.exists()) {
                        Toast.makeText(getContext(), "Pothole no longer exists", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Pothole p = doc.toObject(Pothole.class);
                    if (p == null) return;

                    // Build and show the ReportFragment
                    ReportFragment f = new ReportFragment();
                    Bundle args = new Bundle();

                    args.putString("id", notif.potholeId);
                    args.putString("status", doc.getString("status"));
                    args.putString("severity", doc.getString("severity"));
                    args.putBoolean("allowDelete", false);

                    if (p.getLocation() != null) {
                        args.putDouble("latitude", p.getLocation().getLatitude());
                        args.putDouble("longitude", p.getLocation().getLongitude());
                    }

                    args.putLong("timestampMillis",
                            p.getTimestamp() != null ? p.getTimestamp().toDate().getTime() : 0L
                    );

                    // Already following if notification exists
                    args.putBoolean("isFollowing", true);

                    f.setArguments(args);
                    f.show(getParentFragmentManager(), "notif_pothole_details");
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

                v.setOnClickListener(click -> {
                    int pos = getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        UserNotification notif = list.get(pos);

                        if ("pothole_update".equals(notif.type)) {
                            openPotholeFromNotification(notif);
                        }
                        else if ("closure_nearby".equals(notif.type)) {
                            Toast.makeText(getContext(), "TODO: Open closure details", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
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

            if ("pothole_update".equals(n.type)) {
                h.tvTitle.setText("Pothole Update");
                h.tvSubtitle.setText("Status changed to: " + n.newStatus);
            }
            else if ("closure_nearby".equals(n.type)) {
                h.tvTitle.setText("Road Closure Near You");

                if (n.distanceKm != null) {
                    h.tvSubtitle.setText(
                            String.format("Closure detected %.1f km from your home", n.distanceKm)
                    );
                } else {
                    h.tvSubtitle.setText("A closure near your location was detected");
                }
            }
            else {
                h.tvTitle.setText("Notification");
                h.tvSubtitle.setText("");
            }

            if (n.timestamp != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
                h.tvDate.setText(sdf.format(n.timestamp.toDate()));
            } else {
                h.tvDate.setText("");
            }

            h.itemView.setOnClickListener(v -> {
                UserNotification notif = list.get(h.getBindingAdapterPosition());

                if ("pothole_update".equals(notif.type)) {
                    openPotholeFromNotification(notif);
                }
                else if ("closure_nearby".equals(notif.type)) {
                    openClosureFromNotification(notif);
                }
            });

        }


        @Override
        public int getItemCount() {
            return list.size();
        }
    }
}
