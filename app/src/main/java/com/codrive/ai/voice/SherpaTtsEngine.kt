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
import kotlin.math.ceil

class SherpaTtsEngine(
    private val context: Context,
    private val storage: ModelStorage,
    private val speakObserver: SpeakObserver? = null
) : TtsEngine {
    private val executor = Executors.newSingleThreadExecutor()
    private var tts: OfflineTts? = null
    private val audioTrackLock = Any()
    private var audioTrack: AudioTrack? = null
    @Volatile
    private var stopRequested = false

    override fun speak(message: String) {
        val text = message.trim()
        if (text.isEmpty()) {
            Log.d(TAG, "speak ignored: empty text")
            return
        }
        Log.d(TAG, "speak queued: chars=${text.length}")
        executor.submit {
            try {
                stopRequested = false
                Log.d(TAG, "speak started")
                val engine = tts ?: createTts().also { tts = it }
                Log.d(TAG, "generating audio")
                val audio = engine.generate(text, 0, 1.0f)
                Log.d(TAG, "generated audio: sampleRate=${audio.sampleRate} samples=${audio.samples.size}")
                playAudio(audio)
                waitForPlaybackToFinish(audio)
                Log.d(TAG, "speak completed")
                speakObserver?.onSpeakFinished(true, null)
            } catch (ex: Exception) {
                Log.e(TAG, "Sherpa TTS failed during speak", ex)
                speakObserver?.onSpeakFinished(false, ex.message)
            }
        }
    }

    override fun stop() {
        stopRequested = true
        synchronized(audioTrackLock) {
            val track = audioTrack ?: return
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.stop() }
            runCatching { track.release() }
            audioTrack = null
        }
    }

    fun shutdown() {
        Log.d(TAG, "shutdown requested")
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

        if (!storage.isValidFile(ModelManifest.TTS_MODEL)) {
            throw IllegalStateException("TTS model file invalid: ${modelFile.absolutePath}")
        }
        if (!storage.isValidFile(ModelManifest.TTS_CONFIG)) {
            throw IllegalStateException("TTS config file invalid: ${configFile.absolutePath}")
        }
        if (!storage.isValidFile(ModelManifest.TTS_TOKENS)) {
            throw IllegalStateException("TTS tokens file invalid: ${tokensFile.absolutePath}")
        }
        if (!espeakDir.exists() && !espeakArchive.exists()) {
            throw IllegalStateException("TTS espeak data missing: ${espeakArchive.absolutePath}")
        }

        if (!espeakDir.exists() && espeakArchive.exists()) {
            Log.d(TAG, "Extracting eSpeak archive: ${espeakArchive.absolutePath} -> ${espeakDir.absolutePath}")
            ModelArchiveExtractor.extractTarBz2(espeakArchive, espeakDir)
            Log.d(TAG, "eSpeak archive extracted")
        }

        val espeakActualDir = File(espeakDir, "espeak-ng-data").takeIf { it.exists() } ?: espeakDir
        Log.d(
            TAG,
            "createTts files model=${modelFile.absolutePath}(${modelFile.length()}) " +
                "config=${configFile.absolutePath}(${configFile.length()}) " +
                "tokens=${tokensFile.absolutePath}(${tokensFile.length()}) " +
                "espeak=${espeakActualDir.absolutePath} exists=${espeakActualDir.exists()}"
        )

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
            debug = true
            provider = "cpu"
        }

        val ttsConfig = OfflineTtsConfig().apply {
            model = modelConfig
            ruleFsts = ""
            ruleFars = ""
            maxNumSentences = 1
            silenceScale = 0.2f
        }

        Log.d(TAG, "Creating OfflineTts instance")
        return OfflineTts(null, ttsConfig).also {
            Log.d(TAG, "OfflineTts instance created")
        }
    }

    private fun playAudio(audio: GeneratedAudio) {
        val floatSamples = audio.samples
        if (floatSamples.isEmpty()) {
            Log.w(TAG, "playAudio skipped: generated 0 samples")
            return
        }
        val shorts = ShortArray(floatSamples.size)
        for (i in floatSamples.indices) {
            val clamped = floatSamples[i].coerceIn(-1.0f, 1.0f)
            shorts[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
        }

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(audio.sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val sessionId = VoiceAudioSessionLink.getLinkedAudioSessionIdOrNull()
            ?: AudioManager.AUDIO_SESSION_ID_GENERATE
        val track = AudioTrack(
            attributes,
            format,
            shorts.size * 2,
            AudioTrack.MODE_STATIC,
            sessionId
        )
        val writeResult = track.write(shorts, 0, shorts.size)
        Log.d(
            TAG,
            "AudioTrack writeResult=$writeResult state=${track.state} playState=${track.playState} bufferBytes=${shorts.size * 2}"
        )
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            throw IllegalStateException("AudioTrack not initialized; state=${track.state}")
        }
        track.play()
        synchronized(audioTrackLock) {
            audioTrack?.release()
            audioTrack = track
        }
    }

    private fun waitForPlaybackToFinish(audio: GeneratedAudio) {
        if (audio.sampleRate <= 0 || audio.samples.isEmpty()) {
            return
        }
        val durationMs = ceil((audio.samples.size.toDouble() / audio.sampleRate.toDouble()) * 1000.0).toLong()
        val deadlineMs = System.currentTimeMillis() + (durationMs + 120L).coerceAtMost(15_000L)
        Log.d(TAG, "waiting for playback completion until=${deadlineMs}")
        while (!stopRequested && System.currentTimeMillis() < deadlineMs) {
            val track = synchronized(audioTrackLock) { audioTrack } ?: return
            val isPlaying = runCatching { track.playState == AudioTrack.PLAYSTATE_PLAYING }.getOrElse { false }
            if (!isPlaying) {
                return
            }
            try {
                Thread.sleep(30L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }

    companion object {
        private const val TAG = "SherpaTtsEngine"
    }
}

fun interface SpeakObserver {
    fun onSpeakFinished(success: Boolean, errorMessage: String?)
}
