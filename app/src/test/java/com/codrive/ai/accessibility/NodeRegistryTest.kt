@file:Suppress("DEPRECATION")

package com.codrive.ai.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class NodeRegistryTest {
    @Test
    fun beginSnapshotResetsSnapshotIdAndClearsStoredMappings() {
        val registry = NodeRegistry()

        registry.beginSnapshot(1L)
        assertEquals(1L, registry.currentSnapshotId())
        assertEquals(0, registry.size())
        assertEquals(0, registry.snapshot().size)

        registry.beginSnapshot(2L)
        assertEquals(2L, registry.currentSnapshotId())
        assertEquals(0, registry.size())
        assertEquals(0, registry.snapshot().size)
    }
}



