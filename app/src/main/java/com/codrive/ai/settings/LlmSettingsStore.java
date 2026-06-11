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
    private static final String KEY_AGENTIC_BETA_ENABLED = "agentic_beta_enabled";
    private static final String KEY_AGENTIC_MAX_ITERATIONS = "agentic_max_iterations";
    private static final String KEY_HISTORY_ENABLED = "history_enabled";
    private static final String KEY_HISTORY_DEPTH = "history_depth";
    private static final String KEY_DUAL_ROUTING_ENABLED = "dual_routing_enabled";
    private static final String KEY_DUAL_ROUTING_GROQ_PERCENT = "dual_routing_groq_percent";

    private static final String GROQ_DEFAULT_MODEL = "qwen/qwen3-32b";
    private static final String GEMINI_DEFAULT_MODEL = "gemini-1.5-flash";
    private static final String OPENROUTER_DEFAULT_MODEL = "openrouter/owl-alpha";
    private static final int AGENTIC_MAX_ITERATIONS_DEFAULT = 3;
    private static final int AGENTIC_MAX_ITERATIONS_MIN = 1;
    private static final int AGENTIC_MAX_ITERATIONS_MAX = 10;
    private static final int HISTORY_DEPTH_DEFAULT = 4;
    private static final int HISTORY_DEPTH_MIN = 0;
    private static final int HISTORY_DEPTH_MAX = 20;
    private static final int DUAL_ROUTING_GROQ_PERCENT_DEFAULT = 20;

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

    public boolean isAgenticBetaEnabled() {
        return prefs.getBoolean(KEY_AGENTIC_BETA_ENABLED, false);
    }

    public void setAgenticBetaEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AGENTIC_BETA_ENABLED, enabled).apply();
    }

    public int getAgenticMaxIterations() {
        return clampInt(
                prefs.getInt(KEY_AGENTIC_MAX_ITERATIONS, AGENTIC_MAX_ITERATIONS_DEFAULT),
                AGENTIC_MAX_ITERATIONS_MIN,
                AGENTIC_MAX_ITERATIONS_MAX
        );
    }

    public void setAgenticMaxIterations(int iterations) {
        prefs.edit()
                .putInt(KEY_AGENTIC_MAX_ITERATIONS, clampInt(iterations, AGENTIC_MAX_ITERATIONS_MIN, AGENTIC_MAX_ITERATIONS_MAX))
                .apply();
    }

    public boolean isHistoryEnabled() {
        return prefs.getBoolean(KEY_HISTORY_ENABLED, true);
    }

    public void setHistoryEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_HISTORY_ENABLED, enabled).apply();
    }

    public int getHistoryDepth() {
        return clampInt(
                prefs.getInt(KEY_HISTORY_DEPTH, HISTORY_DEPTH_DEFAULT),
                HISTORY_DEPTH_MIN,
                HISTORY_DEPTH_MAX
        );
    }

    public void setHistoryDepth(int depth) {
        prefs.edit()
                .putInt(KEY_HISTORY_DEPTH, clampInt(depth, HISTORY_DEPTH_MIN, HISTORY_DEPTH_MAX))
                .apply();
    }

    public boolean isDualRoutingEnabled() {
        return prefs.getBoolean(KEY_DUAL_ROUTING_ENABLED, false);
    }

    public void setDualRoutingEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_DUAL_ROUTING_ENABLED, enabled).apply();
    }

    public int getDualRoutingGroqPercent() {
        return clampInt(
                prefs.getInt(KEY_DUAL_ROUTING_GROQ_PERCENT, DUAL_ROUTING_GROQ_PERCENT_DEFAULT),
                0,
                100
        );
    }

    public void setDualRoutingGroqPercent(int percent) {
        prefs.edit()
                .putInt(KEY_DUAL_ROUTING_GROQ_PERCENT, clampInt(percent, 0, 100))
                .apply();
    }

    private String defaultModelFor(LlmProvider provider) {
        if (provider == LlmProvider.GROQ) {
            return GROQ_DEFAULT_MODEL;
        }
        if (provider == LlmProvider.GEMINI) {
            return GEMINI_DEFAULT_MODEL;
        }
        if (provider == LlmProvider.OPENROUTER) {
            return OPENROUTER_DEFAULT_MODEL;
        }
        return "";
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

