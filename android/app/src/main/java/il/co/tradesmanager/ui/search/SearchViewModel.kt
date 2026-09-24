package il.co.tradesmanager.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.evidence.Inspections
import il.co.tradesmanager.core.evidence.Permits
import il.co.tradesmanager.core.evidence.Snags
import il.co.tradesmanager.core.find.Search
import il.co.tradesmanager.core.i18n.SOURCE_LANGUAGE
import il.co.tradesmanager.core.i18n.resolve
import il.co.tradesmanager.core.i18n.searchable
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.repository.EquipmentRepository
import il.co.tradesmanager.data.repository.ProjectRepository
import il.co.tradesmanager.data.repository.PurchasingRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * One box, seven registers.
 *
 * The crew screen says a search box is no use to a safety officer who watched
 * a man walk away, and that is true and is why the trade grid exists: you
 * cannot type a name you never knew. This is the other half of the same
 * problem. Somebody who *does* know part of it — "the Hanson order", "the
 * permit for the third floor", "Dani something" — currently has to know which
 * of forty-three screens it lives behind before they can start looking.
 *
 * Two things this is careful about.
 *
 * **It reads nothing the viewer may not see.** The lens each kind needs is on
 * [Search.Kind], copied off the screens themselves, and it is checked *before*
 * the rows are read rather than after. A search that loads a wage bill and
 * then filters it has already loaded the wage bill.
 *
 * **It is a question, not a subscription.** Every register here has a live
 * Flow and none of them is used: keeping eight queries over the whole database
 * up to date, re-sorted on every keystroke, is how a phone gets hot in
 * somebody's pocket. [mapLatest] cancels the previous search when the next
 * letter arrives, so what is running is always the answer to what is on the
 * screen.
 */
class SearchViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun setQuery(text: String) {
        _query.value = text
    }

    /**
     * The language to show a catalogue item's name in.
     *
     * Set by the screen, which is the only place that knows. It changes what
     * is *displayed*, never what is matched: an item is matched on every name
     * it has, so a Hebrew-speaking storeman finds the box somebody labelled in
     * English.
     */
    private val _language = MutableStateFlow(SOURCE_LANGUAGE)

    fun setLanguage(tag: String) {
        _language.value = tag
    }

    /**
     * Results, with the query they are the answer to.
     *
     * Carrying the query is what lets the screen tell "nothing matched" from
     * "still looking" without a spinner that flickers on every letter: the
     * list is empty and stale until [forQuery] catches up with what has been
     * typed, and only then does "nothing found" mean it.
     */
    data class Answer(val forQuery: String, val hits: List<Search.Hit>)

    @OptIn(ExperimentalCoroutinesApi::class)
    val results: StateFlow<Answer> = combine(_query, _language) { text, language ->
        text to language
    }
        .mapLatest { (text, language) -> Answer(text, find(text, language)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Answer("", emptyList()))

    private suspend fun find(text: String, language: String): List<Search.Hit> {
        val terms = Search.terms(text)
        if (terms.isEmpty()) return emptyList()

        val signedIn = container.session.state.first() as? SessionRepository.State.SignedIn
        // Null is a personal account with no company and no role grid, which
        // the whole app reads as "allowed" — the same `!= false` the screens
        // use. Writing it as `== true` here would lock a sole trader out of
        // searching their own work.
        fun mayRead(kind: Search.Kind) = kind.lenses.any { signedIn?.canRead(it) != false }

        // Which job a permit or an order is on is the most orienting thing
        // that can go on its row — PTW-014 means nothing, PTW-014 on the Ramat
        // Gan job means something — so the jobs are read once here rather than
        // per row. Only when they may be read at all: every other kind that
        // wants a name out of this needs Stuff or Evidence, and a job is
        // readable to anyone holding either, so there is no case where a row
        // wants a job name that its reader is not entitled to.
        val jobRows = if (mayRead(Search.Kind.JOB)) {
            container.projects.observeProjects().first()
        } else {
            emptyList<ProjectEntity>()
        }
        val jobNames = jobRows.associate { it.id to it.name }

        val hits = mutableListOf<Search.Hit>()
        if (mayRead(Search.Kind.JOB)) hits += jobs(terms, jobRows)
        if (mayRead(Search.Kind.PERSON)) hits += people(terms, signedIn)
        if (mayRead(Search.Kind.ITEM)) hits += items(terms, language)
        if (mayRead(Search.Kind.ORDER)) hits += orders(terms, jobNames)
        if (mayRead(Search.Kind.PERMIT)) hits += permits(terms, jobNames)
        if (mayRead(Search.Kind.SNAG)) hits += snags(terms, jobNames)
        if (mayRead(Search.Kind.PLANT)) hits += plant(terms, jobNames)
        if (mayRead(Search.Kind.DRAWING)) hits += drawings(terms, jobNames)
        if (mayRead(Search.Kind.QUERY)) hits += queries(terms, jobNames)
        if (mayRead(Search.Kind.VISITOR)) hits += visitors(terms, jobNames)
        if (mayRead(Search.Kind.INSPECTION)) hits += inspections(terms, jobNames)
        return Search.best(hits)
    }

    /* ------------------------------------------------------------ the jobs */

    private fun jobs(
        terms: List<String>,
        rows: List<ProjectEntity>,
    ): List<Search.Hit> =
        // Already scoped to the active company by the repository, which is
        // where that rule lives for every other screen too.
        rows.mapNotNull { job ->
            hit(
                id = job.id,
                kind = Search.Kind.JOB,
                title = job.name,
                detail = listOfNotNull(job.clientName, job.street, job.city, job.kindLabel)
                    .joinToString(" "),
                matchAlso = job.status,
                isOpen = job.status != ProjectRepository.Status.DONE,
                terms = terms,
            )
        }

    /* ---------------------------------------------------------- the people */

    /**
     * The crew, joined the way the crew screen joins it.
     *
     * Memberships first and accounts second, rather than every account on the
     * device: the account table holds everyone who has ever signed in on this
     * phone, and the crew of the company you are standing in is a different
     * and smaller list. The id on the hit is the **membership**, because that
     * is what opens a profile.
     *
     * An ID number is matched and not shown. Typing one is how the gate finds
     * a man and it has to keep working here — but a list of results is read
     * over somebody's shoulder, and a screen that prints everybody's teudat
     * zehut next to their name is a screen that should not exist. The profile
     * behind the row is where that belongs, and it asks who is looking.
     */
    private suspend fun people(
        terms: List<String>,
        signedIn: SessionRepository.State.SignedIn?,
    ): List<Search.Hit> {
        val companyId = signedIn?.active?.companyId
        val rows = container.memberships.observeForCompany(companyId).first()
        if (rows.isEmpty()) return emptyList()
        val accounts = container.accounts.observeAccounts().first().associateBy { it.id }

        return rows.mapNotNull { row ->
            val account = accounts[row.accountId] ?: return@mapNotNull null
            hit(
                id = row.id,
                kind = Search.Kind.PERSON,
                title = account.displayName,
                detail = account.phone.orEmpty(),
                matchAlso = listOfNotNull(
                    account.username,
                    account.idNumber,
                    account.email,
                    row.role,
                ).joinToString(" "),
                // Somebody who has left the books is still worth finding —
                // half the reason to look a name up is a record from last
                // year — but they sort under the people who are still here.
                isOpen = row.leftAt == null,
                terms = terms,
            )
        }
    }

    /* ----------------------------------------------------------- the items */

    private suspend fun items(terms: List<String>, language: String): List<Search.Hit> =
        container.inventory.observe(
            query = "",
            kind = null,
            lowStockOnly = false,
        ).first().mapNotNull { item ->
            hit(
                id = item.id,
                kind = Search.Kind.ITEM,
                title = item.names.resolve(language),
                detail = item.spec.resolve(language),
                // searchIndex already holds every name, the spec, the tags,
                // the category and the barcode, built at insert time. Matching
                // on it is how an item labelled in one language is found by
                // somebody typing another.
                matchAlso = listOfNotNull(
                    item.names.searchable(),
                    item.searchIndex,
                    item.barcode,
                ).joinToString(" "),
                // Stock that has run out is still a real line in the
                // catalogue; it just is not the one somebody is hoping for.
                isOpen = item.quantity > 0.0,
                terms = terms,
            )
        }

    /* ---------------------------------------------------------- the orders */

    private suspend fun orders(
        terms: List<String>,
        jobNames: Map<String, String>,
    ): List<Search.Hit> =
        container.purchasing.observeOrders().first().mapNotNull { order ->
            hit(
                id = order.id,
                kind = Search.Kind.ORDER,
                title = order.reference,
                detail = listOfNotNull(
                    order.supplierName,
                    order.projectId?.let { jobNames[it] },
                    order.notes,
                ).joinToString(" "),
                matchAlso = order.status,
                isOpen = order.status != PurchasingRepository.Status.RECEIVED &&
                    order.status != PurchasingRepository.Status.CANCELLED,
                terms = terms,
            )
        }

    /* --------------------------------------------------------- the permits */

    private suspend fun permits(
        terms: List<String>,
        jobNames: Map<String, String>,
    ): List<Search.Hit> =
        container.evidence.allPermits().mapNotNull { permit ->
            hit(
                id = permit.id,
                kind = Search.Kind.PERMIT,
                title = permit.reference,
                detail = listOfNotNull(
                    permit.projectId?.let { jobNames[it] },
                    permit.description,
                    permit.location,
                    permit.issuedToName,
                ).joinToString(" "),
                // The type is stored as HOT_WORK and folds to "hot work", so
                // typing what the job is finds the permit for it without the
                // screen having to print a constant at anybody.
                matchAlso = listOfNotNull(permit.type, permit.issuedByName, permit.status)
                    .joinToString(" "),
                // Issued, not "not closed". A draft authorises nothing and a
                // cancelled one authorises nothing, and neither is live work.
                isOpen = permit.status == Permits.Status.ISSUED,
                terms = terms,
            )
        }

    /* ----------------------------------------------------------- the snags */

    private suspend fun snags(
        terms: List<String>,
        jobNames: Map<String, String>,
    ): List<Search.Hit> =
        container.evidence.allSnags().mapNotNull { snag ->
            hit(
                id = snag.id,
                kind = Search.Kind.SNAG,
                // A snag is called what it is, not what it is numbered — but
                // the number is what gets argued about, so it is on the row.
                title = snag.title,
                detail = listOfNotNull(
                    snag.reference,
                    jobNames[snag.projectId],
                    snag.location,
                ).joinToString(" "),
                matchAlso = listOfNotNull(snag.assignedToName, snag.raisedByName, snag.status)
                    .joinToString(" "),
                isOpen = snag.status != Snags.Status.CLOSED &&
                    snag.status != Snags.Status.REJECTED,
                terms = terms,
            )
        }

    /* ----------------------------------------------------------- the plant */

    private suspend fun plant(
        terms: List<String>,
        jobNames: Map<String, String>,
    ): List<Search.Hit> =
        container.equipment.observeAll().first().mapNotNull { kit ->
            hit(
                id = kit.id,
                kind = Search.Kind.PLANT,
                title = kit.name,
                detail = listOfNotNull(
                    kit.assignedProjectId?.let { jobNames[it] },
                    kit.serialNumber,
                    kit.notes,
                ).joinToString(" "),
                matchAlso = kit.status,
                isOpen = kit.status != EquipmentRepository.Status.OFF_HIRE,
                terms = terms,
            )
        }

    /* -------------------------------------------------------- the drawings */

    private suspend fun drawings(
        terms: List<String>,
        jobNames: Map<String, String>,
    ): List<Search.Hit> =
        container.drawings.all().mapNotNull { drawing ->
            hit(
                id = drawing.id,
                kind = Search.Kind.DRAWING,
                // The number is what people say: "the A-101", not the title.
                title = listOf(drawing.number, drawing.title).filter { it.isNotBlank() }.joinToString(" "),
                detail = listOfNotNull(drawing.revision, jobNames[drawing.projectId]).joinToString(" "),
                matchAlso = drawing.notes.orEmpty(),
                // A superseded revision is history: found, but below the one
                // to build from.
                isOpen = drawing.supersededAt == null,
                terms = terms,
                projectId = drawing.projectId,
            )
        }

    /* -------------------------------------------- questions to the designers */

    private suspend fun queries(
        terms: List<String>,
        jobNames: Map<String, String>,
    ): List<Search.Hit> =
        container.designQueries.all().mapNotNull { query ->
            hit(
                id = query.id,
                kind = Search.Kind.QUERY,
                title = query.question,
                detail = listOfNotNull(query.reference, query.askedOf, jobNames[query.projectId]).joinToString(" "),
                matchAlso = listOfNotNull(query.drawingNumber, query.answer).joinToString(" "),
                isOpen = query.answeredAt == null,
                terms = terms,
                projectId = query.projectId,
            )
        }

    /* -------------------------------------------------------- the visitors */

    private suspend fun visitors(
        terms: List<String>,
        jobNames: Map<String, String>,
    ): List<Search.Hit> =
        container.visits.all().mapNotNull { visit ->
            hit(
                id = visit.id,
                kind = Search.Kind.VISITOR,
                title = visit.name,
                detail = listOfNotNull(visit.organisation, jobNames[visit.projectId]).joinToString(" "),
                // Who they came to see is worth matching; their phone number is not shown.
                matchAlso = visit.hostName.orEmpty(),
                isOpen = visit.leftAt == null,
                terms = terms,
                projectId = visit.projectId,
            )
        }

    /* ----------------------------------------------------- the inspections */
    private suspend fun inspections(
        terms: List<String>,
        jobNames: Map<String, String>,
    ): List<Search.Hit> =
        container.inspections.all().mapNotNull { inspection ->
            hit(
                id = inspection.id,
                kind = Search.Kind.INSPECTION,
                title = inspection.reference + " " + inspection.element,
                detail = listOfNotNull(inspection.requestedOf, jobNames[inspection.projectId]).joinToString(" "),
                // Whoever inspected, and what they wrote: "the engineer said
                // the laps were short" is found by "laps".
                matchAlso = listOfNotNull(inspection.inspectorName, inspection.comments).joinToString(" "),
                // Still open until somebody has passed it; a failed one is
                // the most open of all.
                isOpen = !Inspections.passed(Inspections.resultOf(inspection.result)),
                terms = terms,
                projectId = inspection.projectId,
            )
        }

    /**
     * Scores a row, and drops it here rather than building a hit to throw away.
     *
     * [detail] is shown and matched; [matchAlso] is matched and not shown. The
     * split exists because the most useful things to match on are the worst
     * things to print: a status held as PART_RECEIVED, a role held as
     * SITE_MANAGER, an ID number. Folding turns the first two into the words
     * somebody would actually type, and the third belongs behind a screen that
     * asks who is looking.
     */
    private fun hit(
        id: String,
        kind: Search.Kind,
        title: String,
        detail: String,
        matchAlso: String,
        isOpen: Boolean,
        terms: List<String>,
        projectId: String? = null,
    ): Search.Hit? {
        val score = Search.score(terms, title, "$detail $matchAlso")
        if (score == 0) return null
        return Search.Hit(
            id = id,
            kind = kind,
            title = title,
            detail = detail,
            isOpen = isOpen,
            score = score,
            projectId = projectId,
        )
    }
}
