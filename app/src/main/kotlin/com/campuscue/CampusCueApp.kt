package com.campuscue

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.campuscue.worker.UpdateCheckWorker
import com.joshi.core.crash.CrashReporter
import com.joshi.core.security.SecretProvider
import com.joshi.core.worker.BaseScheduler
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class CampusCueApp : Application(), Configuration.Provider {
    @Inject lateinit var crashReporter: CrashReporter

    @Inject lateinit var secretProvider: SecretProvider

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        crashReporter.initialize(
            context = this,
            dsn = secretProvider.sentryDsn,
            environment = if (BuildConfig.DEBUG) "debug" else "release",
            release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}",
            debug = BuildConfig.DEBUG,
        )
        scheduleUpdateChecks()
    }

    private fun scheduleUpdateChecks() {
        BaseScheduler.schedulePeriodicWork(
            context = this,
            uniqueName = UpdateCheckWorker.WORK_NAME,
            workerClass = UpdateCheckWorker::class.java,
            intervalMinutes = TimeUnit.HOURS.toMinutes(UpdateCheckWorker.INTERVAL_HOURS),
        )
    }
}
