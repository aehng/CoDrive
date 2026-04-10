package com.codrive.ai;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final String KEY_SETTINGS_PROMPT_SHOWN = "settings_prompt_shown";

    private TextView statusText;
    private boolean settingsPromptShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        Button openSettingsButton = findViewById(R.id.openSettingsButton);
        openSettingsButton.setOnClickListener(v -> openAccessibilitySettings());

        if (savedInstanceState != null) {
            settingsPromptShown = savedInstanceState.getBoolean(KEY_SETTINGS_PROMPT_SHOWN, false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean serviceEnabled = isAccessibilityServiceEnabled();
        statusText.setText(serviceEnabled
                ? R.string.main_status_enabled
                : R.string.main_status_disabled);

        // First-run onboarding: take the user straight to Accessibility settings.
        if (!serviceEnabled && !settingsPromptShown) {
            settingsPromptShown = true;
            openAccessibilitySettings();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(KEY_SETTINGS_PROMPT_SHOWN, settingsPromptShown);
        super.onSaveInstanceState(outState);
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
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


