package com.codrive.ai.modeldownload

import org.junit.Assert.assertEquals
import org.junit.Test

class InsufficientStorageExceptionTest {
    @Test
    fun message_isPreserved() {
        val exception = InsufficientStorageException("Insufficient space")
        assertEquals("Insufficient space", exception.message)
    }
}

