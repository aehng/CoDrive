package com.codrive.ai.modeldownload

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelDownloadWorkerTest {
    @Test
    fun className_isStable() {
        assertEquals(
            "com.codrive.ai.modeldownload.ModelDownloadWorker",
            ModelDownloadWorker::class.java.name
        )
    }
}

