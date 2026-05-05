package com.codrive.ai.modeldownload

import java.io.File
import java.security.MessageDigest
import java.nio.file.Files
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDownloadManagerTest {
    @Test
    fun downloadModel_emitsSuccess_whenChecksumMatches() = runBlocking {
        val payload = "hello-model".toByteArray()
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", payload.size)
                .setBody(Buffer().write(payload))
        )
        server.start()

        try {
            val destination = createTempDestination("model.bin")
            val expected = sha256Of(payload)
            val manager = ModelDownloadManager(OkHttpClient())

            val states = manager
                .downloadModel(
                    server.url("/model.bin").toString(),
                    destination,
                    expected,
                    expectedSizeBytes = payload.size.toLong()
                )
                .toList()

            assertTrue(states.first() is DownloadState.Idle)
            assertTrue(states.any { it is DownloadState.Downloading })
            assertTrue(states.any { it is DownloadState.Verifying })
            assertTrue(states.last() is DownloadState.Success)
            assertTrue(destination.exists())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun downloadModel_emitsError_andDeletesFile_onChecksumMismatch() = runBlocking {
        val payload = "hello-model".toByteArray()
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", payload.size)
                .setBody(Buffer().write(payload))
        )
        server.start()

        try {
            val destination = createTempDestination("model.bin")
            val manager = ModelDownloadManager(OkHttpClient())

            val states = manager
                .downloadModel(
                    server.url("/model.bin").toString(),
                    destination,
                    "deadbeef",
                    expectedSizeBytes = payload.size.toLong()
                )
                .toList()

            assertTrue(states.last() is DownloadState.Error)
            assertFalse(destination.exists())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun downloadState_downloadingStoresProgress() {
        val state = DownloadState.Downloading(42)
        assertEquals(42, state.progress)
    }

    private fun sha256Of(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(data)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun createTempDestination(fileName: String): File {
        val dir = Files.createTempDirectory("model-download-test").toFile()
        return File(dir, fileName)
    }
}

