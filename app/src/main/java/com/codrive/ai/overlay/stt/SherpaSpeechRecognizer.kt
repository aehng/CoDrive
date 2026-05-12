package com.codrive.ai.overlay.stt

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.codrive.ai.modeldownload.ModelStorage
import com.codrive.ai.voice.SherpaSttEngine

class SherpaSpeechRecognizer(
    context: Context,
    private val callbacks: ContinuousSpeechRecognizer.Callbacks,
    private val endpointer: SpeechCommandEndpointer = SpeechCommandEndpointer(),
    private val storage: ModelStorage
) : OverlaySpeechRecognizer {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val engine = SherpaSttEngine(context, storage) {
        callbacks.onSpeechDetected()
        callbacks.onListeningStateChanged("Speech detected...")
    }
    private var running = false

    override fun start() {
        if (running) {
            return
        }
        running = true
        callbacks.onListeningStateChanged("Listening for commands...")
        startListeningCycle()
    }

    private fun startListeningCycle() {
        if (!running) {
            return
        }
        engine.startListening(
            onProcessing = { callbacks.onListeningStateChanged("Processing speech...") },
            onTranscript = { transcript ->
                if (!running) {
                    return@startListening
                }
                callbacks.onPartialTranscript(transcript)
                val verdict = endpointer.evaluate(transcript, null)
                if (verdict.shouldSubmit) {
                    callbacks.onCommandReady(verdict.command)
                } else {
                    callbacks.onCommandRejected(verdict.reason)
                }
                callbacks.onListeningStateChanged("Listening for commands...")
                if (running) {
                    mainHandler.postDelayed({ startListeningCycle() }, 120L)
                }
            }
        )
    }

    override fun stop() {
        running = false
        mainHandler.removeCallbacksAndMessages(null)
        engine.stopListening()
    }
}
