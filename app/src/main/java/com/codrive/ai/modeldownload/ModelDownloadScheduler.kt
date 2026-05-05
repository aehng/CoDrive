package com.codrive.ai.modeldownload

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.codrive.ai.models.ModelAsset
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ModelDownloadScheduler(private val context: Context) : DownloadScheduler {
    private val workManager = WorkManager.getInstance(context)

    override fun downloadAsset(
        asset: ModelAsset,
        requireUnmetered: Boolean,
        requireCharging: Boolean
    ): Flow<DownloadState> {
        val request = buildRequest(asset, requireUnmetered, requireCharging)
        workManager.enqueueUniqueWork(workName(asset), ExistingWorkPolicy.REPLACE, request)
        return workManager.getWorkInfoByIdFlow(request.id).map { info ->
            mapWorkInfo(asset, info)
        }
    }

    override fun enqueueAll(assets: List<ModelAsset>, requireUnmetered: Boolean, requireCharging: Boolean): List<UUID> {
        return assets.map { asset ->
            val request = buildRequest(asset, requireUnmetered, requireCharging)
            workManager.enqueueUniqueWork(workName(asset), ExistingWorkPolicy.REPLACE, request)
            request.id
        }
    }

    private fun buildRequest(
        asset: ModelAsset,
        requireUnmetered: Boolean,
        requireCharging: Boolean
    ) = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(
                    if (requireUnmetered) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .setRequiresCharging(requireCharging)
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()
        )
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(30))
        .setInputData(workDataOf(ModelDownloadWorker.KEY_FILE_NAME to asset.fileName))
        .build()

    private fun workName(asset: ModelAsset): String = "model_download_${asset.fileName}"

    private fun mapWorkInfo(asset: ModelAsset, info: WorkInfo?): DownloadState {
        if (info == null) {
            return DownloadState.Error(IllegalStateException("Work missing for ${asset.fileName}"))
        }
        return when (info.state) {
            WorkInfo.State.SUCCEEDED -> DownloadState.Success(
                storageFile(asset, info)
            )
            WorkInfo.State.FAILED -> DownloadState.Error(Exception("Download failed for ${asset.fileName}"))
            WorkInfo.State.CANCELLED -> DownloadState.Error(Exception("Download cancelled for ${asset.fileName}"))
            WorkInfo.State.RUNNING -> {
                val progress = info.progress.getInt(ModelDownloadWorker.KEY_PROGRESS, 0)
                if (progress >= 100) DownloadState.Verifying else DownloadState.Downloading(progress)
            }
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.BLOCKED -> DownloadState.Idle
        }
    }

    private fun storageFile(asset: ModelAsset, info: WorkInfo): java.io.File {
        val path = info.outputData.getString(ModelDownloadWorker.KEY_OUTPUT_PATH)
        return if (path.isNullOrBlank()) {
            ModelStorage(java.io.File(context.noBackupFilesDir, "models")).destinationFile(asset)
        } else {
            java.io.File(path)
        }
    }
}


