package il.co.tradesmanager.core.i18n

/**
 * Text carried in every language it has been written in, keyed by language tag.
 *
 * Catalogue content is localised as *data*, not as Android resources: the
 * catalogues ship as JSON that iOS bundles unchanged, and a user typing their
 * own item name in Arabic must be able to store it next to a Hebrew one. A
 * fourth language therefore needs no schema change — only more keys.
 */
typealias LocalizedText = Map<String, String>

/** The source language, used as the fallback before giving up on order. */
const val SOURCE_LANGUAGE: String = "en"

/**
 * Best available rendering of this text for [languageTag].
 *
 * Falls back to the base language of a regional tag ("he-IL" -> "he"), then to
 * the source language, then to any translation present, so a partially
 * translated catalogue still shows something rather than an empty row.
 */
fun LocalizedText.resolve(languageTag: String): String {
    this[languageTag]?.takeIf { it.isNotBlank() }?.let { return it }
    val base = languageTag.substringBefore('-')
    this[base]?.takeIf { it.isNotBlank() }?.let { return it }
    this[SOURCE_LANGUAGE]?.takeIf { it.isNotBlank() }?.let { return it }
    return values.firstOrNull { it.isNotBlank() }.orEmpty()
}

/** Every translation joined, lowercased — what the search index is built from. */
fun LocalizedText.searchable(): String =
    values.joinToString(" ") { it.lowercase() }

fun localizedTextOf(vararg pairs: Pair<String, String>): LocalizedText =
    pairs.filter { it.second.isNotBlank() }.toMap()
