package com.codrive.ai.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public class LlmSettingsStore {
    private static final String PREF_FILE = "codrive_llm_settings";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_MODEL = "model";
    private static final String KEY_API_KEY = "api_key";

    private static final String GROQ_DEFAULT_MODEL = "qwen/qwen3-32b";

    private final SharedPreferences prefs;

    private LlmSettingsStore(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public static LlmSettingsStore create(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            SharedPreferences encryptedPrefs = EncryptedSharedPreferences.create(
                    context,
                    PREF_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            return new LlmSettingsStore(encryptedPrefs);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialize secure key storage.", e);
        }
    }

    public LlmProvider getProvider() {
        return LlmProvider.fromStorageValue(prefs.getString(KEY_PROVIDER, LlmProvider.GROQ.storageValue()));
    }

    public String getModel() {
        String stored = prefs.getString(KEY_MODEL, "");
        if (stored != null && !stored.trim().isEmpty()) {
            return stored.trim();
        }
        return defaultModelFor(getProvider());
    }

    public String getApiKey() {
        String key = prefs.getString(KEY_API_KEY, "");
        return key == null ? "" : key.trim();
    }

    public boolean hasApiKey() {
        return !getApiKey().isEmpty();
    }

    public String getMaskedApiKey() {
        String key = getApiKey();
        if (key.length() < 4) {
            return key.isEmpty() ? "Not set" : "****";
        }
        return "****" + key.substring(key.length() - 4);
    }

    public void saveProviderAndModel(LlmProvider provider, String model) {
        String safeModel = model == null || model.trim().isEmpty()
                ? defaultModelFor(provider)
                : model.trim();
        prefs.edit()
                .putString(KEY_PROVIDER, provider.storageValue())
                .putString(KEY_MODEL, safeModel)
                .apply();
    }

    public void saveApiKey(String apiKey) {
        String safeKey = apiKey == null ? "" : apiKey.trim();
        prefs.edit().putString(KEY_API_KEY, safeKey).apply();
    }

    private String defaultModelFor(LlmProvider provider) {
        if (provider == LlmProvider.GROQ) {
            return GROQ_DEFAULT_MODEL;
        }
        return "";
    }
}

