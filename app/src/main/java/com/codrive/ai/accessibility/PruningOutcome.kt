@file:Suppress("unused")

package com.codrive.ai.accessibility

import com.codrive.ai.model.PrunedUiMap

data class PruningOutcome(
    val uiMap: PrunedUiMap,
    val nodeRegistry: NodeRegistry,
    val unreadableMessage: String? = null,
) {
    val isUnreadable: Boolean
        get() = unreadableMessage != null || uiMap.isUnreadable
}


