package com.codrive.ai.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.codrive.ai.contracts.TtsEngine
import com.codrive.ai.modeldownload.ModelArchiveExtractor
import com.codrive.ai.modeldownload.ModelStorage
import com.codrive.ai.models.ModelManifest
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.concurrent.Executors

class SherpaTtsEngine(
    private val context: Context,
    private val storage: ModelStorage
) : TtsEngine {
    private val executor = Executors.newSingleThreadExecutor()
    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null

    override fun speak(message: String) {
        val text = message.trim()
        if (text.isEmpty()) {
            return
        }
        executor.submit {
            try {
                val engine = tts ?: createTts().also { tts = it }
                val audio = engine.generate(text, 0, 1.0f)
                playAudio(audio)
            } catch (ex: Exception) {
                Log.w(TAG, "Sherpa TTS failed", ex)
            }
        }
    }

    override fun stop() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    fun shutdown() {
        stop()
        tts?.release()
        tts = null
        executor.shutdownNow()
    }

    private fun createTts(): OfflineTts {
        val modelFile = storage.destinationFile(ModelManifest.TTS_MODEL)
        val configFile = storage.destinationFile(ModelManifest.TTS_CONFIG)
        val tokensFile = storage.destinationFile(ModelManifest.TTS_TOKENS)
        val espeakArchive = storage.destinationFile(ModelManifest.TTS_ESPEAK_DATA)
        val espeakDir = storage.extractedDir(ModelManifest.TTS_ESPEAK_DATA)

        if (!modelFile.exists() || modelFile.length() < ModelManifest.TTS_MODEL.sizeBytes) {
            throw IllegalStateException("TTS model file invalid: ${modelFile.absolutePath}")
        }
        if (!configFile.exists() || configFile.length() < ModelManifest.TTS_CONFIG.sizeBytes) {
            throw IllegalStateException("TTS config file invalid: ${configFile.absolutePath}")
        }
        if (!tokensFile.exists() || tokensFile.length() < ModelManifest.TTS_TOKENS.sizeBytes) {
            throw IllegalStateException("TTS tokens file invalid: ${tokensFile.absolutePath}")
        }
        if (!espeakDir.exists() && !espeakArchive.exists()) {
            throw IllegalStateException("TTS espeak data missing: ${espeakArchive.absolutePath}")
        }

        if (!espeakDir.exists() && espeakArchive.exists()) {
            ModelArchiveExtractor.extractTarBz2(espeakArchive, espeakDir)
        }

        // FIX A: Handle the nested folder created by the tarball
        val espeakActualDir = File(espeakDir, "espeak-ng-data").takeIf { it.exists() } ?: espeakDir

        val vitsConfig = OfflineTtsVitsModelConfig().apply {
            model = modelFile.absolutePath
            lexicon = ""
            tokens = tokensFile.absolutePath
            dataDir = espeakActualDir.absolutePath
            dictDir = ""
            noiseScale = 0.667f
            noiseScaleW = 0.8f
            lengthScale = 1.0f
        }

        val modelConfig = OfflineTtsModelConfig().apply {
            vits = vitsConfig
            numThreads = 2
            debug = false
            provider = "cpu"
        }

        val ttsConfig = OfflineTtsConfig().apply {
            model = modelConfig
            ruleFsts = ""
            ruleFars = ""
            maxNumSentences = 1
            silenceScale = 0.2f
        }

        return OfflineTts(null, ttsConfig)
    }

    private fun playAudio(audio: GeneratedAudio) {
        val floatSamples = audio.samples
        val shorts = ShortArray(floatSamples.size)
        for (i in floatSamples.indices) {
            val clamped = floatSamples[i].coerceIn(-1.0f, 1.0f)
            shorts[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
        }

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(audio.sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val track = AudioTrack(
            attributes,
            format,
            shorts.size * 2,
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track.write(shorts, 0, shorts.size)
        track.play()
        audioTrack?.release()
        audioTrack = track
    }

    companion object {
        private const val TAG = "SherpaTtsEngine"
    }
}
