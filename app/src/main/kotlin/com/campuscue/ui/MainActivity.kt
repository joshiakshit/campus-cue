package com.campuscue.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.campuscue.BuildConfig
import com.campuscue.data.repository.AuthRepository
import com.campuscue.data.repository.UpdateRepository
import com.joshi.core.security.BiometricManager
import com.joshi.core.storage.PreferencesStore
import com.joshi.core.ui.theme.AppTheme
import com.joshi.core.ui.theme.ColorProfiles
import com.joshi.core.ui.theme.ThemeMode
import com.joshi.core.ui.theme.ThemeState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var authRepository: AuthRepository

    @Inject lateinit var biometricManager: BiometricManager

    @Inject lateinit var preferencesStore: PreferencesStore

    @Inject lateinit var updateRepository: UpdateRepository

    private val qrScanRequests = MutableStateFlow(0)

    private data class StartupData(
        val themeMode: String,
        val colorProfile: String,
        val sessionState: SessionState,
    )

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_SCAN_QR) qrScanRequests.value += 1
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHighRefreshRate()
        if (intent?.action == ACTION_SCAN_QR) qrScanRequests.value += 1

        var startup by mutableStateOf<StartupData?>(null)
        splashScreen.setKeepOnScreenCondition { startup == null }
        lifecycleScope.launch {
            startup =
                withContext(Dispatchers.IO) {
                    StartupData(
                        themeMode = preferencesStore.getString("theme_mode", ThemeMode.DARK.name).first(),
                        colorProfile =
                            preferencesStore
                                .getString("color_profile", ColorProfiles.Default.name)
                                .first(),
                        sessionState = initialSessionState(),
                    )
                }
        }

        setContent {
            val startupData = startup ?: return@setContent
            val qrScanRequest by qrScanRequests.collectAsStateWithLifecycle()
            val themeModeStr by preferencesStore
                .getString("theme_mode", ThemeMode.DARK.name)
                .collectAsStateWithLifecycle(initialValue = startupData.themeMode)
            val colorProfile by preferencesStore
                .getString("color_profile", ColorProfiles.Default.name)
                .collectAsStateWithLifecycle(initialValue = startupData.colorProfile)

            val themeState =
                ThemeState(
                    mode = ThemeMode.entries.find { it.name == themeModeStr } ?: ThemeMode.DARK,
                    profileName = colorProfile,
                )

            AppTheme(themeState = themeState) {
                AppUpdateGate(updateRepository = updateRepository)
                SessionGate(
                    initialSessionState = startupData.sessionState,
                    authRepository = authRepository,
                    updateRepository = updateRepository,
                    biometricManager = biometricManager,
                    preferencesStore = preferencesStore,
                    qrScanRequest = qrScanRequest,
                    activity = this@MainActivity,
                )
            }
        }
    }

    private suspend fun initialSessionState(): SessionState {
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("force_pending", false) == true) {
            return SessionState.PENDING
        }
        if (!authRepository.isLoggedIn()) return SessionState.LOGGED_OUT
        val cachedApproved = preferencesStore.getUserBoolean(ACCESS_APPROVED_CACHE_KEY).first()
        return if (cachedApproved) SessionState.APPROVED else SessionState.CHECKING_ACCESS
    }

    @Suppress("DEPRECATION")
    private fun requestHighRefreshRate() {
        val display = display ?: window.windowManager.defaultDisplay ?: return
        val bestMode =
            display.supportedModes.maxByOrNull { it.refreshRate } ?: return
        window.attributes =
            window.attributes.apply { preferredDisplayModeId = bestMode.modeId }
    }

    companion object {
        const val ACTION_SCAN_QR = "com.campuscue.action.SCAN_QR"
    }
}
