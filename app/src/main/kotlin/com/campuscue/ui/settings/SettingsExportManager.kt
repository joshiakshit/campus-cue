package com.campuscue.ui.settings

import android.content.Context
import android.widget.Toast
import com.campuscue.data.repository.AttendanceRepository
import com.campuscue.data.repository.AuthRepository
import com.campuscue.data.repository.SELECTED_SEMESTER_CLASS_KEY
import com.campuscue.data.repository.SELECTED_SEMESTER_YEAR_KEY
import com.campuscue.data.repository.TimetableRepository
import com.campuscue.domain.usecase.AttendanceUseCase
import com.campuscue.domain.usecase.ForecastUseCase
import com.campuscue.domain.usecase.SubjectAttendance
import com.campuscue.domain.usecase.TimetableUseCase
import com.campuscue.ui.attendance.AttendanceUiState
import com.campuscue.ui.attendance.DecoratedSubject
import com.campuscue.ui.export.exportAttendancePdf
import com.campuscue.ui.export.exportTimetablePdf
import com.campuscue.ui.timetable.DisplaySlot
import com.campuscue.ui.timetable.TimetableDay
import com.campuscue.ui.timetable.TimetableUiState
import com.joshi.core.storage.PreferencesStore
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal class SettingsExportManager(
    private val preferencesStore: PreferencesStore,
    private val authRepository: AuthRepository,
    private val attendanceRepo: AttendanceRepository,
    private val timetableRepo: TimetableRepository,
    private val attendanceUseCase: AttendanceUseCase,
    private val forecastUseCase: ForecastUseCase,
    private val timetableUseCase: TimetableUseCase,
) {
    @Suppress("TooGenericExceptionCaught")
    suspend fun exportAttendance(context: Context) {
        try {
            val user = authRepository.getUserInfo() ?: error("Not logged in")
            val semester = selectedSemester(user.admno, user.brId)
            val attendance = attendanceRepo.getAttendance(user.admno, user.brId, semester.classId, semester.yearId, false)
            val threshold = preferencesStore.getUserInt("attendance_threshold", 75).first()
            val subjects =
                attendance.table.values.map { entry ->
                    val subjectAttendance =
                        SubjectAttendance(
                            entry.subCode,
                            entry.subname,
                            entry.lecType,
                            entry.present,
                            entry.total,
                            entry.percent,
                        )
                    DecoratedSubject(
                        subject = subjectAttendance,
                        bunkable = attendanceUseCase.bunkBudget(subjectAttendance.present, subjectAttendance.total, threshold),
                        need = attendanceUseCase.mustAttend(subjectAttendance.present, subjectAttendance.total, threshold),
                        tone = attendanceUseCase.tone(subjectAttendance.percent, threshold),
                    )
                }.sortedBy { it.subject.percent }

            val (weekStart, weekEnd) = timetableUseCase.getCurrentWeekRange()
            val timetable =
                timetableRepo.getTimetable(
                    user.admno,
                    user.brId,
                    semester.yearId,
                    weekStart.toString(),
                    weekEnd.toString(),
                    false,
                )
            val subjectAttendances = subjects.map { it.subject }
            val endDate = selectedSemesterEndDate()
            val forecast = forecastUseCase.buildForecast(subjectAttendances, timetable, threshold, endDate)
            val overallPct = attendance.endrow.percentage
            val exportState =
                AttendanceUiState(
                    isLoading = false,
                    overallPercent = overallPct,
                    overallPresent = attendance.endrow.present,
                    overallTotal = attendance.endrow.total,
                    overallTone = attendanceUseCase.tone(overallPct, threshold),
                    threshold = threshold,
                    subjects = subjects.toImmutableList(),
                    forecast = forecast.toImmutableList(),
                )
            exportAttendancePdf(context, exportState)
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun exportTimetable(context: Context) {
        try {
            val user = authRepository.getUserInfo() ?: error("Not logged in")
            val semester = selectedSemester(user.admno, user.brId)
            val (weekStart, weekEnd) = timetableUseCase.getCurrentWeekRange()
            val timetable =
                timetableRepo.getTimetable(
                    user.admno,
                    user.brId,
                    semester.yearId,
                    weekStart.toString(),
                    weekEnd.toString(),
                    false,
                )
            val days =
                DAY_ORDER.mapNotNull { dayKey ->
                    val slots = timetable[dayKey] ?: return@mapNotNull null
                    TimetableDay(
                        dayName = dayKey,
                        slots =
                            slots.sortedBy { it.fromTime }.map { slot ->
                                DisplaySlot(
                                    slot = slot,
                                    displayName = timetableUseCase.displaySubjectName(slot),
                                    progress = null,
                                )
                            }.toImmutableList(),
                    )
                }.toImmutableList()
            val exportState =
                TimetableUiState(
                    isLoading = false,
                    days = days,
                    weekLabel = "${weekStart.format(DATE_LABEL_FORMAT)} - ${weekEnd.format(DATE_LABEL_FORMAT)}",
                )
            exportTimetablePdf(context, exportState)
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun selectedSemester(
        admno: String,
        brId: Int,
    ) = attendanceRepo.getPreferredSemester(
        admno = admno,
        brId = brId,
        selectedYearId = preferencesStore.getUserString(SELECTED_SEMESTER_YEAR_KEY).first(),
        selectedClassId = preferencesStore.getUserString(SELECTED_SEMESTER_CLASS_KEY).first(),
        forceRefresh = false,
    )

    private suspend fun selectedSemesterEndDate(): LocalDate? =
        preferencesStore.getUserString("semester_end_date", "").first()
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private companion object {
        val DAY_ORDER = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val DATE_LABEL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
    }
}
