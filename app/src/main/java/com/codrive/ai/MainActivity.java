package com.codrive.ai;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.codrive.ai.settings.LlmSettingsStore;

public class MainActivity extends AppCompatActivity {
    private TextView statusText;
    private Button openSettingsButton;
    private Button openLlmSettingsButton;
    private Button startChatButton;
    private LlmSettingsStore llmSettingsStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        openSettingsButton = findViewById(R.id.openSettingsButton);
        openSettingsButton.setOnClickListener(v -> openAccessibilitySettings());

        openLlmSettingsButton = findViewById(R.id.openLlmSettingsButton);
        openLlmSettingsButton.setOnClickListener(v -> openLlmSettings());

        startChatButton = findViewById(R.id.startChatButton);
        startChatButton.setOnClickListener(v -> openChat());

        llmSettingsStore = LlmSettingsStore.create(getApplicationContext());
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean serviceEnabled = isAccessibilityServiceEnabled();
        boolean hasLlmKey = llmSettingsStore.hasApiKey();

        // Never auto-open settings. Keep user in-app and let them choose.
        openSettingsButton.setVisibility(serviceEnabled ? View.GONE : View.VISIBLE);
        openLlmSettingsButton.setVisibility(serviceEnabled ? View.VISIBLE : View.GONE);
        startChatButton.setVisibility(serviceEnabled && hasLlmKey ? View.VISIBLE : View.GONE);

        if (!serviceEnabled) {
            statusText.setText(R.string.main_status_disabled);
        } else if (!hasLlmKey) {
            statusText.setText(R.string.main_status_needs_llm_key);
        } else {
            statusText.setText(R.string.main_status_enabled);
        }
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
    }

    private void openChat() {
        Intent intent = new Intent(this, com.codrive.ai.ChatActivity.class);
        startActivity(intent);
    }

    private void openLlmSettings() {
        Intent intent = new Intent(this, com.codrive.ai.SettingsActivity.class);
        startActivity(intent);
    }

    private boolean isAccessibilityServiceEnabled() {
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (TextUtils.isEmpty(enabledServices)) {
            return false;
        }

        String serviceComponent = new ComponentName(this, com.codrive.ai.service.CoDriveAccessibilityService.class)
                .flattenToString();
        String[] flattenedServices = enabledServices.split(":");
        for (String flattened : flattenedServices) {
            if (serviceComponent.equalsIgnoreCase(flattened)) {
                return true;
            }
        }
        return false;
    }
}


