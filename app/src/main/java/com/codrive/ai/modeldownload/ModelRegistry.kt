package com.codrive.ai.modeldownload

data class ModelDescriptor(
    val id: String,
    val displayName: String,
    val url: String,
    val sha256: String,
    val fileName: String,
    val sizeBytes: Long
)

object ModelRegistry {
    // TODO: Replace these placeholders with real model URLs and SHA-256 checksums.
    val models: List<ModelDescriptor> = listOf(
        ModelDescriptor(
            id = "sherpa_zipformer_en_v1",
            displayName = "Sherpa Zipformer EN v1 (placeholder)",
            url = "https://example.com/models/sherpa/zipformer-en-v1.onnx",
            sha256 = "TODO",
            fileName = "zipformer-en-v1.onnx",
            sizeBytes = 1_073_741_824L
        ),
        ModelDescriptor(
            id = "sherpa_vits_en_v1",
            displayName = "Sherpa VITS EN v1 (placeholder)",
            url = "https://example.com/models/sherpa/vits-en-v1.onnx",
            sha256 = "TODO",
            fileName = "vits-en-v1.onnx",
            sizeBytes = 1_073_741_824L
        )
    )
}

