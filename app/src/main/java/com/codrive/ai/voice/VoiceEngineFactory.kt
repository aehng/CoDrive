package com.codrive.ai.voice

import android.content.Context
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
        return settings.isSherpaEnabled() && ModelReadiness.hasTtsModels(storage)
    }

    @JvmStatic
    fun shouldUseSherpaStt(context: Context, settings: VoiceSettingsStore): Boolean {
        val storage = ModelStorage(File(context.noBackupFilesDir, "models"))
        return settings.isSherpaEnabled() && ModelReadiness.hasSttModels(storage)
    }

    @JvmStatic
    fun createTtsEngine(
        context: Context,
        settings: VoiceSettingsStore
    ): com.codrive.ai.contracts.TtsEngine {
        val storage = ModelStorage(File(context.noBackupFilesDir, "models"))
        return if (shouldUseSherpaTts(context, settings)) {
            SherpaTtsEngine(context, storage)
        } else {
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
        return if (shouldUseSherpaStt(context, settings)) {
            SherpaSpeechRecognizer(context, callbacks, endpointer, storage)
        } else {
            ContinuousSpeechRecognizer(context, callbacks, endpointer, localeTag)
        }
    }
}

