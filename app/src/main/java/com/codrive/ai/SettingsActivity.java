package com.codrive.ai;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.codrive.ai.llm.KeyValidationResult;
import com.codrive.ai.llm.LlmKeyValidator;
import com.codrive.ai.settings.LlmProvider;
import com.codrive.ai.settings.LlmSettingsStore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {
    private Spinner providerSpinner;
    private EditText modelInput;
    private EditText apiKeyInput;
    private TextView keyPreview;
    private TextView statusText;
    private Button saveButton;
    private Button continueAnywayButton;
    private LlmSettingsStore settingsStore;
    private LlmKeyValidator keyValidator;
    private ExecutorService backgroundExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        providerSpinner = findViewById(R.id.settingsProviderSpinner);
        modelInput = findViewById(R.id.settingsModelInput);
        apiKeyInput = findViewById(R.id.settingsApiKeyInput);
        keyPreview = findViewById(R.id.settingsKeyPreview);
        statusText = findViewById(R.id.settingsStatusText);
        saveButton = findViewById(R.id.settingsSaveButton);
        continueAnywayButton = findViewById(R.id.settingsContinueAnywayButton);

        settingsStore = LlmSettingsStore.create(getApplicationContext());
        keyValidator = new LlmKeyValidator();
        backgroundExecutor = Executors.newSingleThreadExecutor();

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

        saveButton.setOnClickListener(v -> saveSettings());
        continueAnywayButton.setOnClickListener(v -> {
            setResult(RESULT_OK);
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdownNow();
        }
        super.onDestroy();
    }

    private void loadCurrentSettings() {
        LlmProvider provider = settingsStore.getProvider();
        providerSpinner.setSelection(provider.ordinal());
        modelInput.setText(settingsStore.getModel());
        keyPreview.setText(getString(R.string.settings_key_preview_format, settingsStore.getMaskedApiKey()));
        statusText.setText(R.string.settings_status_idle);
        continueAnywayButton.setVisibility(android.view.View.GONE);
    }

    private void saveSettings() {
        LlmProvider provider = LlmProvider.values()[providerSpinner.getSelectedItemPosition()];
        String model = modelInput.getText().toString().trim();
        String enteredKey = apiKeyInput.getText().toString().trim();

        settingsStore.saveProviderAndModel(provider, model);
        if (!TextUtils.isEmpty(enteredKey)) {
            settingsStore.saveApiKey(enteredKey);
        }

        if (!settingsStore.hasApiKey()) {
            statusText.setText(R.string.settings_key_required);
            return;
        }

        keyPreview.setText(getString(R.string.settings_key_preview_format, settingsStore.getMaskedApiKey()));
        apiKeyInput.setText("");

        saveButton.setEnabled(false);
        continueAnywayButton.setVisibility(android.view.View.GONE);
        statusText.setText(R.string.settings_validating);

        String resolvedModel = settingsStore.getModel();
        String resolvedKey = settingsStore.getApiKey();
        backgroundExecutor.execute(() -> {
            KeyValidationResult validation = keyValidator.validate(provider, resolvedModel, resolvedKey);
            runOnUiThread(() -> handleValidation(validation));
        });
    }

    private void handleValidation(KeyValidationResult validation) {
        saveButton.setEnabled(true);
        statusText.setText(validation.getMessage());
        if (validation.isValid()) {
            setResult(RESULT_OK);
            finish();
        } else {
            // Validation is advisory for tracer bullet; user can still proceed and try runtime execution.
            continueAnywayButton.setVisibility(android.view.View.VISIBLE);
        }
    }
}

