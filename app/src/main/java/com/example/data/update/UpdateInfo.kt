package com.example.data.update

import android.net.Uri
import java.io.File

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val releaseNotes: String,
    val apkUrl: String,
    val sha256: String? = null
)

sealed interface UpdateUiState {
    object Idle : UpdateUiState
    object Checking : UpdateUiState
    data class UpToDate(val currentVersionName: String, val currentVersionCode: Int) : UpdateUiState
    data class UpdateAvailable(val info: UpdateInfo, val currentVersionName: String, val currentVersionCode: Int) : UpdateUiState
    data class Downloading(
        val info: UpdateInfo,
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateUiState
    data class Verifying(val info: UpdateInfo) : UpdateUiState
    data class ReadyToInstall(
        val info: UpdateInfo,
        val apkUri: Uri,
        val apkFile: File
    ) : UpdateUiState
    data class Error(
        val message: String,
        val canRetry: Boolean = true,
        val info: UpdateInfo? = null
    ) : UpdateUiState
}
