package com.universal.carplay.update

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Avtomobillər üçün Avtomatik Simsiz Yenilənmə Meneceri (OTA - Over-The-Air Update Engine)
 * 
 * GitHub Releases API-yə qoşularaq yeni versiyanı yoxlayır, arxa planda yükləyir 
 * və heç bir USB fleşkarta ehtiyac olmadan birbaşa avtomobil ekranında tətbiqi yeniləyir.
 */
class OtaUpdateManager(
    private val context: Context,
    private val currentVersionName: String = "1.4.0",
    private val onUpdateStatusChanged: ((hasUpdate: Boolean, newVersion: String, downloadUrl: String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "CarTwin_OTA"
        private const val RELEASES_API_URL = "https://api.github.com/repos/alasshrafov/UniversalCarPlay/releases/latest"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    data class UpdateInfo(
        val hasUpdate: Boolean,
        val newVersion: String,
        val releaseNotes: String,
        val downloadUrl: String
    )

    /**
     * Yeni versiyanı yoxlayır (silent = true olduqda yalnız bildiriş göstərir)
     */
    fun checkForUpdates(silent: Boolean = true) {
        scope.launch {
            if (!silent) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "🔍 Yeni yenilənmələr yoxlanılır…", Toast.LENGTH_SHORT).show()
                }
            }

            val updateInfo = checkLatestReleaseOnGithub()

            withContext(Dispatchers.Main) {
                if (updateInfo != null && updateInfo.hasUpdate) {
                    onUpdateStatusChanged?.invoke(true, updateInfo.newVersion, updateInfo.downloadUrl)
                    showUpdatePromptDialog(updateInfo)
                } else {
                    onUpdateStatusChanged?.invoke(false, currentVersionName, "")
                    if (!silent) {
                        Toast.makeText(context, "✅ CarTwin ən son versiyadadır (v$currentVersionName)", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun checkLatestReleaseOnGithub(): UpdateInfo? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(RELEASES_API_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "CarTwin-OTA-Client")
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            if (connection.responseCode != 200) {
                Log.w(TAG, "GitHub API xətası: ${connection.responseCode}")
                return null
            }

            val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonText)

            val tagName = json.optString("tag_name", "").removePrefix("v")
            val releaseNotes = json.optString("body", "Yeni optimizasiyalar və xəta düzəlişləri.")

            var apkDownloadUrl = ""
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkDownloadUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }

            if (tagName.isBlank() || apkDownloadUrl.isBlank()) return null

            val isNewer = isVersionNewer(remoteVersion = tagName, currentVersion = currentVersionName)
            Log.i(TAG, "Cari versiya: $currentVersionName, Ən son versiya: $tagName (Yenilik var: $isNewer)")

            return UpdateInfo(
                hasUpdate = isNewer,
                newVersion = tagName,
                releaseNotes = releaseNotes,
                downloadUrl = apkDownloadUrl
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Yeniləmə yoxlanarkən xəta: ${e.message}")
            return null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Versiya nömrələrini müqayisə edir (Məs: 1.4.1 > 1.4.0)
     */
    private fun isVersionNewer(remoteVersion: String, currentVersion: String): Boolean {
        try {
            val remoteParts = remoteVersion.split(".").map { it.filter { char -> char.isDigit() }.toIntOrNull() ?: 0 }
            val currentParts = currentVersion.split(".").map { it.filter { char -> char.isDigit() }.toIntOrNull() ?: 0 }

            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
        } catch (_: Throwable) {}
        return false
    }

    /**
     * Avtomobil ekranında gözəl dialoq pəncərəsi açır
     */
    private fun showUpdatePromptDialog(updateInfo: UpdateInfo) {
        val cleanNotes = updateInfo.releaseNotes.lines()
            .filter { !it.startsWith("#") && it.isNotBlank() }
            .take(6)
            .joinToString("\n")

        AlertDialog.Builder(context)
            .setTitle("🚀 CarTwin Yeni Versiya: v${updateInfo.newVersion}")
            .setMessage("Yeni sistem yenilənməsi hazırdır:\n\n$cleanNotes\n\nİndi avtomatik yüklənib quraşdırılsın?")
            .setPositiveButton("İndi Yenilə") { _, _ ->
                downloadAndInstallApk(updateInfo.downloadUrl, updateInfo.newVersion)
            }
            .setNegativeButton("Sonra", null)
            .setCancelable(true)
            .show()
    }

    /**
     * Yeni APK-nı arxa planda progress bar ilə endirir və quraşdırma ekranını açır
     */
    @Suppress("DEPRECATION")
    fun downloadAndInstallApk(downloadUrl: String, version: String) {
        val progressDialog = ProgressDialog(context).apply {
            setTitle("CarTwin Yenilənir (v$version)")
            setMessage("Fayl endirilir, zəhmət olmasa gözləyin…")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            progress = 0
            setCancelable(false)
            show()
        }

        scope.launch {
            try {
                val destFile = File(context.cacheDir, "CarTwin-update-v$version.apk")
                if (destFile.exists()) destFile.delete()

                val url = URL(downloadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                connection.connect()

                val fileLength = connection.contentLength
                val input = BufferedInputStream(connection.inputStream)
                val output = FileOutputStream(destFile)

                val data = ByteArray(4096)
                var total: Long = 0
                var count: Int

                while (input.read(data).also { count = it } != -1) {
                    total += count
                    output.write(data, 0, count)
                    if (fileLength > 0) {
                        val progressPercent = ((total * 100) / fileLength).toInt()
                        withContext(Dispatchers.Main) {
                            progressDialog.progress = progressPercent
                        }
                    }
                }

                output.flush()
                output.close()
                input.close()
                connection.disconnect()

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    launchPackageInstaller(destFile)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Yükləmə xətası: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(context, "Yükləmə xətası: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Android PackageInstaller pəncərəsini açaraq 1 kliklə quraşdırmanı təmin edir
     */
    private fun launchPackageInstaller(apkFile: File) {
        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            Log.i(TAG, "PackageInstaller uğurla başladıldı.")
        } catch (e: Throwable) {
            Log.e(TAG, "Quraşdırma pəncərəsi açılarkən xəta: ${e.message}", e)
            Toast.makeText(context, "Quraşdırma başladıla bilmədi: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
