package com.codrive.ai.contracts

import com.codrive.ai.model.ActionType
import com.codrive.ai.model.AgentDecision
import com.codrive.ai.model.ExecutionResult
import com.codrive.ai.model.PrunedNodeEntry
import com.codrive.ai.model.PrunedUiMap
import com.codrive.ai.model.UiRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentContractsTest {
    private class FakeStack : SttEngine, TtsEngine, LlmClient, ActionExecutor {
        var lastSpokenMessage: String? = null
        var stopListeningCalls = 0
        var stopTtsCalls = 0
        var lastCommand: String? = null
        var lastUiMap: PrunedUiMap? = null
        var lastDecision: AgentDecision? = null

        override fun startListening(onTranscript: (String) -> Unit) {
            onTranscript("open settings")
        }

        override fun stopListening() {
            stopListeningCalls += 1
        }

        override fun speak(message: String) {
            lastSpokenMessage = message
        }

        override fun stop() {
            stopTtsCalls += 1
        }

        override fun infer(command: String, uiMap: PrunedUiMap): AgentDecision {
            lastCommand = command
            lastUiMap = uiMap
            return AgentDecision(
                actionType = ActionType.FINISH,
                confidenceScore = 1.0,
                voiceFeedback = "Done",
            ).also { lastDecision = it }
        }

        override fun execute(decision: AgentDecision, uiMap: PrunedUiMap): ExecutionResult {
            lastDecision = decision
            lastUiMap = uiMap
            return ExecutionResult(
                success = true,
                message = "Executed ${decision.actionType}",
                performedAction = decision.actionType,
                targetIndex = decision.targetIndex,
            )
        }
    }

    @Test
    fun fakeStackCanWireThePhaseOneContractsTogether() {
        val stack = FakeStack()
        val uiMap = PrunedUiMap(
            snapshotId = 9L,
            entries = listOf(
                PrunedNodeEntry(
                    index = 1,
                    role = UiRole.BUTTON,
                    bounds = intArrayOf(1, 2, 3, 4),
                    text = "Next",
                    isInteractive = true,
                ),
            ),
        )

        var transcriptFromStt: String? = null
        stack.startListening { transcriptFromStt = it }
        val decision = stack.infer(transcriptFromStt.orEmpty(), uiMap)
        val result = stack.execute(decision, uiMap)
        stack.speak(decision.voiceFeedback)
        stack.stopListening()
        stack.stop()

        assertEquals("open settings", transcriptFromStt)
        assertEquals("open settings", stack.lastCommand)
        assertEquals(uiMap, stack.lastUiMap)
        assertEquals(ActionType.FINISH, decision.actionType)
        assertTrue(result.success)
        assertEquals("Done", stack.lastSpokenMessage)
        assertEquals(1, stack.stopListeningCalls)
        assertEquals(1, stack.stopTtsCalls)
    }
}


