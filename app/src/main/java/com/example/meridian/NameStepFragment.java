package com.example.meridian;

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

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class NameStepFragment extends Fragment {

    private EditText etFirstName, etLastName;
    private Button btnNext;
    private Button fabBack; // Changed to private

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // STEP 1: ONLY inflate and return the view here.
        return inflater.inflate(R.layout.fragment_register_name, container, false);
    }

    // --- START: NEW onViewCreated METHOD ---
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // STEP 2: All view logic now goes in here.
        etFirstName = view.findViewById(R.id.et_first_name);
        etLastName = view.findViewById(R.id.et_last_name);
        btnNext = view.findViewById(R.id.btn_next_name);
        fabBack = view.findViewById(R.id.fab_back);

        // This OnClickListener will now work correctly.
        fabBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().finish();
            }
        });

        btnNext.setOnClickListener(v -> {
            String first = etFirstName.getText().toString().trim();
            String last = etLastName.getText().toString().trim();

            if (TextUtils.isEmpty(first) || TextUtils.isEmpty(last)) {
                etLastName.setError("Please fill both fields");
                return;
            }

            // Continue to next step
            ((RegisterActivity) requireActivity()).onNameStepCompleted(first, last);
        });
    }
    // --- END: NEW onViewCreated METHOD ---
}
