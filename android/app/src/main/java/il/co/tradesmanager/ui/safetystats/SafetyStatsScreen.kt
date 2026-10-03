package il.co.tradesmanager.ui.safetystats

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.safety.SafetyStats
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.currentLanguageTag
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.export.ExportDocument
import il.co.tradesmanager.ui.export.Exporter
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * The company's safety record as rates: injuries for every million hours
 * worked, over the period picked, in total and job by job. Printed, it is
 * the page a tender's safety questionnaire or a client's audit asks for.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SafetyStatsScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: SafetyStatsViewModel = viewModel(factory = ViewModelFactory(container) { SafetyStatsViewModel(it) })
    val period by viewModel.period.collectAsStateWithLifecycle()
    val span by viewModel.span.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val allowed by viewModel.allowed.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val languageTag = currentLanguageTag()
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val names = jobs.associate { it.id to it.name }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sst_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    val current = sheet
                    if (current != null) {
                        IconButton(
                            onClick = {
                                val result = Exporter.write(
                                    context = context,
                                    document = ExportDocument.SafetyStatistics(
                                        span = span,
                                        sheet = current,
                                        jobNames = names,
                                        today = LocalDate.now(),
                                    ),
                                    languageTag = languageTag,
                                    locale = locale,
                                    rightToLeft = layoutDirection == LayoutDirection.Rtl,
                                )
                                context.startActivity(Exporter.shareIntent(context, result))
                            },
                        ) {
                            Icon(Icons.Filled.IosShare, contentDescription = stringResource(R.string.set_export))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SafetyStats.Period.entries.forEach { offer ->
                    FilterChip(selected = period == offer, onClick = { viewModel.pickPeriod(offer) }, label = { Text(stringResource(periodLabel(offer))) })
                }
            }
            Text(
                stringResource(R.string.sst_span, Formats.date(span.first, locale), Formats.date(span.last, locale)),
                style = MaterialTheme.typography.titleMedium,
            )
            val current = sheet
            when {
                !allowed -> Text(stringResource(R.string.sst_not_allowed), style = MaterialTheme.typography.bodyMedium)
                current == null -> Text(stringResource(R.string.ws_counting), style = MaterialTheme.typography.bodyMedium)
                else -> {
                    statLines(current.total, locale).forEach { (label, value) ->
                        StatRow(label, value, alarming = alarming(label, current.total))
                    }
                    StatRow(
                        R.string.sst_days_since,
                        current.lastSeriousAt?.let { Formats.quantity(SafetyStats.daysSince(it, LocalDate.now(), ZoneId.systemDefault()).toDouble(), locale) }
                            ?: stringResource(R.string.sst_none_recorded),
                        alarming = false,
                    )
                    val capped = current.total.shiftsCapped
                    if (capped > 0) {
                        Text(
                            pluralStringResource(R.plurals.sst_capped, capped, capped),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    HorizontalDivider()
                    Text(stringResource(R.string.sst_by_job), style = MaterialTheme.typography.titleSmall)
                    if (current.rows.isEmpty() && current.offJob.isEmpty) {
                        Text(stringResource(R.string.sst_nothing), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        current.rows.forEach { row -> JobCard(names[row.projectId].orEmpty(), row.figures, locale) }
                        if (!current.offJob.isEmpty) JobCard(stringResource(R.string.inc_no_job), current.offJob, locale)
                    }
                    Text(stringResource(R.string.sst_how), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun JobCard(name: String, figures: SafetyStats.Figures, locale: Locale) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                stringResource(
                    R.string.sst_row_line,
                    wholeHours(figures, locale),
                    figures.injuries.toString(),
                    rateText(figures.injuryRate, locale) ?: stringResource(R.string.sst_no_hours),
                    figures.nearMisses.toString(),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (figures.injuries > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun StatRow(@StringRes label: Int, value: String, alarming: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = if (alarming) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A rate to two places at most, or null when there were no hours to divide by. */
internal fun rateText(rate: Double?, locale: Locale): String? = rate?.let { Formats.quantity(Math.round(it * 100.0) / 100.0, locale) }

/** Hours worked, to the hour: nobody reads a rate off the minutes. */
internal fun wholeHours(figures: SafetyStats.Figures, locale: Locale): String =
    Formats.quantity(Math.rint(figures.hoursWorked), locale)

/**
 * The headline figures in the order they are read and printed; a rate with no
 * hours under it, or a ratio with no injuries, reads as a dash.
 */
internal fun statLines(figures: SafetyStats.Figures, locale: Locale): List<Pair<Int, String>> = listOf(
    R.string.sst_hours to wholeHours(figures, locale),
    R.string.sst_shifts to figures.shifts.toString(),
    R.string.sst_injuries to figures.injuries.toString(),
    R.string.sst_minor to figures.minorInjuries.toString(),
    R.string.sst_serious to figures.seriousInjuries.toString(),
    R.string.sst_fatal to figures.fatalities.toString(),
    R.string.sst_injury_rate to (rateText(figures.injuryRate, locale) ?: NO_FIGURE),
    R.string.sst_serious_rate to (rateText(figures.seriousRate, locale) ?: NO_FIGURE),
    R.string.ws_near_misses to figures.nearMisses.toString(),
    R.string.sst_near_miss_ratio to (rateText(figures.nearMissesPerInjury, locale) ?: NO_FIGURE),
    R.string.ws_violations to figures.violations.toString(),
    R.string.ws_talks to figures.talksHeld.toString(),
)

@StringRes
internal fun periodLabel(period: SafetyStats.Period): Int = when (period) {
    SafetyStats.Period.THIS_MONTH -> R.string.sst_this_month
    SafetyStats.Period.LAST_MONTH -> R.string.sst_last_month
    SafetyStats.Period.THIS_QUARTER -> R.string.sst_this_quarter
    SafetyStats.Period.THIS_YEAR -> R.string.sst_this_year
    SafetyStats.Period.LAST_12_MONTHS -> R.string.sst_last_12_months
}

/** What a figure that cannot be worked out reads as. */
internal const val NO_FIGURE = "—"

/** The lines shown in red, when what they count is not nought. A near miss is not among them: writing one down is what should happen. */
private fun alarming(@StringRes label: Int, figures: SafetyStats.Figures): Boolean = when (label) {
    R.string.sst_injuries, R.string.sst_injury_rate -> figures.injuries > 0
    R.string.sst_minor -> figures.minorInjuries > 0
    R.string.sst_serious -> figures.seriousInjuries > 0
    R.string.sst_fatal -> figures.fatalities > 0
    R.string.sst_serious_rate -> figures.seriousInjuries + figures.fatalities > 0
    else -> false
}
