package com.jarvisai.assistant.ui.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppReleaseInfo(
    val currentVersion: String,
    val newVersion: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val isNewer: Boolean
) {
    val tagName: String
        get() = newVersion
}

class AppUpdateManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val GITHUB_API_URL = "https://api.github.com/repos/sagarku03/MyAI/releases/latest"
        val CURRENT_VERSION_TAG: String
            get() = "v${BuildConfig.VERSION_NAME.removePrefix("v").trim()}"
    }

    suspend fun checkForUpdates(): Result<AppReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(GITHUB_API_URL)
                .header("User-Agent", "JARVIS-Android-Updater")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: $body")
                val json = JSONObject(body)
                val tagName = json.getString("tag_name").removePrefix("v").trim()
                val notes = json.optString("body", "Bug fixes and performance improvements.")
                val assets = json.optJSONArray("assets")
                var apkUrl = ""

                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.getString("name")
                        if (name.endsWith(".apk")) {
                            apkUrl = asset.getString("browser_download_url")
                            break
                        }
                    }
                }

                val currentVer = BuildConfig.VERSION_NAME.removePrefix("v").trim()
                val isNewer = isVersionGreater(tagName, currentVer)

                AppReleaseInfo(
                    currentVersion = "v$currentVer",
                    newVersion = "v$tagName",
                    releaseNotes = notes,
                    downloadUrl = apkUrl,
                    isNewer = isNewer
                )
            }
        }
    }

    private fun isVersionGreater(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val localParts = local.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    suspend fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(downloadUrl).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("Download failed: HTTP ${resp.code}")
                val body = resp.body ?: throw Exception("Empty response body")
                val totalLength = body.contentLength()
                val targetFile = File(context.getExternalFilesDir(null) ?: context.cacheDir, "update.apk")

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var totalRead = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (totalLength > 0) {
                                val progress = ((totalRead * 100) / totalLength).toInt()
                                onProgress(progress)
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    installApk(context, targetFile)
                }
                targetFile
            }
        }
    }

    fun installApk(context: Context, file: File) {
        // Android 8.0+ Unknown sources check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(settingsIntent)
                return
            }
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}
