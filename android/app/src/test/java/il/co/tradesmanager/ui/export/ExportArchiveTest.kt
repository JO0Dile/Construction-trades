package il.co.tradesmanager.ui.export

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportArchiveTest {

    @Test
    fun `every file goes in under its own name, and a clash gets a number rather than replacing the first`() {
        val dir = Files.createTempDirectory("archive").toFile()
        val a = File(dir, "a").apply { mkdirs() }
        val b = File(dir, "b").apply { mkdirs() }
        val first = File(a, "inspections.pdf").apply { writeText("first") }
        val second = File(b, "inspections.pdf").apply { writeText("second") }
        val csv = File(a, "inspections.csv").apply { writeText("No.,Kind") }
        val missing = File(a, "never-written.csv")

        val zip = ExportArchive.zip(listOf(first, csv, second, missing), File(dir, "out/job.zip"))

        ZipFile(zip).use { archive ->
            val names = archive.entries().toList().map { it.name }
            assertEquals(listOf("inspections.pdf", "inspections.csv", "inspections-2.pdf"), names)
            assertEquals("first", archive.getInputStream(archive.getEntry("inspections.pdf")).reader().readText())
            assertEquals("second", archive.getInputStream(archive.getEntry("inspections-2.pdf")).reader().readText())
        }
        dir.deleteRecursively()
    }

    @Test
    fun `names are numbered past any already taken`() {
        assertEquals("delays.pdf", ExportArchive.uniqueName("delays.pdf", emptySet()))
        assertEquals("delays-3.pdf", ExportArchive.uniqueName("delays.pdf", setOf("delays.pdf", "delays-2.pdf")))
        assertEquals("README-2", ExportArchive.uniqueName("README", setOf("README")))
    }
}
