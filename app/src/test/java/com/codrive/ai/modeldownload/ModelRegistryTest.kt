package com.codrive.ai.modeldownload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRegistryTest {
    @Test
    fun registry_hasPlaceholderEntries() {
        assertTrue(ModelRegistry.models.isNotEmpty())
        ModelRegistry.models.forEach { model ->
            assertTrue(model.id.isNotBlank())
            assertTrue(model.displayName.isNotBlank())
            assertTrue(model.url.isNotBlank())
            assertTrue(model.fileName.isNotBlank())
            assertTrue(model.sizeBytes > 0)
        }
    }

    @Test
    fun modelDescriptor_storesProperties() {
        val descriptor = ModelDescriptor(
            id = "test-id",
            displayName = "Test Model",
            url = "https://example.com/test.bin",
            sha256 = "abc123",
            fileName = "test.bin",
            sizeBytes = 123L
        )

        assertEquals("test-id", descriptor.id)
        assertEquals("Test Model", descriptor.displayName)
        assertEquals("https://example.com/test.bin", descriptor.url)
        assertEquals("abc123", descriptor.sha256)
        assertEquals("test.bin", descriptor.fileName)
        assertEquals(123L, descriptor.sizeBytes)
    }
}

