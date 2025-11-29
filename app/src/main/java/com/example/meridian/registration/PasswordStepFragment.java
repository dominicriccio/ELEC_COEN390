package com.example.meridian.registration;import android.os.Bundle;
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

import com.example.meridian.R;

public class PasswordStepFragment extends Fragment {

    private EditText etPassword, etConfirmPassword, etAdminCode;
    private CheckBox cbAdminToggle;
    private Button btnNext;
    private Button fabBack;

    private static final String SECRET_ADMIN_CODE = "Admin123";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_register_password, container, false);
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        etPassword        = view.findViewById(R.id.et_password);
        etConfirmPassword = view.findViewById(R.id.et_confirm_password);
        etAdminCode       = view.findViewById(R.id.et_admin_code);
        cbAdminToggle     = view.findViewById(R.id.cb_admin_toggle);
        btnNext           = view.findViewById(R.id.btn_next_password);
        fabBack           = view.findViewById(R.id.fab_back);


        fabBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });


        etAdminCode.setVisibility(View.GONE);

        cbAdminToggle.setOnCheckedChangeListener((button, isChecked) -> {
            etAdminCode.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        btnNext.setOnClickListener(v -> validateAndContinue());
    }



    private void validateAndContinue() {
        String pass = etPassword.getText().toString().trim();
        String confirm = etConfirmPassword.getText().toString().trim();


        if (TextUtils.isEmpty(pass) || pass.length() < 6) {
            etPassword.setError("Minimum 6 characters");
            return;
        }

        if (!pass.equals(confirm)) {
            etConfirmPassword.setError("Passwords do not match");
            return;
        }

        boolean wantsAdmin = cbAdminToggle.isChecked();


        if (wantsAdmin) {
            String adminCode = etAdminCode.getText().toString().trim();
            if (!SECRET_ADMIN_CODE.equals(adminCode)) {
                etAdminCode.setError("Invalid admin code");
                return;
            }
        }


        ((RegisterActivity) requireActivity())
                .onPasswordStepCompleted(pass, confirm, wantsAdmin ? "admin" : "user");
    }
}
