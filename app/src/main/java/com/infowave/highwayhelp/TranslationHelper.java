package com.infowave.highwayhelp;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URLEncoder;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TranslationHelper {

    private static final String API_KEY = "AIzaSyCkUxQSJ1jNt0q_CcugieFl5vezsNAUxe0";
    private static final String TRANSLATE_URL = "https://translation.googleapis.com/language/translate/v2";
    private static final OkHttpClient client = new OkHttpClient();

    public interface TranslationCallback {
        void onTranslationComplete(String translatedText);

        void onTranslationError(String error);
    }

    /**
     * Translate text to target language
     * 
     * @param text       Text to translate
     * @param targetLang Target language code (en, hi, gu)
     * @param callback   Callback for result
     */
    public static void translateText(String text, String targetLang, TranslationCallback callback) {
        // If target is English, no need to translate
        if (targetLang.equals("en")) {
            callback.onTranslationComplete(text);
            return;
        }

        new Thread(() -> {
            try {
                String encodedText = URLEncoder.encode(text, "UTF-8");
                String url = TRANSLATE_URL + "?key=" + API_KEY +
                        "&q=" + encodedText +
                        "&target=" + targetLang +
                        "&source=en";

                Request request = new Request.Builder()
                        .url(url)
                        .build();

                Response response = client.newCall(request).execute();

                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    JSONArray translations = jsonResponse
                            .getJSONObject("data")
                            .getJSONArray("translations");

                    String translatedText = translations
                            .getJSONObject(0)
                            .getString("translatedText");

                    // Return on main thread
                    new Handler(Looper.getMainLooper()).post(() -> callback.onTranslationComplete(translatedText));
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onTranslationError("Translation failed"));
                }
            } catch (Exception e) {
                Log.e("TranslationHelper", "Error: " + e.getMessage());
                new Handler(Looper.getMainLooper()).post(() -> callback.onTranslationError(e.getMessage()));
            }
        }).start();
    }

    /**
     * Translate multiple texts at once
     */
    public static void translateMultiple(String[] texts, String targetLang, MultiTranslationCallback callback) {
        if (targetLang.equals("en")) {
            callback.onTranslationComplete(texts);
            return;
        }

        new Thread(() -> {
            try {
                StringBuilder queryBuilder = new StringBuilder();
                for (String text : texts) {
                    queryBuilder.append("&q=").append(URLEncoder.encode(text, "UTF-8"));
                }

                String url = TRANSLATE_URL + "?key=" + API_KEY +
                        queryBuilder.toString() +
                        "&target=" + targetLang +
                        "&source=en";

                Request request = new Request.Builder()
                        .url(url)
                        .build();

                Response response = client.newCall(request).execute();

                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    JSONArray translations = jsonResponse
                            .getJSONObject("data")
                            .getJSONArray("translations");

                    String[] translatedTexts = new String[translations.length()];
                    for (int i = 0; i < translations.length(); i++) {
                        translatedTexts[i] = translations
                                .getJSONObject(i)
                                .getString("translatedText");
                    }

                    // Return on main thread
                    new Handler(Looper.getMainLooper()).post(() -> callback.onTranslationComplete(translatedTexts));
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onTranslationError("Translation failed"));
                }
            } catch (Exception e) {
                Log.e("TranslationHelper", "Error: " + e.getMessage());
                new Handler(Looper.getMainLooper()).post(() -> callback.onTranslationError(e.getMessage()));
            }
        }).start();
    }

    public interface MultiTranslationCallback {
        void onTranslationComplete(String[] translatedTexts);

        void onTranslationError(String error);
    }
}
