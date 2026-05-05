package com.codrive.ai.modeldownload

import com.codrive.ai.models.ModelAsset
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelStorageTest {
    @Test
    fun markVerified_and_isVerified_workTogether() {
        val root = Files.createTempDirectory("model-storage-test").toFile()
        val storage = ModelStorage(root)
        val asset = ModelAsset(
            fileName = "stt/model.onnx",
            downloadUrl = "https://example.com/model.onnx",
            sha256 = "abc123",
            sizeBytes = 100L
        )

        val destination = storage.destinationFile(asset)
        destination.parentFile?.mkdirs()
        destination.writeText("data")

        assertFalse(storage.isVerified(asset))
        storage.markVerified(asset, "abc123")
        assertTrue(storage.isVerified(asset))
    }

    @Test
    fun isVerified_fails_when_file_missing() {
        val root = Files.createTempDirectory("model-storage-test-missing").toFile()
        val storage = ModelStorage(root)
        val asset = ModelAsset(
            fileName = "tts/model.onnx",
            downloadUrl = "https://example.com/model.onnx",
            sha256 = "abc123",
            sizeBytes = 100L
        )

        storage.markVerified(asset, "abc123")
        assertFalse(storage.isVerified(asset))
    }

    @Test
    fun remainingBytes_accountsForPartialTempFile() {
        val root = Files.createTempDirectory("model-storage-temp").toFile()
        val storage = ModelStorage(root)
        val asset = ModelAsset(
            fileName = "vlm/model.bin",
            downloadUrl = "https://example.com/model.bin",
            sha256 = "abc123",
            sizeBytes = 100L
        )

        val temp = storage.tempFile(asset)
        temp.parentFile?.mkdirs()
        temp.writeBytes(ByteArray(40))

        assertTrue(storage.remainingBytes(asset) in 60L..100L)
    }

    @Test
    fun isValidFile_rejectsTruncatedFiles() {
        val root = Files.createTempDirectory("model-storage-size").toFile()
        val storage = ModelStorage(root)
        val asset = ModelAsset(
            fileName = "stt/model.onnx",
            downloadUrl = "https://example.com/model.onnx",
            sha256 = "abc123",
            sizeBytes = 100L
        )

        val destination = storage.destinationFile(asset)
        destination.parentFile?.mkdirs()
        destination.writeBytes(ByteArray(10))

        assertFalse(storage.isValidFile(asset))
    }
}
