package com.accu.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "CleanupWorker"
        const val WORK_NAME = "acc_cleanup_work"
        const val KEY_BYTES_FREED = "bytes_freed"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "CleanupWorker started")
        var totalFreed = 0L
        try {
            totalFreed += cleanDirectory(applicationContext.cacheDir)
            totalFreed += cleanDirectory(applicationContext.codeCacheDir)
            Log.d(TAG, "CleanupWorker freed ${totalFreed} bytes")
            Result.success(
                androidx.work.Data.Builder()
                    .putLong(KEY_BYTES_FREED, totalFreed)
                    .build()
            )
        } catch (e: Exception) {
            Log.e(TAG, "CleanupWorker failed", e)
            Result.failure()
        }
    }

    private fun cleanDirectory(dir: File): Long {
        var freed = 0L
        if (!dir.exists()) return freed
        dir.walkBottomUp().forEach { file ->
            if (file != dir) {
                val size = if (file.isFile) file.length() else 0L
                if (file.delete()) freed += size
            }
        }
        return freed
    }
}
