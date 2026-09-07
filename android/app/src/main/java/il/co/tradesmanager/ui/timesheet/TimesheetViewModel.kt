package il.co.tradesmanager.ui.timesheet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.money.JobFinancials
import il.co.tradesmanager.core.money.Timesheet
import il.co.tradesmanager.data.local.entity.TimeEntryEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The hours clocked on one job, turned into a timesheet.
 *
 * The mapping from stored rows to the arithmetic's own input type happens here:
 * `core.money` does not know what a database is, and this is the layer whose
 * job it is to.
 */
class TimesheetViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    /**
     * A job's shifts, split into the ones whose money this viewer may see and
     * the people whose money they may not.
     *
     * The split happens here, before anything is added up. `core.money` does
     * arithmetic and has no business knowing who is looking, and a total that
     * silently included wages the viewer is not entitled to would be a number
     * that leaks a rate by subtraction.
     */
    data class Visible(
        val shifts: List<Timesheet.Shift>,
        /** How many people on this job are not this viewer's to price. */
        val withheldPeople: Int,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val visible: StateFlow<Visible> = combine(
        container.schedule.observeCompletedTimeEntries(projectId),
        session,
    ) { entries, state -> entries to state }
        .flatMapLatest { (entries, state) ->
            flow { emit(split(entries, state)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Visible(emptyList(), 0))

    private suspend fun split(
        entries: List<TimeEntryEntity>,
        state: SessionRepository.State,
    ): Visible {
        val signedIn = state as? SessionRepository.State.SignedIn
            ?: return Visible(emptyList(), 0)
        val companyId = signedIn.active?.companyId
        // A personal account has no company, no chain and nobody to keep
        // anything from. Everything on it is theirs.
        if (companyId == null) {
            return Visible(entries.mapNotNull { it.toShift() }, 0)
        }
        val me = signedIn.active?.id.orEmpty()
        val role = signedIn.role
        val allowed = container.memberships.payVisibleTo(companyId, me, role)

        val (mine, withheld) = entries.partition { entry ->
            when (val membership = entry.workerMembershipId) {
                // Shifts recorded before the app knew who was working them.
                // Nobody can prove whose they are, so they go to whoever signs
                // the payroll and to nobody else. Showing them to everyone
                // would make the gap in the old data a way around the rule.
                null -> role == Role.OWNER
                else -> membership in allowed
            }
        }
        return Visible(
            shifts = mine.mapNotNull { it.toShift() },
            withheldPeople = withheld.map { it.workerName }.toSet().size,
        )
    }

    private val shifts: StateFlow<List<Timesheet.Shift>> = visible
        .map { it.shifts }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** How many people on this job this viewer may not be shown the money of. */
    val withheldPeople: StateFlow<Int> = visible
        .map { it.withheldPeople }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val days: StateFlow<List<Timesheet.PersonDay>> = shifts
        .map { Timesheet.personDays(it, zone) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totals: StateFlow<List<Timesheet.PersonTotal>> = days
        .map { Timesheet.totals(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * What the hours came to, beside what somebody typed as a labour cost.
     *
     * Two accounts of the same money, kept apart. Adding them would double the
     * job's labour; showing only one would hide whichever is wrong. A gap
     * between them is the useful thing — either hours nobody costed, or a cost
     * line nobody worked.
     */
    data class Reconciliation(
        val fromHours: Double,
        val fromCostLines: Double,
        val anyUnpriced: Boolean,
        /** False when somebody's hours were kept back from this viewer. */
        val complete: Boolean = true,
    ) {
        val difference: Double get() = fromHours - fromCostLines

        /**
         * Worth pointing at only when both sides have something to say, and
         * only when the hours side is the whole job.
         *
         * A reconciliation over part of the labour is a wrong number that
         * looks entirely plausible: it would report a gap between the job's
         * costed labour and the hours of the few people this viewer happens
         * to be senior to, and read as money nobody worked for.
         */
        val disagrees: Boolean
            get() = complete && fromHours > 0.0 && fromCostLines > 0.0 &&
                kotlin.math.abs(difference) > 0.005
    }

    val reconciliation: StateFlow<Reconciliation> = combine(
        days,
        container.money.observeCostsByCategory(projectId),
        withheldPeople,
    ) { personDays, byCategory, withheld ->
        Reconciliation(
            fromHours = Timesheet.totalCost(personDays),
            fromCostLines = byCategory
                .firstOrNull { it.category == JobFinancials.CostCategory.LABOUR }
                ?.total
                ?: 0.0,
            anyUnpriced = Timesheet.hasUnpricedDays(personDays),
            complete = withheld == 0,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        Reconciliation(0.0, 0.0, false),
    )

}

/** Null when the shift is still open: an unfinished shift has no hours yet. */
private fun TimeEntryEntity.toShift(): Timesheet.Shift? {
    val minutes = minutesWorked ?: return null
    return Timesheet.Shift(
        workerName = workerName,
        startedAt = checkInAt,
        minutes = minutes,
        hourlyRate = hourlyRate,
    )
}
