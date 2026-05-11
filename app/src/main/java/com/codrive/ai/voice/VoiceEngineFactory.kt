package com.codrive.ai.voice

import android.content.Context
import android.util.Log
import com.codrive.ai.modeldownload.ModelReadiness
import com.codrive.ai.modeldownload.ModelStorage
import com.codrive.ai.overlay.stt.ContinuousSpeechRecognizer
import com.codrive.ai.overlay.stt.OverlaySpeechRecognizer
import com.codrive.ai.overlay.stt.SherpaSpeechRecognizer
import com.codrive.ai.overlay.stt.SpeechCommandEndpointer
import com.codrive.ai.settings.VoiceSettingsStore
import java.io.File

object VoiceEngineFactory {
    @JvmStatic
    fun shouldUseSherpaTts(context: Context, settings: VoiceSettingsStore): Boolean {
        val storage = ModelStorage(File(context.noBackupFilesDir, "models"))
        val enabled = settings.isSherpaEnabled()
        val ready = ModelReadiness.hasTtsModels(storage)
        val useSherpa = enabled && ready
        Log.d(TAG, "TTS engine decision: sherpaEnabled=$enabled ttsModelsReady=$ready useSherpa=$useSherpa")
        return useSherpa
    }

    @JvmStatic
    fun shouldUseSherpaStt(context: Context, settings: VoiceSettingsStore): Boolean {
        val storage = ModelStorage(File(context.noBackupFilesDir, "models"))
        val enabled = settings.isSherpaEnabled()
        val ready = ModelReadiness.hasSttModels(storage)
        val useSherpa = enabled && ready
        Log.d(TAG, "STT engine decision: sherpaEnabled=$enabled sttModelsReady=$ready useSherpa=$useSherpa")
        return useSherpa
    }

    @JvmStatic
    fun createTtsEngine(
        context: Context,
        settings: VoiceSettingsStore
    ): com.codrive.ai.contracts.TtsEngine {
        val storage = ModelStorage(File(context.noBackupFilesDir, "models"))
        if (!shouldUseSherpaTts(context, settings)) {
            Log.i(TAG, "Using Android TextToSpeech engine.")
            return AndroidTextToSpeechEngine(context, settings)
        }

        return runCatching {
            Log.i(TAG, "Using Sherpa TTS engine.")
            SherpaTtsEngine(context, storage)
        }.getOrElse { error ->
            Log.w(TAG, "Failed to create Sherpa TTS. Falling back to Android TTS.", error)
            AndroidTextToSpeechEngine(context, settings)
        }
    }

    @JvmStatic
    fun createOverlaySpeechRecognizer(
        context: Context,
        callbacks: ContinuousSpeechRecognizer.Callbacks,
        endpointer: SpeechCommandEndpointer,
        localeTag: String,
        settings: VoiceSettingsStore
    ): OverlaySpeechRecognizer {
        val storage = ModelStorage(File(context.noBackupFilesDir, "models"))
        if (!shouldUseSherpaStt(context, settings)) {
            Log.i(TAG, "Using Android SpeechRecognizer engine.")
            return ContinuousSpeechRecognizer(context, callbacks, endpointer, localeTag)
        }

        return runCatching {
            Log.i(TAG, "Using Sherpa STT engine.")
            SherpaSpeechRecognizer(context, callbacks, endpointer, storage)
        }.getOrElse { error ->
            Log.w(TAG, "Failed to create Sherpa STT. Falling back to Android SpeechRecognizer.", error)
            ContinuousSpeechRecognizer(context, callbacks, endpointer, localeTag)
        }
    }

    private const val TAG = "VoiceEngineFactory"
}

