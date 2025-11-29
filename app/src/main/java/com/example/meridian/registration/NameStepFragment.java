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

public class NameStepFragment extends Fragment {

    private EditText etFirstName, etLastName;
    private Button btnNext;
    private Button fabBack;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_register_name, container, false);
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        etFirstName = view.findViewById(R.id.et_first_name);
        etLastName = view.findViewById(R.id.et_last_name);
        btnNext = view.findViewById(R.id.btn_next_name);
        fabBack = view.findViewById(R.id.fab_back);


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


            ((RegisterActivity) requireActivity()).onNameStepCompleted(first, last);
        });
    }

}
