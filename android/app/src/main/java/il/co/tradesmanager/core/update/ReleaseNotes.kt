package il.co.tradesmanager.core.update

import il.co.tradesmanager.core.i18n.LocalizedText
import il.co.tradesmanager.core.i18n.resolve

/**
 * What each version does, and which of it a particular phone needs to hear.
 *
 * The notes are written once, in `shared/assets/releases/notes.json`, in all
 * three languages. The same file ships inside the app and is attached to
 * every release, so a phone being offered an update and a phone that has just
 * installed it read the same words.
 *
 * They stack. Somebody on 0.17.0 being offered 0.20.0 is about to get
 * everything in 0.18, 0.19 and 0.20, and being told only about the last of
 * them is being told a third of what is about to change on their phone.
 */
object ReleaseNotes {

    /** One version and what it does, one point per line, each in three languages. */
    data class Entry(
        val version: String,
        val points: List<LocalizedText>,
    )

    /**
     * The versions after [installed], up to and including [target], newest
     * first.
     *
     * [installed] is null when it is not known -- a phone updating from a
     * version that did not yet record what it had shown -- and then only
     * [target] is given, rather than the whole history as if it were new.
     */
    fun between(entries: List<Entry>, installed: String?, target: String): List<Entry> {
        val ceiling = parse(target)
        val floor = installed?.let(::parse)
        return entries
            .filter { entry ->
                val version = parse(entry.version)
                compare(version, ceiling) <= 0 &&
                    if (floor == null) compare(version, ceiling) == 0 else compare(version, floor) > 0
            }
            .sortedWith { a, b -> compare(parse(b.version), parse(a.version)) }
    }

    /** An entry's points in [languageTag], falling back the way every other text in the app does. */
    fun points(entry: Entry, languageTag: String): List<String> =
        entry.points.map { it.resolve(languageTag) }.filter { it.isNotBlank() }

    /**
     * Whether to show "What's new" on this start.
     *
     * Not on a fresh install: somebody who has just installed the app for the
     * first time has nothing that changed, and a list of what is new since a
     * version they never had is noise on the first screen they see.
     */
    fun shouldShow(lastSeen: String?, current: String, freshInstall: Boolean): Boolean = when {
        freshInstall && lastSeen == null -> false
        lastSeen == null -> true
        else -> compare(parse(current), parse(lastSeen)) > 0
    }

    /** "v1.2.3", "1.2.3-debug" and "1.2.3" are all 1.2.3. */
    fun parse(version: String): List<Int> =
        version.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-')
            .split('.')
            .map { part -> part.filter { it.isDigit() }.toIntOrNull() ?: 0 }

    /** Part by part, a missing part counting as nought. */
    fun compare(a: List<Int>, b: List<Int>): Int {
        for (index in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(index) { 0 }
            val y = b.getOrElse(index) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}
