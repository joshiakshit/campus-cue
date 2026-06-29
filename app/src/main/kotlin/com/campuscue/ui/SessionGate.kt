package com.campuscue.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campuscue.data.repository.AccessStatus
import com.campuscue.data.repository.AuthRepository
import com.campuscue.data.repository.UpdateRepository
import com.campuscue.ui.auth.AccessCheckingScreen
import com.campuscue.ui.auth.AccessWaitingScreen
import com.campuscue.ui.auth.BannedScreen
import com.campuscue.ui.auth.BiometricGateWrapper
import com.campuscue.ui.auth.ForceReauthScreen
import com.campuscue.ui.auth.LoginScreen
import com.joshi.core.security.BiometricManager
import com.joshi.core.storage.PreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Suppress("TopLevelPropertyNaming")
internal const val ACCESS_APPROVED_CACHE_KEY = "access_approved_cached"

internal enum class SessionState {
    LOGGED_OUT,
    CHECKING_ACCESS,
    APPROVED,
    PENDING,
    FORCE_REAUTH,
    BANNED,
}

@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
@Composable
internal fun SessionGate(
    initialSessionState: SessionState,
    authRepository: AuthRepository,
    updateRepository: UpdateRepository,
    biometricManager: BiometricManager,
    preferencesStore: PreferencesStore,
    qrScanRequest: Int,
    activity: FragmentActivity,
) {
    var sessionState by rememberSaveable { mutableStateOf(initialSessionState) }
    var accessError by rememberSaveable { mutableStateOf<String?>(null) }
    var accessRefreshKey by rememberSaveable { mutableStateOf(0) }
    var specialAccessBusy by rememberSaveable { mutableStateOf(false) }
    var specialAccessMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val authScope = rememberCoroutineScope()

    suspend fun checkApprovedSessionIfDue() {
        val now = System.currentTimeMillis()
        val lastCheck =
            preferencesStore.getUserString("last_access_recheck_ms")
                .first().toLongOrNull() ?: 0L
        val oneDayMs = 24L * 60L * 60L * 1000L
        if (now - lastCheck < oneDayMs) return

        preferencesStore.putUserString("last_access_recheck_ms", now.toString())
        runCatching { updateRepository.isForceReauthActive() }
            .onSuccess { forced ->
                if (forced) {
                    preferencesStore.putUserBoolean(ACCESS_APPROVED_CACHE_KEY, false)
                    sessionState = SessionState.FORCE_REAUTH
                    return
                }
            }

        runCatching { authRepository.checkAccessStatusResult() }
            .onSuccess { result ->
                if (result.banned) {
                    preferencesStore.putUserBoolean(ACCESS_APPROVED_CACHE_KEY, false)
                    sessionState = SessionState.BANNED
                    return
                }
                if (result.requiresReauth) {
                    preferencesStore.putUserBoolean(ACCESS_APPROVED_CACHE_KEY, false)
                    sessionState = SessionState.FORCE_REAUTH
                    return
                }
                preferencesStore.putUserBoolean(
                    ACCESS_APPROVED_CACHE_KEY,
                    result.status == AccessStatus.APPROVED,
                )
                if (result.status == AccessStatus.PENDING) {
                    sessionState = SessionState.PENDING
                }
            }
    }

    LaunchedEffect(sessionState, accessRefreshKey) {
        if (sessionState == SessionState.APPROVED) {
            checkApprovedSessionIfDue()
        }

        if (sessionState == SessionState.CHECKING_ACCESS) {
            accessError = null
            val forceReauth =
                runCatching { updateRepository.isForceReauthActive() }
                    .getOrDefault(true)
            if (forceReauth) {
                preferencesStore.putUserBoolean(ACCESS_APPROVED_CACHE_KEY, false)
                sessionState = SessionState.FORCE_REAUTH
                return@LaunchedEffect
            }
            runCatching { authRepository.checkAccessStatusResult() }
                .onSuccess { result ->
                    if (result.banned) {
                        preferencesStore.putUserBoolean(ACCESS_APPROVED_CACHE_KEY, false)
                        sessionState = SessionState.BANNED
                        return@LaunchedEffect
                    }
                    if (result.requiresReauth) {
                        preferencesStore.putUserBoolean(ACCESS_APPROVED_CACHE_KEY, false)
                        sessionState = SessionState.FORCE_REAUTH
                        return@LaunchedEffect
                    }
                    preferencesStore.putUserBoolean(
                        ACCESS_APPROVED_CACHE_KEY,
                        result.status == AccessStatus.APPROVED,
                    )
                    sessionState =
                        when (result.status) {
                            AccessStatus.APPROVED -> SessionState.APPROVED
                            AccessStatus.PENDING -> SessionState.PENDING
                        }
                }
                .onFailure { e ->
                    if (sessionState == SessionState.CHECKING_ACCESS) {
                        accessError = e.message ?: "Could not check approval status"
                        sessionState = SessionState.PENDING
                    }
                }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START && sessionState == SessionState.APPROVED) {
                    authScope.launch {
                        checkApprovedSessionIfDue()
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (sessionState) {
        SessionState.APPROVED -> {
            val biometricEnabled by preferencesStore
                .getBoolean("biometric_lock_enabled")
                .collectAsStateWithLifecycle(initialValue = false)

            BiometricGateWrapper(
                enabled = biometricEnabled,
                onAuthenticate = { onSuccess ->
                    biometricManager.authenticate(
                        activity = activity,
                        title = "Unlock CampusCue",
                        subtitle = "Verify your identity to continue",
                        onSuccess = onSuccess,
                        onError = {},
                    )
                },
            ) {
                MainApp(
                    preferencesStore = preferencesStore,
                    authRepository = authRepository,
                    qrScanRequest = qrScanRequest,
                    onLogout = {
                        authScope.launch { preferencesStore.putUserBoolean(ACCESS_APPROVED_CACHE_KEY, false) }
                        sessionState = SessionState.LOGGED_OUT
                    },
                )
            }
        }
        SessionState.PENDING -> {
            AccessWaitingScreen(
                isRefreshing = false,
                isRequestingSpecialAccess = specialAccessBusy,
                error = accessError,
                specialAccessMessage = specialAccessMessage,
                onRefresh = {
                    accessRefreshKey += 1
                    specialAccessMessage = null
                    sessionState = SessionState.CHECKING_ACCESS
                },
                onRequestSpecialAccess = { referralName ->
                    specialAccessBusy = true
                    specialAccessMessage = null
                    accessError = null
                    authScope.launch {
                        runCatching { authRepository.requestSpecialAccess(referralName) }
                            .onSuccess { msg ->
                                specialAccessMessage = msg
                            }
                            .onFailure { e ->
                                accessError = e.message ?: "Could not request special access"
                            }
                        specialAccessBusy = false
                    }
                },
                onSignOut = {
                    authScope.launch { preferencesStore.putUserBoolean(ACCESS_APPROVED_CACHE_KEY, false) }
                    authRepository.logout()
                    sessionState = SessionState.LOGGED_OUT
                },
            )
        }
        SessionState.FORCE_REAUTH -> {
            ForceReauthScreen(
                onSignOut = {
                    authScope.launch { preferencesStore.putUserBoolean(ACCESS_APPROVED_CACHE_KEY, false) }
                    authRepository.logout()
                    sessionState = SessionState.LOGGED_OUT
                },
            )
        }
        SessionState.BANNED -> BannedScreen()
        SessionState.CHECKING_ACCESS -> AccessCheckingScreen()
        SessionState.LOGGED_OUT -> {
            LoginScreen(
                onLoginSuccess = {
                    authScope.launch {
                        preferencesStore.putUserBoolean(ACCESS_APPROVED_CACHE_KEY, true)
                    }
                    sessionState = SessionState.APPROVED
                },
                onAccessPending = {
                    authScope.launch {
                        preferencesStore.putUserBoolean(ACCESS_APPROVED_CACHE_KEY, false)
                    }
                    sessionState = SessionState.PENDING
                },
            )
        }
    }
}
