package com.codrive.ai;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.codrive.ai.contracts.TtsEngine;
import com.codrive.ai.llm.LlmModelCatalog;
import com.codrive.ai.llm.LlmModelInfo;
import com.codrive.ai.llm.LlmModelListResult;
import com.codrive.ai.modeldownload.ModelReadiness;
import com.codrive.ai.modeldownload.ModelStorage;
import com.codrive.ai.settings.LlmProvider;
import com.codrive.ai.settings.LlmSettingsStore;
import com.codrive.ai.settings.VoiceSettingsStore;
import com.codrive.ai.voice.AndroidTextToSpeechEngine;
import com.codrive.ai.voice.SherpaTtsEngine;
import com.codrive.ai.voice.SpeakObserver;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {
    private Spinner providerSpinner;
    private Spinner modelSpinner;
    private EditText modelInput;
    private EditText apiKeyInput;
    private TextView keyPreview;
    private TextView statusText;
    private EditText sttLocaleInput;
    private EditText ttsLocaleInput;
    private SeekBar ttsRateSeekBar;
    private TextView ttsRateValueText;
    private SeekBar ttsPitchSeekBar;
    private TextView ttsPitchValueText;
    private CheckBox sherpaEnabledCheckbox;
    private CheckBox bargeInEnabledCheckbox;
    private CheckBox speakerphonePreferredCheckbox;
    private SeekBar commandDelaySeekBar;
    private TextView commandDelayValueText;
    private SeekBar speechThresholdSeekBar;
    private TextView speechThresholdValueText;
    private SeekBar minVoicedSeekBar;
    private TextView minVoicedValueText;
    private SeekBar silenceEndSeekBar;
    private TextView silenceEndValueText;
    private TtsEngine testTtsEngine;
    private LlmSettingsStore settingsStore;
    private VoiceSettingsStore voiceSettingsStore;
    private ArrayAdapter<String> modelAdapter;
    private final List<LlmModelInfo> availableModels = new ArrayList<>();
    private LlmModelCatalog modelCatalog;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable voiceTestTimeoutRunnable;
    private static final String DEFAULT_GEMINI_MODEL = "gemini-1.5-flash";
    private static final long COMMAND_DELAY_MIN_MS = 100L;
    private static final long VOICE_TEST_TIMEOUT_MS = 12_000L;
    private static final float TTS_RATE_MIN = 0.7f;
    private static final float TTS_RATE_MAX = 1.3f;
    private static final float TTS_RATE_STEP = 0.01f;
    private static final int TTS_PITCH_MIN_PROGRESS = 0;
    private static final float TTS_PITCH_MIN = 0.7f;
    private static final float TTS_PITCH_MAX = 1.3f;
    private static final float TTS_PITCH_STEP = 0.01f;
    private static final int STT_SPEECH_THRESHOLD_MIN = 200;
    private static final int STT_MIN_VOICED_MIN = 80;
    private static final int STT_SILENCE_END_MIN = 120;
    private static final String TAG = "SettingsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        View settingsRoot = findViewById(R.id.settingsRoot);
        View settingsContent = findViewById(R.id.settingsContent);
        configureInsets(settingsRoot, settingsContent);

        providerSpinner = findViewById(R.id.settingsProviderSpinner);
        modelInput = findViewById(R.id.settingsModelInput);
        apiKeyInput = findViewById(R.id.settingsApiKeyInput);
        keyPreview = findViewById(R.id.settingsKeyPreview);
        statusText = findViewById(R.id.settingsStatusText);
        modelSpinner = findViewById(R.id.settingsModelSpinner);
        sttLocaleInput = findViewById(R.id.settingsSttLocaleInput);
        ttsLocaleInput = findViewById(R.id.settingsTtsLocaleInput);
        ttsRateSeekBar = findViewById(R.id.settingsTtsRateSeekBar);
        ttsRateValueText = findViewById(R.id.settingsTtsRateValue);
        ttsPitchSeekBar = findViewById(R.id.settingsTtsPitchSeekBar);
        ttsPitchValueText = findViewById(R.id.settingsTtsPitchValue);
        sherpaEnabledCheckbox = findViewById(R.id.settingsSherpaEnabled);
        bargeInEnabledCheckbox = findViewById(R.id.settingsBargeInEnabled);
        speakerphonePreferredCheckbox = findViewById(R.id.settingsSpeakerphonePreferred);
        commandDelaySeekBar = findViewById(R.id.settingsCommandDelaySeekBar);
        commandDelayValueText = findViewById(R.id.settingsCommandDelayValue);
        speechThresholdSeekBar = findViewById(R.id.settingsSpeechThresholdSeekBar);
        speechThresholdValueText = findViewById(R.id.settingsSpeechThresholdValue);
        minVoicedSeekBar = findViewById(R.id.settingsMinVoicedSeekBar);
        minVoicedValueText = findViewById(R.id.settingsMinVoicedValue);
        silenceEndSeekBar = findViewById(R.id.settingsSilenceEndSeekBar);
        silenceEndValueText = findViewById(R.id.settingsSilenceEndValue);
        Button loadModelsButton = findViewById(R.id.settingsLoadModelsButton);
        Button saveButton = findViewById(R.id.settingsSaveButton);
        Button testVoiceButton = findViewById(R.id.settingsTestVoiceButton);
        ImageButton menuButton = findViewById(R.id.settingsMenuButton);
        ImageButton backButton = findViewById(R.id.settingsBackButton);

        modelCatalog = new LlmModelCatalog();

        modelAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new ArrayList<>()
        );
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modelSpinner.setAdapter(modelAdapter);

        modelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < availableModels.size()) {
                    LlmModelInfo info = availableModels.get(position);
                    modelInput.setText(info.getId());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // no-op
            }
        });

        menuButton.setOnClickListener(this::showNavigationMenu);
        backButton.setOnClickListener(v -> finish());

        settingsStore = LlmSettingsStore.create(getApplicationContext());
        voiceSettingsStore = VoiceSettingsStore.create(getApplicationContext());

        String[] providerLabels = new String[LlmProvider.values().length];
        for (int i = 0; i < LlmProvider.values().length; i++) {
            providerLabels[i] = LlmProvider.values()[i].displayName();
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                providerLabels
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        providerSpinner.setAdapter(adapter);

        loadCurrentSettings();

        // When the user switches the provider in the UI, load the provider-specific model/key.
        providerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                LlmProvider selected = LlmProvider.values()[position];
                updateForProvider(selected);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // no-op
            }
        });

        saveButton.setOnClickListener(v -> saveSettings());
        loadModelsButton.setOnClickListener(v -> loadModelsForProvider());
        testVoiceButton.setOnClickListener(v -> testVoiceOutput());

        ttsRateSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateTtsRateValue(getTtsRateFromSeekBar());
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        commandDelaySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                long delay = progress + COMMAND_DELAY_MIN_MS;
                commandDelayValueText.setText(delay + " ms");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        ttsPitchSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateTtsPitchValue(getTtsPitchFromSeekBar());
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        speechThresholdSeekBar.setOnSeekBarChangeListener(simpleSeekBarListener(progress ->
                speechThresholdValueText.setText(String.valueOf(progress + STT_SPEECH_THRESHOLD_MIN))
        ));

        minVoicedSeekBar.setOnSeekBarChangeListener(simpleSeekBarListener(progress ->
                minVoicedValueText.setText((progress + STT_MIN_VOICED_MIN) + " ms")
        ));

        silenceEndSeekBar.setOnSeekBarChangeListener(simpleSeekBarListener(progress ->
                silenceEndValueText.setText((progress + STT_SILENCE_END_MIN) + " ms")
        ));

    }

    private void loadCurrentSettings() {
        LlmProvider provider = settingsStore.getProvider();
        providerSpinner.setSelection(provider.ordinal());
        String storedModel = settingsStore.getModelFor(provider);
        if (provider == LlmProvider.GEMINI && (storedModel == null || storedModel.trim().isEmpty())) {
            modelInput.setText(DEFAULT_GEMINI_MODEL);
        } else {
            modelInput.setText(storedModel);
        }
        keyPreview.setText(getString(R.string.settings_key_preview_format, settingsStore.getMaskedApiKeyFor(provider)));
        statusText.setText(R.string.settings_status_idle);
        clearModelList();

        sttLocaleInput.setText(voiceSettingsStore.getSttLocaleTag());
        ttsLocaleInput.setText(voiceSettingsStore.getTtsLocaleTag());
        float ttsRate = voiceSettingsStore.getTtsRate();
        ttsRateSeekBar.setProgress(progressForRate(ttsRate));
        updateTtsRateValue(ttsRate);
        float ttsPitch = voiceSettingsStore.getTtsPitch();
        ttsPitchSeekBar.setProgress(progressForPitch(ttsPitch));
        updateTtsPitchValue(ttsPitch);
        sherpaEnabledCheckbox.setChecked(voiceSettingsStore.isSherpaEnabled());
        bargeInEnabledCheckbox.setChecked(voiceSettingsStore.isBargeInEnabled());
        speakerphonePreferredCheckbox.setChecked(voiceSettingsStore.isPreferSpeakerphone());
        long currentDelay = voiceSettingsStore.getCommandDelayMs();
        commandDelaySeekBar.setProgress((int) Math.max(0L, currentDelay - COMMAND_DELAY_MIN_MS));
        commandDelayValueText.setText(currentDelay + " ms");
        int speechThreshold = voiceSettingsStore.getSttSpeechThreshold();
        speechThresholdSeekBar.setProgress(speechThreshold - STT_SPEECH_THRESHOLD_MIN);
        speechThresholdValueText.setText(String.valueOf(speechThreshold));
        int minVoiced = voiceSettingsStore.getSttMinVoicedMs();
        minVoicedSeekBar.setProgress(minVoiced - STT_MIN_VOICED_MIN);
        minVoicedValueText.setText(minVoiced + " ms");
        int silenceEnd = voiceSettingsStore.getSttSilenceEndMs();
        silenceEndSeekBar.setProgress(silenceEnd - STT_SILENCE_END_MIN);
        silenceEndValueText.setText(silenceEnd + " ms");
    }

    private void saveSettings() {
        LlmProvider provider = LlmProvider.values()[providerSpinner.getSelectedItemPosition()];
        String model = modelInput.getText().toString().trim();
        String enteredKey = apiKeyInput.getText().toString().trim();

        String sttLocale = sttLocaleInput.getText().toString().trim();
        String ttsLocale = ttsLocaleInput.getText().toString().trim();
        float ttsRate = getTtsRateFromSeekBar();
        float ttsPitch = getTtsPitchFromSeekBar();

        voiceSettingsStore.saveVoiceSettings(sttLocale, ttsLocale, ttsRate, ttsPitch);
        voiceSettingsStore.setSherpaEnabled(sherpaEnabledCheckbox.isChecked());
        voiceSettingsStore.setBargeInEnabled(bargeInEnabledCheckbox.isChecked());
        voiceSettingsStore.setPreferSpeakerphone(speakerphonePreferredCheckbox.isChecked());
        voiceSettingsStore.setCommandDelayMs(commandDelaySeekBar.getProgress() + COMMAND_DELAY_MIN_MS);
        voiceSettingsStore.setSttSpeechThreshold(speechThresholdSeekBar.getProgress() + STT_SPEECH_THRESHOLD_MIN);
        voiceSettingsStore.setSttMinVoicedMs(minVoicedSeekBar.getProgress() + STT_MIN_VOICED_MIN);
        voiceSettingsStore.setSttSilenceEndMs(silenceEndSeekBar.getProgress() + STT_SILENCE_END_MIN);

        settingsStore.saveProviderAndModel(provider, model);
        if (!TextUtils.isEmpty(enteredKey)) {
            settingsStore.saveApiKeyFor(provider, enteredKey);
        }

        keyPreview.setText(getString(R.string.settings_key_preview_format, settingsStore.getMaskedApiKeyFor(provider)));
        apiKeyInput.setText("");

        if (!provider.isTracerBulletSupported()) {
            statusText.setText(R.string.settings_saved_provider_not_wired);
            return;
        }

        // For tracer-bullet providers (Groq, Gemini) run a lightweight validation when a key was entered.
        if (provider == LlmProvider.GEMINI && !TextUtils.isEmpty(settingsStore.getApiKeyFor(provider))) {
            statusText.setText(R.string.settings_status_validating);
            final String keyToValidate = settingsStore.getApiKeyFor(provider);
            final String modelToValidate = settingsStore.getModelFor(provider);
            new Thread(() -> {
                String message;
                boolean ok;
                try {
                    // Call into the Kotlin companion validateApiKey via the Java interop
                    kotlin.Pair<Boolean, String> p = com.codrive.ai.llm.GeminiLlmClient.Companion.validateApiKey(keyToValidate, modelToValidate);
                    ok = p.getFirst();
                    message = p.getSecond();
                } catch (Exception e) {
                    ok = false;
                    message = e.getMessage();
                }
                final boolean finalOk = ok;
                final String finalMessage = message;
                runOnUiThread(() -> {
                    if (finalOk) {
                        // If validation succeeded but the validator used an alternate model formatting
                        // (e.g. prefixed with "models/"), persist that alternate model so runtime
                        // requests use the form that the Gemini endpoint accepted.
                        if (finalMessage != null && finalMessage.contains("alt-model")) {
                            String altModel = modelToValidate.startsWith("models/") ? modelToValidate : "models/" + modelToValidate;
                            settingsStore.saveProviderAndModel(provider, altModel);
                        }
                        statusText.setText(R.string.settings_saved);
                    } else {
                        statusText.setText(getString(R.string.settings_saved_but_validation_failed, finalMessage));
                    }
                });
            }).start();
        } else {
            statusText.setText(R.string.settings_saved);
        }
    }

    private void loadModelsForProvider() {
        LlmProvider provider = LlmProvider.values()[providerSpinner.getSelectedItemPosition()];
        String enteredKey = apiKeyInput.getText().toString().trim();
        String storedKey = settingsStore.getApiKeyFor(provider);
        String keyToUse = !TextUtils.isEmpty(enteredKey) ? enteredKey : storedKey;
        if (TextUtils.isEmpty(keyToUse)) {
            statusText.setText(R.string.settings_status_models_missing_key);
            return;
        }
        statusText.setText(R.string.settings_status_loading_models);
        new Thread(() -> {
            LlmModelListResult result = modelCatalog.fetch(provider, keyToUse);
            runOnUiThread(() -> handleModelListResult(result));
        }).start();
    }

    private void handleModelListResult(LlmModelListResult result) {
        if (!result.getSuccess()) {
            statusText.setText(getString(R.string.settings_status_models_failed, result.getMessage()));
            clearModelList();
            return;
        }
        List<LlmModelInfo> models = result.getModels();
        if (models == null || models.isEmpty()) {
            statusText.setText(R.string.settings_status_models_empty);
            clearModelList();
            return;
        }
        availableModels.clear();
        availableModels.addAll(models);
        modelAdapter.clear();
        for (LlmModelInfo info : models) {
            modelAdapter.add(info.getDisplayName());
        }
        modelAdapter.notifyDataSetChanged();
        statusText.setText(R.string.settings_status_models_loaded);

        String currentModel = modelInput.getText().toString().trim();
        int selection = findModelIndex(currentModel);
        if (selection >= 0) {
            modelSpinner.setSelection(selection);
        }
    }

    private int findModelIndex(String modelId) {
        for (int i = 0; i < availableModels.size(); i++) {
            if (availableModels.get(i).getId().equals(modelId)) {
                return i;
            }
        }
        return -1;
    }

    private void clearModelList() {
        availableModels.clear();
        modelAdapter.clear();
        modelAdapter.notifyDataSetChanged();
    }

    private void configureInsets(View root, View content) {
        final int baseRootBottom = root.getPaddingBottom();
        final int baseContentBottom = content.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    view.getPaddingLeft(),
                    view.getPaddingTop(),
                    view.getPaddingRight(),
                    baseRootBottom
            );
            content.setPadding(
                    content.getPaddingLeft(),
                    content.getPaddingTop(),
                    content.getPaddingRight(),
                    baseContentBottom + bars.bottom
            );
            return insets;
        });
    }

    private void showNavigationMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenuInflater().inflate(R.menu.chat_navigation_menu, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(this::onNavigationItemClicked);
        popupMenu.show();
    }

    private void updateForProvider(LlmProvider provider) {
        String providerModel = settingsStore.getModelFor(provider);
        modelInput.setText(providerModel);
        keyPreview.setText(getString(R.string.settings_key_preview_format, settingsStore.getMaskedApiKeyFor(provider)));
        apiKeyInput.setText("");
        statusText.setText(R.string.settings_status_idle);
        clearModelList();
    }

    @Override
    protected void onDestroy() {
        releaseTestTtsEngine();
        super.onDestroy();
    }

    private void testVoiceOutput() {
        cancelVoiceTestTimeout();
        releaseTestTtsEngine();
        boolean wantsSherpa = sherpaEnabledCheckbox.isChecked();
        ModelStorage storage = new ModelStorage(new File(getNoBackupFilesDir(), "models"));
        if (wantsSherpa && !ModelReadiness.INSTANCE.hasTtsModels(storage)) {
            // Log detailed diagnostics about which model files are missing/invalid so we can
            // inspect via logcat when testing Sherpa voice output.
            StringBuilder diag = new StringBuilder();
            diag.append("Sherpa TTS model readiness check failed:\n");
            try {
                // Iterate through each required voice asset and print verification status
                for (com.codrive.ai.models.ModelAsset asset : com.codrive.ai.models.ModelManifest.INSTANCE.getVoiceRequiredModels()) {
                    java.io.File dest = storage.destinationFile(asset);
                    boolean exists = dest.exists();
                    boolean isValid = storage.isValidFile(asset);
                    boolean isVerified = storage.isVerified(asset);
                    diag.append(String.format("- %s\n    path=%s\n    exists=%b valid=%b verified=%b\n",
                            asset.getFileName(), dest.getAbsolutePath(), exists, isValid, isVerified));
                    // For the espeak archive, also show the extracted dir state
                    if (asset.equals(com.codrive.ai.models.ModelManifest.INSTANCE.getTTS_ESPEAK_DATA())) {
                        java.io.File extracted = storage.extractedDir(asset);
                        diag.append(String.format("    extractedDir=%s exists=%b\n",
                                extracted.getAbsolutePath(), extracted.exists()));
                    }
                }
            } catch (Exception ex) {
                Log.e(TAG, "Failed to evaluate model readiness diagnostics", ex);
            }
            // Emit as an error so it stands out in logcat while testing
            Log.e(TAG, diag.toString());
            statusText.setText(R.string.settings_voice_test_missing_models);
            return;
        }
        if (wantsSherpa) {
            testTtsEngine = new SherpaTtsEngine(this, storage, new SpeakObserver() {
                @Override
                public void onSpeakFinished(boolean success, String errorMessage) {
                    runOnUiThread(() -> {
                        cancelVoiceTestTimeout();
                        releaseTestTtsEngine();
                        if (success) {
                            statusText.setText(R.string.settings_voice_test_complete);
                        } else {
                            statusText.setText(getString(R.string.settings_voice_test_failed, errorMessage != null ? errorMessage : "unknown error"));
                        }
                    });
                }
            });
            statusText.setText(R.string.settings_voice_test_sherpa);
        } else {
            testTtsEngine = new AndroidTextToSpeechEngine(this, voiceSettingsStore);
            statusText.setText(R.string.settings_voice_test_native);
        }
        testTtsEngine.speak(getString(R.string.settings_voice_test_sample));
        scheduleVoiceTestTimeout();
    }

    private void releaseTestTtsEngine() {
        cancelVoiceTestTimeout();
        if (testTtsEngine == null) {
            return;
        }
        testTtsEngine.stop();
        if (testTtsEngine instanceof AndroidTextToSpeechEngine) {
            ((AndroidTextToSpeechEngine) testTtsEngine).shutdown();
        } else if (testTtsEngine instanceof SherpaTtsEngine) {
            ((SherpaTtsEngine) testTtsEngine).shutdown();
        }
        testTtsEngine = null;
    }

    private void scheduleVoiceTestTimeout() {
        cancelVoiceTestTimeout();
        voiceTestTimeoutRunnable = () -> {
            if (testTtsEngine != null) {
                Log.w(TAG, "Voice test timed out; releasing test engine");
                releaseTestTtsEngine();
                statusText.setText(R.string.settings_voice_test_timeout);
            }
        };
        mainHandler.postDelayed(voiceTestTimeoutRunnable, VOICE_TEST_TIMEOUT_MS);
    }

    private void cancelVoiceTestTimeout() {
        if (voiceTestTimeoutRunnable != null) {
            mainHandler.removeCallbacks(voiceTestTimeoutRunnable);
            voiceTestTimeoutRunnable = null;
        }
    }

    private float getTtsRateFromSeekBar() {
        return TTS_RATE_MIN + (ttsRateSeekBar.getProgress() * TTS_RATE_STEP);
    }

    private int progressForRate(float rate) {
        float clamped = Math.max(TTS_RATE_MIN, Math.min(TTS_RATE_MAX, rate));
        return Math.round((clamped - TTS_RATE_MIN) / TTS_RATE_STEP);
    }

    private float getTtsPitchFromSeekBar() {
        return TTS_PITCH_MIN + (ttsPitchSeekBar.getProgress() * TTS_PITCH_STEP);
    }

    private int progressForPitch(float pitch) {
        float clamped = Math.max(TTS_PITCH_MIN, Math.min(TTS_PITCH_MAX, pitch));
        return Math.max(TTS_PITCH_MIN_PROGRESS, Math.round((clamped - TTS_PITCH_MIN) / TTS_PITCH_STEP));
    }

    private void updateTtsRateValue(float rate) {
        ttsRateValueText.setText(String.format(Locale.US, "%.2f", rate));
    }

    private void updateTtsPitchValue(float pitch) {
        ttsPitchValueText.setText(String.format(Locale.US, "%.2f", pitch));
    }

    private SeekBar.OnSeekBarChangeListener simpleSeekBarListener(ProgressChanged onProgressChanged) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                onProgressChanged.run(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        };
    }

    private interface ProgressChanged {
        void run(int progress);
    }

    private boolean onNavigationItemClicked(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_chat_home) {
            startActivity(new Intent(this, MainActivity.class));
            return true;
        }
        if (itemId == R.id.menu_chat_chat) {
            startActivity(new Intent(this, ChatActivity.class));
            return true;
        }
        if (itemId == R.id.menu_chat_settings) {
            return true;
        }
        return false;
    }
}
