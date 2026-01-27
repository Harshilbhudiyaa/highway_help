package com.infowave.highwayhelp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.method.DigitsKeyListener;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.ListPopupWindow;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.Collections; // <-- for emptyList()
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    // UI
    private EditText etName, etPhone, etState, etCity;
    private Spinner spinnerLanguage;
    private Button btnRegister;
    private TextView tvGoToLogin, tvLanguageLabel, tvCreateAccount;
    private ProgressBar progressBar;

    // TextInputLayouts for inline errors/translation
    private TextInputLayout tilName, tilPhone, tilState, tilCity;

    // Services
    private ApiInterface apiInterface;
    private SessionManager sessionManager;

    // FCM token
    private String fcmToken = "";

    // Language
    private String currentLanguageCode = "en";
    private boolean languageInitGuard = false;

    // Debounce & lifecycle
    private final AtomicBoolean registerInFlight = new AtomicBoolean(false);
    private final AtomicBoolean otpInFlight = new AtomicBoolean(false);
    private final AtomicBoolean resendInFlight = new AtomicBoolean(false);

    private Call<JsonObject> registerCall;
    private Call<JsonObject> verifyCall;
    private Call<JsonObject> resendCall;

    // OTP bottom sheet
    private BottomSheetDialog otpSheet;
    private CountDownTimer otpTimer;

    // State/City dropdowns
    private ListPopupWindow statePopup, cityPopup;
    private String selectedState = "";
    private String selectedCity = "";

    // For resend request reuse
    private String lastName = "", lastPhone = "", lastLangCode = "", lastState = "", lastCity = "";

    // Static State → City list (extend freely)
    private static final List<String> STATES = Arrays.asList(
            "Gujarat", "Maharashtra", "Rajasthan", "Madhya Pradesh", "Uttar Pradesh",
            "Delhi", "Karnataka", "Tamil Nadu", "Telangana", "West Bengal"
    );
    private static final Map<String, List<String>> STATE_CITY_MAP = new LinkedHashMap<>();
    static {
        STATE_CITY_MAP.put("Gujarat", Arrays.asList(
                "Ahmedabad","Surat","Vadodara","Rajkot","Bhavnagar",
                "Gandhinagar","Jamnagar","Junagadh","Anand","Nadiad",
                "Vapi","Navsari","Bharuch","Bhuj","Morbi",
                "Mehsana","Porbandar","Amreli"
        ));
        STATE_CITY_MAP.put("Maharashtra", Arrays.asList("Mumbai","Pune","Nagpur","Nashik","Thane"));
        STATE_CITY_MAP.put("Rajasthan", Arrays.asList("Jaipur","Udaipur","Jodhpur","Kota","Ajmer"));
        STATE_CITY_MAP.put("Madhya Pradesh", Arrays.asList("Indore","Bhopal","Gwalior","Jabalpur","Ujjain"));
        STATE_CITY_MAP.put("Uttar Pradesh", Arrays.asList("Lucknow","Kanpur","Varanasi","Noida","Ghaziabad"));
        STATE_CITY_MAP.put("Delhi", Arrays.asList("New Delhi","Dwarka","Rohini","Saket","Karol Bagh"));
        STATE_CITY_MAP.put("Karnataka", Arrays.asList("Bengaluru","Mysuru","Mangaluru","Hubballi","Belagavi"));
        STATE_CITY_MAP.put("Tamil Nadu", Arrays.asList("Chennai","Coimbatore","Madurai","Salem","Tiruchirappalli"));
        STATE_CITY_MAP.put("Telangana", Arrays.asList("Hyderabad","Warangal","Nizamabad","Karimnagar","Khammam"));
        STATE_CITY_MAP.put("West Bengal", Arrays.asList("Kolkata","Howrah","Durgapur","Siliguri","Asansol"));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);
        if (sessionManager.isLoggedIn()) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_register);

        bindViews();
        btnRegister.setEnabled(false); // start disabled until fields valid
        setupPhoneField();
        setupLanguageSpinner();
        setupStateCityDropdowns();

        apiInterface = ApiClient.getClient().create(ApiInterface.class);

        // FCM: best-effort, non-blocking
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override public void onComplete(@NonNull Task<String> task) {
                        if (task.isSuccessful() && task.getResult() != null) {
                            fcmToken = task.getResult();
                        }
                    }
                });

        tvGoToLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });

        // Enable/disable submit reactively
        SimpleTextWatcher watcher = new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) { refreshSubmitEnabled(); }
        };
        etName.addTextChangedListener(watcher);
        etPhone.addTextChangedListener(watcher);
        etState.addTextChangedListener(watcher);
        etCity.addTextChangedListener(watcher);

        btnRegister.setOnClickListener(v -> {
            if (registerInFlight.getAndSet(true)) return;

            String name  = safe(etName);
            String phone = safe(etPhone).replace(" ", "");
            String state = safe(etState);
            String city  = safe(etCity);

            // Inline validations (defensive; button already guards)
            if (!isValidName(name))  { setError(tilName, etName, "Enter valid name");  resetRegisterBtn(); return; }
            if (!isValidPhone(phone)){ setError(tilPhone, etPhone, "Enter valid 10-digit number"); resetRegisterBtn(); return; }
            if (TextUtils.isEmpty(state)) { setError(tilState, etState, "Select State"); resetRegisterBtn(); return; }
            if (TextUtils.isEmpty(city))  { setError(tilCity,  etCity,  "Select City");  resetRegisterBtn(); return; }
            if (!cityBelongsToState(state, city)) { setError(tilCity, etCity, "Select a valid city for state"); resetRegisterBtn(); return; }

            String lang = (String) spinnerLanguage.getSelectedItem();
            String langCode = "en";
            if ("Hindi".equals(lang)) langCode = "hi";
            else if ("Gujarati".equals(lang)) langCode = "gu";

            // Cache for resend
            lastName = name; lastPhone = phone; lastLangCode = langCode; lastState = state; lastCity = city;

            hideKeyboard(v);
            startRegister(name, phone, langCode, state, city);
        });

        // First translation (based on saved language)
        currentLanguageCode = sessionManager.getLanguage();
        translateForm(currentLanguageCode);

        refreshSubmitEnabled();
    }

    // Reset UI after a guarded early-return (validation failure, etc.)
    private void resetRegisterBtn() {
        registerInFlight.set(false);
        if (btnRegister != null) btnRegister.setEnabled(true);
        if (progressBar != null) progressBar.setVisibility(View.GONE);
    }

    /* -------------------- Wiring & Setup -------------------- */

    private void bindViews() {
        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        etState = findViewById(R.id.etState);
        etCity = findViewById(R.id.etCity);

        tilName = findViewById(R.id.tilName);
        tilPhone = findViewById(R.id.tilPhone);
        tilState = findViewById(R.id.tilState);
        tilCity = findViewById(R.id.tilCity);

        spinnerLanguage = findViewById(R.id.spinnerLanguage);

        btnRegister = findViewById(R.id.btnRegister);
        tvGoToLogin = findViewById(R.id.tvGoToLogin);
        tvLanguageLabel = findViewById(R.id.tvLanguageLabel);
        tvCreateAccount = findViewById(R.id.tvCreateAccount);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupPhoneField() {
        etPhone.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(10) });
        etPhone.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
    }

    private void setupLanguageSpinner() {
        String[] languages = { "English", "Hindi", "Gujarati" };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, languages);
        spinnerLanguage.setAdapter(adapter);

        // Set selection from saved language without firing translation immediately
        String saved = sessionManager.getLanguage();
        int pos = "hi".equals(saved) ? 1 : "gu".equals(saved) ? 2 : 0;
        spinnerLanguage.setSelection(pos, false);

        spinnerLanguage.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (!languageInitGuard) { languageInitGuard = true; return; }
                String code = position == 1 ? "hi" : position == 2 ? "gu" : "en";
                currentLanguageCode = code;
                sessionManager.saveLanguage(code);
                translateForm(code);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
    }

    private void setupStateCityDropdowns() {
        makeDropdownField(etState);
        makeDropdownField(etCity);

        // State popup
        statePopup = new ListPopupWindow(this);
        statePopup.setModal(true);
        statePopup.setAnchorView(etState);
        statePopup.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, STATES));
        statePopup.setOnItemClickListener((parent, view, position, id) -> {
            selectedState = STATES.get(position);
            etState.setText(selectedState);
            // Reset city when state changes
            selectedCity = "";
            etCity.setText("");

            // Refresh city adapter for the selected state
            List<String> cities = STATE_CITY_MAP.get(selectedState);
            if (cities == null) cities = Collections.emptyList(); // <-- fixed
            cityPopup.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, cities));
            statePopup.dismiss();
            refreshSubmitEnabled();
        });

        // City popup (depends on selected state)
        cityPopup = new ListPopupWindow(this);
        cityPopup.setModal(true);
        cityPopup.setAnchorView(etCity);
        cityPopup.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, Collections.emptyList())); // <-- fixed
        cityPopup.setOnItemClickListener((parent, view, position, id) -> {
            List<String> list = STATE_CITY_MAP.get(selectedState);
            if (list != null && position >= 0 && position < list.size()) {
                selectedCity = list.get(position);
                etCity.setText(selectedCity);
            }
            cityPopup.dismiss();
            refreshSubmitEnabled();
        });

        etState.setOnClickListener(v -> {
            hideKeyboard(v);
            statePopup.setWidth(etState.getWidth());
            statePopup.show();
        });
        etState.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                hideKeyboard(v);
                statePopup.setWidth(etState.getWidth());
                statePopup.show();
            }
        });

        etCity.setOnClickListener(v -> {
            hideKeyboard(v);
            if (TextUtils.isEmpty(selectedState)) {
                Toast.makeText(this, "Select State first", Toast.LENGTH_SHORT).show();
                return;
            }
            cityPopup.setWidth(etCity.getWidth());
            cityPopup.show();
        });
        etCity.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                hideKeyboard(v);
                if (TextUtils.isEmpty(selectedState)) {
                    Toast.makeText(this, "Select State first", Toast.LENGTH_SHORT).show();
                    return;
                }
                cityPopup.setWidth(etCity.getWidth());
                cityPopup.show();
            }
        });
    }

    private void makeDropdownField(EditText et) {
        et.setKeyListener(null);
        et.setFocusable(true);
        et.setFocusableInTouchMode(true);
        et.setClickable(true);
        et.setLongClickable(false);
        et.setInputType(android.text.InputType.TYPE_NULL); // suppress keyboard
    }

    /* -------------------- Register & OTP Flow -------------------- */

    private void startRegister(String name, String phone, String langCode, String state, String city) {
        progressBar.setVisibility(View.VISIBLE);
        btnRegister.setEnabled(false);

        String token = fcmToken == null ? "" : fcmToken;
        registerCall = apiInterface.registerUser("register", phone, name, token, langCode, state, city);
        registerCall.enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                progressBar.setVisibility(View.GONE);
                btnRegister.setEnabled(true);
                registerInFlight.set(false);

                if (response.isSuccessful() && response.body() != null) {
                    boolean success = getBool(response.body(), "success", false);
                    boolean isNew   = getBool(response.body(), "is_new_user", false);

                    if (success) {
                        if (!isNew) {
                            Toast.makeText(RegisterActivity.this, "Account already exists! Redirecting to Login...", Toast.LENGTH_LONG).show();
                            Intent i = new Intent(RegisterActivity.this, LoginActivity.class);
                            i.putExtra("prefill_phone", phone);
                            startActivity(i);
                            finish();
                        } else {
                            showOtpBottomSheet(phone);
                        }
                    } else {
                        String msg = response.body().has("message") ? response.body().get("message").getAsString() : "Network Error";
                        Toast.makeText(RegisterActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(RegisterActivity.this, "Network Error", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(Call<JsonObject> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                btnRegister.setEnabled(true);
                registerInFlight.set(false);
                Toast.makeText(RegisterActivity.this, "Network Error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showOtpBottomSheet(String phone) {
        // Close previous if open
        if (otpSheet != null && otpSheet.isShowing()) otpSheet.dismiss();

        otpSheet = new BottomSheetDialog(this);
        otpSheet.setContentView(R.layout.bottom_sheet_otp);
        otpSheet.setCancelable(true);
        otpSheet.setCanceledOnTouchOutside(false);

        EditText etOtpInput = otpSheet.findViewById(R.id.etOtpInput);
        Button btnVerifyOtp = otpSheet.findViewById(R.id.btnVerifyOtp);
        ProgressBar progressOtp = otpSheet.findViewById(R.id.progressOtp);
        TextView tvTimer = otpSheet.findViewById(R.id.tvTimer);
        TextView tvResendBtn = otpSheet.findViewById(R.id.tvResendBtn);

        if (etOtpInput == null || btnVerifyOtp == null || tvTimer == null || tvResendBtn == null) {
            Toast.makeText(this, "Network Error", Toast.LENGTH_SHORT).show();
            return;
        }

        // OTP rules
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

        // Resend cooldown
        startResendCountdown(tvTimer, tvResendBtn);

        tvResendBtn.setOnClickListener(v -> {
            if (resendInFlight.getAndSet(true)) return;
            tvResendBtn.setEnabled(false);

            String token = fcmToken == null ? "" : fcmToken;
            resendCall = apiInterface.registerUser("register", lastPhone, lastName, token, lastLangCode, lastState, lastCity);
            resendCall.enqueue(new Callback<JsonObject>() {
                @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                    resendInFlight.set(false);
                    Toast.makeText(RegisterActivity.this, "OTP Resent", Toast.LENGTH_SHORT).show();
                    startResendCountdown(tvTimer, tvResendBtn);
                }
                @Override public void onFailure(Call<JsonObject> call, Throwable t) {
                    resendInFlight.set(false);
                    Toast.makeText(RegisterActivity.this, "Network Error", Toast.LENGTH_SHORT).show();
                    startResendCountdown(tvTimer, tvResendBtn);
                }
            });
        });

        // Back key: allow unless verifying
        otpSheet.setOnKeyListener((dialog, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                if (otpInFlight.get()) return true;
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

    @SuppressLint("SetTextI18n")
    private void startVerifyOtp(String phone, String otp, BottomSheetDialog dialog,
                                ProgressBar loader, Button btnVerify) {
        otpInFlight.set(true);
        btnVerify.setEnabled(false);
        btnVerify.setText("Processing...");
        if (loader != null) loader.setVisibility(View.VISIBLE);

        verifyCall = apiInterface.verifyOtp(phone, otp);
        verifyCall.enqueue(new Callback<JsonObject>() {
            @SuppressLint("SetTextI18n")
            @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                otpInFlight.set(false);
                btnVerify.setEnabled(true);
                btnVerify.setText("Verify");
                if (loader != null) loader.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null && getBool(response.body(), "success", false)) {
                    String userId = response.body().has("user_id") && !response.body().get("user_id").isJsonNull()
                            ? response.body().get("user_id").getAsString() : "0";

                    sessionManager.createLoginSession(userId, phone);
                    Toast.makeText(RegisterActivity.this, "Welcome!", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    goToMain();
                } else {
                    Toast.makeText(RegisterActivity.this, "Wrong OTP", Toast.LENGTH_SHORT).show();
                }
            }
            @SuppressLint("SetTextI18n")
            @Override public void onFailure(Call<JsonObject> call, Throwable t) {
                otpInFlight.set(false);
                btnVerify.setEnabled(true);
                btnVerify.setText("Verify");
                if (loader != null) loader.setVisibility(View.GONE);
                Toast.makeText(RegisterActivity.this, "Network Error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /* -------------------- Translation -------------------- */

    @SuppressLint("SetTextI18n")
    private void translateForm(String targetLang) {
        if (targetLang == null) targetLang = "en";
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

        String[] texts = {
                "Create Account","Preferred Language","Full Name","Phone Number",
                "State","City","Join Now","Already have an account? Login"
        };

        TranslationHelper.translateMultiple(texts, targetLang,
                new TranslationHelper.MultiTranslationCallback() {
                    @Override public void onTranslationComplete(String[] t) {
                        tvCreateAccount.setText(t[0]);
                        tvLanguageLabel.setText(t[1]);
                        tilName.setHint(t[2]);
                        tilPhone.setHint(t[3]);
                        tilState.setHint(t[4]);
                        tilCity.setHint(t[5]);
                        btnRegister.setText(t[6]);
                        tvGoToLogin.setText(t[7]);
                    }
                    @Override public void onTranslationError(String error) {
                        // No-op; keep English if translation fails
                    }
                });
    }

    /* -------------------- Helpers -------------------- */

    private void startResendCountdown(TextView tvTimer, TextView tvResendBtn) {
        cancelOtpTimer();
        tvResendBtn.setVisibility(View.GONE);
        tvResendBtn.setEnabled(false);

        otpTimer = new CountDownTimer(30000, 1000) {
            @SuppressLint({"SetTextI18n", "DefaultLocale"})
            public void onTick(long millisUntilFinished) {
                long sec = millisUntilFinished / 1000;
                tvTimer.setText("Resend code in " + String.format("00:%02d", sec));
            }
            @SuppressLint("SetTextI18n")
            public void onFinish() {
                tvTimer.setText("Didn't receive code?");
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
        startActivity(new Intent(RegisterActivity.this, MainActivity.class));
        finishAffinity();
    }

    private void setError(TextInputLayout til, EditText et, String msg) {
        if (til != null) {
            til.setError(msg);
            et.postDelayed(() -> til.setError(null), 2000);
        } else if (et != null) {
            et.setError(msg);
        } else {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isValidPhone(String p) { return p != null && p.matches("^[6-9]\\d{9}$"); }
    private boolean isValidName(String n)   { return n != null && n.trim().length() >= 3; }

    private String safe(EditText et) {
        return (et == null || et.getText() == null) ? "" : et.getText().toString().trim();
    }

    private boolean getBool(JsonObject obj, String key, boolean def) {
        try { return obj != null && obj.has(key) && !obj.get(key).isJsonNull() && obj.get(key).getAsBoolean(); }
        catch (Exception ignore) { return def; }
    }

    private boolean cityBelongsToState(String state, String city) {
        List<String> list = STATE_CITY_MAP.get(state);
        return list != null && list.contains(city);
    }

    private void hideKeyboard(View v) {
        try {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } catch (Exception ignore) {}
    }

    private void refreshSubmitEnabled() {
        String name  = safe(etName);
        String phone = safe(etPhone).replace(" ", "");
        String state = safe(etState);
        String city  = safe(etCity);
        boolean ok = isValidName(name) && isValidPhone(phone) && !TextUtils.isEmpty(state) && !TextUtils.isEmpty(city)
                && cityBelongsToState(state, city);
        btnRegister.setEnabled(ok);
    }

    /* -------------------- Lifecycle -------------------- */

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (registerCall != null) registerCall.cancel();
        if (verifyCall != null) verifyCall.cancel();
        if (resendCall != null) resendCall.cancel();
        cancelOtpTimer();
        if (otpSheet != null && otpSheet.isShowing()) otpSheet.dismiss();
        if (statePopup != null) statePopup.dismiss();
        if (cityPopup != null) cityPopup.dismiss();
    }

    /* -------------------- Minimal TextWatcher adapter -------------------- */
    private abstract static class SimpleTextWatcher implements android.text.TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
