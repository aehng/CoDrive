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
    private static final String KEY_ALLOW_BARGE_IN = "voice_allow_barge_in";
    private static final String KEY_STT_SPEECH_THRESHOLD = "voice_stt_speech_threshold";
    private static final String KEY_STT_VAD_THRESHOLD = "voice_stt_vad_threshold";
    private static final String KEY_STT_MIN_VOICED_MS = "voice_stt_min_voiced_ms";
    private static final String KEY_STT_SILENCE_END_MS = "voice_stt_silence_end_ms";
    private static final String KEY_STT_VAD_WINDOW_SIZE = "voice_stt_vad_window_size";
    private static final String KEY_STT_VAD_NUM_THREADS = "voice_stt_vad_num_threads";
    private static final String KEY_PREFER_SPEAKERPHONE = "voice_prefer_speakerphone";

    private static final String DEFAULT_LOCALE = "en-US";
    private static final float DEFAULT_RATE = 1.0f;
    private static final float DEFAULT_PITCH = 1.0f;
    private static final long DEFAULT_COMMAND_DELAY = 500L;
    private static final long MIN_COMMAND_DELAY = 100L;
    private static final long MAX_COMMAND_DELAY = 5000L;
    private static final String ENGINE_NATIVE = "native";
    private static final String ENGINE_SHERPA = "sherpa";
    private static final boolean DEFAULT_ALLOW_BARGE_IN = false;
    private static final int DEFAULT_STT_SPEECH_THRESHOLD = 750;
    private static final int MIN_STT_SPEECH_THRESHOLD = 200;
    private static final int MAX_STT_SPEECH_THRESHOLD = 2000;
    private static final float DEFAULT_STT_VAD_THRESHOLD = 0.5f;
    private static final float MIN_STT_VAD_THRESHOLD = 0.2f;
    private static final float MAX_STT_VAD_THRESHOLD = 0.9f;
    private static final int DEFAULT_STT_MIN_VOICED_MS = 100;
    private static final int MIN_STT_MIN_VOICED_MS = 80;
    private static final int MAX_STT_MIN_VOICED_MS = 800;
    private static final int DEFAULT_STT_SILENCE_END_MS = 500;
    private static final int MIN_STT_SILENCE_END_MS = 120;
    private static final int MAX_STT_SILENCE_END_MS = 900;
    private static final int DEFAULT_STT_VAD_WINDOW_SIZE = 512;
    private static final int MIN_STT_VAD_WINDOW_SIZE = 256;
    private static final int MAX_STT_VAD_WINDOW_SIZE = 1024;
    private static final int DEFAULT_STT_VAD_NUM_THREADS = 1;
    private static final int MIN_STT_VAD_NUM_THREADS = 1;
    private static final int MAX_STT_VAD_NUM_THREADS = 4;
    private static final boolean DEFAULT_PREFER_SPEAKERPHONE = true;

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
        long clamped = Math.max(MIN_COMMAND_DELAY, Math.min(MAX_COMMAND_DELAY, delayMs));
        prefs.edit().putLong(KEY_COMMAND_DELAY, clamped).apply();
    }

    public boolean isBargeInEnabled() {
        return prefs.getBoolean(KEY_ALLOW_BARGE_IN, DEFAULT_ALLOW_BARGE_IN);
    }

    public void setBargeInEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ALLOW_BARGE_IN, enabled).apply();
    }

    public int getSttSpeechThreshold() {
        int value = prefs.getInt(KEY_STT_SPEECH_THRESHOLD, DEFAULT_STT_SPEECH_THRESHOLD);
        return clampInt(value, MIN_STT_SPEECH_THRESHOLD, MAX_STT_SPEECH_THRESHOLD);
    }

    public void setSttSpeechThreshold(int threshold) {
        prefs.edit()
            .putInt(KEY_STT_SPEECH_THRESHOLD, clampInt(threshold, MIN_STT_SPEECH_THRESHOLD, MAX_STT_SPEECH_THRESHOLD))
            .apply();
    }

    public float getSttVadThreshold() {
        float value = prefs.getFloat(KEY_STT_VAD_THRESHOLD, DEFAULT_STT_VAD_THRESHOLD);
        return clamp(value, MIN_STT_VAD_THRESHOLD, MAX_STT_VAD_THRESHOLD);
    }

    public void setSttVadThreshold(float threshold) {
        prefs.edit()
            .putFloat(KEY_STT_VAD_THRESHOLD, clamp(threshold, MIN_STT_VAD_THRESHOLD, MAX_STT_VAD_THRESHOLD))
            .apply();
    }

    public int getSttMinVoicedMs() {
        int value = prefs.getInt(KEY_STT_MIN_VOICED_MS, DEFAULT_STT_MIN_VOICED_MS);
        return clampInt(value, MIN_STT_MIN_VOICED_MS, MAX_STT_MIN_VOICED_MS);
    }

    public void setSttMinVoicedMs(int minVoicedMs) {
        prefs.edit()
            .putInt(KEY_STT_MIN_VOICED_MS, clampInt(minVoicedMs, MIN_STT_MIN_VOICED_MS, MAX_STT_MIN_VOICED_MS))
            .apply();
    }

    public int getSttSilenceEndMs() {
        int value = prefs.getInt(KEY_STT_SILENCE_END_MS, DEFAULT_STT_SILENCE_END_MS);
        return clampInt(value, MIN_STT_SILENCE_END_MS, MAX_STT_SILENCE_END_MS);
    }

    public void setSttSilenceEndMs(int silenceEndMs) {
        prefs.edit()
            .putInt(KEY_STT_SILENCE_END_MS, clampInt(silenceEndMs, MIN_STT_SILENCE_END_MS, MAX_STT_SILENCE_END_MS))
            .apply();
    }

    public int getSttVadWindowSize() {
        int value = prefs.getInt(KEY_STT_VAD_WINDOW_SIZE, DEFAULT_STT_VAD_WINDOW_SIZE);
        return clampInt(value, MIN_STT_VAD_WINDOW_SIZE, MAX_STT_VAD_WINDOW_SIZE);
    }

    public void setSttVadWindowSize(int windowSize) {
        prefs.edit()
            .putInt(KEY_STT_VAD_WINDOW_SIZE, clampInt(windowSize, MIN_STT_VAD_WINDOW_SIZE, MAX_STT_VAD_WINDOW_SIZE))
            .apply();
    }

    public int getSttVadNumThreads() {
        int value = prefs.getInt(KEY_STT_VAD_NUM_THREADS, DEFAULT_STT_VAD_NUM_THREADS);
        return clampInt(value, MIN_STT_VAD_NUM_THREADS, MAX_STT_VAD_NUM_THREADS);
    }

    public void setSttVadNumThreads(int numThreads) {
        prefs.edit()
            .putInt(KEY_STT_VAD_NUM_THREADS, clampInt(numThreads, MIN_STT_VAD_NUM_THREADS, MAX_STT_VAD_NUM_THREADS))
            .apply();
    }

    public boolean isPreferSpeakerphone() {
        return prefs.getBoolean(KEY_PREFER_SPEAKERPHONE, DEFAULT_PREFER_SPEAKERPHONE);
    }

    public void setPreferSpeakerphone(boolean preferSpeakerphone) {
        prefs.edit().putBoolean(KEY_PREFER_SPEAKERPHONE, preferSpeakerphone).apply();
    }

    public void resetVoiceSettings() {
        prefs.edit()
            .putString(KEY_STT_LOCALE, DEFAULT_LOCALE)
            .putString(KEY_TTS_LOCALE, DEFAULT_LOCALE)
            .putFloat(KEY_TTS_RATE, DEFAULT_RATE)
            .putFloat(KEY_TTS_PITCH, DEFAULT_PITCH)
            .putString(KEY_VOICE_ENGINE, ENGINE_SHERPA)
            .putLong(KEY_COMMAND_DELAY, DEFAULT_COMMAND_DELAY)
            .putBoolean(KEY_ALLOW_BARGE_IN, DEFAULT_ALLOW_BARGE_IN)
            .putInt(KEY_STT_SPEECH_THRESHOLD, DEFAULT_STT_SPEECH_THRESHOLD)
            .putFloat(KEY_STT_VAD_THRESHOLD, DEFAULT_STT_VAD_THRESHOLD)
            .putInt(KEY_STT_MIN_VOICED_MS, DEFAULT_STT_MIN_VOICED_MS)
            .putInt(KEY_STT_SILENCE_END_MS, DEFAULT_STT_SILENCE_END_MS)
            .putInt(KEY_STT_VAD_WINDOW_SIZE, DEFAULT_STT_VAD_WINDOW_SIZE)
            .putInt(KEY_STT_VAD_NUM_THREADS, DEFAULT_STT_VAD_NUM_THREADS)
            .putBoolean(KEY_PREFER_SPEAKERPHONE, DEFAULT_PREFER_SPEAKERPHONE)
            .apply();
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

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

