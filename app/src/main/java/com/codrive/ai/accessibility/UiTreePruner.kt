@file:Suppress("unused", "DEPRECATION")

package com.codrive.ai.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.codrive.ai.model.PrunedNodeEntry
import com.codrive.ai.model.PrunedUiMap
import com.codrive.ai.model.UiRole

class UiTreePruner(
    private val nodeRegistry: NodeRegistry = NodeRegistry(),
) {
    fun prune(root: AccessibilityNodeInfo?, snapshotId: Long = System.currentTimeMillis()): PruningOutcome {
        val snapshotRoot = root?.asSnapshot()
        return prune(snapshotRoot, snapshotId)
    }

    fun prune(root: UiNodeSnapshot?, snapshotId: Long): PruningOutcome {
        nodeRegistry.beginSnapshot(snapshotId)

        if (root == null) {
            return unreadable(snapshotId)
        }

        val entries = mutableListOf<PrunedNodeEntry>()
        var crawlIndex = 0

        fun dfs(node: UiNodeSnapshot, recycleSelf: Boolean) {
            if (!node.isVisibleToUser) {
                if (recycleSelf) {
                    node.recycle()
                }
                return
            }

            val shouldKeep = shouldKeep(node)
            if (shouldKeep) {
                val entryIndex = crawlIndex++
                val entry = PrunedNodeEntry(
                    index = entryIndex,
                    role = roleFor(node),
                    bounds = node.bounds.copyOf(),
                    text = node.text?.toString(),
                    contentDescription = node.contentDescription?.toString(),
                    isInteractive = isInteractive(node),
                )
                entries += entry
                node.liveNode?.let { nodeRegistry.register(entryIndex, it) }
            }

            for (childIndex in 0 until node.childCount) {
                val child = node.childAt(childIndex) ?: continue
                dfs(child, recycleSelf = true)
            }

            if (recycleSelf) {
                node.recycle()
            }
        }

        dfs(root, recycleSelf = false)

        val uiMap = PrunedUiMap(snapshotId = snapshotId, entries = entries)
        return if (uiMap.isUnreadable) {
            PruningOutcome(uiMap, nodeRegistry, unreadableMessage = "This screen is unreadable.")
        } else {
            PruningOutcome(uiMap, nodeRegistry)
        }
    }

    private fun unreadable(snapshotId: Long): PruningOutcome = PruningOutcome(
        uiMap = PrunedUiMap(snapshotId = snapshotId, entries = emptyList()),
        nodeRegistry = nodeRegistry,
        unreadableMessage = "This screen is unreadable.",
    )

    private fun shouldKeep(node: UiNodeSnapshot): Boolean {
        val hasSemanticText = !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()
        return hasSemanticText || isInteractive(node)
    }

    private fun isInteractive(node: UiNodeSnapshot): Boolean =
        node.isClickable || node.isEditable || node.isCheckable

    private fun roleFor(node: UiNodeSnapshot): UiRole = when {
        node.isEditable -> UiRole.INPUT
        node.isCheckable -> UiRole.CHECKBOX
        node.isClickable -> UiRole.BUTTON
        else -> UiRole.TEXT
    }

    private fun AccessibilityNodeInfo.asSnapshot(): UiNodeSnapshot = object : UiNodeSnapshot {
        override val liveNode: AccessibilityNodeInfo = this@asSnapshot
        override val isVisibleToUser: Boolean get() = this@asSnapshot.isVisibleToUser
        override val text: CharSequence? get() = this@asSnapshot.text
        override val contentDescription: CharSequence? get() = this@asSnapshot.contentDescription
        override val isClickable: Boolean get() = this@asSnapshot.isClickable
        override val isEditable: Boolean get() = this@asSnapshot.isEditable
        override val isCheckable: Boolean get() = this@asSnapshot.isCheckable
        override val bounds: IntArray
            get() {
                val rect = Rect()
                this@asSnapshot.getBoundsInScreen(rect)
                return intArrayOf(rect.left, rect.top, rect.right, rect.bottom)
            }
        override val childCount: Int get() = this@asSnapshot.childCount

        override fun childAt(index: Int): UiNodeSnapshot? = this@asSnapshot.getChild(index)?.asSnapshot()

        override fun recycle() {
            this@asSnapshot.recycle()
        }
    }
}



