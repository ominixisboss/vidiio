package com.ominix.vidiio.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.ominix.vidiio.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/** A newer release found on GitHub. */
data class AppUpdate(val version: String, val notes: String, val apkUrl: String, val sizeBytes: Long)

/**
 * Updates from GitHub Releases: the latest release of [REPO] is compared with this build's
 * versionName, and its .apk asset is downloaded and handed to the system installer.
 *
 * Android only installs it over this app when it is signed with the same key, so release APKs
 * must be built on the same machine (or with the same keystore) as the installed one.
 */
class AppUpdater(private val context: Context, private val client: OkHttpClient) {

    /** The update the app is currently offering; UpdatePrompt shows it and clears it when dismissed. */
    val offered = kotlinx.coroutines.flow.MutableStateFlow<AppUpdate?>(null)

    private val prefs by lazy { context.getSharedPreferences("app_updater_prefs", Context.MODE_PRIVATE) }

    fun ignoreVersion(version: String) {
        prefs.edit().putString("ignored_version", version).apply()
        offered.value = null
    }

    fun getIgnoredVersion(): String? = prefs.getString("ignored_version", null)

    /** The latest release if it is newer than this build, else null. Never throws. */
    suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$REPO/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val release = JSONObject(response.body.string())
                val version = release.optString("tag_name").removePrefix("v").removePrefix("V")
                if (version == getIgnoredVersion()) return@withContext null
                if (!isNewer(version, BuildConfig.VERSION_NAME)) return@withContext null
                val assets = release.optJSONArray("assets") ?: return@withContext null
                val assetList = (0 until assets.length()).map { assets.getJSONObject(it) }
                val apk = assetList.firstOrNull { it.optString("name").contains("universal", ignoreCase = true) && it.optString("name").endsWith(".apk", ignoreCase = true) }
                    ?: assetList.firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                    ?: return@withContext null
                AppUpdate(
                    version = version,
                    notes = release.optString("body").take(1_000),
                    apkUrl = apk.getString("browser_download_url"),
                    sizeBytes = apk.optLong("size"),
                )
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Update check failed", e)
            null
        }
    }

    /** Downloads the update's APK, reporting progress 0..1. */
    suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "vidiio-${update.version}.apk")
        client.newCall(Request.Builder().url(update.apkUrl).build()).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("Download failed (HTTP ${response.code})")
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 } ?: update.sizeBytes
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        target
    }

    /** False until the viewer lets this app install apps; [openInstallPermission] takes them there. */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "No install-permission screen", it) }
    }

    /** Opens the system installer on [apk]. */
    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(intent)
    }

    companion object {
        private const val TAG = "AppUpdater"
        const val REPO = "ominixisboss/vidiio"

        /** True when [candidate] ("1.10.0") is a higher dotted version than [current] ("1.9.2"). */
        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val a = parts(candidate)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
