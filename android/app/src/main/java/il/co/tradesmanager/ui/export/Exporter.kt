package il.co.tradesmanager.ui.export

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import il.co.tradesmanager.core.i18n.Formats
import java.io.File
import java.time.LocalDate
import java.util.Locale

/**
 * Writes an [ExportDocument] as CSV and PDF and hands both to the system share
 * sheet. Files go to the cache directory, which is what `res/xml/file_paths.xml`
 * exposes through the FileProvider: an export is a copy, and the system may
 * reclaim it once the user has sent it.
 */
object Exporter {

    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842
    private const val MARGIN = 40f

    data class Result(val csv: File, val pdf: File)

    fun write(
        context: Context,
        document: ExportDocument,
        languageTag: String,
        locale: Locale,
        rightToLeft: Boolean,
    ): Result {
        val table = document.table(context, languageTag, locale)
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val stem = document.fileStem()

        val csv = File(directory, "$stem.csv").apply {
            writeBytes(ExportFormat.csv(table.headers, table.rows).toByteArray(Charsets.UTF_8))
        }
        val pdf = File(directory, "$stem.pdf").apply {
            outputStream().use { writePdf(table, locale, rightToLeft, it) }
        }
        return Result(csv, pdf)
    }


    /**
     * A4 portrait, mirrored for Hebrew and Arabic. Deliberately plain: this
     * ends up in a site file or a tender folder, printed in black and white,
     * and read by someone who never used the app.
     */
    private fun writePdf(
        table: ExportDocument.Table,
        locale: Locale,
        rightToLeft: Boolean,
        output: java.io.OutputStream,
    ) {
        val pdf = PdfDocument()
        val columns = table.headers.size.coerceAtLeast(1)
        val usableWidth = A4_WIDTH - MARGIN * 2
        val columnWidth = usableWidth / columns

        val title = Paint().apply { textSize = 18f; isFakeBoldText = true; isAntiAlias = true }
        val header = Paint().apply { textSize = 10f; isFakeBoldText = true; isAntiAlias = true }
        val cell = Paint().apply { textSize = 10f; isAntiAlias = true }
        listOf(title, header, cell).forEach {
            it.textAlign = if (rightToLeft) Paint.Align.RIGHT else Paint.Align.LEFT
        }

        // Mirroring the column order is what makes an RTL export read correctly,
        // rather than a left-to-right table that happens to hold RTL text.
        fun columnX(index: Int): Float {
            val position = if (rightToLeft) columns - 1 - index else index
            return if (rightToLeft) {
                MARGIN + (position + 1) * columnWidth - 4f
            } else {
                MARGIN + position * columnWidth
            }
        }

        fun edgeX(): Float = if (rightToLeft) A4_WIDTH - MARGIN else MARGIN

        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN + 18f

        canvas.drawText(table.title, edgeX(), y, title)
        y += 22f
        canvas.drawText(Formats.date(LocalDate.now(), locale), edgeX(), y, cell)
        y += 24f
        table.headers.forEachIndexed { index, text -> canvas.drawText(text, columnX(index), y, header) }
        y += 18f

        table.rows.forEach { row ->
            if (y > A4_HEIGHT - MARGIN) {
                pdf.finishPage(page)
                pageNumber += 1
                page = pdf.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = MARGIN + 18f
            }
            row.take(columns).forEachIndexed { index, text ->
                canvas.drawText(truncate(text, cell, columnWidth - 6f), columnX(index), y, cell)
            }
            y += 16f
        }

        pdf.finishPage(page)
        pdf.writeTo(output)
        pdf.close()
    }

    /** Cells are clipped to their column so a long specification cannot
     *  overwrite the neighbouring one. */
    private fun truncate(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = paint.breakText(text, true, maxWidth - paint.measureText("…"), null)
        if (end <= 0) end = 1
        return text.take(end).trimEnd() + "…"
    }

    /** A share chooser holding both files, with read permission granted. */
    fun shareIntent(context: Context, result: Result): Intent {
        val authority = "${context.packageName}.fileprovider"
        val uris = arrayListOf(
            FileProvider.getUriForFile(context, authority, result.pdf),
            FileProvider.getUriForFile(context, authority, result.csv),
        )
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null)
    }
}
