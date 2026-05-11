package com.codrive.ai.modeldownload

import com.codrive.ai.models.ModelAsset
import java.io.File

class ModelStorage(private val rootDir: File) {
    fun destinationFile(asset: ModelAsset): File {
        return File(rootDir, asset.fileName)
    }

    fun tempFile(asset: ModelAsset): File {
        val destination = destinationFile(asset)
        return File(destination.parentFile, destination.name + ".download")
    }

    fun verificationFile(asset: ModelAsset): File {
        val destination = destinationFile(asset)
        return File(destination.parentFile, destination.name + ".sha256")
    }

    fun extractedDir(asset: ModelAsset): File {
        val destination = destinationFile(asset)
        val name = destination.name
        val baseName = name.removeSuffix(".tar.bz2")
        return File(destination.parentFile, baseName)
    }

    fun ensureParentDirs(asset: ModelAsset) {
        destinationFile(asset).parentFile?.mkdirs()
    }

    fun isVerified(asset: ModelAsset): Boolean {
        val destination = destinationFile(asset)
        val verification = verificationFile(asset)
        if (!destination.exists() || !verification.exists()) {
            return false
        }
        val recorded = runCatching { verification.readText().trim() }.getOrNull()
        return recorded != null && recorded.equals(asset.sha256, ignoreCase = true)
    }

    fun isValidFile(asset: ModelAsset): Boolean {
        val destination = destinationFile(asset)
        if (!destination.exists()) {
            return false
        }
        // A matching verification marker means we already accepted this exact artifact hash.
        if (isVerified(asset)) {
            return true
        }
        val length = destination.length()
        if (length <= 0L) {
            return false
        }
        return asset.sizeBytes <= 0L || length >= asset.sizeBytes
    }

    fun hasSufficientSpace(asset: ModelAsset, reserveBytes: Long = MIN_FREE_BYTES): Boolean {
        val parent = destinationFile(asset).parentFile ?: return false
        val available = parent.usableSpace
        val remaining = remainingBytes(asset)
        return available >= remaining + reserveBytes
    }

    fun remainingBytes(asset: ModelAsset): Long {
        val expected = asset.sizeBytes
        if (expected <= 0L) {
            return 0L
        }
        val existing = tempFile(asset).takeIf { it.exists() }?.length() ?: 0L
        return (expected - existing).coerceAtLeast(0L)
    }

    fun markVerified(asset: ModelAsset, sha256: String) {
        val verification = verificationFile(asset)
        runCatching {
            verification.parentFile?.mkdirs()
            verification.writeText(sha256)
        }
    }

    companion object {
        private const val MIN_FREE_BYTES = 50L * 1024 * 1024
    }
}
