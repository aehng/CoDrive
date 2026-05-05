package com.codrive.ai.settings;

import android.content.Context;
import android.content.SharedPreferences;

public final class VoiceSettingsStore {
    private static final String PREF_FILE = "voice_settings";
    private static final String KEY_STT_LOCALE = "voice_stt_locale";
    private static final String KEY_TTS_LOCALE = "voice_tts_locale";
    private static final String KEY_TTS_RATE = "voice_tts_rate";
    private static final String KEY_TTS_PITCH = "voice_tts_pitch";
    private static final String KEY_VOICE_ENGINE = "voice_engine";
    private static final String KEY_COMMAND_DELAY = "voice_command_delay";

    private static final String DEFAULT_LOCALE = "en-US";
    private static final float DEFAULT_RATE = 1.0f;
    private static final float DEFAULT_PITCH = 1.0f;
    private static final long DEFAULT_COMMAND_DELAY = 1500L;
    private static final String ENGINE_NATIVE = "native";
    private static final String ENGINE_SHERPA = "sherpa";

    private final SharedPreferences prefs;

    private VoiceSettingsStore(Context context) {
        prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    public static VoiceSettingsStore create(Context context) {
        return new VoiceSettingsStore(context.getApplicationContext());
    }

    public String getSttLocaleTag() {
        return prefs.getString(KEY_STT_LOCALE, DEFAULT_LOCALE);
    }

    public String getTtsLocaleTag() {
        return prefs.getString(KEY_TTS_LOCALE, DEFAULT_LOCALE);
    }

    public float getTtsRate() {
        return prefs.getFloat(KEY_TTS_RATE, DEFAULT_RATE);
    }

    public float getTtsPitch() {
        return prefs.getFloat(KEY_TTS_PITCH, DEFAULT_PITCH);
    }

    public void saveVoiceSettings(String sttLocaleTag, String ttsLocaleTag, float ttsRate, float ttsPitch) {
        prefs.edit()
            .putString(KEY_STT_LOCALE, sanitizeLocaleTag(sttLocaleTag))
            .putString(KEY_TTS_LOCALE, sanitizeLocaleTag(ttsLocaleTag))
            .putFloat(KEY_TTS_RATE, clamp(ttsRate, 0.7f, 1.3f))
            .putFloat(KEY_TTS_PITCH, clamp(ttsPitch, 0.7f, 1.3f))
            .apply();
    }

    public boolean isSherpaEnabled() {
        return ENGINE_SHERPA.equals(prefs.getString(KEY_VOICE_ENGINE, ENGINE_SHERPA));
    }

    public void setSherpaEnabled(boolean enabled) {
        prefs.edit()
            .putString(KEY_VOICE_ENGINE, enabled ? ENGINE_SHERPA : ENGINE_NATIVE)
            .apply();
    }

    public long getCommandDelayMs() {
        return prefs.getLong(KEY_COMMAND_DELAY, DEFAULT_COMMAND_DELAY);
    }

    public void setCommandDelayMs(long delayMs) {
        long clamped = Math.max(500L, Math.min(5000L, delayMs));
        prefs.edit().putLong(KEY_COMMAND_DELAY, clamped).apply();
    }

    private static String sanitizeLocaleTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return DEFAULT_LOCALE;
        }
        return tag.trim();
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}

