package com.codrive.ai.modeldownload

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.codrive.ai.models.ModelAsset
import com.codrive.ai.models.ModelManifest
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.collect
import okhttp3.OkHttpClient

class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return Result.failure()
        val asset = ModelManifest.allRequiredModels.firstOrNull { it.fileName == fileName }
            ?: return Result.failure()

        val storageRoot = File(applicationContext.noBackupFilesDir, "models")
        val storage = ModelStorage(storageRoot)
        val destination = storage.destinationFile(asset)
        val verification = storage.verificationFile(asset)
        storage.ensureParentDirs(asset)

        val manager = ModelDownloadManager(OkHttpClient())
        var failure: Exception? = null

        manager.downloadModel(
            url = asset.downloadUrl,
            destinationFile = destination,
            expectedSha256 = asset.sha256,
            verificationFile = verification,
            expectedSizeBytes = asset.sizeBytes
        ).collect { state ->
            when (state) {
                is DownloadState.Downloading -> {
                    setProgressAsync(Data.Builder().putInt(KEY_PROGRESS, state.progress).build())
                }
                is DownloadState.Verifying -> {
                    setProgressAsync(Data.Builder().putInt(KEY_PROGRESS, 100).build())
                }
                is DownloadState.Error -> {
                    failure = state.exception
                }
                else -> Unit
            }
        }

        val error = failure
        if (error != null) {
            return when (error) {
                is InsufficientStorageException -> Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, error.message)
                        .build()
                )
                is IOException -> Result.retry()
                else -> Result.failure()
            }
        }

        if (asset.isZipped) {
            val outputDir = storage.extractedDir(asset)
            ModelArchiveExtractor.extractTarBz2(destination, outputDir)
        }

        return Result.success(
            Data.Builder()
                .putString(KEY_FILE_NAME, asset.fileName)
                .putString(KEY_OUTPUT_PATH, destination.absolutePath)
                .build()
        )
    }

    companion object {
        const val KEY_FILE_NAME = "file_name"
        const val KEY_PROGRESS = "progress"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_ERROR_MESSAGE = "error_message"
    }
}

