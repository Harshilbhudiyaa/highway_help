package com.infowave.highwayhelp;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.messaging.FirebaseMessaging; // Import
import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    EditText etName, etPhone, etState, etCity;
    Spinner spinnerLanguage;
    Button btnRegister;
    TextView tvGoToLogin, tvLanguageLabel, tvCreateAccount;
    ProgressBar progressBar;
    ApiInterface apiInterface;
    SessionManager sessionManager;
    String fcmToken = ""; // Token variable

    // For dynamic translation
    com.google.android.material.textfield.TextInputLayout tilName, tilPhone, tilState, tilCity;
    String currentLanguageCode = "en";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        sessionManager = new SessionManager(this);

        // Initialize EditTexts
        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        etState = findViewById(R.id.etState);
        etCity = findViewById(R.id.etCity);
        spinnerLanguage = findViewById(R.id.spinnerLanguage);

        btnRegister = findViewById(R.id.btnRegister);
        tvGoToLogin = findViewById(R.id.tvGoToLogin);
        tvLanguageLabel = findViewById(R.id.tvLanguageLabel);
        tvCreateAccount = findViewById(R.id.tvCreateAccount);
        progressBar = findViewById(R.id.progressBar);

        // Initialize TextInputLayouts for translation
        tilName = findViewById(R.id.tilName);
        tilPhone = findViewById(R.id.tilPhone);
        tilState = findViewById(R.id.tilState);
        tilCity = findViewById(R.id.tilCity);

        apiInterface = ApiClient.getClient().create(ApiInterface.class);

        // 1. Get Token immediately
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        if (!task.isSuccessful()) {
                            return;
                        }
                        fcmToken = task.getResult();
                    }
                });

        String[] languages = { "English", "Hindi", "Gujarati" };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                languages);
        spinnerLanguage.setAdapter(adapter);

        // Load saved language preference and set spinner
        String savedLanguage = sessionManager.getLanguage();
        currentLanguageCode = savedLanguage;
        int savedPosition = 0; // Default to English
        if (savedLanguage.equals("hi")) {
            savedPosition = 1; // Hindi
        } else if (savedLanguage.equals("gu")) {
            savedPosition = 2; // Gujarati
        }
        spinnerLanguage.setSelection(savedPosition, false); // false = don't trigger listener yet

        // Language change listener - Translate form when language changes
        spinnerLanguage.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selectedLang = languages[position];

                // Set language code
                if (selectedLang.equals("Hindi")) {
                    currentLanguageCode = "hi";
                } else if (selectedLang.equals("Gujarati")) {
                    currentLanguageCode = "gu";
                } else {
                    currentLanguageCode = "en";
                }

                // Save language preference
                sessionManager.saveLanguage(currentLanguageCode);

                // Translate the form
                translateForm(currentLanguageCode);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                // Do nothing
            }
        });

        tvGoToLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });

        btnRegister.setOnClickListener(v -> {
            final String name = etName.getText().toString().trim();
            final String phone = etPhone.getText().toString().trim();
            final String state = etState.getText().toString().trim();
            final String city = etCity.getText().toString().trim();
            String lang = spinnerLanguage.getSelectedItem().toString();

            // Calculate langCode
            String tempLangCode = "en";
            if (lang.equals("Hindi"))
                tempLangCode = "hi";
            if (lang.equals("Gujarati"))
                tempLangCode = "gu";

            // Make it final for lambda use
            final String langCode = tempLangCode;

            if (!name.isEmpty() && phone.length() == 10 && !state.isEmpty() && !city.isEmpty()) {
                // 2. Check token before register
                if (!fcmToken.isEmpty()) {
                    registerUser(name, phone, langCode, state, city);
                } else {
                    // Retry token
                    FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
                        fcmToken = token;
                        registerUser(name, phone, langCode, state, city);
                    });
                }
            } else {
                // Translate error message
                String errorMsg = "Please fill all details";
                if (!currentLanguageCode.equals("en")) {
                    TranslationHelper.translateText(errorMsg, currentLanguageCode,
                            new TranslationHelper.TranslationCallback() {
                                @Override
                                public void onTranslationComplete(String translatedText) {
                                    Toast.makeText(RegisterActivity.this, translatedText, Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onTranslationError(String error) {
                                    Toast.makeText(RegisterActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                                }
                            });
                } else {
                    Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void registerUser(String name, String phone, String lang, String state, String city) {
        progressBar.setVisibility(View.VISIBLE);
        btnRegister.setEnabled(false);

        // 3. Pass Token
        Call<JsonObject> call = apiInterface.registerUser("register", phone, name, fcmToken, lang, state, city);

        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                progressBar.setVisibility(View.GONE);
                btnRegister.setEnabled(true);

                if (response.isSuccessful() && response.body() != null) {
                    boolean isNewUser = false;
                    try {
                        if (response.body().has("is_new_user")) {
                            isNewUser = response.body().get("is_new_user").getAsBoolean();
                        }
                    } catch (Exception e) {
                    }

                    if (response.body().get("success").getAsBoolean()) {
                        if (!isNewUser) {
                            Toast.makeText(RegisterActivity.this, "Account already exists! Redirecting to Login...",
                                    Toast.LENGTH_LONG).show();
                            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                            finish();
                        } else {
                            showOtpBottomSheet(phone);
                        }
                    } else {
                        Toast.makeText(RegisterActivity.this, "Error: " + response.body().get("message").getAsString(),
                                Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                btnRegister.setEnabled(true);
                Toast.makeText(RegisterActivity.this, "Network Error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showOtpBottomSheet(String phone) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        bottomSheetDialog.setContentView(R.layout.bottom_sheet_otp);
        bottomSheetDialog.setCancelable(false);

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
                tvResendBtn.setVisibility(View.VISIBLE);
            }
        }.start();

        tvResendBtn.setOnClickListener(v -> {
            // Resend with token
            // Note: We need to re-capture inputs here or pass them.
            // For simplicity, we re-read from fields since we are on the same screen.
            String rName = etName.getText().toString().trim();
            String rState = etState.getText().toString().trim();
            String rCity = etCity.getText().toString().trim();
            // Recalculate lang
            String rLang = spinnerLanguage.getSelectedItem().toString();
            String rLangCode = "en";
            if (rLang.equals("Hindi"))
                rLangCode = "hi";
            if (rLang.equals("Gujarati"))
                rLangCode = "gu";

            registerUser(rName, phone, rLangCode, rState, rCity);
            tvResendBtn.setVisibility(View.GONE);
            Toast.makeText(this, "OTP Resent", Toast.LENGTH_SHORT).show();
        });

        btnVerifyOtp.setOnClickListener(v -> {
            String otp = etOtpInput.getText().toString().trim();
            if (otp.length() >= 4) {
                verifyOtp(phone, otp, bottomSheetDialog, progressOtp);
            } else {
                Toast.makeText(this, "Enter OTP", Toast.LENGTH_SHORT).show();
            }
        });

        bottomSheetDialog.show();
    }

    private void verifyOtp(String phone, String otp, BottomSheetDialog dialog, ProgressBar loader) {
        loader.setVisibility(View.VISIBLE);
        Call<JsonObject> call = apiInterface.verifyOtp(phone, otp);
        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                loader.setVisibility(View.GONE);
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

                        Toast.makeText(RegisterActivity.this, "Welcome!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                        finishAffinity();
                    } else {
                        Toast.makeText(RegisterActivity.this, "Wrong OTP", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                loader.setVisibility(View.GONE);
            }
        });
    }

    /**
     * Translate all form elements to the selected language
     */
    private void translateForm(String targetLang) {
        // If English, set original text
        if (targetLang.equals("en")) {
            tvCreateAccount.setText("Create Account");
            tvLanguageLabel.setText("Preferred Language");
            tilName.setHint("Full Name");
            tilPhone.setHint("Phone Number");
            tilState.setHint("State");
            tilCity.setHint("City");
            btnRegister.setText("Join Now");
            tvGoToLogin.setText("Already have an account? Login");
            return;
        }

        // Texts to translate
        String[] textsToTranslate = {
                "Create Account",
                "Preferred Language",
                "Full Name",
                "Phone Number",
                "State",
                "City",
                "Join Now",
                "Already have an account? Login"
        };

        // Translate all at once
        TranslationHelper.translateMultiple(textsToTranslate, targetLang,
                new TranslationHelper.MultiTranslationCallback() {
                    @Override
                    public void onTranslationComplete(String[] translatedTexts) {
                        // Update UI with translated text
                        tvCreateAccount.setText(translatedTexts[0]);
                        tvLanguageLabel.setText(translatedTexts[1]);
                        tilName.setHint(translatedTexts[2]);
                        tilPhone.setHint(translatedTexts[3]);
                        tilState.setHint(translatedTexts[4]);
                        tilCity.setHint(translatedTexts[5]);
                        btnRegister.setText(translatedTexts[6]);
                        tvGoToLogin.setText(translatedTexts[7]);
                    }

                    @Override
                    public void onTranslationError(String error) {
                        Toast.makeText(RegisterActivity.this, "Translation error: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
    }
}