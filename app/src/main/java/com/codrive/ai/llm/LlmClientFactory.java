package com.codrive.ai.llm;

import com.codrive.ai.contracts.LlmClient;
import com.codrive.ai.model.ActionType;
import com.codrive.ai.model.AgentDecision;
import com.codrive.ai.model.PrunedUiMap;
import com.codrive.ai.settings.LlmProvider;
import com.codrive.ai.settings.LlmSettingsStore;

public final class LlmClientFactory {
    private LlmClientFactory() {
    }

    public static LlmClient create(LlmSettingsStore settingsStore) {
        if (settingsStore.isDualRoutingEnabled()) {
            String groqKey = settingsStore.getApiKeyFor(LlmProvider.GROQ);
            String geminiKey = settingsStore.getApiKeyFor(LlmProvider.GEMINI);
            if (!groqKey.isEmpty() && !geminiKey.isEmpty()) {
                LlmClient groqClient = createFor(
                        LlmProvider.GROQ,
                        settingsStore.getModelFor(LlmProvider.GROQ),
                        groqKey
                );
                LlmClient geminiClient = createFor(
                        LlmProvider.GEMINI,
                        settingsStore.getModelFor(LlmProvider.GEMINI),
                        geminiKey
                );
                return new DualRoutingLlmClient(
                        groqClient,
                        geminiClient,
                        settingsStore.getDualRoutingGroqPercent()
                );
            }
        }

        LlmProvider provider = settingsStore.getProvider();
        return createFor(
                provider,
                settingsStore.getModelFor(provider),
                settingsStore.getApiKeyFor(provider)
        );
    }

    static LlmClient createFor(LlmProvider provider, String model, String apiKey) {

        if (apiKey.isEmpty()) {
            return unsupported("LLM API key is not configured. Open LLM Settings first.");
        }

        if (provider == LlmProvider.GROQ) {
            return new GroqLlmClient(apiKey, model);
        }

        if (provider == LlmProvider.GEMINI) {
            return new GeminiLlmClient(apiKey, model);
        }

        if (provider == LlmProvider.OPENROUTER) {
            return new OpenRouterLlmClient(apiKey, model);
        }

        return unsupported(provider.displayName() + " is selected, but only Groq, Gemini, and OpenRouter are wired in tracer bullet right now.");
    }

    private static LlmClient unsupported(String message) {
        return new LlmClient() {
            @Override
            public AgentDecision infer(String command, PrunedUiMap uiMap) {
                return new AgentDecision(ActionType.FINISH, -1, "", "", message, 0.0);
            }
        };
    }
}


