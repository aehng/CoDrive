package com.codrive.ai.execution

import com.codrive.ai.accessibility.NodeRegistry

interface UiActionNode {
    val isVisibleToUser: Boolean
    val isFocused: Boolean
    val bounds: IntArray

    fun refresh(): Boolean
    fun focus(): Boolean
    fun setText(value: String): Boolean
    fun scrollForward(): Boolean
    fun scrollBackward(): Boolean
}

interface UiActionRuntime {
    fun lookupNode(targetIndex: Int): UiActionNode?
    fun click(bounds: IntArray): Boolean
    fun goHome(): Boolean
    fun goBack(): Boolean
    fun openRecents(): Boolean
}

fun interface RegistryBinder {
    fun bindRegistry(registry: NodeRegistry)
}
