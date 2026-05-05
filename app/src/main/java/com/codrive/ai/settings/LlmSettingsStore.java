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
    private static final String KEY_MODEL_PREFIX = "model_";
    private static final String KEY_API_KEY_PREFIX = "api_key_";

    private static final String GROQ_DEFAULT_MODEL = "qwen/qwen3-32b";
    private static final String GEMINI_DEFAULT_MODEL = "gemini-1.5-flash";

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
            try {
                context.deleteSharedPreferences(PREF_FILE);
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
            } catch (Exception retryException) {
                throw new IllegalStateException("Unable to initialize secure key storage.", retryException);
            }
        }
    }

    public LlmProvider getProvider() {
        return LlmProvider.fromStorageValue(prefs.getString(KEY_PROVIDER, LlmProvider.GROQ.storageValue()));
    }

    public String getModel() {
        return getModelFor(getProvider());
    }

    public String getModelFor(LlmProvider provider) {
        // Prefer provider-scoped model when available
        String stored = prefs.getString(KEY_MODEL_PREFIX + provider.storageValue(), "");
        if (stored != null && !stored.trim().isEmpty()) {
            return stored.trim();
        }

        // Legacy global model: only honor if it was saved while the same provider was selected
        String legacy = prefs.getString(KEY_MODEL, "");
        String legacyProvider = prefs.getString(KEY_PROVIDER, "");
        if (legacy != null && !legacy.trim().isEmpty() && legacyProvider != null && legacyProvider.equals(provider.storageValue())) {
            return legacy.trim();
        }

        // Fall back to provider-specific default
        return defaultModelFor(provider);
    }

    public String getApiKey() {
        return getApiKeyFor(getProvider());
    }

    public String getApiKeyFor(LlmProvider provider) {
        // Prefer provider-scoped API key when available
        String key = prefs.getString(KEY_API_KEY_PREFIX + provider.storageValue(), "");
        if (key != null && !key.trim().isEmpty()) {
            return key.trim();
        }

        // Legacy global API key: only honor if it was saved while the same provider was selected
        String legacy = prefs.getString(KEY_API_KEY, "");
        String legacyProvider = prefs.getString(KEY_PROVIDER, "");
        if (legacy != null && !legacy.trim().isEmpty() && legacyProvider != null && legacyProvider.equals(provider.storageValue())) {
            return legacy.trim();
        }

        return "";
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

    public String getMaskedApiKeyFor(LlmProvider provider) {
        String key = getApiKeyFor(provider);
        if (key.length() < 4) {
            return key.isEmpty() ? "Not set" : "****";
        }
        return "****" + key.substring(key.length() - 4);
    }

    public void saveProviderAndModel(LlmProvider provider, String model) {
        saveModelFor(provider, model);
        prefs.edit()
                .putString(KEY_PROVIDER, provider.storageValue())
                .putString(KEY_MODEL, getModelFor(provider))
                .apply();
    }

    public void saveModelFor(LlmProvider provider, String model) {
        String safeModel = model == null || model.trim().isEmpty()
                ? defaultModelFor(provider)
                : model.trim();
        prefs.edit()
                .putString(KEY_MODEL_PREFIX + provider.storageValue(), safeModel)
                .apply();
    }

    public void saveApiKey(String apiKey) {
        saveApiKeyFor(getProvider(), apiKey);
    }

    public void saveApiKeyFor(LlmProvider provider, String apiKey) {
        String safeKey = apiKey == null ? "" : apiKey.trim();
        prefs.edit()
                .putString(KEY_API_KEY_PREFIX + provider.storageValue(), safeKey)
                .putString(KEY_API_KEY, safeKey)
                .apply();
    }

    private String defaultModelFor(LlmProvider provider) {
        if (provider == LlmProvider.GROQ) {
            return GROQ_DEFAULT_MODEL;
        }
        if (provider == LlmProvider.GEMINI) {
            return GEMINI_DEFAULT_MODEL;
        }
        return "";
    }
}

