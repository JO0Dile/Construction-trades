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
    fun `every catalogue item is named in all shipped languages`() {
        val missing = manifest.trades.flatMap { trade ->
            json.decodeFromString<CatalogItemFile>(read(trade.itemsFile)).items.flatMap { item ->
                languages.filter { item.names[it].isNullOrBlank() }
                    .map { "${item.id}: missing $it name" }
            }
        }
        assertEquals(emptyList<String>(), missing)
    }

    /**
     * A spec is optional — "Side cutters" needs no sentence explaining it, and
     * inventing one in three languages produces filler rather than help. What
     * is not optional is finishing the job: an item with an English spec and
     * no Hebrew one reads as half-translated on the phone it matters on.
     */
    @Test
    fun `an item with a spec has it in every shipped language`() {
        val partial = manifest.trades.flatMap { trade ->
            json.decodeFromString<CatalogItemFile>(read(trade.itemsFile)).items
                .filter { item -> item.spec.values.any { it.isNotBlank() } }
                .flatMap { item ->
                    languages.filter { item.spec[it].isNullOrBlank() }
                        .map { "${item.id}: missing $it spec" }
                }
        }
        assertEquals(emptyList<String>(), partial)
    }

    @Test
    fun `the work breakdown is complete and its references resolve`() {
        val path = manifest.scopesFile
        assertTrue("the manifest names no scopes file", path != null)
        val doc = json.decodeFromString<ScopeFile>(read(path!!))

        assertTrue("no stages", doc.stages.isNotEmpty())
        assertTrue("no scopes", doc.scopes.isNotEmpty())

        val missing = (
            doc.stages.flatMap { stage ->
                languages.filter { stage.names[it].isNullOrBlank() }
                    .map { "stage ${stage.id}: missing $it" }
            } + doc.phases.flatMap { phase ->
                languages.filter { phase.names[it].isNullOrBlank() }
                    .map { "phase ${phase.id}: missing $it" }
            } + doc.scopes.flatMap { scope ->
                languages.filter { scope.names[it].isNullOrBlank() }
                    .map { "scope ${scope.id}: missing $it" }
            }
            )
        assertEquals(
            "an untranslated scope is a task nobody on a mixed site can read",
            emptyList<String>(),
            missing,
        )

        val stageIds = doc.stages.map { it.id }.toSet()
        val phaseIds = doc.phases.map { it.id }.toSet()
        val tradeIds = manifest.trades.map { it.id }.toSet()
        val dangling = doc.scopes.flatMap { scope ->
            listOfNotNull(
                "${scope.id} -> stage ${scope.stageId}".takeIf { scope.stageId !in stageIds },
                "${scope.id} -> phase ${scope.phaseId}".takeIf { scope.phaseId !in phaseIds },
                "${scope.id} -> trade ${scope.tradeId}".takeIf {
                    scope.tradeId.isNotEmpty() && scope.tradeId !in tradeIds
                },
            )
        }
        assertEquals(
            "a scope pointing at a stage, phase or trade that does not exist " +
                "cannot be filtered, priced or assigned",
            emptyList<String>(),
            dangling,
        )

        val duplicates = doc.scopes.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertEquals(emptySet<String>(), duplicates)
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

    /**
     * The id prefix is a per-trade namespace, and the databases already
     * installed on phones are keyed on it. Two trades reaching into the same
     * prefix is how one trade's edit silently renames another's item.
     */
    @Test
    fun `each id prefix belongs to exactly one trade`() {
        val owners = mutableMapOf<String, MutableSet<String>>()
        manifest.trades.forEach { trade ->
            json.decodeFromString<CatalogItemFile>(read(trade.itemsFile)).items.forEach { item ->
                owners.getOrPut(item.id.substringBefore('.')) { mutableSetOf() } += trade.id
            }
        }
        assertEquals(
            emptyMap<String, Set<String>>(),
            owners.filterValues { it.size > 1 },
        )
    }

    /**
     * Across trades a shared tool is fine — an HVAC engineer stocks their own
     * clamp meter, and each trade's list has to stand alone. Twice within one
     * trade is a stock list that shows the same thing twice and counts it
     * twice.
     */
    @Test
    fun `no trade lists the same thing twice`() {
        val repeated = manifest.trades.flatMap { trade ->
            json.decodeFromString<CatalogItemFile>(read(trade.itemsFile)).items
                .groupingBy { it.names["en"].orEmpty().lowercase() }
                .eachCount()
                .filterValues { it > 1 }
                .keys
                .map { "${trade.id}: $it" }
        }
        assertEquals(emptyList<String>(), repeated)
    }

    @Test
    fun `every catalogue category has an icon`() {
        val categories = manifest.trades.flatMap { trade ->
            json.decodeFromString<CatalogItemFile>(read(trade.itemsFile)).items.map { it.category }
        }.toSet()

        assertEquals(
            "a category with no icon draws as a generic box, which is how a new " +
                "trade quietly ships looking unfinished",
            emptySet<String>(),
            categories - manifest.categoryIcons.keys,
        )
        assertEquals(
            "an icon mapping for a category nothing uses is dead weight",
            emptySet<String>(),
            manifest.categoryIcons.keys - categories,
        )
    }

    @Test
    fun `every kind of place is offered in all shipped languages`() {
        assertTrue("no project kinds in the manifest", manifest.projectKinds.isNotEmpty())

        val missing = manifest.projectKinds.flatMap { kind ->
            languages.filter { kind.names[it].isNullOrBlank() }.map { "${kind.id}:$it" }
        }
        assertEquals(
            "an untranslated kind shows a job as \"lobby\" on a Hebrew phone",
            emptyList<String>(),
            missing,
        )

        val duplicates = manifest.projectKinds.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertEquals(emptySet<String>(), duplicates)
    }

    @Test
    fun `every ticket the app suggests is named in all shipped languages`() {
        assertTrue("no certification kinds in the manifest", manifest.certificationKinds.isNotEmpty())

        val missing = manifest.certificationKinds.flatMap { kind ->
            languages.filter { kind.names[it].isNullOrBlank() }.map { "${kind.id}:$it" }
        }
        assertEquals(
            "a ticket suggested only in English is no suggestion to a Hebrew or " +
                "Arabic speaking foreman",
            emptyList<String>(),
            missing,
        )

        val duplicates = manifest.certificationKinds
            .groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertEquals(emptySet<String>(), duplicates)
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
