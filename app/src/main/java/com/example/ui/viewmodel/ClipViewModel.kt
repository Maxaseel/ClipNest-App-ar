package com.example.ui.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.ClipItem
import com.example.data.model.ContentType
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.PreferenceManager
import com.example.data.repository.ClipRepository
import com.example.data.repository.SaveResult
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

enum class ClipFilterCategory(val displayName: String) {
    ALL("All"),
    PINNED("Starred"),
    URLS("Links"),
    CODE("Code & JSON"),
    COLORS("Colors"),
    CONTACTS("Contacts"),
    TEXT("Text")
}

enum class SortOption(val displayName: String) {
    RECENT("Most Recent"),
    OLDEST("Oldest First"),
    MOST_COPIED("Most Copied"),
    ALPHABETICAL("A to Z")
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

        try {
            clipboardManager.addPrimaryClipChangedListener(clipChangedListener)
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
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
                    if (!silent && !fromListener) _eventFlow.emit("Clipboard is currently empty")
                    return@launch
                }

                val clipData = clipboardManager.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val item = clipData.getItemAt(0)
                    val text = item.coerceToText(getApplication())?.toString()?.trim()
                    if (!text.isNullOrBlank()) {
                        when (repository.saveOrUpdateClip(text)) {
                            SaveResult.NEW -> {
                                _eventFlow.emit("Auto-captured from clipboard!")
                            }
                            SaveResult.UPDATED_EXISTING -> {
                                if (!silent && !fromListener) {
                                    _eventFlow.emit("Clipboard item already saved (usage updated)")
                                }
                            }
                            SaveResult.EMPTY_IGNORED -> {
                                if (!silent && !fromListener) {
                                    _eventFlow.emit("Clipboard content is empty")
                                }
                            }
                        }
                    } else if (!silent && !fromListener) {
                        _eventFlow.emit("No text found in clipboard")
                    }
                }
            } catch (e: Exception) {
                if (!silent && !fromListener) _eventFlow.emit("Could not read clipboard: ${e.message}")
            }
        }
    }

    fun handleSharedText(sharedText: String) {
        viewModelScope.launch {
            if (sharedText.isNotBlank()) {
                val result = repository.saveOrUpdateClip(sharedText, "Shared to ClipNest")
                if (result == SaveResult.NEW) {
                    _eventFlow.emit("Saved to ClipNest via Share!")
                } else {
                    _eventFlow.emit("Updated existing clip from Share")
                }
            }
        }
    }

    fun copyClipToClipboard(clip: ClipItem) {
        viewModelScope.launch {
            try {
                val clipData = ClipData.newPlainText("ClipNest Item", clip.content)
                clipboardManager.setPrimaryClip(clipData)
                repository.markUsed(clip.id)
                _eventFlow.emit("Copied to clipboard!")
            } catch (e: Exception) {
                _eventFlow.emit("Failed to copy: ${e.message}")
            }
        }
    }

    fun togglePin(clip: ClipItem) {
        viewModelScope.launch {
            repository.togglePin(clip.id, clip.isPinned)
            val message = if (!clip.isPinned) "Clip starred & protected from cleanup" else "Clip unstarred"
            _eventFlow.emit(message)
        }
    }

    fun addManualClip(content: String, label: String?) {
        viewModelScope.launch {
            val result = repository.saveOrUpdateClip(content, label)
            when (result) {
                SaveResult.NEW -> _eventFlow.emit("Clip added successfully")
                SaveResult.UPDATED_EXISTING -> _eventFlow.emit("Existing clip updated")
                SaveResult.EMPTY_IGNORED -> _eventFlow.emit("Clip cannot be empty")
            }
        }
    }

    fun updateClip(clipId: Long, content: String, label: String?) {
        viewModelScope.launch {
            repository.updateClip(clipId, content, label)
            _eventFlow.emit("Clip updated")
        }
    }

    fun deleteClip(clip: ClipItem) {
        viewModelScope.launch {
            repository.deleteClip(clip)
            _eventFlow.emit("Clip deleted")
        }
    }

    fun clearHistory(keepPinned: Boolean) {
        viewModelScope.launch {
            repository.clearHistory(keepPinned)
            val msg = if (keepPinned) "Cleared unstarred clips" else "Cleared all clipboard history"
            _eventFlow.emit(msg)
        }
    }

    fun updateRetentionDays(days: Int) {
        _retentionDays.value = days
        repository.preferenceManager.retentionDays = days
        viewModelScope.launch {
            val deleted = repository.cleanExpiredClips()
            if (deleted > 0) {
                _eventFlow.emit("Purged $deleted expired clips")
            } else {
                _eventFlow.emit("Retention policy updated")
            }
        }
    }

    fun cleanExpiredNow() {
        viewModelScope.launch {
            val count = repository.cleanExpiredClips()
            if (count > 0) {
                _eventFlow.emit("Removed $count expired clips")
            } else {
                _eventFlow.emit("No expired clips found to clean")
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

    private suspend fun cleanExpiredClipsSilently() {
        try {
            repository.cleanExpiredClips()
        } catch (_: Exception) {}
    }
}
