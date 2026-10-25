package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.StringRes
import com.example.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.ClipItem
import com.example.data.model.ContentType
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.PreferenceManager
import com.example.data.repository.ClipRepository
import com.example.data.repository.SaveResult
import com.example.data.update.UpdateInfo
import com.example.data.update.UpdateManager
import com.example.data.update.UpdateUiState
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ClipFilterCategory(@StringRes val displayNameRes: Int) {
    ALL(R.string.filter_all),
    PINNED(R.string.filter_pinned),
    URLS(R.string.filter_urls),
    CODE(R.string.filter_code),
    COLORS(R.string.filter_colors),
    CONTACTS(R.string.filter_contacts),
    TEXT(R.string.filter_text)
}

enum class SortOption(@StringRes val displayNameRes: Int) {
    RECENT(R.string.sort_recent),
    OLDEST(R.string.sort_oldest),
    MOST_COPIED(R.string.sort_copied),
    ALPHABETICAL(R.string.sort_alphabetical)
}

data class ClipUiStats(
    val totalCount: Int = 0,
    val pinnedCount: Int = 0,
    val retentionDays: Int = PreferenceManager.RETENTION_7_DAYS,
    val lastCleanupTime: Long = 0L
)

class ClipViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ClipRepository
    private val clipboardManager: ClipboardManager =
        application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private val updateManager = UpdateManager(application)

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(ClipFilterCategory.ALL)
    val selectedFilter: StateFlow<ClipFilterCategory> = _selectedFilter.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.RECENT)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _retentionDays = MutableStateFlow(PreferenceManager.RETENTION_7_DAYS)
    val retentionDays: StateFlow<Int> = _retentionDays.asStateFlow()

    private val _autoCaptureEnabled = MutableStateFlow(true)
    val autoCaptureEnabled: StateFlow<Boolean> = _autoCaptureEnabled.asStateFlow()

    private val _isPrivacyBannerDismissed = MutableStateFlow(false)
    val isPrivacyBannerDismissed: StateFlow<Boolean> = _isPrivacyBannerDismissed.asStateFlow()

    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(true)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _eventFlow = MutableSharedFlow<String>()
    val eventFlow: SharedFlow<String> = _eventFlow.asSharedFlow()

    val stats: StateFlow<ClipUiStats>
    val clips: StateFlow<List<ClipItem>>

    // Clipboard change listener for automatic real-time capture while active/focused
    private val clipChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (_autoCaptureEnabled.value) {
            syncClipboard(silent = false, fromListener = true)
        }
    }

    init {
        val database = AppDatabase.getDatabase(application)
        val prefManager = PreferenceManager(application)
        repository = ClipRepository(database.clipDao(), prefManager)

        _retentionDays.value = prefManager.retentionDays
        _autoCaptureEnabled.value = prefManager.autoCaptureOnResume
        _isPrivacyBannerDismissed.value = prefManager.isPrivacyBannerDismissed
        _themeMode.value = prefManager.themeMode
        _dynamicColor.value = prefManager.dynamicColor

        stats = combine(
            repository.totalCount,
            repository.pinnedCount,
            _retentionDays
        ) { total, pinned, days ->
            ClipUiStats(
                totalCount = total,
                pinnedCount = pinned,
                retentionDays = days,
                lastCleanupTime = prefManager.lastCleanupTimestamp
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ClipUiStats()
        )

        clips = combine(
            repository.allClips,
            _searchQuery,
            _selectedFilter,
            _sortOption
        ) { rawClips, query, filter, sort ->
            var filtered = rawClips

            // 1. Filter by category
            filtered = when (filter) {
                ClipFilterCategory.ALL -> filtered
                ClipFilterCategory.PINNED -> filtered.filter { it.isPinned }
                ClipFilterCategory.URLS -> filtered.filter { it.contentType == ContentType.URL.name }
                ClipFilterCategory.CODE -> filtered.filter {
                    it.contentType == ContentType.CODE.name || it.contentType == ContentType.JSON.name
                }
                ClipFilterCategory.COLORS -> filtered.filter { it.contentType == ContentType.HEX_COLOR.name }
                ClipFilterCategory.CONTACTS -> filtered.filter {
                    it.contentType == ContentType.EMAIL.name || it.contentType == ContentType.PHONE.name
                }
                ClipFilterCategory.TEXT -> filtered.filter { it.contentType == ContentType.TEXT.name }
            }

            // 2. Filter by search query
            if (query.isNotBlank()) {
                val q = query.trim().lowercase()
                filtered = filtered.filter {
                    it.content.lowercase().contains(q) || (it.label?.lowercase()?.contains(q) == true)
                }
            }

            // 3. Sort
            when (sort) {
                SortOption.RECENT -> filtered.sortedWith(
                    compareByDescending<ClipItem> { it.isPinned }
                        .thenByDescending { it.lastUsedAt }
                )
                SortOption.OLDEST -> filtered.sortedWith(
                    compareByDescending<ClipItem> { it.isPinned }
                        .thenBy { it.createdAt }
                )
                SortOption.MOST_COPIED -> filtered.sortedWith(
                    compareByDescending<ClipItem> { it.isPinned }
                        .thenByDescending { it.copyCount }
                )
                SortOption.ALPHABETICAL -> filtered.sortedWith(
                    compareByDescending<ClipItem> { it.isPinned }
                        .thenBy { it.content.lowercase() }
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Clean expired clips silently on launch without inserting any fake demo data
        viewModelScope.launch {
            cleanExpiredClipsSilently()
        }

        // Check for updates automatically on app startup (silent if up to date)
        viewModelScope.launch {
            updateManager.checkForUpdate(isManual = false) { state ->
                _updateState.value = state
            }
        }

        try {
            clipboardManager.addPrimaryClipChangedListener(clipChangedListener)
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        updateManager.cancelDownload()
        try {
            clipboardManager.removePrimaryClipChangedListener(clipChangedListener)
        } catch (_: Exception) {}
    }

    fun onResumeCheck() {
        if (_autoCaptureEnabled.value) {
            syncClipboard(silent = true)
        }
        viewModelScope.launch {
            cleanExpiredClipsSilently()
        }
    }

    fun syncClipboard(silent: Boolean = false, fromListener: Boolean = false) {
        viewModelScope.launch {
            try {
                if (!clipboardManager.hasPrimaryClip()) {
                    if (!silent && !fromListener) _eventFlow.emit(getApplication<Application>().getString(R.string.event_clipboard_empty))
                    return@launch
                }

                val clipData = clipboardManager.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val item = clipData.getItemAt(0)
                    val text = item.coerceToText(getApplication())?.toString()?.trim()
                    if (!text.isNullOrBlank()) {
                        when (repository.saveOrUpdateClip(text)) {
                            SaveResult.NEW -> {
                                _eventFlow.emit(getApplication<Application>().getString(R.string.event_auto_captured))
                            }
                            SaveResult.UPDATED_EXISTING -> {
                                if (!silent && !fromListener) {
                                    _eventFlow.emit(getApplication<Application>().getString(R.string.event_clipboard_already_saved))
                                }
                            }
                            SaveResult.EMPTY_IGNORED -> {
                                if (!silent && !fromListener) {
                                    _eventFlow.emit(getApplication<Application>().getString(R.string.event_clipboard_content_empty))
                                }
                            }
                        }
                    } else if (!silent && !fromListener) {
                        _eventFlow.emit(getApplication<Application>().getString(R.string.event_clipboard_no_text))
                    }
                }
            } catch (e: Exception) {
                if (!silent && !fromListener) _eventFlow.emit(getApplication<Application>().getString(R.string.event_clipboard_read_failed, e.message.orEmpty()))
            }
        }
    }

    fun handleSharedText(sharedText: String) {
        viewModelScope.launch {
            if (sharedText.isNotBlank()) {
                val result = repository.saveOrUpdateClip(sharedText, getApplication<Application>().getString(R.string.shared_clip_label))
                if (result == SaveResult.NEW) {
                    _eventFlow.emit(getApplication<Application>().getString(R.string.event_shared_saved))
                } else {
                    _eventFlow.emit(getApplication<Application>().getString(R.string.event_shared_updated))
                }
            }
        }
    }

    fun copyClipToClipboard(clip: ClipItem) {
        viewModelScope.launch {
            try {
                val clipData = ClipData.newPlainText(getApplication<Application>().getString(R.string.clipboard_label), clip.content)
                clipboardManager.setPrimaryClip(clipData)
                repository.markUsed(clip.id)
                _eventFlow.emit(getApplication<Application>().getString(R.string.event_copied))
            } catch (e: Exception) {
                _eventFlow.emit(getApplication<Application>().getString(R.string.event_copy_failed, e.message.orEmpty()))
            }
        }
    }

    fun togglePin(clip: ClipItem) {
        viewModelScope.launch {
            repository.togglePin(clip.id, clip.isPinned)
            val message = if (!clip.isPinned) getApplication<Application>().getString(R.string.event_clip_starred) else getApplication<Application>().getString(R.string.event_clip_unstarred)
            _eventFlow.emit(message)
        }
    }

    fun addManualClip(content: String, label: String?) {
        viewModelScope.launch {
            val result = repository.saveOrUpdateClip(content, label)
            when (result) {
                SaveResult.NEW -> _eventFlow.emit(getApplication<Application>().getString(R.string.event_clip_added))
                SaveResult.UPDATED_EXISTING -> _eventFlow.emit(getApplication<Application>().getString(R.string.event_clip_existing_updated))
                SaveResult.EMPTY_IGNORED -> _eventFlow.emit(getApplication<Application>().getString(R.string.event_clip_empty))
            }
        }
    }

    fun updateClip(clipId: Long, content: String, label: String?) {
        viewModelScope.launch {
            repository.updateClip(clipId, content, label)
            _eventFlow.emit(getApplication<Application>().getString(R.string.event_clip_updated))
        }
    }

    fun deleteClip(clip: ClipItem) {
        viewModelScope.launch {
            repository.deleteClip(clip)
            _eventFlow.emit(getApplication<Application>().getString(R.string.event_clip_deleted))
        }
    }

    fun clearHistory(keepPinned: Boolean) {
        viewModelScope.launch {
            repository.clearHistory(keepPinned)
            val msg = if (keepPinned) getApplication<Application>().getString(R.string.event_cleared_unstarred) else getApplication<Application>().getString(R.string.event_cleared_all)
            _eventFlow.emit(msg)
        }
    }

    fun updateRetentionDays(days: Int) {
        _retentionDays.value = days
        repository.preferenceManager.retentionDays = days
        viewModelScope.launch {
            val deleted = repository.cleanExpiredClips()
            if (deleted > 0) {
                _eventFlow.emit(getApplication<Application>().getString(R.string.event_purged_expired, deleted))
            } else {
                _eventFlow.emit(getApplication<Application>().getString(R.string.event_retention_updated))
            }
        }
    }

    fun cleanExpiredNow() {
        viewModelScope.launch {
            val count = repository.cleanExpiredClips()
            if (count > 0) {
                _eventFlow.emit(getApplication<Application>().getString(R.string.event_removed_expired, count))
            } else {
                _eventFlow.emit(getApplication<Application>().getString(R.string.event_no_expired))
            }
        }
    }

    fun setAutoCapture(enabled: Boolean) {
        _autoCaptureEnabled.value = enabled
        repository.preferenceManager.autoCaptureOnResume = enabled
    }

    fun dismissPrivacyBanner() {
        _isPrivacyBannerDismissed.value = true
        repository.preferenceManager.isPrivacyBannerDismissed = true
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        repository.preferenceManager.themeMode = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        repository.preferenceManager.dynamicColor = enabled
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterCategory(category: ClipFilterCategory) {
        _selectedFilter.value = category
    }

    fun setSortOption(sort: SortOption) {
        _sortOption.value = sort
    }

    fun checkForUpdates(isManual: Boolean = true) {
        viewModelScope.launch {
            updateManager.checkForUpdate(isManual = isManual) { state ->
                _updateState.value = state
            }
        }
    }

    fun startUpdateDownload(info: UpdateInfo) {
        updateManager.startDownload(info, viewModelScope) { state ->
            _updateState.value = state
        }
    }

    fun cancelUpdateDownload() {
        updateManager.cancelDownload()
        _updateState.value = UpdateUiState.Idle
    }

    fun dismissUpdateDialog() {
        updateManager.cancelDownload()
        _updateState.value = UpdateUiState.Idle
    }

    fun installDownloadedUpdate(activity: Activity, apkFile: File): Boolean {
        return updateManager.promptInstall(activity, apkFile)
    }

    private suspend fun cleanExpiredClipsSilently() {
        try {
            repository.cleanExpiredClips()
        } catch (_: Exception) {}
    }
}
