package com.example.meridian;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class PasswordStepFragment extends Fragment {

    private EditText etPassword, etConfirmPassword, etAdminCode;
    private CheckBox cbAdminToggle;
    private Button btnNext;

    private static final String SECRET_ADMIN_CODE = "Admin123";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_register_password, container, false);

        etPassword       = view.findViewById(R.id.et_password);
        etConfirmPassword = view.findViewById(R.id.et_confirm_password);
        etAdminCode       = view.findViewById(R.id.et_admin_code);
        cbAdminToggle     = view.findViewById(R.id.cb_admin_toggle);
        btnNext           = view.findViewById(R.id.btn_next_password);

        // Hide admin code field initially
        etAdminCode.setVisibility(View.GONE);

        cbAdminToggle.setOnCheckedChangeListener((button, isChecked) -> {
            etAdminCode.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        btnNext.setOnClickListener(v -> validateAndContinue());

        return view;
    }

    private void validateAndContinue() {
        String pass = etPassword.getText().toString().trim();
        String confirm = etConfirmPassword.getText().toString().trim();

        // Password checks
        if (TextUtils.isEmpty(pass) || pass.length() < 6) {
            etPassword.setError("Minimum 6 characters");
            return;
        }

        if (!pass.equals(confirm)) {
            etConfirmPassword.setError("Passwords do not match");
            return;
        }

        boolean wantsAdmin = cbAdminToggle.isChecked();

        // If admin mode enabled → validate secret code
        if (wantsAdmin) {
            String adminCode = etAdminCode.getText().toString().trim();
            if (!SECRET_ADMIN_CODE.equals(adminCode)) {
                etAdminCode.setError("Invalid admin code");
                return;
            }
        }

        // Continue to next step (+ role)
        ((RegisterActivity) requireActivity())
                .onPasswordStepCompleted(pass, confirm, wantsAdmin ? "admin" : "user");
    }
}
