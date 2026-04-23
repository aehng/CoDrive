package com.codrive.ai.accessibility

import com.codrive.ai.model.UiRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiTreePrunerTest {
    private class FakeNode(
        override val text: CharSequence? = null,
        override val contentDescription: CharSequence? = null,
        override val isClickable: Boolean = false,
        override val isEditable: Boolean = false,
        override val isCheckable: Boolean = false,
        override val isVisibleToUser: Boolean = true,
        private val children: List<FakeNode> = emptyList(),
        private val screenBounds: IntArray = intArrayOf(0, 0, 1, 1),
        override val liveNode: android.view.accessibility.AccessibilityNodeInfo? = null,
    ) : UiNodeSnapshot {
        var recycleCount = 0

        override val bounds: IntArray
            get() = screenBounds.copyOf()
        override val childCount: Int
            get() = children.size

        override fun childAt(index: Int): UiNodeSnapshot? = children.getOrNull(index)

        override fun recycle() {
            recycleCount += 1
        }
    }

    @Test
    fun pruneKeepsVisibleTextAndInteractiveNodesInDfsOrder() {
        val childInvisible = FakeNode(
            text = "Hidden",
            isVisibleToUser = false,
            screenBounds = intArrayOf(20, 20, 30, 30),
        )
        val childEditable = FakeNode(
            isEditable = true,
            screenBounds = intArrayOf(40, 40, 80, 60),
        )
        val grandChildCheckable = FakeNode(
            isCheckable = true,
            screenBounds = intArrayOf(50, 50, 90, 90),
        )
        val childTextOnly = FakeNode(
            text = "Static label",
            screenBounds = intArrayOf(10, 10, 100, 30),
            children = listOf(grandChildCheckable),
        )
        val root = FakeNode(
            contentDescription = "Home",
            isClickable = true,
            screenBounds = intArrayOf(0, 0, 100, 100),
            children = listOf(childInvisible, childEditable, childTextOnly),
        )

        val outcome = UiTreePruner().prune(root, snapshotId = 7L)

        assertFalse(outcome.isUnreadable)
        assertEquals(4, outcome.uiMap.entries.size)
        assertEquals(listOf(0, 1, 2, 3), outcome.uiMap.entries.map { it.index })
        assertEquals(UiRole.BUTTON, outcome.uiMap.entries[0].role)
        assertEquals("Home Static label", outcome.uiMap.entries[0].text)
        assertEquals(UiRole.INPUT, outcome.uiMap.entries[1].role)
        assertEquals(UiRole.TEXT, outcome.uiMap.entries[2].role)
        assertEquals(UiRole.CHECKBOX, outcome.uiMap.entries[3].role)
        assertEquals(intArrayOf(0, 0, 100, 100).toList(), outcome.uiMap.entries[0].boundsAsList())
        assertEquals(null, outcome.unreadableMessage)
        assertEquals(1, childInvisible.recycleCount)
        assertEquals(1, childEditable.recycleCount)
        assertEquals(1, grandChildCheckable.recycleCount)
        assertEquals(1, childTextOnly.recycleCount)
        assertEquals(0, root.recycleCount)
        assertEquals(0, outcome.nodeRegistry.size())
    }

    @Test
    fun pruneReturnsUnreadableForTextOnlyTreeWithNoInteractables() {
        val leaf = FakeNode(
            text = "Label only",
            screenBounds = intArrayOf(1, 1, 2, 2),
        )
        val root = FakeNode(
            text = "Title",
            children = listOf(leaf),
        )

        val outcome = UiTreePruner().prune(root, snapshotId = 9L)

        assertTrue(outcome.isUnreadable)
        assertEquals(2, outcome.uiMap.entries.size)
        assertEquals("This screen is unreadable.", outcome.unreadableMessage)
        assertEquals(0, outcome.nodeRegistry.size())
    }

    @Test
    fun pruneMergesImmediateChildLabelsIntoClickableParentText() {
        val label = FakeNode(
            text = "Child label",
            screenBounds = intArrayOf(5, 5, 15, 15),
        )
        val root = FakeNode(
            contentDescription = "Parent",
            isClickable = true,
            children = listOf(label),
        )

        val outcome = UiTreePruner().prune(root, snapshotId = 10L)

        assertFalse(outcome.isUnreadable)
        assertEquals(2, outcome.uiMap.entries.size)
        assertEquals("Parent Child label", outcome.uiMap.entries[0].text)
        assertEquals("Parent", outcome.uiMap.entries[0].contentDescription)
    }

    @Test
    fun pruneDoesNotMergeInteractiveChildLabelsIntoClickableParentText() {
        val interactiveChild = FakeNode(
            text = "Do not absorb",
            isClickable = true,
            screenBounds = intArrayOf(5, 5, 15, 15),
        )
        val root = FakeNode(
            text = "Parent",
            isClickable = true,
            children = listOf(interactiveChild),
        )

        val outcome = UiTreePruner().prune(root, snapshotId = 11L)

        assertFalse(outcome.isUnreadable)
        assertEquals(2, outcome.uiMap.entries.size)
        assertEquals("Parent", outcome.uiMap.entries[0].text)
        assertEquals("Do not absorb", outcome.uiMap.entries[1].text)
    }
}








