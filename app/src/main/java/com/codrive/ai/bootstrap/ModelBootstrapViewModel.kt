package com.codrive.ai.bootstrap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codrive.ai.modeldownload.DownloadState
import com.codrive.ai.modeldownload.DownloadScheduler
import com.codrive.ai.modeldownload.InsufficientStorageException
import com.codrive.ai.modeldownload.ModelStorage
import com.codrive.ai.models.ModelAsset
import com.codrive.ai.models.ModelManifest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch

class ModelBootstrapViewModel(
    private val storage: ModelStorage,
    private val scheduler: DownloadScheduler,
    private val models: List<ModelAsset> = ModelManifest.voiceRequiredModels
) : ViewModel() {
    private val _states = MutableStateFlow(initialStates())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _allModelsReady = MutableStateFlow(checkAllReady(_states.value))
    val allModelsReady: StateFlow<Boolean> = _allModelsReady.asStateFlow()

    val assets: List<ModelAsset> = models

    fun startDownloadAll(requireUnmetered: Boolean, requireCharging: Boolean) {
        if (_isDownloading.value) {
            return
        }
        viewModelScope.launch {
            _isDownloading.value = true
            for (asset in models) {
                downloadOne(asset, requireUnmetered, requireCharging)
            }
            _isDownloading.value = false
        }
    }

    fun startDownload(asset: ModelAsset, requireUnmetered: Boolean, requireCharging: Boolean) {
        if (_isDownloading.value) {
            return
        }
        viewModelScope.launch {
            _isDownloading.value = true
            downloadOne(asset, requireUnmetered, requireCharging)
            _isDownloading.value = false
        }
    }

    private suspend fun downloadOne(asset: ModelAsset, requireUnmetered: Boolean, requireCharging: Boolean) {
        storage.ensureParentDirs(asset)
        if (!storage.hasSufficientSpace(asset)) {
            val updated = _states.value.toMutableMap()
            updated[asset.fileName] = DownloadState.Error(
                InsufficientStorageException("Insufficient disk space for ${asset.fileName}")
            )
            _states.value = updated
            _allModelsReady.value = checkAllReady(updated)
            return
        }
        scheduler.downloadAsset(asset, requireUnmetered, requireCharging)
            .transformWhile { state ->
                emit(state)
                state !is DownloadState.Success && state !is DownloadState.Error
            }
            .collect { state ->
                val updated = _states.value.toMutableMap()
                updated[asset.fileName] = state
                _states.value = updated
                _allModelsReady.value = checkAllReady(updated)
            }
    }

    private fun initialStates(): Map<String, DownloadState> {
        return models.associate { asset ->
            val state = if (storage.isVerified(asset)) {
                DownloadState.Success(storage.destinationFile(asset))
            } else {
                DownloadState.Idle
            }
            asset.fileName to state
        }
    }

    private fun checkAllReady(states: Map<String, DownloadState>): Boolean {
        return models.all { asset ->
            val state = states[asset.fileName]
            state is DownloadState.Success
        }
    }
}
