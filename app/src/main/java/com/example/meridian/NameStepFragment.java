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

public class NameStepFragment extends Fragment {

    private EditText etFirstName, etLastName;
    private Button btnNext;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_register_name, container, false);

        etFirstName = view.findViewById(R.id.et_first_name);
        etLastName = view.findViewById(R.id.et_last_name);
        btnNext = view.findViewById(R.id.btn_next_name);

        btnNext.setOnClickListener(v -> {
            String first = etFirstName.getText().toString().trim();
            String last = etLastName.getText().toString().trim();

            if (TextUtils.isEmpty(first) || TextUtils.isEmpty(last)) {
                etLastName.setError("Please fill both fields");
                return;
            }

            ((RegisterActivity) requireActivity()).onNameStepCompleted(first, last);
        });

        return view;
    }
}
