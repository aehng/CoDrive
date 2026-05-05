package com.codrive.ai.vlm

import com.codrive.ai.modeldownload.ModelStorage
import com.codrive.ai.models.ModelManifest
import java.io.File

class InternVlModelLoader @JvmOverloads constructor(
    private val storage: ModelStorage,
    private val modelAsset: com.codrive.ai.models.ModelAsset = ModelManifest.VLM_LLM,
    private val projectionAsset: com.codrive.ai.models.ModelAsset = ModelManifest.VLM_PROJ
) {
    fun isReady(): Boolean {
        return storage.isVerified(modelAsset) &&
            storage.isVerified(projectionAsset) &&
            storage.isValidFile(modelAsset) &&
            storage.isValidFile(projectionAsset)
    }

    fun load(): InternVlModelPaths {
        if (!isReady()) {
            throw IllegalStateException("InternVL models are not verified")
        }
        val modelFile = storage.destinationFile(modelAsset)
        val projectionFile = storage.destinationFile(projectionAsset)
        return InternVlModelPaths(modelFile, projectionFile)
    }
}

data class InternVlModelPaths(
    val modelFile: File,
    val projectionFile: File
)
