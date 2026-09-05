package il.co.tradesmanager.ui.scaffold

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.safety.Scaffolding
import il.co.tradesmanager.core.security.Signature
import il.co.tradesmanager.data.local.entity.ScaffoldEntity
import il.co.tradesmanager.data.local.entity.ScaffoldInspectionEntity
import il.co.tradesmanager.data.repository.ScaffoldRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.DetailRow
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.SectionPlaceholder
import il.co.tradesmanager.ui.components.SignaturePad
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.components.rememberNow
import il.co.tradesmanager.ui.evidence.pluralCount
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * The scaffold register (פנקס פיגומים).
 *
 * A scaffold whose weekly inspection has lapsed looks exactly like one whose
 * has not — same tubes, same boards, same everything. The register is the only
 * thing on the site that can tell anybody, so every row here answers one
 * question, in words rather than in a colour: may this be climbed.
 *
 * The scaffolds nobody may climb sit at the top, and among those the one that
 * has been wrong longest is above the one that lapsed this morning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaffoldRegisterScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: ScaffoldViewModel = viewModel(
        factory = ViewModelFactory(container) { ScaffoldViewModel(it, projectId) },
    )
    val scaffolds by viewModel.scaffolds.collectAsStateWithLifecycle()
    val open by viewModel.open.collectAsStateWithLifecycle()
    val inspections by viewModel.inspections.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val zone = ZoneId.systemDefault()
    // The register is counted in days and only turns over at midnight, so an
    // hour is often enough. This is not the concrete screen.
    val now = rememberNow(intervalMillis = 60L * 60L * 1000L)

    val signedIn = session as? SessionRepository.State.SignedIn
    val canEdit = signedIn?.canWrite(Lens.EVIDENCE) != false
    var adding by remember { mutableStateOf(false) }
    var altering by remember { mutableStateOf(false) }
    val current = open
    var inspecting by remember(current?.id) { mutableStateOf(false) }

    val rows = scaffolds
        .map { it to registerState(it, now, zone) }
        .sortedBy { (scaffold, state) ->
            Scaffolding.urgency(state, scaffold.lastInspectedAt, now, zone)
        }
    val unusable = rows.count { (scaffold, state) ->
        scaffold.dismantledAt == null && !state.mayBeClimbed
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.reference ?: stringResource(R.string.scf_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (current != null) viewModel.openScaffold(null) else onBack()
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (canEdit && current == null) {
                FloatingActionButton(onClick = { adding = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.scf_new))
                }
            }
        },
    ) { padding ->
        if (current == null) {
            if (scaffolds.isEmpty()) {
                EmptyState(
                    message = stringResource(R.string.scf_empty),
                    hint = stringResource(R.string.scf_new),
                    modifier = Modifier.padding(padding),
                )
                return@Scaffold
            }
            LazyColumn(Modifier.padding(padding)) {
                item { UnusableBanner(unusable) }
                items(rows, key = { it.first.id }) { (scaffold, state) ->
                    ScaffoldRow(
                        scaffold = scaffold,
                        state = state,
                        now = now,
                        zone = zone,
                        onOpen = { viewModel.openScaffold(scaffold.id) },
                    )
                }
            }
            return@Scaffold
        }

        val state = registerState(current, now, zone)
        LazyColumn(Modifier.padding(padding)) {
            item {
                Surface(
                    color = containerFor(state),
                    contentColor = contentFor(state),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(stateLabel(state)),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        current.lastInspectedAt?.let {
                            Text(remaining(it, now, zone), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item {
                Column {
                    DetailRow(stringResource(R.string.scf_location), current.location)
                    current.type?.let { DetailRow(stringResource(R.string.scf_type), it) }
                    current.erectedByName?.let {
                        DetailRow(stringResource(R.string.scf_erected_by), it)
                    }
                    current.lastInspectedAt?.let {
                        DetailRow(
                            stringResource(R.string.scf_last_inspected),
                            dateOf(it, zone, locale),
                        )
                        DetailRow(
                            stringResource(R.string.scf_next_due),
                            dateOf(Scaffolding.nextDueAt(it, zone), zone, locale),
                        )
                    }
                    current.lastInspectorName?.let {
                        DetailRow(stringResource(R.string.scf_inspector), it)
                    }
                }
            }

            if (canEdit && current.dismantledAt == null && !inspecting) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = { inspecting = true }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.scf_inspect))
                        }
                        OutlinedButton(
                            onClick = { altering = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.scf_altered))
                        }
                    }
                }
            }

            if (canEdit && inspecting) {
                item {
                    InspectionForm(
                        onCancel = { inspecting = false },
                        onRecord = { passed, reason, defects, action, signature ->
                            inspecting = false
                            viewModel.inspect(passed, reason, defects, action, signature)
                        },
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.scf_inspections)) }
            if (inspections.isEmpty()) {
                item { SectionPlaceholder(stringResource(R.string.scf_no_inspections)) }
            }
            items(inspections, key = { it.id }) { inspection ->
                InspectionRow(inspection = inspection, zone = zone, locale = locale)
            }

            if (current.dismantledAt != null) {
                item { SectionPlaceholder(stringResource(R.string.scf_dismantled)) }
            } else if (canEdit) {
                item {
                    OutlinedButton(
                        onClick = { viewModel.dismantle() },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Text(stringResource(R.string.scf_dismantle))
                    }
                }
            }
        }
    }

    if (adding) {
        AddScaffoldDialog(
            onDismiss = { adding = false },
            onAdd = { location, type, erectedBy ->
                adding = false
                viewModel.erect(location, type, erectedBy)
            },
        )
    }

    if (altering) {
        AlteredDialog(
            onDismiss = { altering = false },
            onConfirm = { note ->
                altering = false
                viewModel.markAltered(note)
            },
        )
    }
}

private fun registerState(scaffold: ScaffoldEntity, now: Long, zone: ZoneId): Scaffolding.State =
    Scaffolding.state(
        lastInspectedAt = scaffold.lastInspectedAt,
        lastInspectionPassed = scaffold.lastInspectionPassed,
        alteredAt = scaffold.alteredAt,
        now = now,
        zone = zone,
    )

/**
 * The one number somebody wants from across the site.
 *
 * Said plainly when it is zero, too. A counter that only appears when there is
 * bad news is a counter nobody trusts on the days it is missing — the point is
 * that somebody can look and know, not that the screen stays quiet.
 */
@Composable
private fun UnusableBanner(unusable: Int) {
    Surface(
        color = if (unusable > 0) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (unusable > 0) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = if (unusable > 0) {
                stringResource(R.string.scf_unusable) + ": " + unusable
            } else {
                stringResource(R.string.scf_all_clear)
            },
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun ScaffoldRow(
    scaffold: ScaffoldEntity,
    state: Scaffolding.State,
    now: Long,
    zone: ZoneId,
    onOpen: () -> Unit,
) {
    // A struck scaffold is history, not a warning. It keeps its row so the
    // register stays a record, but it stops shouting.
    val struck = scaffold.dismantledAt != null
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = if (struck) scheme.surfaceVariant else containerFor(state),
        contentColor = if (struck) scheme.onSurfaceVariant else contentFor(state),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(scaffold.reference, style = MaterialTheme.typography.labelMedium)
            Text(scaffold.location, style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (struck) {
                    stringResource(R.string.scf_dismantled)
                } else {
                    val label = stringResource(stateLabel(state))
                    val left = scaffold.lastInspectedAt?.let { remaining(it, now, zone) }
                    if (left == null) label else "$label · $left"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun InspectionRow(
    inspection: ScaffoldInspectionEntity,
    zone: ZoneId,
    locale: Locale,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = dateOf(inspection.inspectedAt, zone, locale) + " · " + inspection.inspectorName,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(
                if (inspection.passed) R.string.scf_passed else R.string.scf_failed,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = if (inspection.passed) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Text(
            stringResource(reasonLabel(inspection.reason)),
            style = MaterialTheme.typography.bodySmall,
        )
        inspection.defects?.let {
            Text(
                stringResource(R.string.scf_defects) + ": " + it,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        inspection.actionTaken?.let {
            Text(
                stringResource(R.string.scf_action) + ": " + it,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    HorizontalDivider()
}

/**
 * Recording an inspection.
 *
 * Inline rather than in a dialog, and not because it was easier: a signature
 * pad is a hundred and eighty points of drawing surface, and a dialog on a
 * phone has nowhere to put it. The daily log signs the same way for the same
 * reason.
 *
 * The button will not move without a signature. An unsigned inspection is a
 * note somebody typed; a signed one is the thing the register exists to hold.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InspectionForm(
    onCancel: () -> Unit,
    onRecord: (
        passed: Boolean,
        reason: ScaffoldRepository.Reason,
        defects: String?,
        actionTaken: String?,
        signature: String?,
    ) -> Unit,
) {
    var passed by remember { mutableStateOf(true) }
    var reason by remember { mutableStateOf(ScaffoldRepository.Reason.WEEKLY) }
    var defects by remember { mutableStateOf("") }
    var action by remember { mutableStateOf("") }
    var signature by remember { mutableStateOf("") }
    val signed = Signature.isSigned(signature)

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.scf_reason), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScaffoldRepository.Reason.entries.forEach { option ->
                FilterChip(
                    selected = reason == option,
                    onClick = { reason = option },
                    label = { Text(stringResource(reasonLabel(option.name))) },
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = passed,
                onClick = { passed = true },
                label = { Text(stringResource(R.string.scf_passed)) },
            )
            FilterChip(
                selected = !passed,
                onClick = { passed = false },
                label = { Text(stringResource(R.string.scf_failed)) },
            )
        }
        // Only when it failed. Asking everybody what is wrong with a scaffold
        // that is fine is how the field gets filled in with "ok".
        if (!passed) {
            OutlinedTextField(
                value = defects,
                onValueChange = { defects = it },
                label = { Text(stringResource(R.string.scf_defects)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = action,
                onValueChange = { action = it },
                label = { Text(stringResource(R.string.scf_action)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SignaturePad(onSignatureChange = { signature = it })
        if (!signed) {
            Text(
                stringResource(R.string.scf_must_sign),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = signed,
                onClick = {
                    onRecord(
                        passed,
                        reason,
                        defects.trim().takeIf { it.isNotEmpty() },
                        action.trim().takeIf { it.isNotEmpty() },
                        signature,
                    )
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.action_save))
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun remaining(lastInspectedAt: Long, now: Long, zone: ZoneId): String {
    val days = Scaffolding.daysUntilDue(lastInspectedAt, now, zone)
    return if (days < 0) {
        pluralCount(R.plurals.scf_days_over, -days)
    } else {
        pluralCount(R.plurals.days_left, days)
    }
}

private fun dateOf(millis: Long, zone: ZoneId, locale: Locale): String =
    Formats.date(Instant.ofEpochMilli(millis).atZone(zone).toLocalDate(), locale)

internal fun stateLabel(state: Scaffolding.State): Int = when (state) {
    Scaffolding.State.SAFE -> R.string.scf_state_safe
    Scaffolding.State.DUE_SOON -> R.string.scf_state_due
    Scaffolding.State.OVERDUE -> R.string.scf_state_overdue
    Scaffolding.State.FAILED -> R.string.scf_state_failed
    Scaffolding.State.ALTERED -> R.string.scf_state_altered
    Scaffolding.State.NEVER_INSPECTED -> R.string.scf_state_never
}

internal fun reasonLabel(reason: String): Int = when (reason) {
    ScaffoldRepository.Reason.FIRST_USE.name -> R.string.scf_reason_first
    ScaffoldRepository.Reason.AFTER_ALTERATION.name -> R.string.scf_reason_altered
    ScaffoldRepository.Reason.AFTER_WEATHER.name -> R.string.scf_reason_weather
    else -> R.string.scf_reason_weekly
}

@Composable
private fun containerFor(state: Scaffolding.State) = when {
    !state.mayBeClimbed -> MaterialTheme.colorScheme.errorContainer
    state == Scaffolding.State.DUE_SOON -> MaterialTheme.colorScheme.tertiaryContainer
    else -> MaterialTheme.colorScheme.surface
}

@Composable
private fun contentFor(state: Scaffolding.State) = when {
    !state.mayBeClimbed -> MaterialTheme.colorScheme.onErrorContainer
    state == Scaffolding.State.DUE_SOON -> MaterialTheme.colorScheme.onTertiaryContainer
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun AddScaffoldDialog(
    onDismiss: () -> Unit,
    onAdd: (location: String, type: String?, erectedBy: String?) -> Unit,
) {
    var location by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var erectedBy by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scf_new)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(stringResource(R.string.scf_location)) },
                    supportingText = { Text(stringResource(R.string.scf_location_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text(stringResource(R.string.scf_type)) },
                    supportingText = { Text(stringResource(R.string.scf_type_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = erectedBy,
                    onValueChange = { erectedBy = it },
                    label = { Text(stringResource(R.string.scf_erected_by)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = location.isNotBlank(),
                onClick = {
                    onAdd(
                        location.trim(),
                        type.trim().takeIf { it.isNotEmpty() },
                        erectedBy.trim().takeIf { it.isNotEmpty() },
                    )
                },
            ) {
                Text(stringResource(R.string.acc_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun AlteredDialog(onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scf_altered)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.scf_altered_hint))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.scf_defects)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(note.trim().takeIf { it.isNotEmpty() }) }) {
                Text(stringResource(R.string.scf_altered))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
