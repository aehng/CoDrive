package com.codrive.ai;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.codrive.ai.llm.LlmModelCatalog;
import com.codrive.ai.llm.LlmModelInfo;
import com.codrive.ai.llm.LlmModelListResult;
import com.codrive.ai.settings.LlmProvider;
import com.codrive.ai.settings.LlmSettingsStore;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {
    private Spinner providerSpinner;
    private Spinner modelSpinner;
    private EditText modelInput;
    private EditText apiKeyInput;
    private TextView keyPreview;
    private TextView statusText;
    private LlmSettingsStore settingsStore;
    private ArrayAdapter<String> modelAdapter;
    private final List<LlmModelInfo> availableModels = new ArrayList<>();
    private LlmModelCatalog modelCatalog;
    private static final String DEFAULT_GEMINI_MODEL = "gemini-1.5-flash";

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
        Button loadModelsButton = findViewById(R.id.settingsLoadModelsButton);
        Button saveButton = findViewById(R.id.settingsSaveButton);
        ImageButton menuButton = findViewById(R.id.settingsMenuButton);

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

        settingsStore = LlmSettingsStore.create(getApplicationContext());

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
    }

    private void saveSettings() {
        LlmProvider provider = LlmProvider.values()[providerSpinner.getSelectedItemPosition()];
        String model = modelInput.getText().toString().trim();
        String enteredKey = apiKeyInput.getText().toString().trim();

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
