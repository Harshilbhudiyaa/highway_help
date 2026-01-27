package com.infowave.highwayhelp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.InputFilter;
import android.text.method.DigitsKeyListener;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
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
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.gson.JsonObject;

import java.util.concurrent.atomic.AtomicBoolean;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etPhone;
    private MaterialButton btnLogin;
    private TextView tvGoToRegister, tvMobileLabel, tvWelcome, tvSubtitle, tvNewUser;
    private ProgressBar progressBar;
    private TextInputLayout tilPhone;

    private ApiInterface apiInterface;
    private SessionManager sessionManager;
    private String fcmToken = "";

    private String currentLanguageCode = "en";

    // Single-flight / debounce
    private final AtomicBoolean loginInFlight = new AtomicBoolean(false);
    private final AtomicBoolean otpInFlight   = new AtomicBoolean(false);
    private final AtomicBoolean resendInFlight= new AtomicBoolean(false);

    // Network call refs (cancel onDestroy)
    private Call<JsonObject> loginCall;
    private Call<JsonObject> verifyCall;
    private Call<JsonObject> resendCall;

    // OTP UI state
    private BottomSheetDialog otpSheet;
    private CountDownTimer otpTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);
        if (sessionManager.isLoggedIn()) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_login);

        // Views
        etPhone        = findViewById(R.id.etPhone);
        btnLogin       = findViewById(R.id.btnLogin);
        tvGoToRegister = findViewById(R.id.tvGoToRegister);
        tvMobileLabel  = findViewById(R.id.tvMobileLabel);
        tvWelcome      = findViewById(R.id.tvWelcome);
        tvSubtitle     = findViewById(R.id.tvSubtitle);
        tvNewUser      = findViewById(R.id.tvNewUser);
        progressBar    = findViewById(R.id.progressBar);
        tilPhone       = findViewById(R.id.tilPhone);

        apiInterface   = ApiClient.getClient().create(ApiInterface.class);

        // Phone input: 10 digits, digits-only, enable button only when valid
        etPhone.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(10) });
        etPhone.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
        btnLogin.setEnabled(false);
        etPhone.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                btnLogin.setEnabled(s != null && s.length() == 10);
                if (tilPhone != null) tilPhone.setError(null);
            }
        });

        // Get FCM token (non-blocking)
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override public void onComplete(@NonNull Task<String> task) {
                        if (task.isSuccessful()) fcmToken = task.getResult();
                    }
                });

        // Language & translation
        currentLanguageCode = sessionManager.getLanguage();
        translateForm(currentLanguageCode);

        tvGoToRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            finish();
        });

        btnLogin.setOnClickListener(v -> {
            if (loginInFlight.getAndSet(true)) return;

            String phone = safe(etPhone);
            if (phone.length() != 10) {
                setFieldError(tilPhone, etPhone, t("Enter 10 digit number"));
                loginInFlight.set(false);
                return;
            }

            hideKeyboard(v);
            startLogin(phone);
        });
    }

    /* -------------------- Login & OTP -------------------- */

    @SuppressLint("SetTextI18n")
    private void startLogin(String phone) {
        progressBar.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);
        btnLogin.setText(t("Processing..."));

        String tokenForNow = (fcmToken == null) ? "" : fcmToken;

        loginCall = apiInterface.loginUser("login", phone, "User", tokenForNow);
        loginCall.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                progressBar.setVisibility(View.GONE);
                btnLogin.setEnabled(true);
                btnLogin.setText(t("Secure Login"));
                loginInFlight.set(false);

                if (response.isSuccessful() && response.body() != null
                        && getBool(response.body(), "success", false)) {
                    showOtpBottomSheet(phone);
                } else {
                    String msg = response.body() != null && response.body().has("message")
                            ? response.body().get("message").getAsString()
                            : t("Connection Error");
                    Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t1) {
                progressBar.setVisibility(View.GONE);
                btnLogin.setEnabled(true);
                btnLogin.setText(t("Secure Login"));
                loginInFlight.set(false);
                Toast.makeText(LoginActivity.this, t("Connection Error"), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showOtpBottomSheet(String phone) {
        // Close previous if any
        if (otpSheet != null && otpSheet.isShowing()) otpSheet.dismiss();

        otpSheet = new BottomSheetDialog(this);
        otpSheet.setContentView(R.layout.bottom_sheet_otp);
        otpSheet.setCancelable(true);
        otpSheet.setCanceledOnTouchOutside(false);

        EditText etOtpInput      = otpSheet.findViewById(R.id.etOtpInput);
        Button btnVerifyOtp      = otpSheet.findViewById(R.id.btnVerifyOtp);
        ProgressBar progressOtp  = otpSheet.findViewById(R.id.progressOtp);
        TextView tvTimer         = otpSheet.findViewById(R.id.tvTimer);
        TextView tvResendBtn     = otpSheet.findViewById(R.id.tvResendBtn);

        if (etOtpInput == null || btnVerifyOtp == null || tvTimer == null || tvResendBtn == null) {
            Toast.makeText(this, t("Connection Error"), Toast.LENGTH_SHORT).show();
            return;
        }

        // OTP field config
        etOtpInput.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(6) });
        btnVerifyOtp.setEnabled(false);

        etOtpInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                btnVerifyOtp.setEnabled(s != null && s.length() == 6 && !otpInFlight.get());
            }
        });
        etOtpInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        etOtpInput.setOnEditorActionListener((v, actionId, event) -> {
            if (v.getText() != null && v.getText().length() == 6 && btnVerifyOtp.isEnabled()) {
                startVerifyOtp(phone, v.getText().toString().trim(), otpSheet, progressOtp, btnVerifyOtp);
                return true;
            }
            return false;
        });

        // Start resend countdown 30s
        startResendCountdown(tvTimer, tvResendBtn);

        tvResendBtn.setOnClickListener(v -> {
            if (resendInFlight.getAndSet(true)) return;
            tvResendBtn.setEnabled(false);
            // Re-hit login to trigger resend
            String tokenForNow = (fcmToken == null) ? "" : fcmToken;
            resendCall = apiInterface.loginUser("login", phone, "User", tokenForNow);
            resendCall.enqueue(new Callback<JsonObject>() {
                @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                    resendInFlight.set(false);
                    Toast.makeText(LoginActivity.this, t("OTP Resent"), Toast.LENGTH_SHORT).show();
                    startResendCountdown(tvTimer, tvResendBtn);
                }
                @Override public void onFailure(Call<JsonObject> call, Throwable t1) {
                    resendInFlight.set(false);
                    Toast.makeText(LoginActivity.this, t("Connection Error"), Toast.LENGTH_SHORT).show();
                    // Allow another attempt
                    startResendCountdown(tvTimer, tvResendBtn);
                }
            });
        });

        btnVerifyOtp.setOnClickListener(v -> {
            if (otpInFlight.get()) return;
            String otp = etOtpInput.getText() == null ? "" : etOtpInput.getText().toString().trim();
            if (otp.length() != 6) {
                Toast.makeText(this, t("Enter Valid OTP"), Toast.LENGTH_SHORT).show();
                return;
            }
            startVerifyOtp(phone, otp, otpSheet, progressOtp, btnVerifyOtp);
        });

        // Back key behavior: allow dismiss unless verifying
        otpSheet.setOnKeyListener((dialog, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                if (otpInFlight.get()) return true; // block back while verifying
                cancelOtpTimer();
                if (verifyCall != null) verifyCall.cancel();
                dialog.dismiss();
                return true;
            }
            return false;
        });

        otpSheet.setOnDismissListener(d -> {
            cancelOtpTimer();
            if (verifyCall != null) verifyCall.cancel();
            otpInFlight.set(false);
        });

        otpSheet.show();
    }

    private void startVerifyOtp(String phone, String otp, BottomSheetDialog dialog,
                                ProgressBar loader, Button btnVerify) {
        otpInFlight.set(true);
        btnVerify.setEnabled(false);
        btnVerify.setText(t("Processing..."));
        if (loader != null) loader.setVisibility(View.VISIBLE);

        verifyCall = apiInterface.verifyOtp(phone, otp);
        verifyCall.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                otpInFlight.set(false);
                btnVerify.setEnabled(true);
                btnVerify.setText(t("Verify"));
                if (loader != null) loader.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null && getBool(response.body(), "success", false)) {
                    String userId = "0";
                    try {
                        if (response.body().has("user_id") && !response.body().get("user_id").isJsonNull()) {
                            userId = response.body().get("user_id").getAsString();
                        }
                    } catch (Exception ignore) {}

                    sessionManager.createLoginSession(userId, phone);
                    Toast.makeText(LoginActivity.this, t("Login Successful!"), Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    goToMain();
                } else {
                    Toast.makeText(LoginActivity.this, t("Wrong OTP"), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t1) {
                otpInFlight.set(false);
                btnVerify.setEnabled(true);
                btnVerify.setText(t("Verify"));
                if (loader != null) loader.setVisibility(View.GONE);
                Toast.makeText(LoginActivity.this, t("Network Error"), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startResendCountdown(TextView tvTimer, TextView tvResendBtn) {
        cancelOtpTimer();
        tvResendBtn.setVisibility(View.GONE);
        tvResendBtn.setEnabled(false);

        otpTimer = new CountDownTimer(30000, 1000) {
            @SuppressLint({"SetTextI18n", "DefaultLocale"})
            public void onTick(long millisUntilFinished) {
                long sec = millisUntilFinished / 1000;
                tvTimer.setText(t("Resend code in ") + String.format("00:%02d", sec));
            }
            @Override
            public void onFinish() {
                tvTimer.setText(t("Didn't receive code?"));
                tvResendBtn.setVisibility(View.VISIBLE);
                tvResendBtn.setEnabled(true);
            }
        }.start();
    }

    private void cancelOtpTimer() {
        if (otpTimer != null) {
            otpTimer.cancel();
            otpTimer = null;
        }
    }

    private void goToMain() {
        startActivity(new Intent(LoginActivity.this, MainActivity.class));
        finishAffinity();
    }

    /* -------------------- Translation helpers -------------------- */

    private String t(String english) {
        // If you want to live-translate these too, route through TranslationHelper.
        // For now we show English literals here to keep this method fast.
        return english;
    }

    @SuppressLint("SetTextI18n")
    private void translateForm(String targetLang) {
        if (targetLang == null) targetLang = "en";
        if (targetLang.equals("en")) {
            tvWelcome.setText("Welcome Back");
            tvSubtitle.setText("Login to continue");
            tvMobileLabel.setText("Mobile Number");
            if (tilPhone != null) tilPhone.setHint("98765 43210");
            btnLogin.setText("Secure Login");
            tvNewUser.setText("New User? ");
            tvGoToRegister.setText("Create Account");
            return;
        }

        String[] textsToTranslate = {
                "Welcome Back","Login to continue","Mobile Number","98765 43210",
                "Secure Login","New User? ","Create Account"
        };

        TranslationHelper.translateMultiple(textsToTranslate, targetLang,
                new TranslationHelper.MultiTranslationCallback() {
                    @Override public void onTranslationComplete(String[] t) {
                        tvWelcome.setText(t[0]);
                        tvSubtitle.setText(t[1]);
                        tvMobileLabel.setText(t[2]);
                        if (tilPhone != null) tilPhone.setHint(t[3]);
                        btnLogin.setText(t[4]);
                        tvNewUser.setText(t[5]);
                        tvGoToRegister.setText(t[6]);
                    }
                    @Override public void onTranslationError(String error) {
                        Toast.makeText(LoginActivity.this, "Translation error: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /* -------------------- Small utilities -------------------- */

    private void setFieldError(TextInputLayout til, EditText et, String msg) {
        if (til != null) {
            til.setError(msg);
            et.postDelayed(() -> til.setError(null), 2000);
        } else {
            et.setError(msg);
        }
    }

    private String safe(EditText et) {
        return (et == null || et.getText() == null) ? "" : et.getText().toString().trim();
    }

    private static boolean getBool(JsonObject obj, String key, boolean def) {
        try {
            if (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) return obj.get(key).getAsBoolean();
        } catch (Exception ignore) {}
        return def;
    }

    private void hideKeyboard(View v) {
        try {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } catch (Exception ignore) {}
    }

    /* -------------------- Lifecycle -------------------- */

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (loginCall != null)  loginCall.cancel();
        if (verifyCall != null) verifyCall.cancel();
        if (resendCall != null) resendCall.cancel();
        cancelOtpTimer();
        if (otpSheet != null && otpSheet.isShowing()) otpSheet.dismiss();
    }

    // Minimal TextWatcher adapter
    private abstract static class SimpleTextWatcher implements android.text.TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
