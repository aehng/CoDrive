package com.codrive.ai.voice

import java.util.concurrent.atomic.AtomicInteger

/**
 * Shared bridge for linking STT and TTS audio session IDs so platform AEC can
 * associate microphone capture and playback with the same session.
 */
object VoiceAudioSessionLink {
    private val linkedSessionId = AtomicInteger(0)

    fun setSttAudioSessionId(sessionId: Int) {
        if (sessionId > 0) {
            linkedSessionId.set(sessionId)
        }
    }

    fun getLinkedAudioSessionIdOrNull(): Int? {
        val value = linkedSessionId.get()
        return if (value > 0) value else null
    }
}
