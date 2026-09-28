package il.co.tradesmanager.ui.risks

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import il.co.tradesmanager.core.safety.Risks
import il.co.tradesmanager.data.local.entity.RiskAssessmentEntity
import il.co.tradesmanager.data.repository.RiskRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.currentLanguageTag
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.export.ExportDocument
import il.co.tradesmanager.ui.export.Exporter
import java.time.LocalDate
import java.util.Locale

/**
 * One job's risk assessment.
 *
 * Extreme first and in red, because a risk still extreme with its controls in
 * place is the one that should stop somebody before the work starts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RisksScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: RisksViewModel =
        viewModel(factory = ViewModelFactory(container) { RisksViewModel(it, projectId) })
    val mayWrite by viewModel.mayWrite.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val open by viewModel.open.collectAsStateWithLifecycle()
    val refusal by viewModel.refusal.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val jobName by viewModel.jobName.collectAsStateWithLifecycle()
    val languageTag = currentLanguageTag()
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current

    var assessing by remember { mutableStateOf(false) }
    var reviewing by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ra_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    // The assessment as a document: a PDF to print or send, and a CSV.
                    if (rows.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val result = Exporter.write(
                                    context = context,
                                    document = ExportDocument.RiskRegister(jobName = jobName, risks = rows.map { it.risk }),
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
        floatingActionButton = {
            if (mayWrite) {
                FloatingActionButton(onClick = { assessing = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.ra_add))
                }
            }
        },
    ) { padding ->
        if (rows.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.ra_empty),
                hint = stringResource(R.string.ra_blurb),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding)) {
            items(rows, key = { it.risk.id }) { row ->
                val risk = row.risk
                ListItem(
                    overlineContent = { Text(risk.reference + SEPARATOR + risk.activity) },
                    headlineContent = { Text(risk.hazard, maxLines = 2) },
                    supportingContent = {
                        Column {
                            Text(
                                scoreLine(row),
                                color = bandColour(Risks.band(row.residual)),
                                fontWeight = if (row.state == Risks.State.EXTREME) FontWeight.Bold else FontWeight.Normal,
                            )
                            stateLine(row, locale)?.let { (text, alarming) ->
                                Text(
                                    text,
                                    color = if (alarming) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.openRisk(risk.id) },
                )
            }
        }
    }

    open?.let { row ->
        DetailDialog(
            row = row,
            locale = locale,
            mayWrite = mayWrite,
            onDismiss = { viewModel.openRisk(null) },
            onReview = { reviewing = true },
            onClose = { closing = true },
        )
    }

    if (assessing) {
        AssessDialog(
            existing = null,
            onDismiss = { assessing = false },
            onSave = { assessment ->
                assessing = false
                viewModel.assess(assessment)
            },
        )
    }

    val reviewRow = open
    if (reviewing && reviewRow != null) {
        AssessDialog(
            existing = reviewRow.risk,
            onDismiss = { reviewing = false },
            onSave = { assessment ->
                reviewing = false
                viewModel.review(reviewRow.risk.id, assessment)
            },
        )
    }

    val closeRow = open
    if (closing && closeRow != null) {
        AlertDialog(
            onDismissRequest = { closing = false },
            text = { Text(stringResource(R.string.ra_close_confirm, closeRow.risk.reference)) },
            confirmButton = {
                TextButton(onClick = {
                    closing = false
                    viewModel.close(closeRow.risk.id)
                }) { Text(stringResource(R.string.ra_close)) }
            },
            dismissButton = {
                TextButton(onClick = { closing = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    refusal?.let { reason ->
        AlertDialog(
            onDismissRequest = viewModel::clearRefusal,
            text = { Text(stringResource(riskRefusalText(reason))) },
            confirmButton = {
                TextButton(onClick = viewModel::clearRefusal) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

/** "Before 16 · after 6, medium". */
@Composable
private fun scoreLine(row: RisksViewModel.Row): String =
    stringResource(R.string.ra_scores, row.initial, row.residual, stringResource(bandLabel(Risks.band(row.residual))))

/** The line under the score, and whether it is bad news. Null when there is nothing to say. */
@Composable
private fun stateLine(row: RisksViewModel.Row, locale: Locale): Pair<String, Boolean>? {
    val review = row.risk.reviewOnDay?.let { Formats.date(LocalDate.ofEpochDay(it), locale) }
    return when (row.state) {
        Risks.State.EXTREME -> stringResource(R.string.ra_still_extreme) to true
        Risks.State.REVIEW_OVERDUE -> stringResource(R.string.ra_review_overdue, review.orEmpty()) to true
        Risks.State.OPEN -> review?.let { stringResource(R.string.ra_review_by, it) to false }
        Risks.State.CLOSED -> stringResource(R.string.ra_closed) to false
    }
}

@Composable
private fun bandColour(band: Risks.Band): Color = when (band) {
    Risks.Band.LOW -> MaterialTheme.colorScheme.primary
    Risks.Band.MEDIUM -> MaterialTheme.colorScheme.tertiary
    Risks.Band.HIGH, Risks.Band.EXTREME -> MaterialTheme.colorScheme.error
}

@Composable
private fun DetailDialog(
    row: RisksViewModel.Row,
    locale: Locale,
    mayWrite: Boolean,
    onDismiss: () -> Unit,
    onReview: () -> Unit,
    onClose: () -> Unit,
) {
    val risk = row.risk
    val live = !risk.closed
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(risk.reference + SEPARATOR + risk.hazard) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(risk.activity, style = MaterialTheme.typography.bodyLarge)
                risk.whoAtRisk?.let { Text(stringResource(R.string.ra_who_is, it), style = MaterialTheme.typography.bodyMedium) }
                Text(stringResource(R.string.ra_before), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(
                        R.string.ra_score_detail,
                        stringResource(likelihoodLabel(risk.likelihoodBefore)),
                        stringResource(severityLabel(risk.severityBefore)),
                        row.initial,
                        stringResource(bandLabel(Risks.band(row.initial))),
                    ),
                    color = bandColour(Risks.band(row.initial)),
                )
                Text(stringResource(R.string.ra_controls), style = MaterialTheme.typography.labelLarge)
                Text(risk.controls ?: stringResource(R.string.ra_no_controls), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.ra_after), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(
                        R.string.ra_score_detail,
                        stringResource(likelihoodLabel(risk.likelihoodAfter)),
                        stringResource(severityLabel(risk.severityAfter)),
                        row.residual,
                        stringResource(bandLabel(Risks.band(row.residual))),
                    ),
                    color = bandColour(Risks.band(row.residual)),
                    fontWeight = FontWeight.Bold,
                )
                stateLine(row, locale)?.let { (text, alarming) ->
                    Text(text, color = if (alarming) MaterialTheme.colorScheme.error else Color.Unspecified)
                }
                Text(
                    listOfNotNull(
                        risk.ownerName?.let { stringResource(R.string.ra_owner_is, it) },
                        stringResource(R.string.ra_recorded_by, risk.recordedByName),
                        risk.lastReviewedByName?.let { stringResource(R.string.ra_reviewed_by, it) },
                    ).joinToString(SEPARATOR),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (mayWrite && live) {
                    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.ra_close))
                    }
                }
            }
        },
        confirmButton = {
            if (mayWrite && live) {
                TextButton(onClick = onReview) { Text(stringResource(R.string.ra_review)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        },
        dismissButton = {
            if (mayWrite && live) TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

/**
 * Assessing a hazard, or reviewing one. The same form both times, filled in
 * from the row when it is a review, because a review is the whole assessment
 * looked at again rather than one field changed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssessDialog(
    existing: RiskAssessmentEntity?,
    onDismiss: () -> Unit,
    onSave: (RiskRepository.Assessment) -> Unit,
) {
    var activity by remember { mutableStateOf(existing?.activity.orEmpty()) }
    var hazard by remember { mutableStateOf(existing?.hazard.orEmpty()) }
    var who by remember { mutableStateOf(existing?.whoAtRisk.orEmpty()) }
    var likelihoodBefore by remember { mutableStateOf(existing?.likelihoodBefore ?: 3) }
    var severityBefore by remember { mutableStateOf(existing?.severityBefore ?: 3) }
    var controls by remember { mutableStateOf(existing?.controls.orEmpty()) }
    var likelihoodAfter by remember { mutableStateOf(existing?.likelihoodAfter ?: 3) }
    var severityAfter by remember { mutableStateOf(existing?.severityAfter ?: 3) }
    var owner by remember { mutableStateOf(existing?.ownerName.orEmpty()) }
    var reviewInDays by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.ra_add else R.string.ra_review)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = activity,
                    onValueChange = { activity = it },
                    label = { Text(stringResource(R.string.ra_activity)) },
                    supportingText = { Text(stringResource(R.string.ra_activity_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = hazard,
                    onValueChange = { hazard = it },
                    label = { Text(stringResource(R.string.ra_hazard)) },
                    supportingText = { Text(stringResource(R.string.ra_hazard_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = who,
                    onValueChange = { who = it },
                    label = { Text(stringResource(R.string.ra_who)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.ra_before), style = MaterialTheme.typography.titleSmall)
                ScorePicker(R.string.ra_likelihood, likelihoodBefore, ::likelihoodLabel) { likelihoodBefore = it }
                ScorePicker(R.string.ra_severity, severityBefore, ::severityLabel) { severityBefore = it }
                OutlinedTextField(
                    value = controls,
                    onValueChange = { controls = it },
                    label = { Text(stringResource(R.string.ra_controls)) },
                    supportingText = { Text(stringResource(R.string.ra_controls_hint)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.ra_after), style = MaterialTheme.typography.titleSmall)
                ScorePicker(R.string.ra_likelihood, likelihoodAfter, ::likelihoodLabel) { likelihoodAfter = it }
                ScorePicker(R.string.ra_severity, severityAfter, ::severityLabel) { severityAfter = it }
                val residual = Risks.score(likelihoodAfter, severityAfter)
                Text(
                    stringResource(
                        R.string.ra_scores,
                        Risks.score(likelihoodBefore, severityBefore),
                        residual,
                        stringResource(bandLabel(Risks.band(residual))),
                    ),
                    color = bandColour(Risks.band(residual)),
                    fontWeight = FontWeight.Bold,
                )
                OutlinedTextField(
                    value = owner,
                    onValueChange = { owner = it },
                    label = { Text(stringResource(R.string.ra_owner)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.ra_review_in), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = reviewInDays == null,
                        onClick = { reviewInDays = null },
                        label = { Text(stringResource(R.string.ra_no_review)) },
                    )
                    REVIEW_IN.forEach { (days, label) ->
                        FilterChip(selected = reviewInDays == days, onClick = { reviewInDays = days }, label = { Text(stringResource(label)) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = activity.isNotBlank() && hazard.isNotBlank(),
                onClick = {
                    onSave(
                        RiskRepository.Assessment(
                            activity = activity,
                            hazard = hazard,
                            whoAtRisk = who,
                            likelihoodBefore = likelihoodBefore,
                            severityBefore = severityBefore,
                            controls = controls,
                            likelihoodAfter = likelihoodAfter,
                            severityAfter = severityAfter,
                            ownerName = owner,
                            reviewOn = reviewInDays?.let { LocalDate.now().plusDays(it.toLong()) },
                        ),
                    )
                },
            ) { Text(stringResource(R.string.ra_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** One to five, each with its word, because "3" means nothing to the person holding the phone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScorePicker(@StringRes title: Int, selected: Int, label: (Int) -> Int, onSelect: (Int) -> Unit) {
    Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..5).forEach { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text("$value " + stringResource(label(value))) },
            )
        }
    }
}

@StringRes
internal fun likelihoodLabel(value: Int): Int = when (value) {
    1 -> R.string.ra_l1
    2 -> R.string.ra_l2
    3 -> R.string.ra_l3
    4 -> R.string.ra_l4
    else -> R.string.ra_l5
}

@StringRes
internal fun severityLabel(value: Int): Int = when (value) {
    1 -> R.string.ra_s1
    2 -> R.string.ra_s2
    3 -> R.string.ra_s3
    4 -> R.string.ra_s4
    else -> R.string.ra_s5
}

@StringRes
internal fun bandLabel(band: Risks.Band): Int = when (band) {
    Risks.Band.LOW -> R.string.ra_band_low
    Risks.Band.MEDIUM -> R.string.ra_band_medium
    Risks.Band.HIGH -> R.string.ra_band_high
    Risks.Band.EXTREME -> R.string.ra_band_extreme
}

@StringRes
private fun riskRefusalText(refusal: RiskRepository.Refusal): Int = when (refusal) {
    RiskRepository.Refusal.NOT_ALLOWED -> R.string.ra_refused_not_allowed
    RiskRepository.Refusal.BLANK_ACTIVITY -> R.string.ra_refused_activity
    RiskRepository.Refusal.BLANK_HAZARD -> R.string.ra_refused_hazard
    RiskRepository.Refusal.OUT_OF_RANGE -> R.string.ra_refused_range
    RiskRepository.Refusal.NO_CONTROLS -> R.string.ra_refused_controls
    RiskRepository.Refusal.RESIDUAL_ABOVE_INITIAL -> R.string.ra_refused_worse
    RiskRepository.Refusal.REVIEW_IN_PAST -> R.string.ra_refused_review
    RiskRepository.Refusal.ALREADY_CLOSED -> R.string.ra_refused_closed
    RiskRepository.Refusal.UNKNOWN -> R.string.write_not_saved
}

/** "Review in" offers, in days from today. */
private val REVIEW_IN = listOf(
    7 to R.string.ra_in_a_week,
    30 to R.string.ra_in_a_month,
    90 to R.string.ra_in_3_months,
)

private const val SEPARATOR = " · "
