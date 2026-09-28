package il.co.tradesmanager.ui.export

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Many exported files as one: the whole record of a job, for the client at
 * handover or the firm's own archive.
 *
 * Plain JVM on purpose, so it is tested without Android. Entries are the
 * files' own names at the top of the archive; a second file with a name
 * already taken gets a number before its extension rather than silently
 * replacing the first.
 */
object ExportArchive {

    fun zip(files: List<File>, target: File): File {
        target.parentFile?.mkdirs()
        val used = mutableSetOf<String>()
        ZipOutputStream(target.outputStream().buffered()).use { out ->
            files.filter { it.isFile }.forEach { file ->
                val name = uniqueName(file.name, used)
                used += name
                out.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        return target
    }

    /** "inspections.pdf", then "inspections-2.pdf", "inspections-3.pdf". */
    fun uniqueName(name: String, used: Set<String>): String {
        if (name !in used) return name
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val stem = name.substring(0, dot)
        val extension = name.substring(dot)
        var n = 2
        while ("$stem-$n$extension" in used) n++
        return "$stem-$n$extension"
    }
}
