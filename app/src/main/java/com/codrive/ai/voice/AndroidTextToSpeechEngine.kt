package com.codrive.ai.voice

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.codrive.ai.contracts.TtsEngine
import com.codrive.ai.settings.VoiceSettingsStore
import java.util.Locale

class AndroidTextToSpeechEngine(
    context: Context,
    private val settingsStore: VoiceSettingsStore,
) : TtsEngine {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingMessage: String? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = configureVoice()
                pendingMessage?.let { message ->
                    pendingMessage = null
                    speak(message)
                }
            } else {
                Log.w(TAG, "TextToSpeech init failed with status=$status")
            }
        }
    }

    override fun speak(message: String) {
        val text = message.trim()
        if (text.isEmpty()) {
            return
        }
        val engine = tts ?: return
        if (!ready) {
            pendingMessage = text
            return
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    override fun stop() {
        pendingMessage = null
        tts?.stop()
    }

    fun shutdown() {
        pendingMessage = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun configureVoice(): Boolean {
        val engine = tts ?: return false
        val localeTag = settingsStore.getTtsLocaleTag()
        val locale = Locale.forLanguageTag(localeTag)
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "TTS language not supported: $localeTag")
            return false
        }

        val embeddedVoice = engine.voices
            ?.firstOrNull { voice ->
                voice.locale == locale && voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS) == true
            }
        if (embeddedVoice != null) {
            engine.voice = embeddedVoice
        }

        val activeVoice = engine.voice
        val hasEmbedded = activeVoice?.features?.contains(TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS) == true
        if (!hasEmbedded) {
            Log.w(TAG, "No embedded/offline TTS voice available for $localeTag; using default system TTS voice.")
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        engine.setAudioAttributes(audioAttributes)

        engine.setPitch(settingsStore.getTtsPitch())
        engine.setSpeechRate(settingsStore.getTtsRate())
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // No-op.
            }

            override fun onDone(utteranceId: String?) {
                // No-op.
            }

            override fun onError(utteranceId: String?) {
                Log.w(TAG, "TTS utterance error for id=$utteranceId")
            }
        })

        return true
    }

    companion object {
        private const val TAG = "AndroidTextToSpeech"
        private const val UTTERANCE_ID = "codrive_tts"
    }
}

