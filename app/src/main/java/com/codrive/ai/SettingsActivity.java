package com.codrive.ai;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
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

import com.codrive.ai.settings.LlmProvider;
import com.codrive.ai.settings.LlmSettingsStore;

public class SettingsActivity extends AppCompatActivity {
    private Spinner providerSpinner;
    private EditText modelInput;
    private EditText apiKeyInput;
    private TextView keyPreview;
    private TextView statusText;
    private LlmSettingsStore settingsStore;

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
        Button saveButton = findViewById(R.id.settingsSaveButton);
        ImageButton menuButton = findViewById(R.id.settingsMenuButton);

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

        saveButton.setOnClickListener(v -> saveSettings());
    }

    private void loadCurrentSettings() {
        LlmProvider provider = settingsStore.getProvider();
        providerSpinner.setSelection(provider.ordinal());
        modelInput.setText(settingsStore.getModel());
        keyPreview.setText(getString(R.string.settings_key_preview_format, settingsStore.getMaskedApiKey()));
        statusText.setText(R.string.settings_status_idle);
    }

    private void saveSettings() {
        LlmProvider provider = LlmProvider.values()[providerSpinner.getSelectedItemPosition()];
        String model = modelInput.getText().toString().trim();
        String enteredKey = apiKeyInput.getText().toString().trim();

        settingsStore.saveProviderAndModel(provider, model);
        if (!TextUtils.isEmpty(enteredKey)) {
            settingsStore.saveApiKey(enteredKey);
        }

        keyPreview.setText(getString(R.string.settings_key_preview_format, settingsStore.getMaskedApiKey()));
        apiKeyInput.setText("");

        if (!provider.isTracerBulletSupported()) {
            statusText.setText(R.string.settings_saved_provider_not_wired);
            return;
        }

        statusText.setText(R.string.settings_saved);
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
