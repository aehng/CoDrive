package com.codrive.ai.bootstrap

import com.codrive.ai.modeldownload.DownloadScheduler
import com.codrive.ai.modeldownload.DownloadState
import com.codrive.ai.modeldownload.ModelStorage
import com.codrive.ai.models.ModelAsset
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelBootstrapViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startDownloadAll_updatesStateToSuccess() = runTest {
        val root = Files.createTempDirectory("bootstrap-vm").toFile()
        val storage = ModelStorage(root)
        val asset = ModelAsset(
            fileName = "stt/model.onnx",
            downloadUrl = "https://example.com/model.onnx",
            sha256 = "abc123",
            sizeBytes = 100L
        )

        val scheduler = object : DownloadScheduler {
            override fun downloadAsset(
                asset: ModelAsset,
                requireUnmetered: Boolean,
                requireCharging: Boolean
            ) = flow {
                val destination = storage.destinationFile(asset)
                destination.parentFile?.mkdirs()
                destination.writeText("data")
                emit(DownloadState.Downloading(100))
                emit(DownloadState.Verifying)
                emit(DownloadState.Success(destination))
            }

            override fun enqueueAll(
                assets: List<ModelAsset>,
                requireUnmetered: Boolean,
                requireCharging: Boolean
            ) = emptyList<java.util.UUID>()
        }

        val viewModel = ModelBootstrapViewModel(storage, scheduler, listOf(asset))
        viewModel.startDownloadAll(requireUnmetered = false, requireCharging = false)
        advanceUntilIdle()

        val state = viewModel.states.value[asset.fileName]
        assertTrue(state is DownloadState.Success)
        assertTrue(viewModel.allModelsReady.value)
    }

    @Test
    fun initialState_marksVerifiedAssetsReady() = runTest {
        val root = Files.createTempDirectory("bootstrap-vm-verified").toFile()
        val storage = ModelStorage(root)
        val asset = ModelAsset(
            fileName = "tts/model.onnx",
            downloadUrl = "https://example.com/model.onnx",
            sha256 = "abc123",
            sizeBytes = 100L
        )

        val destination = storage.destinationFile(asset)
        destination.parentFile?.mkdirs()
        destination.writeText("data")
        storage.markVerified(asset, "abc123")

        val scheduler = object : DownloadScheduler {
            override fun downloadAsset(
                asset: ModelAsset,
                requireUnmetered: Boolean,
                requireCharging: Boolean
            ) = flow<DownloadState> { }

            override fun enqueueAll(
                assets: List<ModelAsset>,
                requireUnmetered: Boolean,
                requireCharging: Boolean
            ) = emptyList<java.util.UUID>()
        }

        val viewModel = ModelBootstrapViewModel(storage, scheduler, listOf(asset))
        val state = viewModel.states.value[asset.fileName]

        assertTrue(state is DownloadState.Success)
        assertTrue(viewModel.allModelsReady.value)
    }
}




