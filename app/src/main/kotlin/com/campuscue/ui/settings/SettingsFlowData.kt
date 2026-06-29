package com.campuscue.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joshi.core.ui.theme.AppShapes
import com.joshi.core.ui.theme.cardColor

private val navRouteOptions =
    listOf(
        "dashboard" to "Dashboard",
        "attendance" to "Attendance",
        "timetable" to "Timetable",
        "planner" to "Planner",
    )

@Composable
internal fun HomePagePicker(
    defaultPage: String,
    onSelect: (String) -> Unit,
) {
    SettingsCard {
        Text("Home Page", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            navRouteOptions.forEach { (route, label) ->
                val selected = defaultPage == route
                Surface(
                    modifier = Modifier.weight(1f),
                    selected = selected,
                    onClick = { onSelect(route) },
                    shape = AppShapes.small,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            cardColor()
                        },
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color =
                            if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ExportActions(
    context: Context,
    viewModel: SettingsViewModel,
) {
    SettingsCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.exportAttendance(context) },
                modifier = Modifier.weight(1f),
                shape = AppShapes.small,
            ) {
                Text("Attendance", fontSize = 13.sp)
            }
            Button(
                onClick = { viewModel.exportTimetable(context) },
                modifier = Modifier.weight(1f),
                shape = AppShapes.small,
            ) {
                Text("Timetable", fontSize = 13.sp)
            }
        }
    }
}

@Composable
internal fun SecuritySettings(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    SettingsCard {
        ToggleRow("Biometric Lock", state.biometricEnabled, viewModel::setBiometricEnabled)
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
        )
        ToggleRow("Class Reminders", state.classReminders, viewModel::setClassReminders)
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Clear Cache", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            TextButton(onClick = viewModel::clearCache, enabled = !state.isClearing) {
                Text(if (state.isClearing) "Clearing..." else "Clear", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
