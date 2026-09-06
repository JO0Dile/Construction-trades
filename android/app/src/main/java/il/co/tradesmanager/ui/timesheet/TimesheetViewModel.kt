package il.co.tradesmanager.ui.timesheet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.money.Timesheet
import il.co.tradesmanager.data.local.entity.TimeEntryEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

    private val shifts: StateFlow<List<Timesheet.Shift>> = container.schedule
        .observeCompletedTimeEntries(projectId)
        .map { entries -> entries.mapNotNull { it.toShift() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
    ) {
        val difference: Double get() = fromHours - fromCostLines

        /** Worth pointing at only when both sides have something to say. */
        val disagrees: Boolean
            get() = fromHours > 0.0 && fromCostLines > 0.0 &&
                kotlin.math.abs(difference) > 0.005
    }

    val reconciliation: StateFlow<Reconciliation> = combine(
        days,
        container.money.observeCostsByCategory(projectId),
    ) { personDays, byCategory ->
        Reconciliation(
            fromHours = Timesheet.totalCost(personDays),
            fromCostLines = byCategory
                .firstOrNull { it.category == LABOUR }
                ?.total
                ?: 0.0,
            anyUnpriced = Timesheet.hasUnpricedDays(personDays),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        Reconciliation(0.0, 0.0, false),
    )

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    private companion object {
        const val LABOUR = "LABOUR"
    }
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
