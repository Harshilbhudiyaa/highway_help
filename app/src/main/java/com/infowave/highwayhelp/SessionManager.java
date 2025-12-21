package com.infowave.highwayhelp;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private SharedPreferences pref;
    private SharedPreferences.Editor editor;
    private Context context;

    private static final String PREF_NAME = "HighwayHelpSession";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_LANGUAGE = "language"; // Language preference

    public SessionManager(Context context) {
        this.context = context;
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = pref.edit();
    }

    public void createLoginSession(String userId, String phone) {
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_PHONE, phone);
        editor.commit();
    }

    public boolean isLoggedIn() {
        return pref.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public String getUserId() {
        return pref.getString(KEY_USER_ID, null);
    }

    public void logoutUser() {
        editor.clear();
        editor.commit();
    }

    /**
     * Save user's preferred language
     * 
     * @param languageCode Language code (en, hi, gu)
     */
    public void saveLanguage(String languageCode) {
        editor.putString(KEY_LANGUAGE, languageCode);
        editor.commit();
    }

    /**
     * Get user's preferred language
     * 
     * @return Language code (default: "en")
     */
    public String getLanguage() {
        return pref.getString(KEY_LANGUAGE, "en");
    }
}