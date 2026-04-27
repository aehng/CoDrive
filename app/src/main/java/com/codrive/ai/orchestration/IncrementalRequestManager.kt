package com.codrive.ai.orchestration

import com.codrive.ai.accessibility.PruningOutcome
import com.codrive.ai.model.ActionType
import com.codrive.ai.model.AgentDecision
import com.codrive.ai.model.AgentPolicy
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BiFunction
import java.util.function.Consumer

class IncrementalRequestManager @JvmOverloads constructor(
    private val executor: ExecutorService,
    private val maxPromptChars: Int = AgentPolicy.groqHardTokenBudget * 4,
) {
    private val lock = Any()
    private val generationId = AtomicInteger(0)

    @Volatile
    private var activeSession: IncrementalSession? = null

    @Volatile
    private var activeFuture: Future<*>? = null

    @Volatile
    private var callback: Consumer<TracerBulletResult>? = null

    fun registerCallback(callback: Consumer<TracerBulletResult>) {
        this.callback = callback
    }

    fun startSession(
        initialCommand: String,
        pruningOutcome: PruningOutcome,
        runner: BiFunction<String, PruningOutcome, TracerBulletResult>,
    ) {
        val normalized = normalize(initialCommand) ?: return
        val session = synchronized(lock) {
            activeFuture?.cancel(true)
            val generation = generationId.incrementAndGet()
            IncrementalSession(
                generation = generation,
                prompt = normalized,
                pruningOutcome = pruningOutcome,
                buffer = normalized,
            ).also {
                activeSession = it
            }
        }
        launchSession(session, runner)
    }

    fun appendToActiveRequest(
        additionalText: String,
        latestPruningOutcome: PruningOutcome?,
        runner: BiFunction<String, PruningOutcome, TracerBulletResult>,
    ) {
        val normalized = normalize(additionalText) ?: return
        val session = synchronized(lock) {
            val previous = activeSession
            val mergedPrompt = mergePrompt(previous?.prompt, normalized)
            val mergedBuffer = mergeBuffer(previous?.buffer, normalized)
            val pruningOutcome = latestPruningOutcome ?: previous?.pruningOutcome
            if (pruningOutcome == null) {
                return
            }

            activeFuture?.cancel(true)
            val generation = generationId.incrementAndGet()
            IncrementalSession(
                generation = generation,
                prompt = mergedPrompt,
                pruningOutcome = pruningOutcome,
                buffer = mergedBuffer,
            ).also {
                activeSession = it
            }
        }
        launchSession(session, runner)
    }

    fun cancelActiveRequest() {
        synchronized(lock) {
            generationId.incrementAndGet()
            activeFuture?.cancel(true)
            activeFuture = null
        }
    }

    fun endSession() {
        synchronized(lock) {
            generationId.incrementAndGet()
            activeFuture?.cancel(true)
            activeFuture = null
            activeSession = null
        }
    }

    fun getCurrentGeneration(): Int = generationId.get()

    private fun launchSession(
        session: IncrementalSession,
        runner: BiFunction<String, PruningOutcome, TracerBulletResult>,
    ) {
        val future = executor.submit {
            val result = runCatching { runner.apply(session.prompt, session.pruningOutcome) }
                .recoverCatching { error ->
                    if (error is CancellationException || error is InterruptedException) {
                        throw error
                    }
                    fallbackFailureResult(error)
                }
                .getOrElse { return@submit }

            if (session.generation != generationId.get()) {
                return@submit
            }

            callback?.accept(result)

            if (result.didExecute || result.decision.actionType == ActionType.FINISH) {
                synchronized(lock) {
                    if (activeSession?.generation == session.generation) {
                        activeSession = null
                    }
                }
            }
        }

        synchronized(lock) {
            if (activeSession?.generation == session.generation) {
                activeFuture = future
            } else {
                future.cancel(true)
            }
        }
    }

    private fun mergePrompt(previousPrompt: String?, additionalText: String): String {
        if (previousPrompt.isNullOrBlank()) {
            return additionalText
        }
        return truncatePrompt("$previousPrompt CONTINUATION: $additionalText")
    }

    private fun mergeBuffer(previousBuffer: String?, additionalText: String): String {
        if (previousBuffer.isNullOrBlank()) {
            return additionalText
        }
        return truncatePrompt("$previousBuffer $additionalText")
    }

    private fun truncatePrompt(value: String): String {
        if (value.length <= maxPromptChars) {
            return value
        }
        return value.takeLast(maxPromptChars)
    }

    private fun normalize(text: String): String? {
        val normalized = text.trim()
        return normalized.takeIf { it.isNotEmpty() }
    }

    private fun fallbackFailureResult(error: Throwable): TracerBulletResult {
        val message = error.message?.takeIf { it.isNotBlank() }
            ?: "I could not complete that request."
        return TracerBulletResult(
            finalFeedback = message,
            decision = AgentDecision(
                actionType = ActionType.FINISH,
                targetIndex = -1,
                textToType = "",
                toolQuery = "",
                voiceFeedback = "",
                confidenceScore = 0.0,
            ),
            executionResult = null,
            didExecute = false,
        )
    }
}

