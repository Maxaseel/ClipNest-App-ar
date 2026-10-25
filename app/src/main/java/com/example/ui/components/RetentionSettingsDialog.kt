package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.AppLockPreferences
import com.example.data.preferences.PreferenceManager
import com.example.ui.theme.StarGold

@Composable
fun RetentionSettingsDialog(
    currentRetentionDays: Int,
    isAutoCaptureEnabled: Boolean,
    currentThemeMode: AppThemeMode,
    isDynamicColorEnabled: Boolean,
    appLockEnabled: Boolean,
    appLockMethod: AppLockPreferences.Method,
    appLockPolicy: AppLockPreferences.Policy,
    appLockTimeoutMinutes: Int,
    hasAppLockPin: Boolean,
    onSaveAppLock: (Boolean, AppLockPreferences.Method, AppLockPreferences.Policy, Int, String?) -> Unit,
    onDismiss: () -> Unit,
    onSaveRetention: (days: Int) -> Unit,
    onToggleAutoCapture: (Boolean) -> Unit,
    onSaveThemeMode: (AppThemeMode) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onCleanNow: () -> Unit,
    onClearHistory: (keepPinned: Boolean) -> Unit,
    onCheckForUpdates: () -> Unit
) {
    var selectedOption by remember {
        mutableStateOf(
            when (currentRetentionDays) {
                PreferenceManager.RETENTION_1_DAY -> "1"
                PreferenceManager.RETENTION_7_DAYS -> "7"
                PreferenceManager.RETENTION_30_DAYS -> "30"
                PreferenceManager.RETENTION_NEVER -> "never"
                else -> "custom"
            }
        )
    }

    var customDaysText by remember {
        mutableStateOf(
            if (currentRetentionDays > 0 &&
                currentRetentionDays !in listOf(1, 7, 30)
            ) {
                currentRetentionDays.toString()
            } else {
                "14"
            }
        )
    }

    var selectedTheme by remember { mutableStateOf(currentThemeMode) }
    var dynamicColorState by remember { mutableStateOf(isDynamicColorEnabled) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var lockEnabledState by remember { mutableStateOf(appLockEnabled) }
    var lockMethodState by remember { mutableStateOf(appLockMethod) }
    var lockPolicyState by remember { mutableStateOf(appLockPolicy) }
    var lockTimeoutText by remember { mutableStateOf(appLockTimeoutMinutes.toString()) }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "ClipNest Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Section 1: Theme & Appearance
                Text(
                    text = "Theme & Appearance",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Select your preferred visual style and color behavior.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Theme Mode: System default
                ThemeOptionRow(
                    label = "System default",
                    subtitle = "Automatically follows Android's dark or light theme",
                    icon = Icons.Default.SettingsBrightness,
                    selected = selectedTheme == AppThemeMode.SYSTEM,
                    onSelect = {
                        selectedTheme = AppThemeMode.SYSTEM
                        onSaveThemeMode(AppThemeMode.SYSTEM)
                    },
                    testTag = "theme_option_system"
                )

                // Theme Mode: Fixed Light
                ThemeOptionRow(
                    label = "Fixed Light",
                    subtitle = "Always clean and bright, unaffected by system theme",
                    icon = Icons.Default.LightMode,
                    selected = selectedTheme == AppThemeMode.LIGHT,
                    onSelect = {
                        selectedTheme = AppThemeMode.LIGHT
                        onSaveThemeMode(AppThemeMode.LIGHT)
                    },
                    testTag = "theme_option_light"
                )

                // Theme Mode: Fixed Dark
                ThemeOptionRow(
                    label = "Fixed Dark",
                    subtitle = "Always dark and battery-friendly, unaffected by system theme",
                    icon = Icons.Default.DarkMode,
                    selected = selectedTheme == AppThemeMode.DARK,
                    onSelect = {
                        selectedTheme = AppThemeMode.DARK
                        onSaveThemeMode(AppThemeMode.DARK)
                    },
                    testTag = "theme_option_dark"
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Dynamic Color Toggle (Material You)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newVal = !dynamicColorState
                            dynamicColorState = newVal
                            onToggleDynamicColor(newVal)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dynamic Color (Material You)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Derive colors from your system wallpaper (Android 12+)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = dynamicColorState,
                        onCheckedChange = {
                            dynamicColorState = it
                            onToggleDynamicColor(it)
                        },
                        modifier = Modifier.testTag("dynamic_color_switch")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(14.dp))

                // Section 2: Clipboard Retention Policy
                Text(
                    text = "Clip History Retention",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Automatically purge old clips after the selected duration to keep your storage fast and tidy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Option: 1 Day
                RetentionOptionRow(
                    label = "1 Day",
                    subtitle = "Aggressive privacy - purges items daily",
                    selected = selectedOption == "1",
                    onSelect = { selectedOption = "1" },
                    testTag = "retention_option_1_day"
                )

                // Option: 7 Days
                RetentionOptionRow(
                    label = "7 Days (Recommended)",
                    subtitle = "Balanced history window",
                    selected = selectedOption == "7",
                    onSelect = { selectedOption = "7" },
                    testTag = "retention_option_7_days"
                )

                // Option: 30 Days
                RetentionOptionRow(
                    label = "30 Days",
                    subtitle = "Generous archive window",
                    selected = selectedOption == "30",
                    onSelect = { selectedOption = "30" },
                    testTag = "retention_option_30_days"
                )

                // Option: Custom
                RetentionOptionRow(
                    label = "Custom duration",
                    subtitle = "Specify any custom day count",
                    selected = selectedOption == "custom",
                    onSelect = { selectedOption = "custom" },
                    testTag = "retention_option_custom"
                )

                if (selectedOption == "custom") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 36.dp, end = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customDaysText,
                            onValueChange = { input ->
                                if (input.all { it.isDigit() } && input.length <= 4) {
                                    customDaysText = input
                                }
                            },
                            label = { Text("Days") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .width(110.dp)
                                .testTag("custom_retention_days_input")
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "days until clip expires",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Option: Never Expire
                RetentionOptionRow(
                    label = "Never expire",
                    subtitle = "Keep all clips indefinitely until manually deleted",
                    selected = selectedOption == "never",
                    onSelect = { selectedOption = "never" },
                    testTag = "retention_option_never"
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Starred Protection Note
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = StarGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Starred items are always protected and will never be auto-deleted.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(14.dp))

                // Section 3: Security / App Lock
                Text(stringResource(R.string.security_app_lock), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.security_app_lock_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.security_enable_lock), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.security_enable_lock_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = lockEnabledState, onCheckedChange = { lockEnabledState = it }, modifier = Modifier.testTag("app_lock_switch"))
                }
                if (lockEnabledState) {
                    Text(stringResource(R.string.security_method), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(lockMethodState == AppLockPreferences.Method.BIOMETRIC, { lockMethodState = AppLockPreferences.Method.BIOMETRIC })
                        Text(stringResource(R.string.security_biometric))
                        Spacer(Modifier.width(12.dp))
                        RadioButton(lockMethodState == AppLockPreferences.Method.PIN, { lockMethodState = AppLockPreferences.Method.PIN })
                        Text(stringResource(R.string.security_pin))
                    }
                    Text(stringResource(R.string.security_policy), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    listOf(
                        AppLockPreferences.Policy.ON_APP_CLOSE to R.string.security_policy_close,
                        AppLockPreferences.Policy.ON_SCREEN_OFF to R.string.security_policy_screen,
                        AppLockPreferences.Policy.IMMEDIATELY to R.string.security_policy_leave,
                        AppLockPreferences.Policy.AFTER_TIMEOUT to R.string.security_policy_timeout
                    ).forEach { (policy, labelRes) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(lockPolicyState == policy, { lockPolicyState = policy })
                            Text(stringResource(labelRes))
                        }
                    }
                    if (lockPolicyState == AppLockPreferences.Policy.AFTER_TIMEOUT) {
                        OutlinedTextField(lockTimeoutText, { if (it.all(Char::isDigit) && it.length <= 3) lockTimeoutText = it }, label = { Text(stringResource(R.string.security_timeout_minutes)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.width(120.dp))
                    }
                    if (lockMethodState == AppLockPreferences.Method.PIN) {
                        Text(stringResource(R.string.security_set_pin), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                        OutlinedTextField(newPin, { newPin = it }, label = { Text(stringResource(R.string.security_pin_new)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(confirmPin, { confirmPin = it; pinError = false }, label = { Text(stringResource(R.string.security_pin_confirm)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true, modifier = Modifier.fillMaxWidth())
                        if (pinError) Text(stringResource(if (newPin.length < 4) R.string.security_pin_minimum else R.string.security_pin_mismatch), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(14.dp))
                // Section 4: Automatic Capture Settings
                Text(
                    text = "Automatic Capture",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleAutoCapture(!isAutoCaptureEnabled) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Automatic Clipboard Capture",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Inspect and save new copied items automatically whenever ClipNest gains focus or receives a clip update",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isAutoCaptureEnabled,
                        onCheckedChange = onToggleAutoCapture,
                        modifier = Modifier.testTag("auto_capture_switch")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(14.dp))

                // Section 4: Maintenance Actions
                Text(
                    text = "Maintenance Actions",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(10.dp))

                FilledTonalButton(
                    onClick = onCleanNow,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clean_expired_now_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Purge Expired Clips Now")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showClearConfirmDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clear_history_open_dialog")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Clip History...")
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(14.dp))

                // Section 5: App Updates
                Text(
                    text = "App Updates",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Check the official repository for new versions, security patches, and features.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                FilledTonalButton(
                    onClick = {
                        onDismiss()
                        onCheckForUpdates()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_check_updates_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Check for updates")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val days = when (selectedOption) {
                        "1" -> 1
                        "7" -> 7
                        "30" -> 30
                        "never" -> PreferenceManager.RETENTION_NEVER
                        else -> {
                            val parsed = customDaysText.toIntOrNull()
                            if (parsed != null && parsed > 0) parsed else 7
                        }
                    }
                    onSaveRetention(days)
                    onSaveThemeMode(selectedTheme)
                    onToggleDynamicColor(dynamicColorState)
                    val timeout = lockTimeoutText.toIntOrNull()?.coerceIn(1, 120) ?: 5
                    val hasValidNewPin = newPin.isNotBlank() && newPin == confirmPin && newPin.length >= 4
                    if (lockEnabledState && lockMethodState == AppLockPreferences.Method.PIN && !hasAppLockPin && !hasValidNewPin) {
                        pinError = true
                    } else {
                        val pin = if (hasValidNewPin) newPin else null
                        onSaveAppLock(lockEnabledState, lockMethodState, lockPolicyState, timeout, pin)
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("save_settings_button")
            ) {
                Text("Save Settings")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_settings_button")
            ) {
                Text("Close")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )

    // Clear confirmation sub-dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = "Clear Clip History",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Choose whether to preserve your starred clips or wipe all saved clipboard items completely."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        onClearHistory(true)
                    },
                    modifier = Modifier.testTag("clear_keep_starred_button")
                ) {
                    Text("Keep Starred (Safe)")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showClearConfirmDialog = false
                        onClearHistory(false)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("clear_everything_button")
                ) {
                    Text("Wipe Everything")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun ThemeOptionRow(
    label: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onSelect: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 5.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RetentionOptionRow(
    label: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 5.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
