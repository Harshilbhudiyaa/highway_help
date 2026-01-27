package com.infowave.highwayhelp;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    private static final String PREF_NAME       = "HighwayHelpSession";
    private static final String KEY_IS_LOGGED_IN= "isLoggedIn";
    private static final String KEY_USER_ID     = "userId";
    private static final String KEY_PHONE       = "phone";
    private static final String KEY_LANGUAGE    = "language"; // "en", "hi", "gu"

    private final SharedPreferences pref;

    public SessionManager(Context context) {
        // Use application context to avoid leaking an Activity
        Context app = context.getApplicationContext();
        this.pref = app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /** Create/overwrite the login session (non-blocking). */
    public void createLoginSession(String userId, String phone) {
        pref.edit()
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .putString(KEY_USER_ID, userId)
                .putString(KEY_PHONE, phone)
                .apply();
    }

    /** Update only the stored phone (non-blocking). */
    public void updatePhone(String phone) {
        pref.edit().putString(KEY_PHONE, phone).apply();
    }

    /** True if a user is logged in. */
    public boolean isLoggedIn() {
        return pref.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    /** Returns stored userId or null. */
    public String getUserId() {
        return pref.getString(KEY_USER_ID, null);
    }

    /** Returns stored phone or null. */
    public String getPhone() {
        return pref.getString(KEY_PHONE, null);
    }

    /** Log out but keep user's language preference intact. */
    public void logoutUser() {
        String lang = getLanguage(); // preserve language
        pref.edit().clear().apply();
        pref.edit().putString(KEY_LANGUAGE, lang).apply();
    }

    /** Save preferred language ("en","hi","gu"). */
    public void saveLanguage(String languageCode) {
        if (languageCode == null) languageCode = "en";
        pref.edit().putString(KEY_LANGUAGE, languageCode).apply();
    }

    /** Get preferred language (default "en"). */
    public String getLanguage() {
        return pref.getString(KEY_LANGUAGE, "en");
    }

    /** Optional convenience: both flags must be present for a valid session. */
    public boolean hasValidSession() {
        return isLoggedIn() && getUserId() != null;
    }
}
