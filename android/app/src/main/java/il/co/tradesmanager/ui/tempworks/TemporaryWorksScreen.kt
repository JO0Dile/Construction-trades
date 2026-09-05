package il.co.tradesmanager.ui.tempworks

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
import il.co.tradesmanager.core.safety.TemporaryWorks
import il.co.tradesmanager.data.local.entity.ConcretePourEntity
import il.co.tradesmanager.data.local.entity.TemporaryWorksEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.data.repository.TemporaryWorksRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.DetailRow
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.components.rememberNow
import il.co.tradesmanager.ui.evidence.pluralCount
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * The temporary works register.
 *
 * Two gates, one above the other, because they are the two moments these
 * structures fail: when somebody loads them, and when somebody takes them away.
 * Everything in between — designed, checked, erected, inspected — exists to
 * open the first; and the second is a decision that has to have a name on it,
 * because striking props under a slab that has not reached strength drops the
 * slab, and afterwards nobody can say who released it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemporaryWorksScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: TemporaryWorksViewModel = viewModel(
        factory = ViewModelFactory(container) { TemporaryWorksViewModel(it, projectId) },
    )
    val items by viewModel.items.collectAsStateWithLifecycle()
    val open by viewModel.open.collectAsStateWithLifecycle()
    val pours by viewModel.pours.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val zone = ZoneId.systemDefault()
    // Counted in days; the register only turns over at midnight.
    val now = rememberNow(intervalMillis = 60L * 60L * 1000L)

    val signedIn = session as? SessionRepository.State.SignedIn
    val canEdit = signedIn?.canWrite(Lens.EVIDENCE) != false
    // Releasing propping for striking is a supervisor's act, not a writer's.
    val canRelease = signedIn == null || signedIn.role.canManageJobs
    var adding by remember { mutableStateOf(false) }
    var editingDesign by remember { mutableStateOf(false) }
    var recordingCheck by remember { mutableStateOf(false) }

    val current = open

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.reference ?: stringResource(R.string.tw_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = { if (current != null) viewModel.openItem(null) else onBack() },
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
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tw_new))
                }
            }
        },
    ) { padding ->
        if (current == null) {
            if (items.isEmpty()) {
                EmptyState(
                    message = stringResource(R.string.tw_empty),
                    hint = stringResource(R.string.tw_new),
                    modifier = Modifier.padding(padding),
                )
                return@Scaffold
            }
            LazyColumn(Modifier.padding(padding)) {
                items(items, key = { it.id }) { item ->
                    ItemRow(
                        item = item,
                        now = now,
                        zone = zone,
                        onOpen = { viewModel.openItem(item.id) },
                    )
                }
            }
            return@Scaffold
        }

        val loadBlockers = TemporaryWorksViewModel.loadBlockers(current)
        val strikeBlockers = TemporaryWorksViewModel.strikeBlockers(current, now, zone)
        val owed = TemporaryWorks.daysStillOwed(
            current.supportsPourAt,
            current.minimumStrikingDays,
            now,
            zone,
        )

        LazyColumn(Modifier.padding(padding)) {
            item {
                Gate(
                    clearMessage = R.string.tw_may_load,
                    blockedMessage = R.string.tw_may_not_load,
                    reasons = loadBlockers.map { loadBlockerLabel(it) },
                )
            }
            item {
                Gate(
                    clearMessage = R.string.tw_may_strike,
                    blockedMessage = R.string.tw_may_not_strike,
                    reasons = strikeBlockers.map { strikeBlockerLabel(it) },
                    footnote = if (owed > 0L) pluralCount(R.plurals.tw_days_owed, owed) else null,
                )
            }

            item {
                Column {
                    DetailRow(stringResource(R.string.tw_what), current.description)
                    DetailRow(
                        stringResource(R.string.tw_kind),
                        stringResource(kindLabel(TemporaryWorksViewModel.kindOf(current))),
                    )
                    DetailRow(
                        stringResource(R.string.tw_category),
                        stringResource(categoryLabel(TemporaryWorksViewModel.categoryOf(current))),
                    )
                    current.designReference?.let {
                        DetailRow(stringResource(R.string.tw_design_ref), it)
                    }
                    current.designerName?.let {
                        DetailRow(stringResource(R.string.tw_designer), it)
                    }
                    current.checkerName?.let {
                        DetailRow(stringResource(R.string.tw_checker), it)
                    }
                    DetailRow(
                        stringResource(R.string.tw_supports_pour),
                        current.supportsPourAt
                            ?.let { dateOf(it, zone, locale) }
                            ?: stringResource(R.string.tw_supports_pour_none),
                    )
                    DetailRow(
                        stringResource(R.string.tw_striking_days),
                        current.minimumStrikingDays.toString(),
                    )
                    current.releasedByName?.let {
                        DetailRow(stringResource(R.string.tw_released_by), it)
                    }
                }
            }

            if (canEdit && current.struckAt == null) {
                item { SectionHeader(stringResource(R.string.tw_title)) }
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { editingDesign = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.tw_design_ref))
                        }
                        // Each step is a separate act with a separate name on
                        // it. One form with six date fields would let a single
                        // person fill in all six on the day of the accident.
                        StepButton(
                            labelRes = R.string.tw_mark_checked,
                            done = current.checkedAt != null,
                            onClick = { recordingCheck = true },
                        )
                        StepButton(
                            labelRes = R.string.tw_mark_erected,
                            done = current.erectedAt != null,
                            onClick = { viewModel.markErected() },
                        )
                        StepButton(
                            labelRes = R.string.tw_mark_inspected,
                            done = current.inspectedAt != null,
                            onClick = { viewModel.markInspected() },
                        )
                        Text(
                            stringResource(R.string.tw_mark_inspected_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        StepButton(
                            labelRes = R.string.tw_mark_loaded,
                            done = current.loadedAt != null,
                            // The first gate bites here: nothing is recorded as
                            // carrying load until it is allowed to.
                            enabled = TemporaryWorks.mayLoad(loadBlockers),
                            onClick = { viewModel.markLoaded() },
                        )
                    }
                }

                item {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (canRelease) {
                            StepButton(
                                labelRes = R.string.tw_release,
                                done = current.releasedAt != null,
                                onClick = { viewModel.release() },
                            )
                            Text(
                                stringResource(R.string.tw_release_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            // And the second gate bites here.
                            enabled = TemporaryWorks.mayStrike(strikeBlockers),
                            onClick = { viewModel.markStruck() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.tw_mark_struck))
                        }
                    }
                }
            } else {
                // Pulled out as a local rather than relying on a smart cast
                // reaching inside a lazy item's lambda.
                val struckAt = current.struckAt
                if (struckAt != null) {
                    item {
                        Text(
                            stringResource(R.string.tw_struck) + " · " +
                                dateOf(struckAt, zone, locale),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }

    if (adding) {
        AddDialog(
            onDismiss = { adding = false },
            onAdd = { description, kind, category ->
                adding = false
                viewModel.create(description, kind, category)
            },
        )
    }

    if (editingDesign) {
        current?.let { item ->
            DesignDialog(
                item = item,
                pours = pours,
                locale = locale,
                zone = zone,
                onDismiss = { editingDesign = false },
                onSave = { reference, designer, days, pour ->
                    editingDesign = false
                    viewModel.setDesign(reference, designer, days, pour)
                },
            )
        }
    }

    if (recordingCheck) {
        CheckerDialog(
            onDismiss = { recordingCheck = false },
            onRecord = {
                recordingCheck = false
                viewModel.markChecked(it)
            },
        )
    }
}

/**
 * A verdict and its reasons.
 *
 * Both gates render through the same thing on purpose. They are the same shape
 * of question, and giving them different shapes on screen would make one of
 * them look like the important one.
 */
@Composable
private fun Gate(
    clearMessage: Int,
    blockedMessage: Int,
    reasons: List<Int>,
    footnote: String? = null,
) {
    val clear = reasons.isEmpty()
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = if (clear) scheme.surfaceVariant else scheme.errorContainer,
        contentColor = if (clear) scheme.onSurfaceVariant else scheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(if (clear) clearMessage else blockedMessage),
                style = MaterialTheme.typography.titleMedium,
            )
            reasons.forEach { Text("· " + stringResource(it)) }
            footnote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
    HorizontalDivider()
}

@Composable
private fun StepButton(
    labelRes: Int,
    done: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    if (done) {
        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(labelRes))
        }
    } else {
        Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(labelRes))
        }
    }
}

@Composable
private fun ItemRow(
    item: TemporaryWorksEntity,
    now: Long,
    zone: ZoneId,
    onOpen: () -> Unit,
) {
    val struck = item.struckAt != null
    val loadBlockers = TemporaryWorksViewModel.loadBlockers(item)
    val owed = TemporaryWorks.daysStillOwed(item.supportsPourAt, item.minimumStrikingDays, now, zone)
    val scheme = MaterialTheme.colorScheme
    // Amber, not red: propping that is doing its job and still owes days is the
    // normal state of a building site, not a fault.
    val waiting = !struck && owed > 0L
    Surface(
        color = when {
            struck -> scheme.surfaceVariant
            item.loadedAt == null && !TemporaryWorks.mayLoad(loadBlockers) -> scheme.errorContainer
            waiting -> scheme.tertiaryContainer
            else -> scheme.surface
        },
        contentColor = when {
            struck -> scheme.onSurfaceVariant
            item.loadedAt == null && !TemporaryWorks.mayLoad(loadBlockers) -> scheme.onErrorContainer
            waiting -> scheme.onTertiaryContainer
            else -> scheme.onSurface
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.reference, style = MaterialTheme.typography.labelMedium)
            Text(item.description, style = MaterialTheme.typography.titleMedium)
            Text(
                text = when {
                    struck -> stringResource(R.string.tw_struck)
                    owed > 0L -> pluralCount(R.plurals.tw_days_owed, owed)
                    item.loadedAt != null -> stringResource(R.string.tw_mark_loaded)
                    TemporaryWorks.mayLoad(loadBlockers) -> stringResource(R.string.tw_may_load)
                    else -> stringResource(R.string.tw_may_not_load)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    HorizontalDivider()
}

private fun dateOf(millis: Long, zone: ZoneId, locale: Locale): String =
    Formats.date(Instant.ofEpochMilli(millis).atZone(zone).toLocalDate(), locale)

internal fun kindLabel(kind: TemporaryWorksRepository.Kind): Int = when (kind) {
    TemporaryWorksRepository.Kind.PROPPING -> R.string.tw_kind_propping
    TemporaryWorksRepository.Kind.FORMWORK -> R.string.tw_kind_formwork
    TemporaryWorksRepository.Kind.SHORING -> R.string.tw_kind_shoring
    TemporaryWorksRepository.Kind.FACADE_RETENTION -> R.string.tw_kind_facade
    TemporaryWorksRepository.Kind.EDGE_PROTECTION -> R.string.tw_kind_edge
    TemporaryWorksRepository.Kind.OTHER -> R.string.tw_kind_other
}

internal fun categoryLabel(category: TemporaryWorks.CheckCategory): Int = when (category) {
    TemporaryWorks.CheckCategory.ZERO -> R.string.tw_category_0
    TemporaryWorks.CheckCategory.ONE -> R.string.tw_category_1
    TemporaryWorks.CheckCategory.TWO -> R.string.tw_category_2
    TemporaryWorks.CheckCategory.THREE -> R.string.tw_category_3
}

internal fun loadBlockerLabel(blocker: TemporaryWorks.LoadBlocker): Int = when (blocker) {
    TemporaryWorks.LoadBlocker.NO_DESIGN -> R.string.tw_block_no_design
    TemporaryWorks.LoadBlocker.NOT_CHECKED -> R.string.tw_block_not_checked
    TemporaryWorks.LoadBlocker.CHECKER_NOT_INDEPENDENT -> R.string.tw_block_not_independent
    TemporaryWorks.LoadBlocker.NOT_ERECTED -> R.string.tw_block_not_erected
    TemporaryWorks.LoadBlocker.NOT_INSPECTED -> R.string.tw_block_not_inspected
    TemporaryWorks.LoadBlocker.STRUCK -> R.string.tw_block_struck
}

internal fun strikeBlockerLabel(blocker: TemporaryWorks.StrikeBlocker): Int = when (blocker) {
    TemporaryWorks.StrikeBlocker.NOT_ERECTED -> R.string.tw_block_not_erected
    TemporaryWorks.StrikeBlocker.ALREADY_STRUCK -> R.string.tw_block_already_struck
    TemporaryWorks.StrikeBlocker.CONCRETE_TOO_YOUNG -> R.string.tw_block_too_young
    TemporaryWorks.StrikeBlocker.NOT_RELEASED -> R.string.tw_block_not_released
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddDialog(
    onDismiss: () -> Unit,
    onAdd: (
        description: String,
        kind: TemporaryWorksRepository.Kind,
        category: TemporaryWorks.CheckCategory,
    ) -> Unit,
) {
    var description by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(TemporaryWorksRepository.Kind.PROPPING) }
    var category by remember { mutableStateOf(TemporaryWorks.CheckCategory.ONE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tw_new)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.tw_what)) },
                    supportingText = { Text(stringResource(R.string.tw_what_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.tw_kind), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TemporaryWorksRepository.Kind.entries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kind = option },
                            label = { Text(stringResource(kindLabel(option))) },
                        )
                    }
                }
                Text(
                    stringResource(R.string.tw_category),
                    style = MaterialTheme.typography.labelLarge,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TemporaryWorks.CheckCategory.entries.forEach { option ->
                        FilterChip(
                            selected = category == option,
                            onClick = { category = option },
                            label = { Text(stringResource(categoryLabel(option))) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = description.isNotBlank(),
                onClick = { onAdd(description.trim(), kind, category) },
            ) {
                Text(stringResource(R.string.acc_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * The design, and what it is holding up.
 *
 * Tying propping to a pour the app already recorded is what makes the striking
 * gate worth having: the register then knows when the concrete went in, rather
 * than relying on somebody's memory of which Thursday it was.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesignDialog(
    item: TemporaryWorksEntity,
    pours: List<ConcretePourEntity>,
    locale: Locale,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onSave: (
        designReference: String?,
        designerName: String?,
        minimumStrikingDays: Long,
        pour: ConcretePourEntity?,
    ) -> Unit,
) {
    var reference by remember { mutableStateOf(item.designReference.orEmpty()) }
    var designer by remember { mutableStateOf(item.designerName.orEmpty()) }
    var days by remember { mutableStateOf(item.minimumStrikingDays.toString()) }
    var pour by remember {
        mutableStateOf(pours.firstOrNull { it.id == item.supportsPourId })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tw_design_ref)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    label = { Text(stringResource(R.string.tw_design_ref)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = designer,
                    onValueChange = { designer = it },
                    label = { Text(stringResource(R.string.tw_designer)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = days,
                    onValueChange = { days = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.tw_striking_days)) },
                    supportingText = { Text(stringResource(R.string.tw_striking_days_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.tw_supports_pour),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = pour == null,
                        onClick = { pour = null },
                        label = { Text(stringResource(R.string.tw_supports_pour_none)) },
                    )
                    pours.forEach { option ->
                        FilterChip(
                            selected = pour?.id == option.id,
                            onClick = { pour = option },
                            label = {
                                Text(
                                    option.element + " · " +
                                        dateOf(option.startedAt, zone, locale),
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        reference.trim().takeIf { it.isNotEmpty() },
                        designer.trim().takeIf { it.isNotEmpty() },
                        days.toLongOrNull() ?: TemporaryWorks.DEFAULT_STRIKING_DAYS,
                        pour,
                    )
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Who checked the design.
 *
 * Typed rather than taken from whoever is signed in, because the person
 * checking a category-three design is usually not the person holding the
 * phone — and recording the wrong name is worse than recording none.
 */
@Composable
private fun CheckerDialog(onDismiss: () -> Unit, onRecord: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tw_mark_checked)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.tw_checker)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onRecord(name.trim()) },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
