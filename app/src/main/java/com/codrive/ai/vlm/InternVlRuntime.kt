package com.codrive.ai.vlm

class InternVlRuntime(private val loader: InternVlModelLoader) {
    @Volatile
    private var cached: InternVlModelPaths? = null

    fun isReady(): Boolean = loader.isReady()

    @Synchronized
    fun ensureLoaded(): InternVlModelPaths {
        val current = cached
        if (current != null) {
            return current
        }
        val loaded = loader.load()
        cached = loaded
        return loaded
    }
}

