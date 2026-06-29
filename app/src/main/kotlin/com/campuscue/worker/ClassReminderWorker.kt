package com.campuscue.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.campuscue.R
import com.campuscue.data.repository.AuthRepository
import com.campuscue.data.repository.TimetableRepository
import com.campuscue.domain.usecase.TimetableUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@HiltWorker
class ClassReminderWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val timetableRepo: TimetableRepository,
        private val authRepository: AuthRepository,
        private val timetableUseCase: TimetableUseCase,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            return try {
                val user = authRepository.getUserInfo() ?: return Result.failure()
                val year = timetableUseCase.getAcadYear()
                val (weekStart, weekEnd) = timetableUseCase.getCurrentWeekRange()

                val timetable =
                    timetableRepo.getTimetable(
                        user.admno,
                        user.brId,
                        year,
                        weekStart.toString(),
                        weekEnd.toString(),
                        false,
                    )

                val todayKey = LocalDate.now().dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                val slots = timetableUseCase.sortSlotsByTime(timetable[todayKey] ?: emptyList())
                val now = LocalTime.now()

                val upcoming =
                    slots.firstOrNull { slot ->
                        val startMin = timetableUseCase.timeToMinutes(slot.fromTime)
                        val nowMin = now.hour * 60 + now.minute
                        startMin > nowMin && startMin - nowMin <= 15
                    }

                if (upcoming != null) {
                    sendNotification(
                        timetableUseCase.displaySubjectName(upcoming),
                        upcoming.fromTime,
                        upcoming.roomno,
                    )
                }

                Result.success()
            } catch (_: Exception) {
                Result.retry()
            }
        }

        private fun sendNotification(
            subject: String,
            time: String,
            room: String,
        ) {
            val channelId = "class_reminders"
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "Class Reminders", NotificationManager.IMPORTANCE_DEFAULT)
                nm.createNotificationChannel(channel)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val roomInfo = if (room.isNotBlank()) " · Room $room" else ""
            val notification =
                NotificationCompat.Builder(applicationContext, channelId)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Upcoming: $subject")
                    .setContentText("$time$roomInfo")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()

            NotificationManagerCompat.from(applicationContext).notify(time.hashCode(), notification)
        }

        companion object {
            const val WORK_NAME = "class_reminders"
            const val INTERVAL_MINUTES = 15L
        }
    }
