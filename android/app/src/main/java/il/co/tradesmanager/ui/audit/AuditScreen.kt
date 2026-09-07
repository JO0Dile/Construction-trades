package il.co.tradesmanager.ui.audit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.security.AuditChain
import il.co.tradesmanager.core.security.Retention
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.SectionPlaceholder
import il.co.tradesmanager.ui.components.currentLanguageTag
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.export.ExportDocument
import il.co.tradesmanager.ui.export.Exporter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * What happened, in order, and whether the record can still be believed.
 *
 * The trail was being written from the first version and never shown. A log
 * nobody can read is not a trail — it is a table that only becomes evidence if
 * somebody knows to go looking with a SQLite browser, which is not a
 * procedure anybody follows on a site.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: AuditViewModel = viewModel(
        factory = ViewModelFactory(container) { AuditViewModel(it) },
    )
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val verdict by viewModel.verdict.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val languageTag = currentLanguageTag()
    val zone = ZoneId.systemDefault()
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val pending by viewModel.pendingExport.collectAsStateWithLifecycle()
    val retention by viewModel.retentionDays.collectAsStateWithLifecycle()
    val purged by viewModel.purged.collectAsStateWithLifecycle()

    // Writing the file and opening the share sheet is the screen's job: both
    // need a Context, and a view model holding one outlives the screen that
    // gave it.
    LaunchedEffect(pending) {
        val snapshot = pending ?: return@LaunchedEffect
        val result = Exporter.write(
            context = context,
            document = ExportDocument.AuditTrail(
                entries = snapshot.entries,
                verdict = snapshot.verdict,
                exportedOn = LocalDate.now(),
            ),
            languageTag = languageTag,
            locale = locale,
            rightToLeft = layoutDirection == LayoutDirection.Rtl,
        )
        context.startActivity(Exporter.shareIntent(context, result))
        viewModel.exportHandled()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.audit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.export() }, enabled = !checking) {
                        Icon(
                            Icons.Filled.IosShare,
                            contentDescription = stringResource(R.string.audit_export),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                Text(
                    text = stringResource(R.string.audit_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            item { VerdictCard(verdict, viewModel.window) }

            item {
                Text(
                    text = stringResource(R.string.audit_export_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            item {
                OutlinedButton(
                    onClick = { viewModel.check() },
                    enabled = !checking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.audit_verify))
                }
            }

            item { SectionHeader(stringResource(R.string.ret_title)) }
            item {
                Text(
                    text = stringResource(R.string.ret_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    items(Retention.CHOICES) { days ->
                        FilterChip(
                            selected = retention == days,
                            onClick = { viewModel.setRetention(days) },
                            label = { Text(stringResource(retentionLabel(days))) },
                        )
                    }
                }
            }
            // Only offered once a period is set: "apply" with nothing to apply
            // is a button that either does nothing or, worse, looks like it
            // did something.
            if (retention > 0) {
                item {
                    OutlinedButton(
                        onClick = { viewModel.applyRetention() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Text(stringResource(R.string.ret_apply))
                    }
                }
            }
            purged?.let { count ->
                item {
                    Text(
                        text = if (count > 0) {
                            stringResource(R.string.ret_applied, count)
                        } else {
                            stringResource(R.string.ret_nothing)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.audit_title)) }

            if (entries.isEmpty()) {
                item { SectionPlaceholder(stringResource(R.string.audit_empty)) }
            }

            items(entries, key = { it.id }) { entry ->
                ListItem(
                    // The sequence number in front, because the thing worth
                    // noticing on this screen is a gap in it.
                    overlineContent = {
                        Text("#${entry.sequence} · ${entry.action}")
                    },
                    headlineContent = { Text(entry.summary) },
                    supportingContent = {
                        Text(
                            entry.actorName + " · " +
                                Formats.dateTime(
                                    Instant.ofEpochMilli(entry.occurredAt)
                                        .atZone(zone).toLocalDate(),
                                    Instant.ofEpochMilli(entry.occurredAt)
                                        .atZone(zone).toLocalTime(),
                                    locale,
                                ),
                        )
                    },
                )
            }
        }
    }
}

/** The words for each retention choice. */
private fun retentionLabel(days: Int): Int = when (days) {
    0 -> R.string.ret_keep_all
    365 -> R.string.ret_1_year
    3 * 365 -> R.string.ret_3_years
    else -> R.string.ret_7_years
}

/**
 * The verdict, in the words somebody reading it would use.
 *
 * "Intact" is deliberately not the resting state: before anybody presses the
 * button the card says it has not been checked. A screen that showed a green
 * tick it had not earned would be worse than one that showed nothing.
 */
@Composable
private fun VerdictCard(verdict: AuditChain.Verdict?, window: Int) {
    val colours = when (verdict) {
        is AuditChain.Verdict.Failed -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
        else -> CardDefaults.cardColors()
    }
    Card(
        colors = colours,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (verdict) {
                null -> Text(stringResource(R.string.audit_not_checked))

                AuditChain.Verdict.Empty -> Text(stringResource(R.string.audit_empty))

                is AuditChain.Verdict.Intact -> {
                    Text(
                        text = stringResource(R.string.audit_intact),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(stringResource(R.string.audit_intact_detail, verdict.checked))
                    if (verdict.unchained > 0) {
                        Text(
                            text = stringResource(R.string.audit_unchained, verdict.unchained),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is AuditChain.Verdict.Failed -> {
                    Text(
                        text = stringResource(R.string.audit_failed),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(
                            when (verdict.fault) {
                                AuditChain.Fault.ALTERED -> R.string.audit_fault_altered
                                AuditChain.Fault.BROKEN_LINK -> R.string.audit_fault_broken
                                AuditChain.Fault.MISSING -> R.string.audit_fault_missing
                            },
                            verdict.sequence,
                        ),
                    )
                }
            }
            Text(
                text = stringResource(R.string.audit_window, window),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
