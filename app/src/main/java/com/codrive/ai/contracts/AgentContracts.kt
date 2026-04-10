package com.codrive.ai.contracts

import com.codrive.ai.model.AgentDecision
import com.codrive.ai.model.ExecutionResult
import com.codrive.ai.model.PrunedUiMap

interface SttEngine {
    fun startListening(onTranscript: (String) -> Unit)
    fun stopListening()
}

interface TtsEngine {
    fun speak(message: String)
    fun stop()
}

interface LlmClient {
    fun infer(command: String, uiMap: PrunedUiMap): AgentDecision
}

interface ActionExecutor {
    fun execute(decision: AgentDecision, uiMap: PrunedUiMap): ExecutionResult
}


