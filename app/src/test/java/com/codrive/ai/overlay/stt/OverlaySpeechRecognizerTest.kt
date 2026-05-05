package com.codrive.ai.overlay.stt

import org.junit.Assert.assertTrue
import org.junit.Test

class OverlaySpeechRecognizerTest {
    @Test
    fun overlaySpeechRecognizer_isInterface() {
        assertTrue(OverlaySpeechRecognizer::class.java.isInterface)
    }
}

