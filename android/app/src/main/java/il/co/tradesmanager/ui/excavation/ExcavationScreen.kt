package il.co.tradesmanager.ui.excavation

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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.safety.Excavation
import il.co.tradesmanager.core.security.Signature
import il.co.tradesmanager.data.local.entity.ExcavationEntity
import il.co.tradesmanager.data.local.entity.ExcavationInspectionEntity
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
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * The excavation register.
 *
 * Two things kill people in trenches and they are not the same thing. The sides
 * come in — a cubic metre of soil weighs about a tonne and a half, and somebody
 * buried to the chest cannot be pulled out by hand. Or what was already in the
 * ground is struck: a live cable, a gas main, a water main that floods the
 * trench with the man still in it.
 *
 * The second is settled before the first spade goes in, which is why locating
 * services is a gate here and not a checklist item somewhere else. And the
 * inspection is worth a **day**, not a week: it rains overnight, the sides dry
 * and crack, a lorry parks near the edge, and the man climbing in at six is
 * relying on somebody having looked since all of that.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcavationScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: ExcavationViewModel = viewModel(
        factory = ViewModelFactory(container) { ExcavationViewModel(it, projectId) },
    )
    val excavations by viewModel.excavations.collectAsStateWithLifecycle()
    val open by viewModel.open.collectAsStateWithLifecycle()
    val inspections by viewModel.inspections.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val zone = ZoneId.systemDefault()
    // The window is a calendar day, so it turns over at midnight. An hour is
    // often enough to notice, and the screen is not a countdown.
    val now = rememberNow(intervalMillis = 60L * 60L * 1000L)

    val signedIn = session as? SessionRepository.State.SignedIn
    val canEdit = signedIn?.canWrite(Lens.EVIDENCE) != false
    var adding by remember { mutableStateOf(false) }
    var recordingServices by remember { mutableStateOf(false) }
    var disturbing by remember { mutableStateOf(false) }
    var editingDimensions by remember { mutableStateOf(false) }
    val current = open
    var inspecting by remember(current?.id) { mutableStateOf(false) }

    val rows = excavations
        .map { it to ExcavationViewModel.blockersFor(it, now, zone) }
        .sortedBy { (_, blockers) -> Excavation.urgency(blockers) }
    val closed = rows.count { (item, blockers) ->
        item.backfilledAt == null && !Excavation.mayEnter(blockers)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.reference ?: stringResource(R.string.exc_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (current != null) viewModel.openExcavation(null) else onBack()
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
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.exc_new))
                }
            }
        },
    ) { padding ->
        if (current == null) {
            if (excavations.isEmpty()) {
                EmptyState(
                    message = stringResource(R.string.exc_empty),
                    hint = stringResource(R.string.exc_new),
                    modifier = Modifier.padding(padding),
                )
                return@Scaffold
            }
            LazyColumn(Modifier.padding(padding)) {
                item {
                    val scheme = MaterialTheme.colorScheme
                    Surface(
                        color = if (closed > 0) scheme.errorContainer else scheme.surfaceVariant,
                        contentColor = if (closed > 0) {
                            scheme.onErrorContainer
                        } else {
                            scheme.onSurfaceVariant
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (closed > 0) {
                                stringResource(R.string.exc_may_not_enter) + ": " + closed
                            } else {
                                stringResource(R.string.exc_may_enter)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(rows, key = { it.first.id }) { (item, blockers) ->
                    ExcavationRow(
                        item = item,
                        blockers = blockers,
                        onOpen = { viewModel.openExcavation(item.id) },
                    )
                }
            }
            return@Scaffold
        }

        val blockers = ExcavationViewModel.blockersFor(current, now, zone)

        LazyColumn(Modifier.padding(padding)) {
            item {
                val clear = Excavation.mayEnter(blockers)
                val scheme = MaterialTheme.colorScheme
                Surface(
                    color = if (clear) scheme.surfaceVariant else scheme.errorContainer,
                    contentColor = if (clear) scheme.onSurfaceVariant else scheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            stringResource(
                                if (clear) R.string.exc_may_enter else R.string.exc_may_not_enter,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        blockers.forEach { Text("· " + stringResource(blockerLabel(it))) }
                    }
                }
                HorizontalDivider()
            }

            item {
                Column {
                    DetailRow(stringResource(R.string.exc_location), current.location)
                    DetailRow(
                        stringResource(R.string.exc_depth),
                        current.depthMetres?.let { Formats.quantity(it, locale) } ?: "—",
                    )
                    DetailRow(
                        stringResource(R.string.exc_support),
                        stringResource(supportLabel(ExcavationViewModel.supportOf(current))),
                    )
                    DetailRow(
                        stringResource(R.string.exc_services),
                        current.servicesNotes
                            ?: current.servicesLocatedAt
                                ?.let { dateOf(it, zone, locale) }
                            ?: stringResource(R.string.exc_services_none),
                    )
                    current.servicesLocatedByName?.let {
                        DetailRow(stringResource(R.string.exc_services_by), it)
                    }
                    current.lastInspectorName?.let {
                        DetailRow(stringResource(R.string.exc_inspect), it)
                    }
                }
            }

            if (canEdit && current.backfilledAt == null) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (current.servicesLocatedAt == null) {
                            Button(
                                onClick = { recordingServices = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.exc_services_record))
                            }
                            Text(
                                stringResource(R.string.exc_services_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { inspecting = true },
                                enabled = !inspecting,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.exc_inspect))
                            }
                            OutlinedButton(
                                onClick = { editingDimensions = true },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.exc_depth))
                            }
                        }
                        OutlinedButton(
                            onClick = { disturbing = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.exc_disturbed))
                        }
                    }
                }
            }

            if (canEdit && inspecting) {
                item {
                    InspectionForm(
                        onCancel = { inspecting = false },
                        onRecord = { passed, defects, action, signature ->
                            inspecting = false
                            viewModel.inspect(passed, defects, action, signature)
                        },
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.exc_inspections)) }
            if (inspections.isEmpty()) {
                item { SectionPlaceholder(stringResource(R.string.exc_no_inspections)) }
            }
            items(inspections, key = { it.id }) { inspection ->
                InspectionRow(inspection = inspection, zone = zone, locale = locale)
            }

            val backfilledAt = current.backfilledAt
            if (backfilledAt != null) {
                item {
                    SectionPlaceholder(
                        stringResource(R.string.exc_backfilled) + " · " +
                            dateOf(backfilledAt, zone, locale),
                    )
                }
            } else if (canEdit) {
                item {
                    OutlinedButton(
                        onClick = { viewModel.backfill() },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Text(stringResource(R.string.exc_backfill))
                    }
                }
            }
        }
    }

    if (adding) {
        AddDialog(
            onDismiss = { adding = false },
            onAdd = { location, depth, support ->
                adding = false
                viewModel.create(location, depth, support)
            },
        )
    }

    if (editingDimensions) {
        current?.let { item ->
            DimensionsDialog(
                item = item,
                onDismiss = { editingDimensions = false },
                onSave = { depth, support ->
                    editingDimensions = false
                    viewModel.setDimensions(depth, support)
                },
            )
        }
    }

    if (recordingServices) {
        ServicesDialog(
            onDismiss = { recordingServices = false },
            onRecord = {
                recordingServices = false
                viewModel.recordServicesLocated(it)
            },
        )
    }

    if (disturbing) {
        DisturbedDialog(
            onDismiss = { disturbing = false },
            onConfirm = {
                disturbing = false
                viewModel.markDisturbed(it)
            },
        )
    }
}

@Composable
private fun ExcavationRow(
    item: ExcavationEntity,
    blockers: Set<Excavation.Blocker>,
    onOpen: () -> Unit,
) {
    val filled = item.backfilledAt != null
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = when {
            filled -> scheme.surfaceVariant
            Excavation.mayEnter(blockers) -> scheme.surface
            else -> scheme.errorContainer
        },
        contentColor = when {
            filled -> scheme.onSurfaceVariant
            Excavation.mayEnter(blockers) -> scheme.onSurface
            else -> scheme.onErrorContainer
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.reference, style = MaterialTheme.typography.labelMedium)
            Text(item.location, style = MaterialTheme.typography.titleMedium)
            Text(
                text = when {
                    filled -> stringResource(R.string.exc_backfilled)
                    Excavation.mayEnter(blockers) -> stringResource(R.string.exc_may_enter)
                    // The first reason, not all of them: the list is a glance,
                    // and the worst thing wrong is already sorted to the top.
                    else -> blockers.firstOrNull()
                        ?.let { stringResource(blockerLabel(it)) }
                        .orEmpty()
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun InspectionRow(
    inspection: ExcavationInspectionEntity,
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
                if (inspection.passed) R.string.exc_passed else R.string.exc_failed,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = if (inspection.passed) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        inspection.defects?.let {
            Text(
                stringResource(R.string.exc_defects) + ": " + it,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        inspection.actionTaken?.let {
            Text(
                stringResource(R.string.exc_action) + ": " + it,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    HorizontalDivider()
}

/** Inline, not in a dialog — the signature pad does not fit in one. */
@Composable
private fun InspectionForm(
    onCancel: () -> Unit,
    onRecord: (
        passed: Boolean,
        defects: String?,
        actionTaken: String?,
        signature: String?,
    ) -> Unit,
) {
    var passed by remember { mutableStateOf(true) }
    var defects by remember { mutableStateOf("") }
    var action by remember { mutableStateOf("") }
    var signature by remember { mutableStateOf("") }
    val signed = Signature.isSigned(signature)

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = passed,
                onClick = { passed = true },
                label = { Text(stringResource(R.string.exc_passed)) },
            )
            FilterChip(
                selected = !passed,
                onClick = { passed = false },
                label = { Text(stringResource(R.string.exc_failed)) },
            )
        }
        if (!passed) {
            OutlinedTextField(
                value = defects,
                onValueChange = { defects = it },
                label = { Text(stringResource(R.string.exc_defects)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = action,
                onValueChange = { action = it },
                label = { Text(stringResource(R.string.exc_action)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SignaturePad(onSignatureChange = { signature = it })
        if (!signed) {
            Text(
                stringResource(R.string.exc_must_sign),
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

private fun dateOf(millis: Long, zone: ZoneId, locale: Locale): String =
    Formats.date(Instant.ofEpochMilli(millis).atZone(zone).toLocalDate(), locale)

internal fun supportLabel(support: Excavation.Support): Int = when (support) {
    Excavation.Support.NONE -> R.string.exc_support_none
    Excavation.Support.BATTERED -> R.string.exc_support_battered
    Excavation.Support.SHORED -> R.string.exc_support_shored
    Excavation.Support.PILED -> R.string.exc_support_piled
}

internal fun blockerLabel(blocker: Excavation.Blocker): Int = when (blocker) {
    Excavation.Blocker.SERVICES_NOT_LOCATED -> R.string.exc_block_services
    Excavation.Blocker.NO_SUPPORT -> R.string.exc_block_support
    Excavation.Blocker.NEVER_INSPECTED -> R.string.exc_block_never
    Excavation.Blocker.NOT_INSPECTED_TODAY -> R.string.exc_block_today
    Excavation.Blocker.LAST_INSPECTION_FAILED -> R.string.exc_block_failed
    Excavation.Blocker.DISTURBED -> R.string.exc_block_disturbed
    Excavation.Blocker.BACKFILLED -> R.string.exc_block_backfilled
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SupportPicker(selected: Excavation.Support, onSelect: (Excavation.Support) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.exc_support), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Excavation.Support.entries.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    label = { Text(stringResource(supportLabel(option))) },
                )
            }
        }
    }
}

@Composable
private fun DepthField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        // Digits and a dot only, so a phone set to a comma decimal cannot write
        // a number the app then fails to read back.
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }) },
        label = { Text(stringResource(R.string.exc_depth)) },
        supportingText = { Text(stringResource(R.string.exc_depth_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AddDialog(
    onDismiss: () -> Unit,
    onAdd: (location: String, depthMetres: Double?, support: Excavation.Support) -> Unit,
) {
    var location by remember { mutableStateOf("") }
    var depth by remember { mutableStateOf("") }
    var support by remember { mutableStateOf(Excavation.Support.NONE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exc_new)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(stringResource(R.string.exc_location)) },
                    supportingText = { Text(stringResource(R.string.exc_location_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DepthField(depth) { depth = it }
                SupportPicker(support) { support = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = location.isNotBlank(),
                onClick = { onAdd(location.trim(), depth.toDoubleOrNull(), support) },
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
private fun DimensionsDialog(
    item: ExcavationEntity,
    onDismiss: () -> Unit,
    onSave: (depthMetres: Double?, support: Excavation.Support) -> Unit,
) {
    var depth by remember(item.id) { mutableStateOf(item.depthMetres?.toString().orEmpty()) }
    var support by remember(item.id) {
        mutableStateOf(ExcavationViewModel.supportOf(item))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exc_depth)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DepthField(depth) { depth = it }
                SupportPicker(support) { support = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(depth.toDoubleOrNull(), support) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * What is buried here.
 *
 * Recorded once, with a note of how it was established, because unlike every
 * other field on this screen the answer does not change and the mistake cannot
 * be undone.
 */
@Composable
private fun ServicesDialog(onDismiss: () -> Unit, onRecord: (String?) -> Unit) {
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exc_services_record)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.exc_services_hint))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.exc_services)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = notes.isNotBlank(),
                onClick = { onRecord(notes.trim()) },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun DisturbedDialog(onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exc_disturbed)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.exc_disturbed_hint))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.exc_defects)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(note.trim().takeIf { it.isNotEmpty() }) }) {
                Text(stringResource(R.string.exc_disturbed))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
