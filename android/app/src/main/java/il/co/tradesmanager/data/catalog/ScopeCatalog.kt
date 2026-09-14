package il.co.tradesmanager.data.catalog

import il.co.tradesmanager.core.i18n.LocalizedText

/**
 * The work breakdown, read once and held.
 *
 * Not seeded into the database like the trades are. Stages and scopes are
 * read-only reference data that nothing edits, nothing counts and nothing
 * takes stock of — putting them in Room would buy a migration and a DAO and
 * answer no question the file cannot.
 */
class ScopeCatalog(private val source: CatalogSource) {

    private val document: ScopeFile by lazy {
        val path = source.manifest().scopesFile ?: return@lazy EMPTY
        runCatching { source.decode<ScopeFile>(path) }.getOrDefault(EMPTY)
    }

    val stages: List<WorkStage> get() = document.stages

    val phases: List<WorkPhase> get() = document.phases

    val scopes: List<WorkScope> get() = document.scopes

    fun stage(id: String?): WorkStage? =
        id?.let { wanted -> stages.firstOrNull { it.id == wanted } }

    fun scope(id: String?): WorkScope? =
        id?.let { wanted -> scopes.firstOrNull { it.id == wanted } }

    /** The scopes belonging to one stage, for narrowing a picker. */
    fun scopesInStage(stageId: String): List<WorkScope> = scopes.filter { it.stageId == stageId }

    /**
     * Search across the formal name and what the crew actually calls it.
     *
     * The colloquial terms are the point. A foreman types "العقدة" and has to
     * land on slab conduit; a search that only knows the contract wording
     * finds nothing and teaches him the search does not work.
     */
    fun search(query: String): List<WorkScope> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return scopes
        return scopes.filter { scope ->
            scope.names.matches(needle) || scope.colloquial.matches(needle)
        }
    }

    private fun LocalizedText.matches(needle: String): Boolean =
        values.any { it.lowercase().contains(needle) }

    private companion object {
        val EMPTY = ScopeFile(schemaVersion = 0, catalogVersion = 0)
    }
}
