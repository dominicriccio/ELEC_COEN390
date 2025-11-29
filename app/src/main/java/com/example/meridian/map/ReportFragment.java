package com.example.meridian.map;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.meridian.R;
import com.example.meridian.navigation.TrackingFragment;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReportFragment extends DialogFragment implements OnMapReadyCallback {

    private MapView mapView;
    private GoogleMap googleMap;
    boolean allowDelete;
    private double latitude, longitude;
    private String potholeId;
    private boolean isFollowing;

    private ReportFragmentListener listener;

    public interface ReportFragmentListener {
        void onFollowStatusChanged();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);


        Fragment parent = getParentFragment();
        if (parent instanceof ReportFragmentListener) {
            listener = (ReportFragmentListener) parent;
        }
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        View view = inflater.inflate(R.layout.fragment_report, container, false);

        TextView tvDetails = view.findViewById(R.id.tvDetails);
        ImageView starIcon = view.findViewById(R.id.iv_star);
        Button btnDelete = view.findViewById(R.id.btnDeletePothole);
        mapView = view.findViewById(R.id.mapView);


        btnDelete.setVisibility(View.GONE);


        String status = "Unknown";
        String severity = "Unknown";
        String formattedDate = "Unknown";

        if (getArguments() != null) {
            potholeId = getArguments().getString("id", null);
            status = getArguments().getString("status", "Unknown");
            severity = getArguments().getString("severity", "Unknown");
            isFollowing = getArguments().getBoolean("isFollowing", true);
            allowDelete = getArguments().getBoolean("allowDelete", false);

            long tsMillis = getArguments().getLong("timestampMillis", 0L);
            String timestampString = getArguments().getString("timestamp", null);

            if (tsMillis > 0) {
                Date date = new Date(tsMillis);
                formattedDate = new SimpleDateFormat("EEEE, MMM dd yyyy", Locale.getDefault())
                        .format(date);
            } else if (timestampString != null) {
                try {
                    SimpleDateFormat parser =
                            new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH);
                    Date parsed = parser.parse(timestampString);
                    if (parsed != null) {
                        formattedDate = new SimpleDateFormat("EEEE, MMM dd yyyy", Locale.getDefault())
                                .format(parsed);
                    }
                } catch (ParseException ignored) {
                }
            }

            latitude = getArguments().getDouble("latitude", 0);
            longitude = getArguments().getDouble("longitude", 0);
        }


        tvDetails.setText(String.format(
                Locale.getDefault(),
                "ID: %s\nStatus: %s\nSeverity: %s\nReported on: %s",
                potholeId, status, severity, formattedDate
        ));


        starIcon.setImageResource(isFollowing
                ? R.drawable.ic_star_filled
                : R.drawable.ic_star_border);

        starIcon.setOnClickListener(v -> toggleFollow(starIcon));


        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);


        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String uid = FirebaseAuth.getInstance().getUid();

        if (uid != null) {
            if(allowDelete) {
                db.collection("users").document(uid).get()
                        .addOnSuccessListener(doc -> {
                            if (doc.exists() && "admin".equals(doc.getString("role"))) {
                                btnDelete.setVisibility(View.VISIBLE);
                                btnDelete.setOnClickListener(v -> deletePothole());
                            }
                        });
            } else {
                btnDelete.setVisibility(View.GONE);
            }
        }

        return view;
    }


    private void deletePothole() {
        if (potholeId == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("potholes")
                .document(potholeId)
                .delete()
                .addOnSuccessListener(a -> {
                    Toast.makeText(requireContext(), "Pothole deleted", Toast.LENGTH_SHORT).show();
                    dismiss();


                    FragmentManager fm = getParentFragmentManager();
                    Fragment f = fm.findFragmentByTag("TRACKING_FRAGMENT");

                    if (f instanceof TrackingFragment) {
                        ((TrackingFragment) f).forceReload();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Delete failed", Toast.LENGTH_SHORT).show()
                );
    }

    private void toggleFollow(ImageView starIcon) {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null || potholeId == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        if (isFollowing) {
            db.collection("potholes").document(potholeId)
                    .update("followers", FieldValue.arrayRemove(userId))
                    .addOnSuccessListener(aVoid -> {
                        isFollowing = false;
                        starIcon.setImageResource(R.drawable.ic_star_border);

                        Toast.makeText(getContext(), "Unfollowed", Toast.LENGTH_SHORT).show();

                        if (listener != null) listener.onFollowStatusChanged();
                    });
        } else {
            db.collection("potholes").document(potholeId)
                    .update("followers", FieldValue.arrayUnion(userId))
                    .addOnSuccessListener(aVoid -> {
                        isFollowing = true;
                        starIcon.setImageResource(R.drawable.ic_star_filled);

                        Toast.makeText(getContext(), "Following", Toast.LENGTH_SHORT).show();

                        if (listener != null) listener.onFollowStatusChanged();
                    });
        }
    }


    @Override
    public void onMapReady(@NonNull GoogleMap gMap) {
        googleMap = gMap;
        LatLng loc = new LatLng(latitude, longitude);
        googleMap.addMarker(new MarkerOptions().position(loc).title("Pothole Location"));
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(loc, 16f));
        googleMap.getUiSettings().setAllGesturesEnabled(false);
    }

    @Override public void onResume() { super.onResume(); mapView.onResume(); }
    @Override public void onPause() { super.onPause(); mapView.onPause(); }
    @Override public void onDestroy() { super.onDestroy(); mapView.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            int width = (int) (requireContext().getResources().getDisplayMetrics().widthPixels * 0.9);
            getDialog().getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }
}
