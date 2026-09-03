package il.co.tradesmanager.ui.export

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFormatTest {

    @Test
    fun `plain fields are written unquoted`() {
        assertEquals("Socket", ExportFormat.escape("Socket"))
        assertEquals("מפסק פחת", ExportFormat.escape("מפסק פחת"))
    }

    @Test
    fun `a comma forces quoting`() {
        // Specifications routinely contain commas: "3 cores, 1.5 mm²".
        assertEquals("\"3 cores, 1.5 mm²\"", ExportFormat.escape("3 cores, 1.5 mm²"))
    }

    @Test
    fun `an embedded quote is doubled, not dropped`() {
        assertEquals("\"Pipe wrench 14\"\"\"", ExportFormat.escape("Pipe wrench 14\""))
    }

    @Test
    fun `a newline inside a note keeps the row intact`() {
        val escaped = ExportFormat.escape("line one\nline two")
        assertTrue(escaped.startsWith("\"") && escaped.endsWith("\""))
        assertTrue(escaped.contains("\n"))
    }

    @Test
    fun `csv starts with the BOM so Excel reads Hebrew as text`() {
        val csv = ExportFormat.csv(listOf("שם"), listOf(listOf("כבל")))
        assertEquals(ExportFormat.UTF8_BOM, csv.first())
    }

    @Test
    fun `csv uses CRLF line endings and one row per record`() {
        val csv = ExportFormat.csv(
            headers = listOf("a", "b"),
            rows = listOf(listOf("1", "2"), listOf("3", "4")),
        )
        assertEquals("${ExportFormat.UTF8_BOM}a,b\r\n1,2\r\n3,4\r\n", csv)
    }

    @Test
    fun `a field with a comma does not add a column`() {
        val csv = ExportFormat.csv(listOf("name", "spec"), listOf(listOf("Cable", "3 cores, 1.5")))
        val body = csv.dropWhile { it == ExportFormat.UTF8_BOM }.lines()[1]
        assertEquals("Cable,\"3 cores, 1.5\"", body)
    }

    @Test
    fun `filenames are reduced to ascii so they survive Windows and mail`() {
        val stem = ExportFormat.safeFileStem("דירה 4 חדרים", LocalDate.of(2026, 9, 3))
        assertEquals("4-2026-09-03", stem)
    }

    @Test
    fun `runs of replaced characters collapse rather than piling up dashes`() {
        assertEquals("Flat-A-2026-01-02", ExportFormat.safeFileStem("Flat // A", LocalDate.of(2026, 1, 2)))
    }

    @Test
    fun `a name with nothing usable still produces a filename`() {
        assertEquals("export-2026-01-02", ExportFormat.safeFileStem("״״", LocalDate.of(2026, 1, 2)))
    }
}
