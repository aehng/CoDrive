package com.codrive.ai.settings;

public enum LlmProvider {
    GROQ("Groq"),
    GEMINI("Gemini"),
    OPENAI("OpenAI"),
    CUSTOM_HTTP("Custom HTTP");

    private final String displayName;

    LlmProvider(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public String storageValue() {
        return name();
    }

    public static LlmProvider fromStorageValue(String raw) {
        if (raw == null || raw.isEmpty()) {
            return GROQ;
        }
        for (LlmProvider provider : values()) {
            if (provider.name().equalsIgnoreCase(raw)) {
                return provider;
            }
        }
        return GROQ;
    }

    public boolean isTracerBulletSupported() {
        return this == GROQ;
    }
}

