package com.merlottv.kotlin.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.merlottv.kotlin.data.local.SettingsDataStore
import com.merlottv.kotlin.domain.model.DefaultData
import com.merlottv.kotlin.domain.repository.EpgRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class EpgSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val epgRepository: EpgRepository,
    private val settingsDataStore: SettingsDataStore
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "EpgSyncWorker"
        const val WORK_NAME = "epg_periodic_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<EpgSyncWorker>(
                4, TimeUnit.HOURS,
                30, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Scheduled periodic EPG sync every 4 hours")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting EPG sync...")
            val defaultUrls = DefaultData.EPG_SOURCES.map { it.url }
            val customSources = settingsDataStore.customEpgSources.first()
            val customUrls = customSources.filter { it.enabled }.map { it.url }
            val allUrls = (defaultUrls + customUrls).distinct()

            epgRepository.forceRefresh(allUrls)
            Log.d(TAG, "EPG sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "EPG sync failed (attempt $runAttemptCount)", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
