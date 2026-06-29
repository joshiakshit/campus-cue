package com.campuscue.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campuscue.data.repository.AttendanceRepository
import com.campuscue.data.repository.AuthRepository
import com.campuscue.data.repository.DownloadedUpdate
import com.campuscue.data.repository.InstallLaunchResult
import com.campuscue.data.repository.SELECTED_SEMESTER_CLASS_KEY
import com.campuscue.data.repository.SELECTED_SEMESTER_YEAR_KEY
import com.campuscue.data.repository.TimetableRepository
import com.campuscue.data.repository.UpdateCheckResult
import com.campuscue.data.repository.UpdateInfo
import com.campuscue.data.repository.UpdateRepository
import com.campuscue.domain.model.SemesterOption
import com.campuscue.domain.usecase.AttendanceUseCase
import com.campuscue.domain.usecase.ForecastUseCase
import com.campuscue.domain.usecase.TimetableUseCase
import com.joshi.core.storage.PreferencesStore
import com.joshi.core.ui.theme.ColorProfiles
import com.joshi.core.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val userName: String = "",
    val admno: String = "",
    val themeMode: ThemeMode = ThemeMode.DARK,
    val colorProfile: String = ColorProfiles.Default.name,
    val biometricEnabled: Boolean = false,
    val classReminders: Boolean = false,
    val compactNavBar: Boolean = false,
    val defaultPage: String = "dashboard",
    val threshold: Int = 75,
    val semesterEndDate: String = "",
    val selectedSemester: SemesterOption? = null,
    val semesterOptions: List<SemesterOption> = emptyList(),
    val semesterError: String? = null,
    val combinedAttendance: Boolean = false,
    val isClearing: Boolean = false,
    val updateState: SettingsUpdateState = SettingsUpdateState.Idle,
) {
    companion object {
        val validPages = listOf("dashboard", "attendance", "timetable", "planner")
    }
}

sealed interface SettingsUpdateState {
    data object Idle : SettingsUpdateState

    data object Checking : SettingsUpdateState

    data object NoUpdate : SettingsUpdateState

    data class Available(val info: UpdateInfo) : SettingsUpdateState

    data class Downloading(val info: UpdateInfo) : SettingsUpdateState

    data class PermissionRequired(val downloaded: DownloadedUpdate) : SettingsUpdateState

    data class InstallerOpened(val info: UpdateInfo) : SettingsUpdateState

    data class Error(val message: String) : SettingsUpdateState
}

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val preferencesStore: PreferencesStore,
        private val authRepository: AuthRepository,
        private val attendanceRepo: AttendanceRepository,
        private val timetableRepo: TimetableRepository,
        private val attendanceUseCase: AttendanceUseCase,
        private val forecastUseCase: ForecastUseCase,
        private val timetableUseCase: TimetableUseCase,
        private val updateRepository: UpdateRepository,
        @ApplicationContext private val appContext: Context,
    ) : ViewModel() {
        private val exportManager =
            SettingsExportManager(
                preferencesStore = preferencesStore,
                authRepository = authRepository,
                attendanceRepo = attendanceRepo,
                timetableRepo = timetableRepo,
                attendanceUseCase = attendanceUseCase,
                forecastUseCase = forecastUseCase,
                timetableUseCase = timetableUseCase,
            )

        private val _state = MutableStateFlow(SettingsUiState())
        val state: StateFlow<SettingsUiState> = _state.asStateFlow()

        init {
            loadSettings()
        }

        private fun loadSettings() {
            viewModelScope.launch {
                val user = authRepository.getUserInfo()
                val themeStr = preferencesStore.getString("theme_mode", ThemeMode.DARK.name).first()
                val profile = preferencesStore.getString("color_profile", ColorProfiles.Default.name).first()
                val biometric = preferencesStore.getBoolean("biometric_lock_enabled").first()
                val reminders = preferencesStore.getBoolean("class_reminders").first()
                val compactNavBar = preferencesStore.getBoolean("compact_nav_bar").first()
                val defaultPage = preferencesStore.getString("default_page", "dashboard").first()
                val threshold = preferencesStore.getUserInt("attendance_threshold", 75).first()
                val semesterEnd = preferencesStore.getUserString("semester_end_date", "").first()
                val combinedAttendance = preferencesStore.getUserBoolean("combined_attendance").first()
                val selectedYearId = preferencesStore.getUserString(SELECTED_SEMESTER_YEAR_KEY).first()
                val selectedClassId = preferencesStore.getUserString(SELECTED_SEMESTER_CLASS_KEY).first()
                val semestersResult =
                    runCatching {
                        user?.let {
                            attendanceRepo.getSemesterOptions(it.admno, it.brId, false)
                        }.orEmpty()
                    }
                val semesters = semestersResult.getOrDefault(emptyList())
                val selectedSemester =
                    semesters.firstOrNull { it.yearId == selectedYearId && it.classId == selectedClassId }
                        ?: semesters.firstOrNull()

                _state.update {
                    it.copy(
                        userName = user?.name ?: "",
                        admno = user?.admno ?: "",
                        themeMode = ThemeMode.entries.find { m -> m.name == themeStr } ?: ThemeMode.DARK,
                        colorProfile = profile,
                        biometricEnabled = biometric,
                        classReminders = reminders,
                        compactNavBar = compactNavBar,
                        defaultPage = defaultPage.takeIf { route -> route in SettingsUiState.validPages } ?: "dashboard",
                        threshold = threshold,
                        semesterEndDate = semesterEnd,
                        selectedSemester = selectedSemester,
                        semesterOptions = semesters,
                        semesterError = semestersResult.exceptionOrNull()?.message,
                        combinedAttendance = combinedAttendance,
                    )
                }
            }
        }

        fun setThemeMode(mode: ThemeMode) {
            viewModelScope.launch {
                preferencesStore.putString("theme_mode", mode.name)
                _state.update { it.copy(themeMode = mode) }
            }
        }

        fun setColorProfile(name: String) {
            viewModelScope.launch {
                preferencesStore.putString("color_profile", name)
                _state.update { it.copy(colorProfile = name) }
            }
        }

        fun setBiometricEnabled(enabled: Boolean) {
            viewModelScope.launch {
                preferencesStore.putBoolean("biometric_lock_enabled", enabled)
                _state.update { it.copy(biometricEnabled = enabled) }
            }
        }

        fun setClassReminders(enabled: Boolean) {
            viewModelScope.launch {
                preferencesStore.putBoolean("class_reminders", enabled)
                _state.update { it.copy(classReminders = enabled) }
            }
        }

        fun setCompactNavBar(enabled: Boolean) {
            viewModelScope.launch {
                preferencesStore.putBoolean("compact_nav_bar", enabled)
                _state.update { it.copy(compactNavBar = enabled) }
            }
        }

        fun setDefaultPage(route: String) {
            if (route !in SettingsUiState.validPages) return
            viewModelScope.launch {
                preferencesStore.putString("default_page", route)
                _state.update { it.copy(defaultPage = route) }
            }
        }

        fun setThreshold(value: Int) {
            viewModelScope.launch {
                val clamped = value.coerceIn(50, 95)
                preferencesStore.putUserInt("attendance_threshold", clamped)
                _state.update { it.copy(threshold = clamped) }
            }
        }

        fun setSemesterEndDate(date: String) {
            viewModelScope.launch {
                preferencesStore.putUserString("semester_end_date", date)
                _state.update { it.copy(semesterEndDate = date) }
            }
        }

        fun setSelectedSemester(option: SemesterOption) {
            viewModelScope.launch {
                preferencesStore.putUserString(SELECTED_SEMESTER_YEAR_KEY, option.yearId)
                preferencesStore.putUserString(SELECTED_SEMESTER_CLASS_KEY, option.classId)
                _state.update { it.copy(selectedSemester = option) }
            }
        }

        fun setCombinedAttendance(enabled: Boolean) {
            viewModelScope.launch {
                preferencesStore.putUserBoolean("combined_attendance", enabled)
                _state.update { it.copy(combinedAttendance = enabled) }
            }
        }

        fun clearCache() {
            viewModelScope.launch {
                _state.update { it.copy(isClearing = true) }
                try {
                    attendanceRepo.clearCache()
                    timetableRepo.clearCache()
                    clearGeneratedFiles()
                } finally {
                    _state.update { it.copy(isClearing = false) }
                }
            }
        }

        fun logout(onLoggedOut: () -> Unit) {
            viewModelScope.launch {
                attendanceRepo.clearCache()
                timetableRepo.clearCache()
                clearGeneratedFiles()
                preferencesStore.clearUserScoped()
                authRepository.logout()
                onLoggedOut()
            }
        }

        private fun clearGeneratedFiles() {
            listOf("exports", "updates").forEach { child ->
                appContext.cacheDir.resolve(child).deleteRecursively()
            }
        }

        fun checkForUpdates() {
            viewModelScope.launch {
                _state.update { it.copy(updateState = SettingsUpdateState.Checking) }
                runCatching { updateRepository.checkForUpdate() }
                    .onSuccess { result ->
                        _state.update {
                            it.copy(
                                updateState =
                                    when (result) {
                                        UpdateCheckResult.NoUpdate -> SettingsUpdateState.NoUpdate
                                        is UpdateCheckResult.Available -> SettingsUpdateState.Available(result.info)
                                    },
                            )
                        }
                    }
                    .onFailure { e ->
                        _state.update { it.copy(updateState = SettingsUpdateState.Error(e.message ?: "Update check failed")) }
                    }
            }
        }

        fun downloadAndInstallUpdate(info: UpdateInfo) {
            viewModelScope.launch {
                _state.update { it.copy(updateState = SettingsUpdateState.Downloading(info)) }
                runCatching { updateRepository.downloadUpdate(info) }
                    .onSuccess(::launchDownloadedUpdate)
                    .onFailure { e ->
                        _state.update { it.copy(updateState = SettingsUpdateState.Error(e.message ?: "Update download failed")) }
                    }
            }
        }

        fun installDownloadedUpdate(downloaded: DownloadedUpdate) {
            launchDownloadedUpdate(downloaded)
        }

        private fun launchDownloadedUpdate(downloaded: DownloadedUpdate) {
            runCatching { updateRepository.launchInstall(downloaded) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            updateState =
                                when (result) {
                                    InstallLaunchResult.INSTALLER_OPENED -> SettingsUpdateState.InstallerOpened(downloaded.info)
                                    InstallLaunchResult.PERMISSION_SETTINGS_OPENED -> SettingsUpdateState.PermissionRequired(downloaded)
                                },
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(updateState = SettingsUpdateState.Error(e.message ?: "Could not open installer")) }
                }
        }

        fun exportAttendance(context: Context) {
            viewModelScope.launch {
                exportManager.exportAttendance(context)
            }
        }

        fun exportTimetable(context: Context) {
            viewModelScope.launch {
                exportManager.exportTimetable(context)
            }
        }
    }
