package com.campuscue.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.campuscue.R
import com.campuscue.data.repository.UpdateCheckResult
import com.campuscue.data.repository.UpdateRepository
import com.campuscue.ui.MainActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class UpdateCheckWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val updateRepository: UpdateRepository,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            return try {
                val result = updateRepository.checkForUpdate()
                if (result is UpdateCheckResult.Available) {
                    sendNotification(result.info.latestVersionName, result.info.releaseNotes)
                }
                Result.success()
            } catch (_: Exception) {
                Result.retry()
            }
        }

        private fun sendNotification(
            version: String,
            notes: String?,
        ) {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(CHANNEL_ID, "App Updates", NotificationManager.IMPORTANCE_DEFAULT)
                channel.description = "Notifications when a new version of CampusCue is available"
                nm.createNotificationChannel(channel)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val intent =
                Intent(applicationContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            val pendingIntent =
                PendingIntent.getActivity(
                    applicationContext,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val body = notes?.takeIf { it.isNotBlank() } ?: "Tap to open CampusCue and install."

            val notification =
                NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("CampusCue v$version available")
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()

            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        }

        companion object {
            const val WORK_NAME = "update_check"
            const val INTERVAL_HOURS = 12L
            const val CHANNEL_ID = "app_updates"
            const val NOTIFICATION_ID = 9001
        }
    }
