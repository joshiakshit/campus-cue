package com.joshi.core.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BaseScheduler {
    fun schedulePeriodicWork(
        context: Context,
        uniqueName: String,
        workerClass: Class<out androidx.work.ListenableWorker>,
        intervalMinutes: Long,
        requireNetwork: Boolean = false,
    ) {
        val constraints =
            Constraints.Builder()
                .apply { if (requireNetwork) setRequiredNetworkType(NetworkType.CONNECTED) }
                .build()

        val request =
            PeriodicWorkRequest.Builder(workerClass, intervalMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            uniqueName,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelWork(
        context: Context,
        uniqueName: String,
    ) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName)
    }
}
