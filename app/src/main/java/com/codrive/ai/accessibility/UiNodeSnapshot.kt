@file:Suppress("unused")

package com.codrive.ai.accessibility

import android.view.accessibility.AccessibilityNodeInfo

interface UiNodeSnapshot {
    val liveNode: AccessibilityNodeInfo?
    val isVisibleToUser: Boolean
    val text: CharSequence?
    val contentDescription: CharSequence?
    val isClickable: Boolean
    val isEditable: Boolean
    val isCheckable: Boolean
    val bounds: IntArray
    val childCount: Int

    fun childAt(index: Int): UiNodeSnapshot?
    fun recycle()
}



