package com.infowave.highwayhelp;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.messaging.FirebaseMessaging; // Import Zaroori Hai
import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    TextInputEditText etPhone;
    MaterialButton btnLogin;
    TextView tvGoToRegister;
    ProgressBar progressBar;
    ApiInterface apiInterface;
    SessionManager sessionManager;
    String fcmToken = ""; // Token yahan aayega

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);
        if (sessionManager.isLoggedIn()) {
            goToMain();
        }

        setContentView(R.layout.activity_login);

        etPhone = findViewById(R.id.etPhone);
        btnLogin = findViewById(R.id.btnLogin);
        tvGoToRegister = findViewById(R.id.tvGoToRegister);
        progressBar = findViewById(R.id.progressBar);

        apiInterface = ApiClient.getClient().create(ApiInterface.class);

        // 1. TOKEN FETCH KARO (Sabse Pehle)
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        if (!task.isSuccessful()) {
                            Log.w("FCM", "Fetching FCM registration token failed", task.getException());
                            return;
                        }
                        // Get new FCM registration token
                        fcmToken = task.getResult();
                        // Log.d("FCM", "User Token: " + fcmToken);
                    }
                });

        tvGoToRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            finish();
        });

        btnLogin.setOnClickListener(v -> {
            String phone = etPhone.getText().toString().trim();
            if (phone.length() == 10) {
                // Agar token mil gaya hai tabhi login karo, warna wait karo ya retry karo
                if (!fcmToken.isEmpty()) {
                    loginUser(phone);
                } else {
                    // Fallback: Retry getting token
                    FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
                        fcmToken = token;
                        loginUser(phone);
                    });
                }
            } else {
                etPhone.setError("Enter 10 digit number");
            }
        });
    }

    private void loginUser(String phone) {
        progressBar.setVisibility(android.view.View.VISIBLE);
        btnLogin.setEnabled(false);
        btnLogin.setText("Processing...");

        // 2. TOKEN API KO BHEJO
        Call<JsonObject> call = apiInterface.loginUser("login", phone, "User", fcmToken);

        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                progressBar.setVisibility(android.view.View.GONE);
                btnLogin.setEnabled(true);
                btnLogin.setText("Secure Login");

                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().get("success").getAsBoolean()) {
                        showOtpBottomSheet(phone);
                    } else {
                        Toast.makeText(LoginActivity.this, "Error: " + response.body().get("message").getAsString(), Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                progressBar.setVisibility(android.view.View.GONE);
                btnLogin.setEnabled(true);
                btnLogin.setText("Secure Login");
                Toast.makeText(LoginActivity.this, "Connection Error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showOtpBottomSheet(String phone) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        bottomSheetDialog.setContentView(R.layout.bottom_sheet_otp);
        bottomSheetDialog.setCancelable(true);

        EditText etOtpInput = bottomSheetDialog.findViewById(R.id.etOtpInput);
        Button btnVerifyOtp = bottomSheetDialog.findViewById(R.id.btnVerifyOtp);
        ProgressBar progressOtp = bottomSheetDialog.findViewById(R.id.progressOtp);
        TextView tvTimer = bottomSheetDialog.findViewById(R.id.tvTimer);
        TextView tvResendBtn = bottomSheetDialog.findViewById(R.id.tvResendBtn);

        new CountDownTimer(30000, 1000) {
            public void onTick(long millisUntilFinished) {
                tvTimer.setText("Resend code in 00:" + millisUntilFinished / 1000);
            }
            public void onFinish() {
                tvTimer.setText("Didn't receive code?");
                tvResendBtn.setVisibility(android.view.View.VISIBLE);
            }
        }.start();

        tvResendBtn.setOnClickListener(v -> {
            loginUser(phone);
            tvResendBtn.setVisibility(android.view.View.GONE);
            Toast.makeText(this, "OTP Resent", Toast.LENGTH_SHORT).show();
        });

        btnVerifyOtp.setOnClickListener(v -> {
            String otp = etOtpInput.getText().toString().trim();
            if(otp.length() >= 4) {
                verifyOtp(phone, otp, bottomSheetDialog, progressOtp);
            } else {
                Toast.makeText(this, "Enter Valid OTP", Toast.LENGTH_SHORT).show();
            }
        });

        bottomSheetDialog.show();
    }

    private void verifyOtp(String phone, String otp, BottomSheetDialog dialog, ProgressBar loader) {
        loader.setVisibility(android.view.View.VISIBLE);

        Call<JsonObject> call = apiInterface.verifyOtp(phone, otp);
        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                loader.setVisibility(android.view.View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().get("success").getAsBoolean()) {

                        String userId = "0";
                        try {
                            if (response.body().has("user_id")) {
                                userId = response.body().get("user_id").getAsString();
                            }
                        } catch (Exception e) {}

                        sessionManager.createLoginSession(userId, phone);

                        Toast.makeText(LoginActivity.this, "Login Successful!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        goToMain();
                    } else {
                        Toast.makeText(LoginActivity.this, "Wrong OTP", Toast.LENGTH_SHORT).show();
                    }
                }
            }
            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                loader.setVisibility(android.view.View.GONE);
                Toast.makeText(LoginActivity.this, "Network Error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void goToMain() {
        startActivity(new Intent(LoginActivity.this, MainActivity.class));
        finishAffinity();
    }
}