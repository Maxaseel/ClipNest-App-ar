package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "clips",
    indices = [
        Index(value = ["content"]),
        Index(value = ["isPinned", "lastUsedAt"]),
        Index(value = ["contentType"])
    ]
)
data class ClipItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val contentType: String = ContentType.TEXT.name,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val copyCount: Int = 1,
    val charCount: Int = content.length,
    val wordCount: Int = if (content.isBlank()) 0 else content.trim().split(Regex("\\s+")).size,
    val label: String? = null,
    val colorHex: String? = null
)
