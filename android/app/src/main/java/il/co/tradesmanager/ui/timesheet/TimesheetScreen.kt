package il.co.tradesmanager.ui.timesheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.money.Labour
import il.co.tradesmanager.core.money.Timesheet
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.DetailRow
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.currentLocale
import java.util.Locale

/**
 * The hours clocked on a job, and what they came to.
 *
 * Two things this screen refuses to do. It does not add its figure to the job's
 * costs — labour clocked and labour typed are two accounts of the same money,
 * and summing them doubles it — and it does not show an unpriced day as free.
 * Both would make a job look better than it is, which is the direction a cost
 * screen must never be wrong in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimesheetScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: TimesheetViewModel = viewModel(
        factory = ViewModelFactory(container) { TimesheetViewModel(it, projectId) },
    )
    val days by viewModel.days.collectAsStateWithLifecycle()
    val totals by viewModel.totals.collectAsStateWithLifecycle()
    val reconciliation by viewModel.reconciliation.collectAsStateWithLifecycle()
    val locale = currentLocale()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ts_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (days.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.ts_empty),
                hint = stringResource(R.string.ts_empty_hint),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(Modifier.padding(padding)) {
            item { Reconciliation(reconciliation, locale) }

            item { SectionHeader(stringResource(R.string.ts_by_person)) }
            items(totals, key = { it.workerName }) { total ->
                PersonRow(total = total, locale = locale)
            }

            item { SectionHeader(stringResource(R.string.ts_by_day)) }
            items(days, key = { it.workerName + it.date }) { day ->
                DayRow(day = day, locale = locale)
            }
        }
    }
}

/**
 * The two figures, side by side, and the gap between them.
 *
 * Never one number. A gap is either hours nobody costed or a cost line nobody
 * worked, and both are worth somebody's attention — which is exactly what a
 * single reconciled total would take away.
 */
@Composable
private fun Reconciliation(
    reconciliation: TimesheetViewModel.Reconciliation,
    locale: Locale,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = if (reconciliation.disagrees) scheme.tertiaryContainer else scheme.surfaceVariant,
        contentColor = if (reconciliation.disagrees) {
            scheme.onTertiaryContainer
        } else {
            scheme.onSurfaceVariant
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            DetailRow(
                stringResource(R.string.ts_from_hours),
                Formats.money(reconciliation.fromHours, locale),
            )
            DetailRow(
                stringResource(R.string.ts_from_costs),
                Formats.money(reconciliation.fromCostLines, locale),
            )
            if (reconciliation.disagrees) {
                Text(
                    stringResource(R.string.ts_disagree),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (reconciliation.anyUnpriced) {
                Text(
                    stringResource(R.string.ts_unpriced),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun PersonRow(total: Timesheet.PersonTotal, locale: Locale) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(total.workerName, style = MaterialTheme.typography.titleMedium)
        Text(
            hoursLine(total.hours, locale),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = total.cost?.let { Formats.money(it, locale) }
                ?: stringResource(R.string.ts_no_rate),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (total.rateIsIncomplete && total.cost != null) {
            // The figure is real but partial, and saying so is the difference
            // between a number somebody can use and one that misleads.
            Text(
                stringResource(R.string.ts_partial),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun DayRow(day: Timesheet.PersonDay, locale: Locale) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = Formats.date(day.date, locale) + " · " + day.workerName,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = hoursLine(day.hours, locale) +
                if (day.isRestDay) " · " + stringResource(R.string.ts_rest_day) else "",
            style = MaterialTheme.typography.bodySmall,
        )
        day.cost?.let {
            Text(Formats.money(it, locale), style = MaterialTheme.typography.bodySmall)
        }
    }
    HorizontalDivider()
}

/**
 * Hours as a line: the total, and the bands when there are any.
 *
 * A day showing one figure hides the two hours that cost a quarter more, which
 * is the number somebody is looking for when they open this.
 */
@Composable
private fun hoursLine(hours: Labour.Day, locale: Locale): String {
    val total = Formats.quantity(hours.total, locale) + " " + stringResource(R.string.ts_hours)
    if (!hours.hasOvertime) return total
    val bands = buildList {
        if (hours.atFirstOvertimeRate > 0.0) {
            add(Formats.quantity(hours.atFirstOvertimeRate, locale) + " × 1.25")
        }
        if (hours.atFurtherOvertimeRate > 0.0) {
            add(Formats.quantity(hours.atFurtherOvertimeRate, locale) + " × 1.5")
        }
    }
    return total + " (" + bands.joinToString(", ") + ")"
}
