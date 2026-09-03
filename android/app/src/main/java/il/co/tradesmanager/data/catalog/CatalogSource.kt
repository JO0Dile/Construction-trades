package il.co.tradesmanager.data.catalog

import android.content.Context
import java.io.File
import kotlinx.serialization.json.Json

/**
 * Where catalogue files are read from.
 *
 * The app ships a complete copy in its assets, so a phone that has never had a
 * signal still onboards with full catalogues. An optional cloud refresh writes
 * a newer set into the app's own files directory; this class prefers that copy
 * when its catalogVersion is higher, which is how catalogues can be corrected
 * between store releases without an app update.
 */
class CatalogSource(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    private val downloadedRoot: File
        get() = File(context.filesDir, DOWNLOADED_DIR)

    fun manifest(): CatalogManifest {
        val bundled = json.decodeFromString<CatalogManifest>(readAsset(MANIFEST))
        val downloaded = runCatching {
            json.decodeFromString<CatalogManifest>(File(downloadedRoot, MANIFEST).readText())
        }.getOrNull()
        return when {
            downloaded == null -> bundled
            downloaded.schemaVersion != bundled.schemaVersion -> bundled
            downloaded.catalogVersion > bundled.catalogVersion -> downloaded
            else -> bundled
        }
    }

    inline fun <reified T> decode(relativePath: String): T =
        jsonParser.decodeFromString(readText(relativePath))

    /** Downloaded copy wins per file, so a partial refresh is still coherent. */
    fun readText(relativePath: String): String {
        val downloaded = File(downloadedRoot, "$ROOT/$relativePath")
        return if (downloaded.isFile) downloaded.readText() else readAsset("$ROOT/$relativePath")
    }

    private fun readAsset(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    @PublishedApi
    internal val jsonParser: Json get() = json

    companion object {
        const val ROOT = "catalog"
        const val MANIFEST = "catalog/manifest.json"
        const val DOWNLOADED_DIR = "catalog-update"
    }
}
