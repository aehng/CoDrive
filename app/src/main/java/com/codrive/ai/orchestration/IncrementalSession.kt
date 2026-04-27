package com.codrive.ai.orchestration

import com.codrive.ai.accessibility.PruningOutcome

data class IncrementalSession(
    val generation: Int,
    var prompt: String,
    var pruningOutcome: PruningOutcome,
    var buffer: String,
)

