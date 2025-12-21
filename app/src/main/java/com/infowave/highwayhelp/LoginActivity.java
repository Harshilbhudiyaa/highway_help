package com.infowave.highwayhelp;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
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
    TextView tvGoToRegister, tvMobileLabel, tvWelcome, tvSubtitle, tvNewUser;
    ProgressBar progressBar;
    ApiInterface apiInterface;
    SessionManager sessionManager;
    String fcmToken = ""; // Token yahan aayega

    // For dynamic translation
    com.google.android.material.textfield.TextInputLayout tilPhone;
    String currentLanguageCode = "en";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);
        if (sessionManager.isLoggedIn()) {
            goToMain();
        }

        setContentView(R.layout.activity_login);

        // Initialize views
        etPhone = findViewById(R.id.etPhone);
        btnLogin = findViewById(R.id.btnLogin);
        tvGoToRegister = findViewById(R.id.tvGoToRegister);
        tvMobileLabel = findViewById(R.id.tvMobileLabel);
        tvWelcome = findViewById(R.id.tvWelcome);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        tvNewUser = findViewById(R.id.tvNewUser);
        progressBar = findViewById(R.id.progressBar);
        tilPhone = findViewById(R.id.tilPhone);

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

        // Load saved language and translate form automatically
        currentLanguageCode = sessionManager.getLanguage();
        translateForm(currentLanguageCode);

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
                // Translate error message
                String errorMsg = "Enter 10 digit number";
                if (!currentLanguageCode.equals("en")) {
                    TranslationHelper.translateText(errorMsg, currentLanguageCode,
                            new TranslationHelper.TranslationCallback() {
                                @Override
                                public void onTranslationComplete(String translatedText) {
                                    etPhone.setError(translatedText);
                                }

                                @Override
                                public void onTranslationError(String error) {
                                    etPhone.setError(errorMsg);
                                }
                            });
                } else {
                    etPhone.setError(errorMsg);
                }
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
                        Toast.makeText(LoginActivity.this, "Error: " + response.body().get("message").getAsString(),
                                Toast.LENGTH_LONG).show();
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
            if (otp.length() >= 4) {
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
                        } catch (Exception e) {
                        }

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

    /**
     * Translate all form elements to the selected language
     */
    private void translateForm(String targetLang) {
        // If English, set original text
        if (targetLang.equals("en")) {
            tvWelcome.setText("Welcome Back");
            tvSubtitle.setText("Login to continue");
            tvMobileLabel.setText("Mobile Number");
            tilPhone.setHint("98765 43210");
            btnLogin.setText("Secure Login");
            tvNewUser.setText("New User? ");
            tvGoToRegister.setText("Create Account");
            return;
        }

        // Texts to translate
        String[] textsToTranslate = {
                "Welcome Back",
                "Login to continue",
                "Mobile Number",
                "98765 43210",
                "Secure Login",
                "New User? ",
                "Create Account"
        };

        // Translate all at once
        TranslationHelper.translateMultiple(textsToTranslate, targetLang,
                new TranslationHelper.MultiTranslationCallback() {
                    @Override
                    public void onTranslationComplete(String[] translatedTexts) {
                        // Update UI with translated text
                        tvWelcome.setText(translatedTexts[0]);
                        tvSubtitle.setText(translatedTexts[1]);
                        tvMobileLabel.setText(translatedTexts[2]);
                        tilPhone.setHint(translatedTexts[3]);
                        btnLogin.setText(translatedTexts[4]);
                        tvNewUser.setText(translatedTexts[5]);
                        tvGoToRegister.setText(translatedTexts[6]);
                    }

                    @Override
                    public void onTranslationError(String error) {
                        Toast.makeText(LoginActivity.this, "Translation error: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
    }
}