package com.codrive.ai.models

data class ModelAsset(
    val fileName: String,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val isZipped: Boolean = false
)

object ModelManifest {
    // --- 1. THE EYES & BRAIN: InternVL3-2B (Visual Grounding) ---
    // Total VLM footprint: ~1.5 GB
    val VLM_LLM = ModelAsset(
        fileName = "vlm/internvl3_2b_q4_k_m.gguf",
        downloadUrl = "https://huggingface.co/Zoont/InternVL3-2B-4-Bit-GGUF-with-mmproj/resolve/main/InternVL3-2B-Q4_K_M.gguf",
        sha256 = "dc5f4de0de7f5ec23a74159141830bef8a5fcc8fac4b4b1cb71e79eeeb47359e",
        sizeBytes = 1_120_000_000L
    )

    val VLM_PROJ = ModelAsset(
        fileName = "vlm/mmproj_q8_0.gguf",
        downloadUrl = "https://huggingface.co/Zoont/InternVL3-2B-4-Bit-GGUF-with-mmproj/resolve/main/mmproj-InternVL3-2B-Q8_0.gguf",
        sha256 = "7bd80d420e6b43274c683ee346b605392420ddda0afdc437e74005a4f5cad0e8",
        sizeBytes = 350_000_000L
    )

    // --- 2. THE EARS: SenseVoice Small (Noise-Resilient STT) ---
    // Optimized for car cabin noise
    val STT_MODEL = ModelAsset(
        fileName = "stt/sensevoice_int8.onnx",
        downloadUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main/model.int8.onnx",
        sha256 = "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51",
        sizeBytes = 239_000_000L
    )

    val STT_TOKENS = ModelAsset(
        fileName = "stt/tokens.txt",
        downloadUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main/tokens.txt",
        sha256 = "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc",
        sizeBytes = 200_000L
    )

    // --- 3. THE VOICE: Piper Ryan-Low (Fast TTS) ---
    // Ultra-low latency for real-time interaction
    val TTS_MODEL = ModelAsset(
        fileName = "tts/ryan_low.onnx",
        downloadUrl = "https://huggingface.co/csukuangfj/vits-piper-en_US-ryan-low/resolve/main/en_US-ryan-low.onnx",
        sha256 = "2ad52f13bf0cbfa3bf0cc9afbab91e71485a6a3f6aee507cbc6c2c753b30d0d1",
        sizeBytes = 63_100_000L
    )

    val TTS_CONFIG = ModelAsset(
        fileName = "tts/ryan_low.json",
        downloadUrl = "https://huggingface.co/csukuangfj/vits-piper-en_US-ryan-low/resolve/main/en_US-ryan-low.onnx.json",
        sha256 = "b27147e56b0525962609f82f58171f4618cbf17c6fb043d7d724ff28cc4aed60",
        sizeBytes = 4_000L
    )

    val TTS_TOKENS = ModelAsset(
        fileName = "tts/tokens.txt",
        downloadUrl = "https://huggingface.co/csukuangfj/vits-piper-en_US-ryan-low/resolve/main/tokens.txt",
        sha256 = "42D1A69ED2B91A51928A711AA228ED9F3DC021C6D359A3E9C4F37EB1D20F80BD",
        sizeBytes = 1_000L
    )

    // Phoneme data required for the Sherpa-ONNX TTS engine
    val TTS_ESPEAK_DATA = ModelAsset(
        fileName = "tts/espeak-ng-data.tar.bz2",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/espeak-ng-data.tar.bz2",
        sha256 = "4135ccf82e1f40613491c0874d4945ae9e9c7840933d8e25a6f9e003d9ebf533",
        sizeBytes = 3_000_000L,
        isZipped = true
    )

    val voiceRequiredModels = listOf(
        STT_MODEL, STT_TOKENS,
        TTS_MODEL, TTS_CONFIG, TTS_TOKENS, TTS_ESPEAK_DATA
    )

    val vlmRequiredModels = listOf(
        VLM_LLM, VLM_PROJ
    )

    val allRequiredModels = listOf(
        VLM_LLM, VLM_PROJ,
        STT_MODEL, STT_TOKENS,
        TTS_MODEL, TTS_CONFIG, TTS_TOKENS, TTS_ESPEAK_DATA
    )
}
