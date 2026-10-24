package com.example.data.repository

import com.example.data.local.ClipDao
import com.example.data.model.ClipItem
import com.example.data.model.ContentType
import com.example.data.preferences.PreferenceManager
import com.example.util.ContentTypeDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

enum class SaveResult {
    NEW,
    UPDATED_EXISTING,
    EMPTY_IGNORED
}

class ClipRepository(
    private val clipDao: ClipDao,
    val preferenceManager: PreferenceManager
) {

    val allClips: Flow<List<ClipItem>> = clipDao.getAllClips()
    val totalCount: Flow<Int> = clipDao.getTotalCount()
    val pinnedCount: Flow<Int> = clipDao.getPinnedCount()

    fun getFilteredClips(
        query: String,
        typeFilter: String,
        pinnedOnly: Boolean
    ): Flow<List<ClipItem>> {
        return clipDao.filterClips(
            query = query.trim(),
            typeFilter = typeFilter,
            pinnedOnly = pinnedOnly
        )
    }

    suspend fun saveOrUpdateClip(
        rawContent: String,
        customLabel: String? = null
    ): SaveResult = withContext(Dispatchers.IO) {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) {
            return@withContext SaveResult.EMPTY_IGNORED
        }

        val existing = clipDao.findByContent(trimmed)
        if (existing != null) {
            // Already saved; bump the timestamp and increment copy counter
            clipDao.incrementCopyCount(existing.id, System.currentTimeMillis())
            if (customLabel != null && customLabel != existing.label) {
                clipDao.update(existing.copy(label = customLabel))
            }
            return@withContext SaveResult.UPDATED_EXISTING
        }

        val detected = ContentTypeDetector.detect(trimmed)
        val newClip = ClipItem(
            content = trimmed,
            contentType = detected.type.name,
            createdAt = System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis(),
            isPinned = false,
            copyCount = 1,
            label = customLabel,
            colorHex = detected.colorHex
        )
        clipDao.insert(newClip)
        return@withContext SaveResult.NEW
    }

    suspend fun updateClip(clipId: Long, newContent: String, newLabel: String?) = withContext(Dispatchers.IO) {
        val existing = clipDao.findById(clipId) ?: return@withContext
        val trimmed = newContent.trim()
        val detected = ContentTypeDetector.detect(trimmed)
        clipDao.update(
            existing.copy(
                content = trimmed,
                contentType = detected.type.name,
                label = if (newLabel.isNullOrBlank()) null else newLabel.trim(),
                colorHex = detected.colorHex,
                charCount = trimmed.length,
                wordCount = if (trimmed.isBlank()) 0 else trimmed.split(Regex("\\s+")).size
            )
        )
    }

    suspend fun togglePin(id: Long, currentPinned: Boolean) = withContext(Dispatchers.IO) {
        clipDao.updatePinStatus(id, !currentPinned)
    }

    suspend fun markUsed(id: Long) = withContext(Dispatchers.IO) {
        clipDao.incrementCopyCount(id, System.currentTimeMillis())
    }

    suspend fun deleteById(id: Long) = withContext(Dispatchers.IO) {
        clipDao.deleteById(id)
    }

    suspend fun deleteClip(clip: ClipItem) = withContext(Dispatchers.IO) {
        clipDao.delete(clip)
    }

    suspend fun clearHistory(keepPinned: Boolean) = withContext(Dispatchers.IO) {
        if (keepPinned) {
            clipDao.clearUnpinned()
        } else {
            clipDao.clearAll()
        }
    }

    suspend fun cleanExpiredClips(): Int = withContext(Dispatchers.IO) {
        val days = preferenceManager.retentionDays
        if (days == PreferenceManager.RETENTION_NEVER || days <= 0) {
            return@withContext 0
        }

        val millisInDay = 24L * 60L * 60L * 1000L
        val expirationThreshold = System.currentTimeMillis() - (days * millisInDay)
        val deletedCount = clipDao.deleteExpired(expirationThreshold)
        preferenceManager.lastCleanupTimestamp = System.currentTimeMillis()
        return@withContext deletedCount
    }
}
