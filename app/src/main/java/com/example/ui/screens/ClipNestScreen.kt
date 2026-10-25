package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.example.data.model.ClipItem
import com.example.data.preferences.PreferenceManager
import com.example.data.preferences.AppLockPreferences
import com.example.ui.components.AddEditClipDialog
import com.example.ui.components.AppLockScreen
import com.example.ui.components.ClipCard
import com.example.ui.components.ClipNestTopBar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ExportHistoryDialog
import com.example.ui.components.InAppUpdateDialog
import com.example.ui.components.PrivacyBanner
import com.example.ui.components.RetentionSettingsDialog
import com.example.ui.components.SearchBarWithFilters
import com.example.ui.theme.StarGold
import com.example.ui.viewmodel.ClipFilterCategory
import com.example.ui.viewmodel.ClipViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ClipNestScreen(
    viewModel: ClipViewModel,
    modifier: Modifier = Modifier
) {
    val clips by viewModel.clips.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val retentionDays by viewModel.retentionDays.collectAsStateWithLifecycle()
    val autoCaptureEnabled by viewModel.autoCaptureEnabled.collectAsStateWithLifecycle()
    val isPrivacyBannerDismissed by viewModel.isPrivacyBannerDismissed.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = context as? Activity
    val appLockPreferences = remember { AppLockPreferences(context) }
    var isAppLocked by remember { mutableStateOf(appLockPreferences.enabled) }
    var lastUnlockAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var appLockEnabledState by remember { mutableStateOf(appLockPreferences.enabled) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var isActivityResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog state
    var showAddDialog by remember { mutableStateOf(false) }
    var editingClip by remember { mutableStateOf<ClipItem?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    // Lifecycle observer to auto-capture clipboard whenever user switches back to the app
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isActivityResumed = true
                    viewModel.onResumeCheck()
                    if (appLockPreferences.enabled) {
                        val timedOut = appLockPreferences.policy == AppLockPreferences.Policy.AFTER_TIMEOUT &&
                            System.currentTimeMillis() - lastUnlockAt >= appLockPreferences.timeoutMinutes * 60_000L
                        if (appLockPreferences.policy == AppLockPreferences.Policy.IMMEDIATELY || timedOut) isAppLocked = true
                    }
                }
                Lifecycle.Event.ON_PAUSE -> isActivityResumed = false
                Lifecycle.Event.ON_STOP -> if (appLockPreferences.enabled && appLockPreferences.policy == AppLockPreferences.Policy.ON_APP_CLOSE) isAppLocked = true
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF &&
                    appLockPreferences.enabled &&
                    appLockPreferences.policy == AppLockPreferences.Policy.ON_SCREEN_OFF
                ) {
                    isAppLocked = true
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Snackbars
    LaunchedEffect(Unit) {
        viewModel.eventFlow.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    if (isAppLocked && appLockPreferences.enabled) {
        AppLockScreen(lockPreferences = appLockPreferences, isActivityResumed = isActivityResumed) {
            isAppLocked = false
            lastUnlockAt = System.currentTimeMillis()
        }
        return
    }

    Scaffold(
        topBar = {
            ClipNestTopBar(
                totalClips = stats.totalCount,
                retentionDays = retentionDays,
                onSyncClipboard = { viewModel.syncClipboard(silent = false) },
                onOpenSettings = { showSettingsDialog = true },
                onExportHistory = { showExportDialog = true },
                modifier = Modifier.testTag("clipnest_top_bar")
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                },
                text = { Text(stringResource(R.string.new_clip), fontWeight = FontWeight.SemiBold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_clip_fab")
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Android Privacy explainer banner (dismissible)
            PrivacyBanner(
                isVisible = !isPrivacyBannerDismissed,
                onDismiss = { viewModel.dismissPrivacyBanner() }
            )

            // Search Bar & Filter Chips
            SearchBarWithFilters(
                searchQuery = searchQuery,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                selectedFilter = selectedFilter,
                onFilterSelect = { viewModel.setFilterCategory(it) },
                currentSort = sortOption,
                onSortSelect = { viewModel.setSortOption(it) }
            )

            // Result summary indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (searchQuery.isNotBlank() || selectedFilter != ClipFilterCategory.ALL) {
                        stringResource(R.string.clips_matching_count, clips.size, stats.totalCount)
                    } else {
                        stringResource(R.string.clips_all_count, clips.size)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (stats.pinnedCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = StarGold,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = stringResource(R.string.clips_starred_count, stats.pinnedCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = StarGold,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Clips List or Empty State
            if (clips.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateView(
                        hasFilterActive = searchQuery.isNotBlank() || selectedFilter != ClipFilterCategory.ALL,
                        onResetFilter = {
                            viewModel.setSearchQuery("")
                            viewModel.setFilterCategory(ClipFilterCategory.ALL)
                        },
                        onSyncClipboard = { viewModel.syncClipboard(silent = false) },
                        onAddClip = { showAddDialog = true }
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 88.dp // Avoid FAB overlap
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("clips_lazy_column")
                ) {
                    items(
                        items = clips,
                        key = { it.id }
                    ) { clip ->
                        ClipCard(
                            clip = clip,
                            retentionDays = retentionDays,
                            onCopy = { viewModel.copyClipToClipboard(clip) },
                            onTogglePin = { viewModel.togglePin(clip) },
                            onEdit = { editingClip = clip },
                            onDelete = { viewModel.deleteClip(clip) },
                            modifier = Modifier.widthIn(max = 680.dp)
                        )
                    }
                }
            }
        }
    }

    // Add Clip Dialog
    if (showAddDialog) {
        AddEditClipDialog(
            initialClip = null,
            onDismiss = { showAddDialog = false },
            onSave = { content, label ->
                showAddDialog = false
                viewModel.addManualClip(content, label)
            }
        )
    }

    // Edit Clip Dialog
    if (editingClip != null) {
        AddEditClipDialog(
            initialClip = editingClip,
            onDismiss = { editingClip = null },
            onSave = { content, label ->
                val id = editingClip?.id
                editingClip = null
                if (id != null) {
                    viewModel.updateClip(id, content, label)
                }
            }
        )
    }

    // Retention Settings Dialog
    if (showSettingsDialog) {
        RetentionSettingsDialog(
            currentRetentionDays = retentionDays,
            isAutoCaptureEnabled = autoCaptureEnabled,
            currentThemeMode = themeMode,
            isDynamicColorEnabled = dynamicColor,
            appLockEnabled = appLockEnabledState,
            appLockMethod = appLockPreferences.method,
            appLockPolicy = appLockPreferences.policy,
            appLockTimeoutMinutes = appLockPreferences.timeoutMinutes,
            hasAppLockPin = appLockPreferences.hasPin,
            onSaveAppLock = { enabled, method, policy, timeout, pin ->
                appLockPreferences.enabled = enabled
                appLockPreferences.method = method
                appLockPreferences.policy = policy
                appLockPreferences.timeoutMinutes = timeout
                if (pin != null) appLockPreferences.setPin(pin)
                appLockEnabledState = enabled
                if (!enabled) isAppLocked = false
            },
            onDismiss = { showSettingsDialog = false },
            onSaveRetention = { days ->
                viewModel.updateRetentionDays(days)
            },
            onToggleAutoCapture = { enabled ->
                viewModel.setAutoCapture(enabled)
            },
            onSaveThemeMode = { mode ->
                viewModel.setThemeMode(mode)
            },
            onToggleDynamicColor = { enabled ->
                viewModel.setDynamicColor(enabled)
            },
            onCleanNow = {
                viewModel.cleanExpiredNow()
            },
            onClearHistory = { keepPinned ->
                viewModel.clearHistory(keepPinned)
            },
            onCheckForUpdates = {
                viewModel.checkForUpdates(isManual = true)
            }
        )
    }

    // Export History Dialog
    if (showExportDialog) {
        ExportHistoryDialog(
            clips = clips,
            onDismiss = { showExportDialog = false }
        )
    }

    // In-App Update Dialog
    InAppUpdateDialog(
        updateState = updateState,
        onDismiss = { viewModel.dismissUpdateDialog() },
        onStartDownload = { info -> viewModel.startUpdateDownload(info) },
        onCancelDownload = { viewModel.cancelUpdateDownload() },
        onInstall = { apkFile ->
            if (activity != null) {
                viewModel.installDownloadedUpdate(activity, apkFile)
            }
        },
        onRetryDownload = { info -> viewModel.startUpdateDownload(info) }
    )
}
