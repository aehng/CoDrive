package com.codrive.ai.memory

class MemorySearchTool(
    private val identityDaoProvider: () -> IdentityDao?,
    private val sessionContextDaoProvider: () -> SessionContextDao?,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) {
    fun search(toolQuery: String): String {
        val query = toolQuery.trim()
        if (query.isEmpty()) {
            return "NO_QUERY"
        }

        val identityDao = identityDaoProvider()
        val sessionDao = sessionContextDaoProvider()

        sessionDao?.purgeExpired(nowProvider())

        val identityMatches = identityDao
            ?.getAll()
            ?.filter { it.key.contains(query, ignoreCase = true) || it.value.contains(query, ignoreCase = true) }
            .orEmpty()

        val sessionMatches = sessionDao
            ?.getAll()
            ?.filter { it.taskKey.contains(query, ignoreCase = true) || it.value.contains(query, ignoreCase = true) }
            .orEmpty()

        if (identityMatches.isEmpty() && sessionMatches.isEmpty()) {
            return "NO_MATCH"
        }

        val compactIdentity = identityMatches.joinToString(separator = "; ") { "${it.key}=${it.value}" }
        val compactSession = sessionMatches.joinToString(separator = "; ") { "${it.taskKey}=${it.value}" }

        return buildString {
            if (compactIdentity.isNotEmpty()) {
                append("IDENTITY[").append(compactIdentity).append(']')
            }
            if (compactSession.isNotEmpty()) {
                if (isNotEmpty()) append(' ')
                append("SESSION[").append(compactSession).append(']')
            }
        }
    }
}

