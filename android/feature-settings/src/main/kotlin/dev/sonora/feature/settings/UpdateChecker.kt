package dev.sonora.feature.settings

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface UpdateResult {
    data class Available(val version: String, val releaseUrl: String) : UpdateResult
    data class Current(val version: String) : UpdateResult
    data object NoRelease : UpdateResult
}

/** Checks the project's public GitHub Releases API; installation remains user-confirmed by Android. */
internal object UpdateChecker {
    private const val RELEASE_API = "https://api.github.com/repos/zafaringz-ui/sonodroid/releases/latest"

    suspend fun check(context: Context): UpdateResult = withContext(Dispatchers.IO) {
        val currentVersion = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().substringBefore('-').removePrefix("v")
        val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Sonora-Android-Update-Checker")
        }
        try {
            if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return@withContext UpdateResult.NoRelease
            if (connection.responseCode !in 200..299) error("Update check failed")
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@withContext UpdateResult.NoRelease
            val tag = release.optString("tag_name").removePrefix("v").substringBefore('-')
            val releaseUrl = release.optString("html_url")
            if (tag.isBlank() || !releaseUrl.startsWith("https://github.com/zafaringz-ui/sonodroid/releases/")) {
                error("Invalid release information")
            }
            if (compareVersions(tag, currentVersion) > 0) {
                UpdateResult.Available(tag, releaseUrl)
            } else {
                UpdateResult.Current(currentVersion)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun compareVersions(left: String, right: String): Int {
        val a = left.split('.').map { it.toIntOrNull() ?: 0 }
        val b = right.split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(a.size, b.size)) {
            val difference = (a.getOrElse(index) { 0 }).compareTo(b.getOrElse(index) { 0 })
            if (difference != 0) return difference
        }
        return 0
    }
}
