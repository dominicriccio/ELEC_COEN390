package com.example.meridian;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AccountFragment extends Fragment {

    private FirebaseAuth mAuth;
    private View loggedInView;
    private View loggedOutView;

    public AccountFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate both possible layouts
        loggedOutView = inflater.inflate(R.layout.fragment_account_logged_out, container, false);
        loggedInView = inflater.inflate(R.layout.fragment_account_logged_in, container, false);

        // Decide which view to return based on the current login state
        return updateView();
    }

    @Override
    public void onResume() {
        super.onResume();
        // The view needs to be updated when the user comes back to this fragment,
        // for example, after logging in.
        updateView();
    }

    private View updateView() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // User is logged in, show the logged-in view
            setupLoggedInView(loggedInView);
            // If the logged-out view is currently being shown, we need to switch it.
            // This logic is tricky with onCreateView, so we handle it by replacing the content.
            if (getView() != null && getView() != loggedInView) {
                ViewGroup parent = (ViewGroup) getView().getParent();
                if (parent != null) {
                    parent.removeView(getView());
                    parent.addView(loggedInView);
                }
            }
            return loggedInView;
        } else {
            // User is logged out, show the logged-out view
            setupLoggedOutView(loggedOutView);
            if (getView() != null && getView() != loggedOutView) {
                ViewGroup parent = (ViewGroup) getView().getParent();
                if (parent != null) {
                    parent.removeView(getView());
                    parent.addView(loggedOutView);
                }
            }
            return loggedOutView;
        }
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
            updateView(); // Refresh the view to show the logged-out state
        });
    }
}
