package com.example.data.update

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class UpdateManager(private val context: Context) {

    companion object {
        const val UPDATE_JSON_URL =
            "https://raw.githubusercontent.com/aiwebnix-oss/ClipNest-App/refs/heads/main/update.json"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var currentDownloadId: Long? = null
    private var downloadTrackingJob: Job? = null

    suspend fun checkForUpdate(
        isManual: Boolean,
        onStateChange: (UpdateUiState) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (isManual) {
            withContext(Dispatchers.Main) {
                onStateChange(UpdateUiState.Checking)
            }
        }

        val currentVersionName: String
        val currentVersionCode: Int

        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }

            currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
            currentVersionName = packageInfo.versionName ?: context.getString(com.example.R.string.default_version_name)
        } catch (e: Exception) {
            if (isManual) {
                withContext(Dispatchers.Main) {
                    onStateChange(UpdateUiState.Error(context.getString(com.example.R.string.update_error_inspect_version, e.message)))
                }
            }
            return@withContext
        }

        try {
            val request = Request.Builder()
                .url(UPDATE_JSON_URL)
                .header("Cache-Control", "no-cache")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                if (isManual) {
                    withContext(Dispatchers.Main) {
                        onStateChange(UpdateUiState.Error(context.getString(com.example.R.string.update_error_server_status, response.code)))
                    }
                }
                return@withContext
            }

            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                if (isManual) {
                    withContext(Dispatchers.Main) {
                        onStateChange(UpdateUiState.Error(context.getString(com.example.R.string.update_error_empty_payload)))
                    }
                }
                return@withContext
            }

            val json = JSONObject(body)
            val remoteVersionCode = json.optInt("versionCode", -1)
            val remoteVersionName = json.optString("versionName", "")
            val releaseNotes = json.optString("releaseNotes", context.getString(com.example.R.string.default_release_notes))
            val apkUrl = json.optString("apkUrl", "")
            val sha256 = if (json.has("sha256") && !json.isNull("sha256")) {
                json.getString("sha256").trim().ifBlank { null }
            } else {
                null
            }

            if (remoteVersionCode <= 0 || apkUrl.isBlank()) {
                if (isManual) {
                    withContext(Dispatchers.Main) {
                        onStateChange(UpdateUiState.Error(context.getString(com.example.R.string.update_error_invalid_metadata)))
                    }
                }
                return@withContext
            }

            val info = UpdateInfo(
                versionCode = remoteVersionCode,
                versionName = remoteVersionName.ifBlank { "v$remoteVersionCode" },
                releaseNotes = releaseNotes,
                apkUrl = apkUrl,
                sha256 = sha256
            )

            withContext(Dispatchers.Main) {
                if (remoteVersionCode > currentVersionCode) {
                    onStateChange(
                        UpdateUiState.UpdateAvailable(
                            info = info,
                            currentVersionName = currentVersionName,
                            currentVersionCode = currentVersionCode
                        )
                    )
                } else {
                    if (isManual) {
                        onStateChange(
                            UpdateUiState.UpToDate(
                                currentVersionName = currentVersionName,
                                currentVersionCode = currentVersionCode
                            )
                        )
                    } else {
                        onStateChange(UpdateUiState.Idle)
                    }
                }
            }
        } catch (e: Exception) {
            if (isManual) {
                withContext(Dispatchers.Main) {
                        onStateChange(
                            UpdateUiState.Error(
                                context.getString(
                                    com.example.R.string.update_error_connection,
                                    e.localizedMessage ?: context.getString(com.example.R.string.network_error)
                                )
                            )
                        )
                }
            }
        }
    }

    fun startDownload(
        info: UpdateInfo,
        scope: CoroutineScope,
        onStateChange: (UpdateUiState) -> Unit
    ) {
        cancelDownload()

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (downloadManager == null) {
            onStateChange(UpdateUiState.Error(context.getString(com.example.R.string.update_error_download_manager_unavailable), canRetry = false))
            return
        }

        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir == null) {
            onStateChange(UpdateUiState.Error(context.getString(com.example.R.string.update_error_storage_unavailable), canRetry = false))
            return
        }

        // Clean any old apk files in directory
        try {
            downloadsDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".apk")) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}

        val fileName = "ClipNest-update-${info.versionCode}.apk"
        val destinationFile = File(downloadsDir, fileName)

        try {
            val uri = Uri.parse(info.apkUrl)
            val request = DownloadManager.Request(uri).apply {
                setTitle(context.getString(com.example.R.string.download_notification_title, info.versionName))
                setDescription(context.getString(com.example.R.string.download_notification_description))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setMimeType("application/vnd.android.package-archive")
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val downloadId = downloadManager.enqueue(request)
            currentDownloadId = downloadId

            onStateChange(
                UpdateUiState.Downloading(
                    info = info,
                    progressPercent = 0,
                    downloadedBytes = 0L,
                    totalBytes = 0L
                )
            )

            downloadTrackingJob = scope.launch(Dispatchers.IO) {
                trackDownloadProgress(downloadManager, downloadId, info, destinationFile, onStateChange)
            }
        } catch (e: Exception) {
            onStateChange(UpdateUiState.Error(context.getString(com.example.R.string.update_error_start_download, e.message), canRetry = true, info = info))
        }
    }

    private suspend fun trackDownloadProgress(
        downloadManager: DownloadManager,
        downloadId: Long,
        info: UpdateInfo,
        destinationFile: File,
        onStateChange: (UpdateUiState) -> Unit
    ) {
        var isDownloading = true

        while (isDownloading && CoroutineScope(Dispatchers.IO).isActive) {
            delay(400)

            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)

            if (cursor != null && cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val downloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                val status = if (statusIndex >= 0) cursor.getInt(statusIndex) else -1
                val downloaded = if (downloadedIndex >= 0) cursor.getLong(downloadedIndex) else 0L
                val total = if (totalIndex >= 0) cursor.getLong(totalIndex) else 0L

                cursor.close()

                when (status) {
                    DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
                        val percent = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 99) else 0
                        withContext(Dispatchers.Main) {
                            onStateChange(
                                UpdateUiState.Downloading(
                                    info = info,
                                    progressPercent = percent,
                                    downloadedBytes = downloaded,
                                    totalBytes = total
                                )
                            )
                        }
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        isDownloading = false
                        withContext(Dispatchers.Main) {
                            onStateChange(UpdateUiState.Verifying(info))
                        }

                        // Verify SHA-256 checksum if provided in JSON
                        val checksumValid = if (!info.sha256.isNullOrBlank()) {
                            verifySha256(destinationFile, info.sha256)
                        } else {
                            true
                        }

                        withContext(Dispatchers.Main) {
                            if (!checksumValid) {
                                destinationFile.delete()
                                onStateChange(
                                    UpdateUiState.Error(
                                        message = context.getString(com.example.R.string.update_error_checksum),
                                        canRetry = false,
                                        info = info
                                    )
                                )
                            } else {
                                try {
                                    val apkUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        destinationFile
                                    )
                                    onStateChange(
                                        UpdateUiState.ReadyToInstall(
                                            info = info,
                                            apkUri = apkUri,
                                            apkFile = destinationFile
                                        )
                                    )
                                } catch (e: Exception) {
                                    onStateChange(
                                        UpdateUiState.Error(
                                            message = context.getString(com.example.R.string.update_error_prepare_apk, e.message),
                                            canRetry = true,
                                            info = info
                                        )
                                    )
                                }
                            }
                        }
                    }
                    DownloadManager.STATUS_FAILED -> {
                        isDownloading = false
                        withContext(Dispatchers.Main) {
                            onStateChange(
                                UpdateUiState.Error(
                                    message = context.getString(com.example.R.string.update_error_download_failed),
                                    canRetry = true,
                                    info = info
                                )
                            )
                        }
                    }
                }
            } else {
                cursor?.close()
            }
        }
    }

    fun cancelDownload() {
        downloadTrackingJob?.cancel()
        downloadTrackingJob = null
        currentDownloadId?.let { id ->
            try {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                dm?.remove(id)
            } catch (_: Exception) {}
        }
        currentDownloadId = null
    }

    private fun verifySha256(file: File, expectedSha256: String): Boolean {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buffer = ByteArray(8192)
                var bytes = stream.read(buffer)
                while (bytes > 0) {
                    digest.update(buffer, 0, bytes)
                    bytes = stream.read(buffer)
                }
            }
            val computed = digest.digest().joinToString("") { "%02x".format(it) }
            computed.equals(expectedSha256.trim(), ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    fun promptInstall(activity: Activity, apkFile: File): Boolean {
        if (!apkFile.exists()) {
            return false
        }

        // Android 8.0+ unknown sources permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
                return false
            }
        }

        return try {
            val contentUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(installIntent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
