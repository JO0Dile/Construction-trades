package il.co.tradesmanager.ui.export

import java.time.LocalDate

/**
 * The parts of exporting that are pure text, kept out of the Android-only
 * renderers so they can be unit tested. The PDF needs a Canvas; a correctly
 * quoted CSV and a filename that survives Windows do not.
 */
object ExportFormat {

    /**
     * Excel on Windows needs this to read a UTF-8 CSV as text rather than
     * mojibake — the single most common complaint about exported site data
     * once it leaves a Hebrew or Arabic phone.
     */
    const val UTF8_BOM: Char = '﻿'

    private const val QUOTE = "\""
    private const val CRLF = "\r\n"

    /** RFC 4180: fields holding a comma, quote or newline are quoted, and an
     *  embedded quote is doubled. */
    fun escape(field: String): String {
        if (field.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) return field
        return QUOTE + field.replace(QUOTE, QUOTE + QUOTE) + QUOTE
    }

    fun csv(headers: List<String>, rows: List<List<String>>): String = buildString {
        append(UTF8_BOM)
        append(headers.joinToString(",", transform = ::escape)).append(CRLF)
        rows.forEach { row ->
            append(row.joinToString(",", transform = ::escape)).append(CRLF)
        }
    }

    /**
     * A filename that survives the trip out of the app.
     *
     * Hebrew and Arabic filenames are fine on the device and in the share
     * sheet, but not in every mail client, Windows share or tender portal an
     * export lands in — so the name is reduced to ASCII and the content keeps
     * the language.
     */
    fun safeFileStem(base: String, date: LocalDate = LocalDate.now()): String {
        val safe = base
            .map { if (it.isLetterOrDigit() && it.code < 128) it else '-' }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')
            .ifEmpty { "export" }
        return "$safe-$date"
    }
}
