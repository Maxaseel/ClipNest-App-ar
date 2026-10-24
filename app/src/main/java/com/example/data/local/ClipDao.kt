package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ClipItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipDao {

    @Query("SELECT * FROM clips ORDER BY isPinned DESC, lastUsedAt DESC")
    fun getAllClips(): Flow<List<ClipItem>>

    @Query("""
        SELECT * FROM clips 
        WHERE (:query = '' OR content LIKE '%' || :query || '%' OR (label IS NOT NULL AND label LIKE '%' || :query || '%'))
        AND (:typeFilter = '' OR contentType = :typeFilter)
        AND (:pinnedOnly = 0 OR isPinned = 1)
        ORDER BY isPinned DESC, lastUsedAt DESC
    """)
    fun filterClips(query: String, typeFilter: String, pinnedOnly: Boolean): Flow<List<ClipItem>>

    @Query("SELECT * FROM clips WHERE content = :content LIMIT 1")
    suspend fun findByContent(content: String): ClipItem?

    @Query("SELECT * FROM clips WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ClipItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(clip: ClipItem): Long

    @Update
    suspend fun update(clip: ClipItem)

    @Delete
    suspend fun delete(clip: ClipItem)

    @Query("DELETE FROM clips WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE clips SET isPinned = :isPinned WHERE id = :id")
    suspend fun updatePinStatus(id: Long, isPinned: Boolean)

    @Query("UPDATE clips SET lastUsedAt = :timestamp, copyCount = copyCount + 1 WHERE id = :id")
    suspend fun incrementCopyCount(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM clips WHERE isPinned = 0 AND lastUsedAt < :thresholdMillis")
    suspend fun deleteExpired(thresholdMillis: Long): Int

    @Query("DELETE FROM clips WHERE isPinned = 0")
    suspend fun clearUnpinned()

    @Query("DELETE FROM clips")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM clips")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM clips WHERE isPinned = 1")
    fun getPinnedCount(): Flow<Int>
}
