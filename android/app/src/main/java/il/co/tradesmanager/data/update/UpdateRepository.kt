package il.co.tradesmanager.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import il.co.tradesmanager.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Updates for builds installed by hand rather than from a store.
 *
 * The app is handed round as a downloaded APK long before it is on Play, and
 * telling someone to visit a repository page every time is how a site ends up
 * running four different versions. So the app asks the release feed itself.
 *
 * Deliberately narrow:
 *  - It reads one public JSON document and downloads one file. There is no
 *    token in the app, which means the repository has to be public for this
 *    to work at all — a private one returns 404 to an anonymous request.
 *  - It never installs anything silently. Android shows its own installer
 *    and the user confirms, the same as any sideloaded APK.
 *  - It is compiled out of store builds ([BuildConfig.SELF_UPDATE]), because
 *    self-updating breaks Google Play's Device and Network Abuse policy and
 *    cannot be done on iOS at all.
 */
class UpdateRepository(private val context: Context) {

    sealed interface Result {
        data object UpToDate : Result
        data class Available(val release: Release) : Result

        /**
         * The feed answered, and there is nothing in it.
         *
         * Told apart from [Unavailable] deliberately. GitHub returns 404 for a
         * repository with no releases published, which is indistinguishable
         * from a missing repository at the HTTP level but means something
         * completely different to a user: "nobody has published a version yet"
         * is not "your phone has no signal", and saying the second when the
         * first is true sends people to check their reception.
         */
        data object NoReleases : Result

        /** Network down, rate limited, or the repository is not public. */
        data object Unavailable : Result
    }

    data class Release(
        val versionName: String,
        val notes: String,
        val downloadUrl: String,
        val sizeBytes: Long,
    )

    @Serializable
    private data class ReleaseDto(
        @SerialName("tag_name") val tagName: String = "",
        val name: String? = null,
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<AssetDto> = emptyList(),
    )

    @Serializable
    private data class AssetDto(
        val name: String = "",
        val size: Long = 0,
        @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    )

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(): Result = withContext(Dispatchers.IO) {
        if (!BuildConfig.SELF_UPDATE) return@withContext Result.UpToDate

        val response = fetch(BuildConfig.RELEASES_API)
        val body = when (response) {
            is Response.Body -> response.text
            Response.Empty -> return@withContext Result.NoReleases
            Response.Failed -> return@withContext Result.Unavailable
        }

        val dto = runCatching { json.decodeFromString<ReleaseDto>(body) }.getOrNull()
            ?: return@withContext Result.Unavailable
        // A draft is not published, so from out here it does not exist.
        if (dto.draft) return@withContext Result.NoReleases

        // The build splits per ABI, so a release carries several APKs. The
        // universal one runs everywhere, which is the right default for
        // someone tapping "install" without knowing what a CPU ABI is.
        val asset = dto.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
            .let { apks -> apks.firstOrNull { it.name.contains("universal") } ?: apks.firstOrNull() }
        // A release with no APK on it is a release nobody can install: the
        // same thing, to a user, as no release at all.
            ?: return@withContext Result.NoReleases

        val remote = normalise(dto.tagName)
        if (!isNewer(remote, normalise(BuildConfig.VERSION_NAME))) {
            return@withContext Result.UpToDate
        }

        Result.Available(
            Release(
                versionName = dto.name?.takeIf { it.isNotBlank() } ?: dto.tagName,
                notes = dto.body.orEmpty().trim(),
                downloadUrl = asset.browserDownloadUrl,
                sizeBytes = asset.size,
            ),
        )
    }

    /**
     * Downloads into the cache and returns the file. Reported through
     * [onProgress] as a fraction, or as -1 when the server sends no length —
     * a bar that sits at zero is worse than a spinner.
     */
    suspend fun download(release: Release, onProgress: (Float) -> Unit): File? =
        withContext(Dispatchers.IO) {
            val target = File(context.cacheDir, "updates").apply { mkdirs() }
                .let { File(it, "update.apk") }
            target.delete()

            runCatching {
                val connection = open(release.downloadUrl)
                if (connection.responseCode !in 200..299) {
                    connection.disconnect()
                    error("HTTP ${connection.responseCode}")
                }
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            onProgress(if (total > 0) copied.toFloat() / total else -1f)
                        }
                    }
                }
                connection.disconnect()
            }.getOrElse {
                target.delete()
                return@withContext null
            }

            target.takeIf { it.length() > 0 }
        }

    /** Hands the file to Android's installer; the user confirms there. */
    fun installIntent(apk: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** What came back, in the three shapes the caller actually cares about. */
    private sealed interface Response {
        data class Body(val text: String) : Response
        /** The feed answered "there is nothing here" — a 404 with no release. */
        data object Empty : Response
        data object Failed : Response
    }

    private fun fetch(url: String): Response {
        var connection: HttpURLConnection? = null
        return runCatching {
            val opened = open(url)
            connection = opened
            when (opened.responseCode) {
                in 200..299 -> Response.Body(
                    opened.inputStream.bufferedReader().use { it.readText() },
                )
                // GitHub answers 404 both for a repository with no releases
                // and for one it will not show us. The first is by far the
                // likelier here and the honest thing to report.
                HttpURLConnection.HTTP_NOT_FOUND -> Response.Empty
                else -> Response.Failed
            }
        }.getOrElse { Response.Failed }.also { connection?.disconnect() }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT
            readTimeout = TIMEOUT
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "TradesWorkManager/${BuildConfig.VERSION_NAME}")
        }

    internal companion object {
        private const val TIMEOUT = 15_000
        private const val BUFFER = 32 * 1024

        /** "v1.2.3", "1.2.3-debug" and "1.2.3" all compare as 1.2.3. */
        fun normalise(version: String): List<Int> =
            version.trim().removePrefix("v").removePrefix("V")
                .substringBefore('-')
                .split('.')
                .map { part -> part.filter { it.isDigit() }.toIntOrNull() ?: 0 }

        /** Compares part by part, treating a missing part as zero. */
        fun isNewer(remote: List<Int>, installed: List<Int>): Boolean {
            val length = maxOf(remote.size, installed.size)
            for (index in 0 until length) {
                val a = remote.getOrElse(index) { 0 }
                val b = installed.getOrElse(index) { 0 }
                if (a != b) return a > b
            }
            return false
        }
    }
}
