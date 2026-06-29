package com.campuscue.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campuscue.BuildConfig
import com.campuscue.data.repository.UpdateInfo
import com.joshi.core.ui.theme.AppShapes

@Composable
internal fun UpdateSettings(
    updateState: SettingsUpdateState,
    viewModel: SettingsViewModel,
) {
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Check for updates", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Current: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = viewModel::checkForUpdates,
                enabled = updateState !is SettingsUpdateState.Checking && updateState !is SettingsUpdateState.Downloading,
            ) {
                Text("Check")
            }
        }
        UpdateStateContent(updateState, viewModel)
    }
}

@Composable
private fun UpdateStateContent(
    updateState: SettingsUpdateState,
    viewModel: SettingsViewModel,
) {
    when (updateState) {
        SettingsUpdateState.Idle -> Unit
        SettingsUpdateState.Checking -> UpdateProgressText("Checking for updates...")
        SettingsUpdateState.NoUpdate -> UpdateMessage("You're up to date.")
        is SettingsUpdateState.Available -> UpdateAvailableContent(updateState.info, viewModel)
        is SettingsUpdateState.Downloading -> UpdateProgressText("Downloading ${updateState.info.latestVersionName}...")
        is SettingsUpdateState.PermissionRequired -> {
            UpdateMessage("Install permission opened. Return here after allowing CampusCue to install APKs.")
            Button(
                onClick = { viewModel.installDownloadedUpdate(updateState.downloaded) },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.small,
            ) {
                Text("Open installer")
            }
        }
        is SettingsUpdateState.InstallerOpened -> UpdateMessage("Android installer opened. Confirm the update to finish.")
        is SettingsUpdateState.Error -> UpdateMessage(updateState.message, isError = true)
    }
}

@Composable
private fun UpdateAvailableContent(
    info: UpdateInfo,
    viewModel: SettingsViewModel,
) {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 12.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
    )
    Text(
        if (info.isRequired) "Critical update available" else "Update available",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = if (info.isRequired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
    Text(
        "Version ${info.latestVersionName}${info.sizeBytes?.let { " • ${formatBytes(it)}" }.orEmpty()}",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (!info.releaseNotes.isNullOrBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(info.releaseNotes, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = { viewModel.downloadAndInstallUpdate(info) },
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.small,
    ) {
        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Download & install")
    }
}

@Composable
private fun UpdateProgressText(text: String) {
    Row(
        modifier = Modifier.padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UpdateMessage(
    text: String,
    isError: Boolean = false,
) {
    Text(
        text,
        modifier = Modifier.padding(top = 12.dp),
        fontSize = 12.sp,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(java.util.Locale.US, "%.1f MB", mb)
}
