package com.codrive.ai.orchestration

object UiTransitionMonitor {
    private val lock = Object()

    @Volatile
    private var version: Long = 0L

    @JvmStatic
    fun signalChange() {
        synchronized(lock) {
            version += 1
            lock.notifyAll()
        }
    }

    @JvmStatic
    fun currentVersion(): Long = version

    @JvmStatic
    fun awaitAdvance(previousVersion: Long, timeoutMs: Long): Long {
        val deadline = System.currentTimeMillis() + timeoutMs
        synchronized(lock) {
            while (version <= previousVersion) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0L) {
                    break
                }
                try {
                    lock.wait(remaining)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
            return version
        }
    }
}
