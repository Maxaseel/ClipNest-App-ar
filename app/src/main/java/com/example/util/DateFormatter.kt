package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateFormatter {

    private val fullDateFormat = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

    fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        if (diff < 0) return "Just now"

        val seconds = TimeUnit.MILLISECONDS.toSeconds(diff)
        if (seconds < 60) return "Just now"

        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        if (minutes < 60) return "${minutes}m ago"

        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        if (hours < 24) return "${hours}h ago"

        val days = TimeUnit.MILLISECONDS.toDays(diff)
        if (days == 1L) return "Yesterday"
        if (days < 7) return "${days}d ago"

        return shortDateFormat.format(Date(timestamp))
    }

    fun formatFullDateTime(timestamp: Long): String {
        return fullDateFormat.format(Date(timestamp))
    }

    fun getExpiryText(lastUsedAt: Long, retentionDays: Int, isPinned: Boolean): String {
        if (isPinned) return "Never expires (Starred)"
        if (retentionDays <= 0) return "Never expires"

        val expirationThreshold = lastUsedAt + (retentionDays * 24L * 60L * 60L * 1000L)
        val remainingMillis = expirationThreshold - System.currentTimeMillis()

        if (remainingMillis <= 0) return "Expired (cleans soon)"

        val remainingDays = TimeUnit.MILLISECONDS.toDays(remainingMillis)
        val remainingHours = TimeUnit.MILLISECONDS.toHours(remainingMillis) % 24

        return if (remainingDays > 0) {
            "Expires in ${remainingDays}d ${remainingHours}h"
        } else {
            val remainingMins = TimeUnit.MILLISECONDS.toMinutes(remainingMillis) % 60
            "Expires in ${remainingHours}h ${remainingMins}m"
        }
    }
}
