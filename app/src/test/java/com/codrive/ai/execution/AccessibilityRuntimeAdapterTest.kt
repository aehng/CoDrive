package com.codrive.ai.execution

import com.codrive.ai.accessibility.NodeRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessibilityRuntimeAdapterTest {
    @Test
    fun bindRegistryReplacesActiveRegistryReference() {
        val adapter = AccessibilityRuntimeAdapter(service = null)

        val first = NodeRegistry().apply { beginSnapshot(1L) }
        val second = NodeRegistry().apply { beginSnapshot(2L) }

        adapter.bindRegistry(first)
        assertNull(adapter.lookupNode(1))

        adapter.bindRegistry(second)
        assertNull(adapter.lookupNode(1))
        assertEquals(false, adapter.click(intArrayOf(0, 0, 1, 1)))
    }
}

