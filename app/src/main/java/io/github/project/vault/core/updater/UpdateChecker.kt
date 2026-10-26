package io.github.project.vault.core.updater

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0L
)

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val assets: List<GitHubAsset> = emptyList()
)

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val currentVersion: String,
    val releaseTitle: String,
    val changelog: String,
    val releaseUrl: String,
    val apkDownloadUrl: String?
)

object UpdateChecker {
    private const val GITHUB_REPO = "KaKE-567/FrostBackup"
    private const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentVersionName = packageInfo.versionName ?: "1.0.0"

            val request = Request.Builder()
                .url(RELEASES_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "FrostBackup-App")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val responseBody = response.body?.string() ?: return@withContext null

                val release = json.decodeFromString<GitHubRelease>(responseBody)
                val cleanLatestTag = release.tagName.removePrefix("v").trim()
                val cleanCurrentVersion = currentVersionName.removePrefix("v").trim()

                val isNewer = isVersionNewer(cleanLatestTag, cleanCurrentVersion)

                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

                return@withContext UpdateInfo(
                    hasUpdate = isNewer,
                    latestVersion = release.tagName,
                    currentVersion = currentVersionName,
                    releaseTitle = release.name ?: "FrostBackup ${release.tagName}",
                    changelog = release.body.orEmpty(),
                    releaseUrl = release.htmlUrl,
                    apkDownloadUrl = apkAsset?.downloadUrl ?: release.htmlUrl
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun isVersionNewer(latest: String, current: String): Boolean {
        try {
            val latestParts = latest.split(".").map { it.filter { char -> char.isDigit() }.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.filter { char -> char.isDigit() }.toIntOrNull() ?: 0 }

            val maxLen = maxOf(latestParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            return false
        } catch (_: Exception) {
            return latest != current
        }
    }
}
