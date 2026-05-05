package com.codrive.ai.modeldownload

import com.codrive.ai.models.ModelAsset
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface DownloadScheduler {
    fun downloadAsset(asset: ModelAsset, requireUnmetered: Boolean, requireCharging: Boolean): Flow<DownloadState>
    fun enqueueAll(assets: List<ModelAsset>, requireUnmetered: Boolean, requireCharging: Boolean): List<UUID>
}

