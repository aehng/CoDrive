package com.codrive.ai.execution

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.codrive.ai.accessibility.NodeRegistry
import com.codrive.ai.service.CoDriveAccessibilityService

class AccessibilityRuntimeAdapter(
    private val service: CoDriveAccessibilityService?,
) : UiActionRuntime, RegistryBinder {
    private var registry: NodeRegistry = NodeRegistry()

    override fun bindRegistry(registry: NodeRegistry) {
        this.registry = registry
    }

    override fun lookupNode(targetIndex: Int): UiActionNode? {
        val node = registry.lookup(targetIndex) ?: return null
        return object : UiActionNode {
            override val isVisibleToUser: Boolean
                get() = node.isVisibleToUser

            override val isFocused: Boolean
                get() = node.isFocused

            override val bounds: IntArray
                get() {
                    val rect = android.graphics.Rect()
                    node.getBoundsInScreen(rect)
                    return intArrayOf(rect.left, rect.top, rect.right, rect.bottom)
                }

            override fun refresh(): Boolean = node.refresh()

            override fun focus(): Boolean = node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

            override fun setText(value: String): Boolean {
                val args = Bundle()
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
                return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }

            override fun scrollForward(): Boolean =
                node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)

            override fun scrollBackward(): Boolean =
                node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        }
    }

    override fun click(bounds: IntArray): Boolean = service?.dispatchMidpointTap(bounds) == true
}

