package com.codrive.ai.overlay.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class ContinuousSpeechRecognizer(
    private val context: Context,
    private val callbacks: Callbacks,
    private val endpointer: SpeechCommandEndpointer = SpeechCommandEndpointer(),
    private val languageTag: String? = null,
) : OverlaySpeechRecognizer {
    interface Callbacks {
        fun onListeningStateChanged(message: String)
        fun onSpeechDetected()
        fun onCommandReady(command: String)
        fun onPartialTranscript(partial: String?)
        fun onCommandRejected(reason: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false

    override fun start() {
        if (running) {
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callbacks.onListeningStateChanged("Speech recognition unavailable on this device.")
            return
        }

        running = true
        if (recognizer == null) {
            val onDeviceRecognizer = runCatching {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            }.getOrNull()
            if (onDeviceRecognizer == null) {
                callbacks.onListeningStateChanged("On-device speech recognition unavailable.")
                running = false
                return
            }
            recognizer = onDeviceRecognizer.apply {
                setRecognitionListener(buildListener())
            }
        }

        callbacks.onListeningStateChanged("Listening for commands...")
        startListeningSoon(50L)
    }

    override fun stop() {
        running = false
        mainHandler.removeCallbacksAndMessages(null)
        recognizer?.apply {
            runCatching { stopListening() }
            runCatching { cancel() }
            destroy()
        }
        recognizer = null
    }

    private fun startListeningSoon(delayMs: Long) {
        if (!running) {
            return
        }
        mainHandler.postDelayed({ startListeningInternal() }, delayMs)
    }

    private fun startListeningInternal() {
        if (!running) {
            return
        }
        val r = recognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 600L)
            if (!languageTag.isNullOrBlank()) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            }
        }

        runCatching {
            r.startListening(intent)
        }.onFailure {
            callbacks.onListeningStateChanged("Could not start listening. Retrying...")
            startListeningSoon(500L)
        }
    }

    private fun buildListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                callbacks.onListeningStateChanged("Listening...")
            }

            override fun onBeginningOfSpeech() {
                callbacks.onSpeechDetected()
                callbacks.onListeningStateChanged("Speech detected...")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // No-op for now.
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // No-op for now.
            }

            override fun onEndOfSpeech() {
                callbacks.onListeningStateChanged("Processing speech...")
            }

            override fun onError(error: Int) {
                if (!running) {
                    return
                }
                if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
                    callbacks.onListeningStateChanged(
                        "Offline speech recognition unavailable. Install an offline language pack."
                    )
                    callbacks.onCommandRejected("Offline speech recognition unavailable.")
                    stop()
                    return
                }
                callbacks.onListeningStateChanged(errorMessage(error))
                startListeningSoon(restartDelay(error))
            }

            override fun onResults(results: Bundle?) {
                if (!running) {
                    return
                }
                val (text, confidence) = extractBestResult(results)
                val verdict = endpointer.evaluate(text, confidence)
                if (verdict.shouldSubmit) {
                    callbacks.onCommandReady(verdict.command)
                } else {
                    callbacks.onCommandRejected(verdict.reason)
                }
                callbacks.onListeningStateChanged("Listening for commands...")
                startListeningSoon(120L)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Surface partial transcripts so the UI can show what the recognizer hears.
                val (text, _) = extractBestResult(partialResults)
                callbacks.onPartialTranscript(text)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // No-op.
            }
        }
    }

    private fun extractBestResult(results: Bundle?): Pair<String?, Float?> {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val best = matches?.firstOrNull()
        val confidenceArray = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        val confidence = confidenceArray?.firstOrNull()?.takeIf { it >= 0f }
        return best to confidence
    }

    private fun errorMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech command recognized."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy. Restarting..."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Offline speech recognition unavailable."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission missing."
            else -> "Recognizer error. Restarting..."
        }
    }

    private fun restartDelay(error: Int): Long {
        return if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 350L else 120L
    }
}

