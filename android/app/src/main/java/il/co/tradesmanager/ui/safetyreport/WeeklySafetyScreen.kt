package il.co.tradesmanager.ui.safetyreport

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.safety.WeeklySafety
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.currentLanguageTag
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.export.ExportDocument
import il.co.tradesmanager.ui.export.Exporter
import il.co.tradesmanager.ui.projects.attentionLabel

/**
 * One job's week of safety: what the registers recorded Sunday to Saturday,
 * and what is still open on the job now. Printed, it is the page the safety
 * officer signs at the end of the week.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WeeklySafetyScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: WeeklySafetyViewModel =
        viewModel(factory = ViewModelFactory(container) { WeeklySafetyViewModel(it, projectId) })
    val weeksBack by viewModel.weeksBack.collectAsStateWithLifecycle()
    val weekStart by viewModel.weekStart.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()
    val openNow by viewModel.openNow.collectAsStateWithLifecycle()
    val jobName by viewModel.jobName.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val languageTag = currentLanguageTag()
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ws_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    val current = report
                    if (current != null) {
                        IconButton(
                            onClick = {
                                val result = Exporter.write(
                                    context = context,
                                    document = ExportDocument.WeeklySafetyReport(
                                        jobName = jobName,
                                        weekStart = weekStart,
                                        report = current,
                                        openNow = openNow,
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
                WEEK_OFFERS.forEach { (back, label) ->
                    FilterChip(selected = weeksBack == back, onClick = { viewModel.pickWeek(back) }, label = { Text(stringResource(label)) })
                }
            }
            Text(
                stringResource(
                    R.string.ws_week_of,
                    Formats.date(weekStart, locale),
                    Formats.date(weekStart.plusDays(WeeklySafety.DAYS - 1), locale),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            val current = report
            if (current == null) {
                Text(stringResource(R.string.ws_counting), style = MaterialTheme.typography.bodyMedium)
            } else {
                if (current.nothingWentWrong) {
                    Text(stringResource(R.string.ws_nothing_wrong), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                reportLines(current).forEach { (label, count) -> CountRow(label, count, alarming = count > 0 && label in ALARMING) }
                HorizontalDivider()
                Text(stringResource(R.string.ws_open_now), style = MaterialTheme.typography.titleSmall)
                if (openNow.isEmpty()) {
                    Text(stringResource(R.string.ws_nothing_open), style = MaterialTheme.typography.bodyMedium)
                } else {
                    openNow.forEach { line -> CountRow(attentionLabel(line.item), line.count, alarming = true) }
                }
                Text(stringResource(R.string.ws_counted_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CountRow(@StringRes label: Int, count: Int, alarming: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
        Text(
            count.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = if (alarming) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** The report's lines in the order they are read and printed. */
internal fun reportLines(report: WeeklySafety.Report): List<Pair<Int, Int>> = listOf(
    R.string.ws_people to report.peopleOnSite,
    R.string.ws_visitors to report.visitors,
    R.string.ws_talks to report.talksHeld,
    R.string.ws_permits to report.permitsIssued,
    R.string.ws_near_misses to report.nearMisses,
    R.string.ws_incidents to report.incidents,
    R.string.ws_violations to report.violations,
    R.string.ws_inspections_passed to report.inspectionsPassed,
    R.string.ws_inspections_failed to report.inspectionsFailed,
    R.string.ws_fire_faults to report.fireFaults,
    R.string.ws_complaints to report.complaintsReceived,
)

/** The lines shown in red when they are not nought. A near miss is not among them: writing one down is what should happen. */
private val ALARMING = setOf(R.string.ws_incidents, R.string.ws_violations, R.string.ws_inspections_failed, R.string.ws_fire_faults)

private val WEEK_OFFERS = listOf(
    0L to R.string.ws_this_week,
    1L to R.string.ws_last_week,
    2L to R.string.ws_2_weeks_ago,
    3L to R.string.ws_3_weeks_ago,
)
