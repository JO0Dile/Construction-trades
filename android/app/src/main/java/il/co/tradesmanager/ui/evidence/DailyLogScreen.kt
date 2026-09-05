package il.co.tradesmanager.ui.evidence

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.evidence.DailyLog
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.DetailRow
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.SignaturePad
import il.co.tradesmanager.ui.components.currentLocale
import java.time.LocalDate

/**
 * The daily log — יומן עבודה.
 *
 * The page is in two halves on purpose. Above the line is what the app already
 * watched happen, counted rather than remembered, because a manager sitting
 * down at five o'clock should not be asked what was delivered at nine. Below
 * it are the three things only a person knows: the weather, how many were on
 * site, and what the day was actually like.
 *
 * Signing closes it. There is no edit afterwards and no button to ask for one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyLogScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: DailyLogViewModel = viewModel(
        factory = ViewModelFactory(container) { DailyLogViewModel(it, projectId) },
    )
    val log by viewModel.log.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val locale = currentLocale()

    val signedIn = session as? SessionRepository.State.SignedIn
    val canWrite = signedIn?.canWrite(Lens.EVIDENCE) != false
    val current = log
    val editable = current != null && canWrite && DailyLog.canEdit(current.status)

    var weather by remember(current?.id) { mutableStateOf(current?.weather.orEmpty()) }
    var headcount by remember(current?.id) {
        mutableStateOf(current?.workforceCount?.toString().orEmpty())
    }
    var notes by remember(current?.id) { mutableStateOf(current?.notes.orEmpty()) }
    var signature by remember(current?.id) { mutableStateOf("") }

    // The typed fields are saved as they are left rather than behind a button:
    // a log half filled in at five o'clock and abandoned is the common case.
    LaunchedEffect(weather, headcount, notes, current?.id) {
        if (editable) viewModel.save(weather, headcount.toIntOrNull(), notes)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.log_title)) },
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
        LazyColumn(Modifier.padding(padding)) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = current?.let {
                            Formats.date(LocalDate.ofEpochDay(it.logDate), locale)
                        } ?: stringResource(R.string.log_today),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = stringResource(
                            if (current != null && !DailyLog.canEdit(current.status)) {
                                R.string.log_state_signed
                            } else {
                                R.string.log_state_draft
                            },
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.log_gathered)) }
            item {
                Text(
                    text = stringResource(R.string.log_gathered_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                Column {
                    DetailRow(stringResource(R.string.log_tasks_done), "${summary.tasksCompleted}")
                    DetailRow(stringResource(R.string.log_deliveries), "${summary.deliveries}")
                    DetailRow(stringResource(R.string.log_permits), "${summary.permitsIssued}")
                    DetailRow(stringResource(R.string.log_talks), "${summary.talksHeld}")
                    DetailRow(stringResource(R.string.log_snags), "${summary.snagsRaised}")
                    DetailRow(stringResource(R.string.log_incidents), "${summary.incidents}")
                }
            }
            if (summary.isQuiet) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.log_quiet),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader(stringResource(R.string.log_typed)) }
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = weather,
                        onValueChange = { weather = it },
                        label = { Text(stringResource(R.string.log_weather)) },
                        enabled = editable,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = headcount,
                        onValueChange = { typed -> headcount = typed.filter { it.isDigit() } },
                        label = { Text(stringResource(R.string.log_headcount)) },
                        enabled = editable,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(stringResource(R.string.log_notes)) },
                        supportingText = { Text(stringResource(R.string.log_notes_hint)) },
                        enabled = editable,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (current != null && !DailyLog.canEdit(current.status)) {
                        current.signedAt?.let {
                            DetailRow(
                                stringResource(R.string.log_signed_by),
                                current.preparedByName,
                            )
                        }
                        Text(
                            text = stringResource(R.string.log_locked),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (editable) {
                        Text(
                            text = stringResource(R.string.log_sign),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        SignaturePad(onSignatureChange = { signature = it })
                        if (!DailyLog.canSign(current?.status.orEmpty(), signature)) {
                            Text(
                                text = stringResource(R.string.log_must_sign),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Button(
                            onClick = { viewModel.sign(signature) },
                            enabled = DailyLog.canSign(current?.status.orEmpty(), signature),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.log_sign))
                        }
                    }
                }
            }

            if (history.size > 1) {
                item { HorizontalDivider() }
                item { SectionHeader(stringResource(R.string.log_title)) }
                items(history, key = { it.id }) { entry ->
                    ListItem(
                        headlineContent = {
                            Text(Formats.date(LocalDate.ofEpochDay(entry.logDate), locale))
                        },
                        supportingContent = {
                            Text(
                                stringResource(
                                    if (DailyLog.canEdit(entry.status)) {
                                        R.string.log_state_draft
                                    } else {
                                        R.string.log_state_signed
                                    },
                                ),
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.open(entry.logDate) },
                    )
                }
            }
        }
    }
}
