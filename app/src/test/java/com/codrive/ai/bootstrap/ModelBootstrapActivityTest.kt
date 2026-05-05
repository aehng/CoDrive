package com.codrive.ai.bootstrap

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelBootstrapActivityTest {
    @Test
    fun className_isStable() {
        assertEquals(
            "com.codrive.ai.bootstrap.ModelBootstrapActivity",
            ModelBootstrapActivity::class.java.name
        )
    }
}

