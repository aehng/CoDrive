package com.codrive.ai.vlm

import com.codrive.ai.modeldownload.ModelStorage
import com.codrive.ai.models.ModelManifest
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InternVlRuntimeTest {
    @Test
    fun ensureLoaded_cachesPaths() {
        val root = Files.createTempDirectory("vlm-runtime").toFile()
        val storage = ModelStorage(root)

        val smallVlm = ModelManifest.VLM_LLM.copy(sizeBytes = 4L)
        val smallProj = ModelManifest.VLM_PROJ.copy(sizeBytes = 4L)

        val modelFile = storage.destinationFile(smallVlm)
        modelFile.parentFile?.mkdirs()
        modelFile.writeBytes(ByteArray(4))
        storage.markVerified(smallVlm, smallVlm.sha256)

        val projFile = storage.destinationFile(smallProj)
        projFile.parentFile?.mkdirs()
        projFile.writeBytes(ByteArray(4))
        storage.markVerified(smallProj, smallProj.sha256)

        val runtime = InternVlRuntime(InternVlModelLoader(storage, smallVlm, smallProj))
        assertTrue(runtime.isReady())

        val first = runtime.ensureLoaded()
        val second = runtime.ensureLoaded()

        assertEquals(first, second)
        assertEquals(modelFile, first.modelFile)
        assertEquals(projFile, first.projectionFile)
    }
}
