package com.example.meridian;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ReviewStepFragment extends Fragment {

    private static final String ARG_FIRST = "firstName";
    private static final String ARG_LAST = "lastName";
    private static final String ARG_EMAIL = "email";
    private static final String ARG_ADDRESS = "address";

    public static ReviewStepFragment newInstance(
            String first, String last, String email, String address) {

        ReviewStepFragment fragment = new ReviewStepFragment();

        Bundle b = new Bundle();
        b.putString(ARG_FIRST, first);
        b.putString(ARG_LAST, last);
        b.putString(ARG_EMAIL, email);
        b.putString(ARG_ADDRESS, address);
        fragment.setArguments(b);

        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_register_review, container, false);

        TextView tvName = view.findViewById(R.id.tv_review_name);
        TextView tvEmail = view.findViewById(R.id.tv_review_email);
        TextView tvAddress = view.findViewById(R.id.tv_review_address);
        Button btnSubmit = view.findViewById(R.id.btn_create_account);

        if (getArguments() != null) {
            tvName.setText(getArguments().getString(ARG_FIRST) + " " +
                    getArguments().getString(ARG_LAST));
            tvEmail.setText(getArguments().getString(ARG_EMAIL));
            tvAddress.setText(getArguments().getString(ARG_ADDRESS));
        }

        btnSubmit.setOnClickListener(v ->
                ((RegisterActivity) requireActivity()).onSubmitRegistration()
        );

        return view;
    }
}
