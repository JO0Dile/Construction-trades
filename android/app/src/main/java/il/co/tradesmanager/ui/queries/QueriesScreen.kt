package il.co.tradesmanager.ui.queries

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.work.Queries
import il.co.tradesmanager.data.local.entity.DesignQueryEntity
import il.co.tradesmanager.data.repository.DesignQueryRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.currentLocale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * One job's questions to its designers.
 *
 * Overdue ones first and in red, because the only reason anybody opens this
 * screen on a busy morning is to find out what is holding the work up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueriesScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: QueriesViewModel =
        viewModel(factory = ViewModelFactory(container) { QueriesViewModel(it, projectId) })
    val mayWrite by viewModel.mayWrite.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val refusal by viewModel.refusal.collectAsStateWithLifecycle()
    val locale = currentLocale()

    var raising by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf<DesignQueryEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.qry_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            if (mayWrite) {
                FloatingActionButton(onClick = { raising = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.qry_raise))
                }
            }
        },
    ) { padding ->
        if (rows.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.qry_empty),
                hint = stringResource(R.string.qry_blurb),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding)) {
            items(rows, key = { it.query.id }) { row ->
                val query = row.query
                ListItem(
                    overlineContent = {
                        Text(listOfNotNull(query.reference, query.askedOf, query.drawingNumber).joinToString(SEPARATOR))
                    },
                    headlineContent = { Text(query.question, maxLines = 3) },
                    supportingContent = {
                        Text(
                            stateLine(row.state, query, locale),
                            color = if (row.state == Queries.State.OVERDUE) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (row.state == Queries.State.OVERDUE) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().clickable { reading = query },
                )
            }
        }
    }

    if (raising) {
        RaiseDialog(
            onDismiss = { raising = false },
            onRaise = { question, askedOf, drawing, neededBy ->
                raising = false
                viewModel.raise(question, askedOf, drawing, neededBy)
            },
        )
    }

    reading?.let { query ->
        ReadDialog(
            query = query,
            locale = locale,
            mayAnswer = mayWrite && query.answeredAt == null,
            onDismiss = { reading = null },
            onAnswer = { answer ->
                reading = null
                viewModel.answer(query.id, answer)
            },
        )
    }

    refusal?.let { reason ->
        AlertDialog(
            onDismissRequest = viewModel::clearRefusal,
            text = { Text(stringResource(refusalText(reason))) },
            confirmButton = {
                TextButton(onClick = viewModel::clearRefusal) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

@Composable
private fun stateLine(state: Queries.State, query: DesignQueryEntity, locale: Locale): String = when (state) {
    Queries.State.ANSWERED -> stringResource(R.string.qry_answered_on, dateOf(query.answeredAt ?: query.askedAt, locale))
    Queries.State.OVERDUE -> stringResource(R.string.qry_overdue, dateOf(query.neededBy ?: query.askedAt, locale))
    Queries.State.OPEN -> query.neededBy?.let { stringResource(R.string.qry_needed_by, dateOf(it, locale)) }
        ?: stringResource(R.string.qry_asked_on, dateOf(query.askedAt, locale))
}

/**
 * Asking a question. Who it is asked of is picked from the usual answers or
 * typed, and when the site needs it by from a few days out -- the dates a
 * foreman actually says.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RaiseDialog(
    onDismiss: () -> Unit,
    onRaise: (question: String, askedOf: String, drawing: String, neededBy: Long?) -> Unit,
) {
    var question by remember { mutableStateOf("") }
    var askedOf by remember { mutableStateOf("") }
    var drawing by remember { mutableStateOf("") }
    var neededInDays by remember { mutableStateOf<Int?>(null) }
    val usual = listOf(R.string.qry_architect, R.string.qry_engineer, R.string.qry_supervisor, R.string.qry_client)
        .map { stringResource(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.qry_raise)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text(stringResource(R.string.qry_question)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.qry_asked_of), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    usual.forEach { who ->
                        FilterChip(selected = askedOf == who, onClick = { askedOf = who }, label = { Text(who) })
                    }
                }
                OutlinedTextField(
                    value = askedOf,
                    onValueChange = { askedOf = it },
                    label = { Text(stringResource(R.string.qry_asked_of_typed)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = drawing,
                    onValueChange = { drawing = it },
                    label = { Text(stringResource(R.string.qry_drawing)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.qry_needed_by_label), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = neededInDays == null,
                        onClick = { neededInDays = null },
                        label = { Text(stringResource(R.string.qry_no_date)) },
                    )
                    NEEDED_IN.forEach { (days, label) ->
                        FilterChip(
                            selected = neededInDays == days,
                            onClick = { neededInDays = days },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = question.isNotBlank() && askedOf.isNotBlank(),
                onClick = {
                    val neededBy = neededInDays?.let { days ->
                        LocalDate.now().plusDays(days.toLong()).atStartOfDay(ZoneId.systemDefault())
                            .toInstant().toEpochMilli()
                    }
                    onRaise(question, askedOf, drawing, neededBy)
                },
            ) { Text(stringResource(R.string.qry_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** A question in full, its answer if there is one, and the place to type it if not. */
@Composable
private fun ReadDialog(
    query: DesignQueryEntity,
    locale: Locale,
    mayAnswer: Boolean,
    onDismiss: () -> Unit,
    onAnswer: (String) -> Unit,
) {
    var answer by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(query.reference + SEPARATOR + query.askedOf) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(query.question, style = MaterialTheme.typography.bodyLarge)
                Text(
                    listOfNotNull(
                        stringResource(R.string.qry_asked_on, dateOf(query.askedAt, locale)),
                        query.askedByName,
                        query.drawingNumber,
                    ).joinToString(SEPARATOR),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (query.answer != null) {
                    Text(stringResource(R.string.qry_answer), style = MaterialTheme.typography.labelLarge)
                    Text(query.answer, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        listOfNotNull(
                            query.answeredAt?.let { stringResource(R.string.qry_answered_on, dateOf(it, locale)) },
                            query.answerRecordedByName,
                        ).joinToString(SEPARATOR),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (mayAnswer) {
                    OutlinedTextField(
                        value = answer,
                        onValueChange = { answer = it },
                        label = { Text(stringResource(R.string.qry_answer_typed)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            if (mayAnswer && query.answer == null) {
                TextButton(enabled = answer.isNotBlank(), onClick = { onAnswer(answer) }) {
                    Text(stringResource(R.string.qry_record_answer))
                }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        },
        dismissButton = {
            if (mayAnswer && query.answer == null) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

private fun dateOf(at: Long, locale: Locale): String =
    Formats.date(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate(), locale)

@StringRes
private fun refusalText(refusal: DesignQueryRepository.Refusal): Int = when (refusal) {
    DesignQueryRepository.Refusal.NOT_ALLOWED -> R.string.qry_refused_not_allowed
    DesignQueryRepository.Refusal.BLANK_QUESTION -> R.string.qry_refused_question
    DesignQueryRepository.Refusal.NOBODY_ASKED -> R.string.qry_refused_nobody
    DesignQueryRepository.Refusal.NEEDED_BY_TOO_EARLY -> R.string.qry_refused_date
    DesignQueryRepository.Refusal.BLANK_ANSWER -> R.string.qry_refused_answer
    DesignQueryRepository.Refusal.ALREADY_ANSWERED -> R.string.qry_refused_answered
    DesignQueryRepository.Refusal.UNKNOWN -> R.string.write_not_saved
}

/** "Needed by" offers, in days from today. */
private val NEEDED_IN = listOf(
    2 to R.string.qry_in_2_days,
    7 to R.string.qry_in_a_week,
    14 to R.string.qry_in_2_weeks,
)

private const val SEPARATOR = " · "
