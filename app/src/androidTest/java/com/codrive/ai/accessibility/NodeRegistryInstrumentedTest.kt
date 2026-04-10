@file:Suppress("DEPRECATION")

package com.codrive.ai.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NodeRegistryInstrumentedTest {
    @Test
    fun registerAndInvalidateLiveNodesAcrossSnapshots() {
        val registry = NodeRegistry()
        val firstNode = AccessibilityNodeInfo.obtain()
        val secondNode = AccessibilityNodeInfo.obtain()

        registry.beginSnapshot(100L)
        registry.register(1, firstNode)
        assertSame(firstNode, registry.lookup(1))
        assertEquals(100L, registry.currentSnapshotId())

        registry.beginSnapshot(101L)
        assertNull(registry.lookup(1))
        registry.register(2, secondNode)
        assertSame(secondNode, registry.lookup(2))
        assertEquals(1, registry.size())
    }
}


