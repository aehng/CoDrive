package com.codrive.ai.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelManifestTest {
    @Test
    fun allRequiredModels_containsExpectedEntries() {
        assertEquals(8, ModelManifest.allRequiredModels.size)
        assertTrue(ModelManifest.allRequiredModels.contains(ModelManifest.VLM_LLM))
        assertTrue(ModelManifest.allRequiredModels.contains(ModelManifest.VLM_PROJ))
        assertTrue(ModelManifest.allRequiredModels.contains(ModelManifest.STT_MODEL))
        assertTrue(ModelManifest.allRequiredModels.contains(ModelManifest.STT_TOKENS))
        assertTrue(ModelManifest.allRequiredModels.contains(ModelManifest.TTS_MODEL))
        assertTrue(ModelManifest.allRequiredModels.contains(ModelManifest.TTS_CONFIG))
        assertTrue(ModelManifest.allRequiredModels.contains(ModelManifest.TTS_TOKENS))
        assertTrue(ModelManifest.allRequiredModels.contains(ModelManifest.TTS_ESPEAK_DATA))
    }

    @Test
    fun modelAsset_storesValues() {
        val asset = ModelAsset(
            fileName = "file.bin",
            downloadUrl = "https://example.com/file.bin",
            sha256 = "abc123",
            sizeBytes = 42L,
            isZipped = true
        )

        assertEquals("file.bin", asset.fileName)
        assertEquals("https://example.com/file.bin", asset.downloadUrl)
        assertEquals("abc123", asset.sha256)
        assertEquals(42L, asset.sizeBytes)
        assertTrue(asset.isZipped)
    }

    @Test
    fun voiceRequiredModels_containsSherpaAssets() {
        assertEquals(6, ModelManifest.voiceRequiredModels.size)
        assertTrue(ModelManifest.voiceRequiredModels.contains(ModelManifest.STT_MODEL))
        assertTrue(ModelManifest.voiceRequiredModels.contains(ModelManifest.STT_TOKENS))
        assertTrue(ModelManifest.voiceRequiredModels.contains(ModelManifest.TTS_MODEL))
        assertTrue(ModelManifest.voiceRequiredModels.contains(ModelManifest.TTS_CONFIG))
        assertTrue(ModelManifest.voiceRequiredModels.contains(ModelManifest.TTS_TOKENS))
        assertTrue(ModelManifest.voiceRequiredModels.contains(ModelManifest.TTS_ESPEAK_DATA))
    }
}
