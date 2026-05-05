package com.codrive.ai.modeldownload

import com.codrive.ai.models.ModelManifest
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelReadinessTest {
    @Test
    fun readinessFlagsTrackVerifiedModels() {
        val root = Files.createTempDirectory("readiness").toFile()
        val storage = ModelStorage(root)

        assertFalse(ModelReadiness.hasSttModels(storage))
        assertFalse(ModelReadiness.hasTtsModels(storage))
        assertFalse(ModelReadiness.hasVlmModels(storage))

        val sttModel = storage.destinationFile(ModelManifest.STT_MODEL)
        sttModel.parentFile?.mkdirs()
        sttModel.writeBytes(ByteArray(ModelManifest.STT_MODEL.sizeBytes.toInt()))
        storage.markVerified(ModelManifest.STT_MODEL, ModelManifest.STT_MODEL.sha256)

        val sttTokens = storage.destinationFile(ModelManifest.STT_TOKENS)
        sttTokens.parentFile?.mkdirs()
        sttTokens.writeBytes(ByteArray(ModelManifest.STT_TOKENS.sizeBytes.toInt()))
        storage.markVerified(ModelManifest.STT_TOKENS, ModelManifest.STT_TOKENS.sha256)

        assertTrue(ModelReadiness.hasSttModels(storage))
    }

    @Test
    fun readinessFailsWhenFilesAreEmpty() {
        val root = Files.createTempDirectory("readiness-empty").toFile()
        val storage = ModelStorage(root)

        val sttModel = storage.destinationFile(ModelManifest.STT_MODEL)
        sttModel.parentFile?.mkdirs()
        sttModel.writeText("")
        storage.markVerified(ModelManifest.STT_MODEL, ModelManifest.STT_MODEL.sha256)

        val sttTokens = storage.destinationFile(ModelManifest.STT_TOKENS)
        sttTokens.parentFile?.mkdirs()
        sttTokens.writeText("")
        storage.markVerified(ModelManifest.STT_TOKENS, ModelManifest.STT_TOKENS.sha256)

        assertFalse(ModelReadiness.hasSttModels(storage))
    }
}
