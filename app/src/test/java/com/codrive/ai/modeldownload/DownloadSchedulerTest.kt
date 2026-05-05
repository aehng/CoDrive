package com.codrive.ai.modeldownload

import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSchedulerTest {
    @Test
    fun downloadScheduler_isInterface() {
        assertTrue(DownloadScheduler::class.java.isInterface)
    }
}

