@file:Suppress("unused")

package com.codrive.ai.accessibility

import android.view.accessibility.AccessibilityNodeInfo

class NodeRegistry {
    private var snapshotId: Long = -1L
    private val liveNodes: HashMap<Int, AccessibilityNodeInfo> = HashMap()

    fun beginSnapshot(snapshotId: Long) {
        this.snapshotId = snapshotId
        liveNodes.clear()
    }

    fun register(targetIndex: Int, node: AccessibilityNodeInfo) {
        liveNodes[targetIndex] = node
    }

    fun lookup(targetIndex: Int): AccessibilityNodeInfo? = liveNodes[targetIndex]

    fun snapshot(): HashMap<Int, AccessibilityNodeInfo> = HashMap(liveNodes)

    fun size(): Int = liveNodes.size

    fun currentSnapshotId(): Long = snapshotId
}


