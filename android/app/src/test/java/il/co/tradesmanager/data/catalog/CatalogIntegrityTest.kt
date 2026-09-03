package il.co.tradesmanager.data.catalog

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the shipped catalogues against the mistakes that are invisible until
 * a user is standing on a site with the wrong language or a template that
 * points at an item nobody stocks.
 *
 * These files are read unchanged by the iOS target too, so a break here breaks
 * both apps — which is exactly why the check lives with the data rather than
 * in either app's UI tests.
 */
class CatalogIntegrityTest {

    private val languages = listOf("en", "he", "ar")

    // Deliberately strict: an unknown key means the JSON and the DTOs have
    // drifted, which is the failure that would otherwise surface as a silently
    // dropped field at runtime.
    private val json = Json { ignoreUnknownKeys = false }

    /**
     * Walks up from the working directory to find shared/assets/catalog, so
     * the test passes whether Gradle runs it from the app module, from the
     * android/ root, or from the repository root.
     */
    private val root: File = generateSequence(File(".").absoluteFile) { it.parentFile }
        .map { File(it, "shared/assets/catalog") }
        .firstOrNull { File(it, "manifest.json").isFile }
        ?: File("shared/assets/catalog")

    private fun read(path: String): String = File(root, path).readText()

    private val manifest: CatalogManifest by lazy {
        json.decodeFromString(read("manifest.json"))
    }

    @Test
    fun `catalogue root is where both platforms expect it`() {
        assertTrue(
            "shared/assets/catalog not found above ${File(".").absolutePath} — the " +
                "Android asset source set and the iOS bundle both point at it",
            File(root, "manifest.json").isFile,
        )
    }

    @Test
    fun `every trade file parses and matches its manifest entry`() {
        assertTrue("no trades in the manifest", manifest.trades.isNotEmpty())
        manifest.trades.forEach { trade ->
            val items = json.decodeFromString<CatalogItemFile>(read(trade.itemsFile))
            assertEquals("tradeId mismatch in ${trade.itemsFile}", trade.id, items.tradeId)
            assertTrue("${trade.id} ships no items", items.items.isNotEmpty())
        }
    }

    @Test
    fun `every catalogue item is named and specified in all shipped languages`() {
        val missing = manifest.trades.flatMap { trade ->
            json.decodeFromString<CatalogItemFile>(read(trade.itemsFile)).items.flatMap { item ->
                languages.mapNotNull { lang ->
                    when {
                        item.names[lang].isNullOrBlank() -> "${item.id}: missing $lang name"
                        item.spec[lang].isNullOrBlank() -> "${item.id}: missing $lang spec"
                        else -> null
                    }
                }
            }
        }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `catalogue item ids are unique across every trade`() {
        val ids = manifest.trades.flatMap { trade ->
            json.decodeFromString<CatalogItemFile>(read(trade.itemsFile)).items.map { it.id }
        }
        val duplicates = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertEquals(
            "duplicate ids would make the seeder's duplicate guard ambiguous",
            emptySet<String>(),
            duplicates,
        )
    }

    @Test
    fun `every safety check is written in all shipped languages`() {
        val missing = manifest.trades.mapNotNull { it.safetyFile }.flatMap { path ->
            json.decodeFromString<SafetyFile>(read(path)).checklists.flatMap { list ->
                languages.filter { list.titles[it].isNullOrBlank() }.map { "${list.id}: missing $it title" } +
                    list.items.flatMap { check ->
                        languages.filter { check.texts[it].isNullOrBlank() }
                            .map { "${check.id}: missing $it text" }
                    }
            }
        }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `mandatory checklists carry at least one critical check`() {
        val toothless = manifest.trades.mapNotNull { it.safetyFile }.flatMap { path ->
            json.decodeFromString<SafetyFile>(read(path)).checklists
                .filter { it.mandatoryBeforeWork && it.items.none { check -> check.critical } }
                .map { it.id }
        }
        assertEquals(
            "a checklist that blocks work with nothing critical in it can always be signed",
            emptyList<String>(),
            toothless,
        )
    }

    @Test
    fun `every safety checklist cites the standard or regulation it comes from`() {
        val unsourced = manifest.trades.mapNotNull { it.safetyFile }.flatMap { path ->
            json.decodeFromString<SafetyFile>(read(path)).checklists
                .filter { it.references.isEmpty() }
                .map { it.id }
        }
        assertEquals(emptyList<String>(), unsourced)
    }

    @Test
    fun `every template material line resolves to a real catalogue item`() {
        val known = manifest.trades.flatMap { trade ->
            json.decodeFromString<CatalogItemFile>(read(trade.itemsFile)).items.map { it.id }
        }.toSet()

        val dangling = manifest.trades.mapNotNull { it.templatesFile }.flatMap { path ->
            json.decodeFromString<TemplateFile>(read(path)).templates.flatMap { template ->
                template.materials
                    .filterNot { it.itemId in known }
                    .map { "${template.id} -> ${it.itemId}" }
            }
        }
        assertEquals(
            "a template that names a missing item creates a project with an unbuyable line",
            emptyList<String>(),
            dangling,
        )
    }

    @Test
    fun `every template is named in all shipped languages and has ordered tasks`() {
        val problems = manifest.trades.mapNotNull { it.templatesFile }.flatMap { path ->
            json.decodeFromString<TemplateFile>(read(path)).templates.flatMap { template ->
                languages.filter { template.names[it].isNullOrBlank() }
                    .map { "${template.id}: missing $it name" } +
                    if (template.tasks.map { it.sortOrder }.distinct().size == template.tasks.size) {
                        emptyList()
                    } else {
                        listOf("${template.id}: duplicate task order")
                    }
            }
        }
        assertEquals(emptyList<String>(), problems)
    }
}
