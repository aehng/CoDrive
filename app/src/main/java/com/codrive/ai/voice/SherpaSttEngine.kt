package com.codrive.ai.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder.AudioSource
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.codrive.ai.contracts.SttEngine
import com.codrive.ai.modeldownload.ModelStorage
import com.codrive.ai.models.ModelManifest
import com.codrive.ai.settings.VoiceSettingsStore
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.HomophoneReplacerConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.QnnConfig
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class SherpaSttEngine(
    private val context: android.content.Context,
    private val storage: ModelStorage,
    private val onSpeechDetected: (() -> Unit)? = null
) : SttEngine {
    private val executor = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private var recognizer: OfflineRecognizer? = null
    private val voiceSettingsStore = VoiceSettingsStore.create(context)

    override fun startListening(onTranscript: (String) -> Unit) {
        startListening(null, onTranscript)
    }

    fun startListening(onProcessing: (() -> Unit)? = null, onTranscript: (String) -> Unit) {
        if (!running.compareAndSet(false, true)) {
            return
        }
        executor.submit {
            try {
                val engine = recognizer ?: createRecognizer().also { recognizer = it }
                val audio = captureAudio()
                
                // Immediately tell UI that we are done listening and are now thinking
                onProcessing?.invoke() 

                val stream = engine.createStream()
                stream.acceptWaveform(audio, SAMPLE_RATE)
                engine.decode(stream)
                val result = engine.getResult(stream)
                onTranscript(result.text)
            } catch (ex: Exception) {
                Log.w(TAG, "Sherpa STT failed", ex)
                onTranscript("")
            } finally {
                running.set(false)
            }
        }
    }

    override fun stopListening() {
        running.set(false)
    }

    fun shutdown() {
        running.set(false)
        recognizer?.release()
        recognizer = null
        executor.shutdownNow()
    }

    private fun createRecognizer(): OfflineRecognizer {
        val modelFile = storage.destinationFile(ModelManifest.STT_MODEL)
        val tokensFile = storage.destinationFile(ModelManifest.STT_TOKENS)

        if (!modelFile.exists() || modelFile.length() < ModelManifest.STT_MODEL.sizeBytes) {
            throw IllegalStateException("STT model file invalid: ${modelFile.absolutePath}")
        }
        if (!tokensFile.exists() || tokensFile.length() < ModelManifest.STT_TOKENS.sizeBytes) {
            throw IllegalStateException("STT tokens file invalid: ${tokensFile.absolutePath}")
        }

        val modelPath = modelFile.absolutePath
        val tokensPath = tokensFile.absolutePath

        val senseVoice = OfflineSenseVoiceModelConfig().apply {
            model = modelPath
            language = "auto"
            useInverseTextNormalization = true
            qnnConfig = QnnConfig()
        }

        val modelConfig = OfflineModelConfig().apply {
            this.senseVoice = senseVoice
            this.tokens = tokensPath
            this.numThreads = 2
            this.debug = false
            this.provider = "cpu"
        }

        val config = OfflineRecognizerConfig().apply {
            featConfig = FeatureConfig()
            this.modelConfig = modelConfig
            hr = HomophoneReplacerConfig()
            decodingMethod = "greedy_search"
            maxActivePaths = 4
            hotwordsFile = ""
            hotwordsScore = 1.5f
            ruleFsts = ""
            ruleFars = ""
            blankPenalty = 0.0f
        }

        return OfflineRecognizer(assetManager = null, config = config)
    }

    private fun captureAudio(): FloatArray {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = (minBuffer * 2).coerceAtLeast(SAMPLE_RATE)
        val audioRecord = AudioRecord(
            AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        val sessionId = audioRecord.audioSessionId
        val aec = if (AcousticEchoCanceler.isAvailable()) {
            runCatching { AcousticEchoCanceler.create(sessionId) }.getOrNull()?.apply { enabled = true }
        } else {
            null
        }
        val ns = if (NoiseSuppressor.isAvailable()) {
            runCatching { NoiseSuppressor.create(sessionId) }.getOrNull()?.apply { enabled = true }
        } else {
            null
        }

        val buffer = ShortArray(bufferSize / 2)
        val samples = ArrayList<Short>()
        var silenceMs = 0L
        var sawSpeech = false
        var voicedMs = 0L

        audioRecord.startRecording()
        while (running.get()) {
            val read = audioRecord.read(buffer, 0, buffer.size)
            if (read <= 0) {
                continue
            }
            var peak = 0
            for (i in 0 until read) {
                val sample = buffer[i].toInt()
                samples.add(buffer[i])
                val abs = kotlin.math.abs(sample)
                if (abs > peak) peak = abs
            }

            val frameMs = (read * 1000L) / SAMPLE_RATE
            if (peak > voiceSettingsStore.sttSpeechThreshold) {
                if (!sawSpeech) {
                    sawSpeech = true
                    onSpeechDetected?.invoke()
                }
                voicedMs += frameMs
                silenceMs = 0L
            } else if (sawSpeech) {
                silenceMs += frameMs
                if (silenceMs >= voiceSettingsStore.sttSilenceEndMs.toLong()) {
                    break
                }
            }

            if (samples.size >= MAX_SAMPLES) {
                break
            }
        }

        audioRecord.stop()
        audioRecord.release()
        runCatching { aec?.release() }
        runCatching { ns?.release() }

        // Reject short impulsive sounds (cough/click/tap noise) that often decode as garbage tokens.
        if (!sawSpeech || voicedMs < voiceSettingsStore.sttMinVoicedMs.toLong()) {
            return floatArrayOf()
        }

        val floats = FloatArray(samples.size)
        for (i in samples.indices) {
            floats[i] = samples[i] / 32768.0f
        }
        return floats
    }

    companion object {
        private const val TAG = "SherpaSttEngine"
        private const val SAMPLE_RATE = 16000
        private const val MAX_SECONDS = 12
        private const val MAX_SAMPLES = SAMPLE_RATE * MAX_SECONDS
    }
}
