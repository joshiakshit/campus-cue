package com.campuscue.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.campuscue.data.repository.DownloadedUpdate
import com.campuscue.data.repository.InstallLaunchResult
import com.campuscue.data.repository.UpdateCheckResult
import com.campuscue.data.repository.UpdateInfo
import com.campuscue.data.repository.UpdateRepository
import kotlinx.coroutines.launch

@Suppress("LongMethod")
@Composable
internal fun AppUpdateGate(updateRepository: UpdateRepository) {
    val scope = rememberCoroutineScope()
    var updatePrompt by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloadedUpdate by remember { mutableStateOf<DownloadedUpdate?>(null) }
    var updateBusy by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { updateRepository.checkForUpdate() }
            .onSuccess { result ->
                if (result is UpdateCheckResult.Available) {
                    val info = result.info
                    if (info.isRequired) {
                        updatePrompt = info
                    } else {
                        val lastNotified = updateRepository.getLastNotifiedVersionCode()
                        if (info.latestVersionCode > lastNotified) {
                            updatePrompt = info
                        }
                    }
                }
            }
    }

    updatePrompt?.let { info ->
        AppUpdateDialog(
            info = info,
            busy = updateBusy,
            message = updateMessage,
            onInstall = {
                scope.launch {
                    updateBusy = true
                    updateMessage = null
                    runCatching {
                        val downloaded =
                            downloadedUpdate
                                ?: updateRepository.downloadUpdate(info).also { downloadedUpdate = it }
                        updateRepository.launchInstall(downloaded)
                    }.onSuccess { result ->
                        updateMessage =
                            when (result) {
                                InstallLaunchResult.INSTALLER_OPENED ->
                                    "Android installer opened. Confirm the update to finish."
                                InstallLaunchResult.PERMISSION_SETTINGS_OPENED ->
                                    "Allow CampusCue to install APKs, then return and tap Install again."
                            }
                    }.onFailure { e ->
                        updateMessage = e.message ?: "Could not install update"
                    }
                    updateBusy = false
                }
            },
            onDismiss = {
                if (!info.isRequired) {
                    scope.launch { updateRepository.setLastNotifiedVersionCode(info.latestVersionCode) }
                    updatePrompt = null
                    downloadedUpdate = null
                    updateMessage = null
                }
            },
        )
    }
}

@Composable
private fun AppUpdateDialog(
    info: UpdateInfo,
    busy: Boolean,
    message: String?,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onInstall, enabled = !busy) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (busy) "Preparing..." else "Install update")
            }
        },
        dismissButton = {
            if (!info.isRequired) {
                TextButton(onClick = onDismiss, enabled = !busy) {
                    Text("Later")
                }
            }
        },
        title = {
            Text(if (info.isRequired) "Critical update required" else "Update available")
        },
        text = {
            Column {
                Text("Version ${info.latestVersionName} is ready.")
                if (!info.releaseNotes.isNullOrBlank()) {
                    Spacer(Modifier.size(8.dp))
                    Text(info.releaseNotes, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (message != null) {
                    Spacer(Modifier.size(8.dp))
                    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )
}
