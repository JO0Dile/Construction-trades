package il.co.tradesmanager.data.update

import android.content.Context
import il.co.tradesmanager.core.update.ReleaseNotes
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `shared/assets/releases/notes.json`, as the app reads it: from its own
 * assets for "What's new", and from the copy attached to a release for what
 * an update is about to bring.
 *
 * Decoding never throws. A notes file that will not parse costs the list of
 * what changed, and must never cost the update itself.
 */
object ReleaseNotesFile {

    /** Where the file sits inside the app's assets. */
    const val ASSET = "releases/notes.json"

    /** What the release workflow names the copy it attaches to a release. */
    const val RELEASE_ASSET = "release-notes.json"

    @Serializable
    private data class FileDto(val versions: List<EntryDto> = emptyList())

    @Serializable
    private data class EntryDto(
        val version: String = "",
        val points: List<Map<String, String>> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun decode(text: String): List<ReleaseNotes.Entry> = runCatching {
        json.decodeFromString<FileDto>(text).versions
            .filter { it.version.isNotBlank() }
            .map { ReleaseNotes.Entry(version = it.version, points = it.points) }
    }.getOrElse { emptyList() }

    fun bundled(context: Context): List<ReleaseNotes.Entry> = runCatching {
        context.assets.open(ASSET).bufferedReader().use { decode(it.readText()) }
    }.getOrElse { emptyList() }
}
