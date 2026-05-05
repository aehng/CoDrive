package com.codrive.ai.modeldownload

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    object Verifying : DownloadState()
    data class Success(val file: File) : DownloadState()
    data class Error(val exception: Exception) : DownloadState()
}

class ModelDownloadManager(
    private val okHttpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    @Volatile
    private var activeCall: Call? = null

    fun downloadModel(
        url: String,
        destinationFile: File,
        expectedSha256: String,
        verificationFile: File? = null,
        expectedSizeBytes: Long? = null
    ): Flow<DownloadState> = flow {
        emit(DownloadState.Idle)

        try {
            destinationFile.parentFile?.mkdirs()
            if (destinationFile.exists()) {
                val verified = verificationFile?.let { isVerificationMatch(it, expectedSha256) } ?: false
                if (verified || computeSha256(destinationFile).equals(expectedSha256, ignoreCase = true)) {
                    verificationFile?.let { writeVerification(it, expectedSha256) }
                    emit(DownloadState.Success(destinationFile))
                    return@flow
                }
                destinationFile.delete()
            }

            val tempFile = File(destinationFile.parentFile, destinationFile.name + ".download")
            var existingBytes = if (tempFile.exists()) tempFile.length() else 0L
            if (expectedSizeBytes != null && expectedSizeBytes > 0 && existingBytes > expectedSizeBytes) {
                tempFile.delete()
                existingBytes = 0L
            }

            if (expectedSizeBytes != null && expectedSizeBytes > 0) {
                val remaining = (expectedSizeBytes - existingBytes).coerceAtLeast(0L)
                val available = destinationFile.parentFile?.usableSpace ?: 0L
                val required = remaining + MIN_FREE_BYTES
                if (available < required) {
                    throw InsufficientStorageException(
                        "Insufficient disk space. Need $remaining bytes + reserve, available $available"
                    )
                }
            }
            val requestBuilder = Request.Builder().url(url)
            if (existingBytes > 0L) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
            }
            val request = requestBuilder.build()

            val call = okHttpClient.newCall(request)
            activeCall = call
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} ${response.message}")
                }

                val body = response.body ?: throw IOException("Empty response body")
                val contentLength = body.contentLength()
                if (response.code == 200) {
                    existingBytes = 0L
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                }
                val totalLength = if (contentLength > 0L) contentLength + existingBytes else contentLength

                val append = existingBytes > 0L && response.code == 206
                FileOutputStream(tempFile, append).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var totalRead = existingBytes
                        var lastProgress = -1

                        if (totalLength <= 0L) {
                            emit(DownloadState.Downloading(0))
                        }

                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) {
                                break
                            }
                            output.write(buffer, 0, read)
                            totalRead += read

                            if (totalLength > 0L) {
                                val progress = ((totalRead * 100) / totalLength)
                                    .toInt()
                                    .coerceIn(0, 100)
                                if (progress != lastProgress) {
                                    lastProgress = progress
                                    emit(DownloadState.Downloading(progress))
                                }
                            }
                        }
                        output.flush()
                    }
                }
            }
            activeCall = null

            emit(DownloadState.Verifying)

            val actualSha256 = computeSha256(tempFile)
            val normalizedExpected = expectedSha256.trim().lowercase()

            if (actualSha256.equals(normalizedExpected, ignoreCase = true)) {
                if (destinationFile.exists()) {
                    destinationFile.delete()
                }
                if (!tempFile.renameTo(destinationFile)) {
                    tempFile.copyTo(destinationFile, overwrite = true)
                    tempFile.delete()
                }
                verificationFile?.let { writeVerification(it, actualSha256) }
                emit(DownloadState.Success(destinationFile))
            } else {
                tempFile.delete()
                emit(
                    DownloadState.Error(
                        Exception("SHA-256 mismatch. Expected $normalizedExpected, got $actualSha256")
                    )
                )
            }
        } catch (e: Exception) {
            activeCall = null
            val tempFile = File(destinationFile.parentFile, destinationFile.name + ".download")
            tempFile.delete()
            emit(DownloadState.Error(e))
        }
    }.flowOn(ioDispatcher)

    fun cancel() {
        activeCall?.cancel()
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun isVerificationMatch(verificationFile: File, expectedSha256: String): Boolean {
        if (!verificationFile.exists()) {
            return false
        }
        val recorded = runCatching { verificationFile.readText().trim() }.getOrNull()
        return recorded != null && recorded.equals(expectedSha256, ignoreCase = true)
    }

    private fun writeVerification(verificationFile: File, sha256: String) {
        runCatching {
            verificationFile.parentFile?.mkdirs()
            verificationFile.writeText(sha256)
        }
    }

    companion object {
        private const val MIN_FREE_BYTES = 50L * 1024 * 1024
    }
}

