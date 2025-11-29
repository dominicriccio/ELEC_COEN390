package com.example.meridian.notifications;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;

import java.util.Locale;

public class NotificationListenerManager {

    private static ListenerRegistration listener;
    private static final String PREFS = "notif_prefs";
    private static final String LAST_TS_KEY = "last_notification_timestamp";

    public static void start(Context context) {

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long lastSeenTs = prefs.getLong(LAST_TS_KEY, 0);

        listener = db.collection("users")
                .document(user.getUid())
                .collection("notifications")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, err) -> {

                    if (err != null || snap == null) return;

                    long newLastTs = lastSeenTs;

                    for (DocumentChange change : snap.getDocumentChanges()) {
                        DocumentSnapshot doc = change.getDocument();

                        com.google.firebase.Timestamp ts = doc.getTimestamp("timestamp");
                        if (ts == null) continue;

                        long tsLong = ts.toDate().getTime();


                        if (tsLong <= lastSeenTs) continue;


                        String type = doc.getString("type");
                        if (type == null) continue;

                        String title = "";
                        String message = "";

                        switch (type) {

                            case "pothole_update":
                                String newStatus = doc.getString("newStatus");
                                if (newStatus == null) continue;

                                title = "Pothole Update";
                                message = "A pothole you're following is now: " + newStatus;
                                break;

                            case "closure_nearby":
                                Double dist = doc.getDouble("distanceKm");
                                if (dist == null) continue;

                                title = "Road Closure Nearby";
                                message = String.format(
                                        Locale.getDefault(),
                                        "A new road closure is %.1f km from your home.",
                                        dist
                                );
                                break;

                            default:
                                continue;
                        }


                        NotificationHelper.showNotification(context, title, message);

                        if (tsLong > newLastTs)
                            newLastTs = tsLong;
                    }


                    prefs.edit().putLong(LAST_TS_KEY, newLastTs).apply();
                });
    }

    public static void stop() {
        if (listener != null) listener.remove();
    }
}
