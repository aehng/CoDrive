package com.codrive.ai.modeldownload

import com.codrive.ai.models.ModelManifest

object ModelReadiness {
    fun hasSttModels(storage: ModelStorage): Boolean {
        return storage.isVerified(ModelManifest.STT_MODEL) &&
            storage.isVerified(ModelManifest.STT_TOKENS) &&
            storage.isVerified(ModelManifest.STT_VAD) &&
            storage.isValidFile(ModelManifest.STT_MODEL) &&
            storage.isValidFile(ModelManifest.STT_TOKENS) &&
            storage.isValidFile(ModelManifest.STT_VAD)
    }

    fun hasTtsModels(storage: ModelStorage): Boolean {
        val espeakArchiveReady = storage.isVerified(ModelManifest.TTS_ESPEAK_DATA) &&
            storage.isValidFile(ModelManifest.TTS_ESPEAK_DATA)
        val espeakExtractedReady = storage.extractedDir(ModelManifest.TTS_ESPEAK_DATA).exists()
        val espeakReady = espeakArchiveReady || espeakExtractedReady
        return storage.isVerified(ModelManifest.TTS_MODEL) &&
            storage.isVerified(ModelManifest.TTS_CONFIG) &&
            storage.isVerified(ModelManifest.TTS_TOKENS) &&
            storage.isValidFile(ModelManifest.TTS_MODEL) &&
            storage.isValidFile(ModelManifest.TTS_CONFIG) &&
            storage.isValidFile(ModelManifest.TTS_TOKENS) &&
            espeakReady
    }

    fun hasVlmModels(storage: ModelStorage): Boolean {
        return storage.isVerified(ModelManifest.VLM_LLM) &&
            storage.isVerified(ModelManifest.VLM_PROJ) &&
            storage.isValidFile(ModelManifest.VLM_LLM) &&
            storage.isValidFile(ModelManifest.VLM_PROJ)
    }

    fun hasAllModels(storage: ModelStorage): Boolean {
        return hasVlmModels(storage) && hasSttModels(storage) && hasTtsModels(storage)
    }
}
