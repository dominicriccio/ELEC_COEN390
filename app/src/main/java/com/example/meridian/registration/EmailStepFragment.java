package com.example.meridian.registration;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.meridian.R;

public class EmailStepFragment extends Fragment {

    private EditText etEmail;
    private Button btnNext;
    private Button fabBack; // Changed to private


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // STEP 1: ONLY inflate and return the view here.
        return inflater.inflate(R.layout.fragment_register_email, container, false);
    }

    // --- START: NEW onViewCreated METHOD ---
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // STEP 2: All view logic now goes in here.
        etEmail = view.findViewById(R.id.et_email);
        btnNext = view.findViewById(R.id.btn_next_email);
        fabBack = view.findViewById(R.id.fab_back);

        // This OnClickListener will now work correctly.
        fabBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });

        btnNext.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();

            if (TextUtils.isEmpty(email) ||
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.setError("Enter a valid email");
                return;
            }

            // Continue to next step
            ((RegisterActivity) requireActivity()).onEmailStepCompleted(email);
        });
    }
    // --- END: NEW onViewCreated METHOD ---
}
