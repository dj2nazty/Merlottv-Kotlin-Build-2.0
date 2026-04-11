package com.merlottv.kotlin.ui.viewmodels

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class AutoUpdateState(
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val updateReady: Boolean = false,       // APK downloaded, waiting for user
    val latestVersion: String = "",
    val downloadUrl: String = "",
    val dismissed: Boolean = false,          // User tapped "Later" this session
    val error: String? = null
)

/**
 * Global ViewModel for automatic app updates.
 * On init: checks GitHub for a newer release → auto-downloads APK → shows dialog.
 * Used by MerlotApp composable at the root level so it runs on every app launch.
 */
@HiltViewModel
class AutoUpdateViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(AutoUpdateState())
    val state: StateFlow<AutoUpdateState> = _state.asStateFlow()

    private var apkFile: File? = null

    init {
        // Auto-check on launch with a small delay to let the app settle
        viewModelScope.launch {
            delay(3000) // wait 3s after launch so splash can finish
            checkAndDownload()
        }
    }

    private suspend fun checkAndDownload() {
        _state.value = _state.value.copy(isChecking = true, error = null)
        try {
            val (latestVersion, downloadUrl) = withContext(Dispatchers.IO) {
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url("https://api.github.com/repos/dj2nazty/Merlottv-Kotlin-Build-2.0/releases?per_page=10")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: "[]"
                val releases = org.json.JSONArray(body)

                var foundVersion = ""
                var foundUrl = ""
                for (i in 0 until releases.length()) {
                    val release = releases.getJSONObject(i)
                    val assets = release.optJSONArray("assets")
                    if (assets != null && assets.length() > 0) {
                        var apkUrl = ""
                        for (j in 0 until assets.length()) {
                            val asset = assets.getJSONObject(j)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk")) {
                                apkUrl = asset.optString("browser_download_url", "")
                                break
                            }
                        }
                        if (apkUrl.isNotEmpty()) {
                            foundVersion = release.optString("tag_name", "").removePrefix("v")
                            foundUrl = apkUrl
                            break
                        }
                    }
                }
                Pair(foundVersion, foundUrl)
            }

            val isNewer = latestVersion.isNotEmpty() && isVersionNewer(latestVersion, BuildConfig.VERSION_NAME)

            if (isNewer) {
                _state.value = _state.value.copy(
                    isChecking = false,
                    latestVersion = latestVersion,
                    downloadUrl = downloadUrl
                )
                Log.d("AutoUpdate", "New version available: $latestVersion (current: ${BuildConfig.VERSION_NAME})")
                downloadApk(downloadUrl)
            } else {
                _state.value = _state.value.copy(isChecking = false)
                Log.d("AutoUpdate", "App is up to date (${BuildConfig.VERSION_NAME})")
            }
        } catch (e: Exception) {
            Log.e("AutoUpdate", "Update check failed", e)
            _state.value = _state.value.copy(isChecking = false, error = e.message)
        }
    }

    private suspend fun downloadApk(url: String) {
        _state.value = _state.value.copy(isDownloading = true, downloadProgress = 0, error = null)
        try {
            val file = withContext(Dispatchers.IO) {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) throw Exception("Download failed: ${response.code}")

                val body = response.body ?: throw Exception("Empty response")
                val contentLength = body.contentLength()
                val cacheDir = getApplication<Application>().cacheDir
                val apkFile = File(cacheDir, "merlottv_update.apk")

                apkFile.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Long = 0
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read
                            if (contentLength > 0) {
                                val progress = (bytesRead * 100 / contentLength).toInt()
                                _state.value = _state.value.copy(downloadProgress = progress)
                            }
                        }
                    }
                }
                apkFile
            }

            apkFile = file
            _state.value = _state.value.copy(
                isDownloading = false,
                downloadProgress = 100,
                updateReady = true
            )
            Log.d("AutoUpdate", "APK downloaded: ${file.length() / 1024 / 1024}MB")
        } catch (e: Exception) {
            Log.e("AutoUpdate", "Download failed", e)
            _state.value = _state.value.copy(
                isDownloading = false,
                error = e.message
            )
        }
    }

    /** User tapped "Install Now" — launch system installer */
    fun installNow() {
        val file = apkFile ?: return
        try {
            val context = getApplication<Application>()
            val authority = "${context.packageName}.fileprovider"
            val apkUri = FileProvider.getUriForFile(context, authority, file)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)

            // Clean up APK after 10s (give installer time to read it)
            viewModelScope.launch {
                delay(10_000)
                try { if (file.exists()) file.delete() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e("AutoUpdate", "Install failed", e)
        }
    }

    /** User tapped "Later" — dismiss for this session */
    fun dismiss() {
        _state.value = _state.value.copy(dismissed = true)
    }

    /** Recheck (called from Settings manual button) */
    fun manualCheck() {
        viewModelScope.launch { checkAndDownload() }
    }

    private fun isVersionNewer(latest: String, current: String): Boolean {
        try {
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
        } catch (_: Exception) {}
        return false
    }
}
