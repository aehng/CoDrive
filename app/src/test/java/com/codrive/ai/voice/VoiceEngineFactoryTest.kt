package com.codrive.ai.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceEngineFactoryTest {
    @Test
    fun className_isStable() {
        assertEquals(
            "com.codrive.ai.voice.VoiceEngineFactory",
            VoiceEngineFactory::class.java.name
        )
    }
}

