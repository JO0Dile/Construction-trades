package il.co.tradesmanager.ui.work

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import il.co.tradesmanager.core.access.Party
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.i18n.resolve
import il.co.tradesmanager.core.work.Assignment
import il.co.tradesmanager.data.catalog.WorkScope
import il.co.tradesmanager.data.catalog.WorkStage
import il.co.tradesmanager.data.local.entity.AssignmentEntity
import il.co.tradesmanager.data.local.entity.EngagementEntity
import il.co.tradesmanager.data.repository.EngagementRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.DetailRow
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.PhotoStrip
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.currentLocale

/**
 * Work packages on one job.
 *
 * The screen a crew leader opens to see what they have been asked to do, and
 * the same screen a contractor opens to see what they have given out. Which
 * one it is depends on which side of each agreement the signed-in firm sits,
 * and the buttons follow from that rather than from a role: only the crew
 * accepts and submits, only the payer approves and sends back.
 *
 * The buttons are derived from `core.work.Assignment`'s own transition table,
 * not written out again here. A second copy would drift, and the copy that
 * drifts is always the one showing a button that then refuses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkPackagesScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: WorkPackagesViewModel = viewModel(
        factory = ViewModelFactory(container) { WorkPackagesViewModel(it, projectId) },
    )
    val packages by viewModel.packages.collectAsStateWithLifecycle()
    val open by viewModel.open.collectAsStateWithLifecycle()
    val orgId by viewModel.orgId.collectAsStateWithLifecycle()
    val refusal by viewModel.refusal.collectAsStateWithLifecycle()
    val photos by viewModel.proofPhotos.collectAsStateWithLifecycle()
    val engagements by viewModel.engagements.collectAsStateWithLifecycle()
    val myParty by viewModel.myParty.collectAsStateWithLifecycle()
    val language = currentLocale().toLanguageTag()
    val locale = currentLocale()

    var rejecting by remember { mutableStateOf(false) }
    var engaging by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val current = open

    Scaffold(
        floatingActionButton = {
            // A package needs somebody to give it to, so the button offers
            // whichever step is actually available: bring a firm on first,
            // hand out work once there is one.
            if (current == null && myParty?.canSubcontract == true) {
                FloatingActionButton(
                    onClick = { if (engagements.size <= 1) engaging = true else creating = true },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.wp_new))
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(current?.reference ?: stringResource(R.string.wp_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (current != null) viewModel.openPackage(null) else onBack()
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
    ) { padding ->
        if (current == null) {
            // The empty state used to return early, which hid the one thing a
            // firm on a fresh job actually has to do first.
            if (packages.isEmpty() && engagements.isEmpty() && myParty != null) {
                EmptyState(
                    message = stringResource(R.string.wp_empty),
                    modifier = Modifier.padding(padding),
                )
                return@Scaffold
            }
            LazyColumn(Modifier.padding(padding)) {
                if (myParty == null) {
                    item {
                        DeclareSelfCard(onDeclare = viewModel::declareSelf)
                    }
                }
                if (engagements.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.eng_title)) }
                    items(engagements, key = { "eng-" + it.id }) { row ->
                        DetailRow(row.orgName, stringResource(partyLabel(row.party)))
                    }
                    item { HorizontalDivider() }
                    item { SectionHeader(stringResource(R.string.wp_title)) }
                }
                items(packages, key = { it.id }) { item ->
                    PackageRow(
                        item = item,
                        orgId = orgId,
                        locale = locale,
                        onOpen = { viewModel.openPackage(item.id) },
                    )
                }
            }
            return@Scaffold
        }

        val side = WorkPackagesViewModel.sideOf(current, orgId)
        LazyColumn(Modifier.padding(padding)) {
            item {
                Column(Modifier.padding(vertical = 8.dp)) {
                    DetailRow(stringResource(R.string.wp_title), current.title)
                    DetailRow(
                        stringResource(R.string.wp_amount),
                        Formats.money(current.amount, locale),
                    )
                    viewModel.stageName(current.stageId, language)?.let {
                        DetailRow(stringResource(R.string.wp_stage), it)
                    }
                    viewModel.scopeName(current.scopeId, language)?.let {
                        DetailRow(stringResource(R.string.wp_scope), it)
                    }
                    current.location?.let { DetailRow(stringResource(R.string.wp_location), it) }
                    DetailRow(
                        stringResource(R.string.proj_status),
                        stringResource(statusLabel(current.status)),
                    )
                    DetailRow(
                        stringResource(R.string.party_title),
                        stringResource(
                            if (side == Assignment.Side.PAYER) {
                                R.string.wp_given
                            } else {
                                R.string.wp_taken
                            },
                        ),
                    )
                }
            }

            current.rejectionReason?.let { reason ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.wp_rejected_because),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(reason, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Approved is where people assume the money has moved. It has not:
            // the application still has to be raised and still has to run its
            // terms, which under shotef plus thirty is another six weeks.
            if (current.status == Assignment.Status.APPROVED) {
                item {
                    Text(
                        text = stringResource(R.string.wp_approved_not_paid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            if (side == Assignment.Side.PAYEE) {
                item { SectionHeader(stringResource(R.string.snag_photo_raised)) }
                item {
                    PhotoStrip(
                        photos = photos,
                        canAdd = true,
                        newCameraTarget = viewModel::newCameraTarget,
                        onCaptured = viewModel::recordCameraPhoto,
                        onPicked = viewModel::importPhoto,
                    )
                }
                if (current.status == Assignment.Status.IN_PROGRESS && photos.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.wp_need_photo_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            items(WorkPackagesViewModel.moves(current, orgId), key = { it }) { move ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    if (move == Assignment.Status.REJECTED) {
                        OutlinedButton(
                            onClick = { rejecting = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.wp_reject)) }
                    } else {
                        Button(
                            // Disabled rather than refused. The rule is the
                            // repository's, and the strip that satisfies it is
                            // directly above — so the button waits for the
                            // photograph instead of complaining about it.
                            enabled = move != Assignment.Status.SUBMITTED ||
                                Assignment.canSubmit(current.status, photos.size),
                            onClick = { viewModel.move(move) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(moveLabel(move))) }
                    }
                }
            }

            if (Assignment.canInvoice(current.status, current.invoicedAt != null) &&
                side == Assignment.Side.PAYEE
            ) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.markInvoiced() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.wp_invoice)) }
                    }
                }
            }
        }
    }

    if (engaging) {
        EngageDialog(
            // Only positions this firm may actually engage are offered. A
            // list that showed every position and refused four of them would
            // be teaching people that the app says no for no reason.
            offered = myParty?.mayEngage().orEmpty().toList().sortedBy { it.depth },
            onDismiss = { engaging = false },
            onEngage = { name, party, scope ->
                engaging = false
                viewModel.engage(name, party, scope)
            },
        )
    }

    if (creating) {
        CreateDialog(
            engagements = engagements.filter { it.orgId != orgId && it.endedAt == null },
            stages = viewModel.stages,
            scopesFor = viewModel::scopesFor,
            language = language,
            onDismiss = { creating = false },
            onCreate = { title, amount, payee, stageId, scopeId, location ->
                creating = false
                viewModel.create(title, amount, payee, stageId, scopeId, location)
            },
        )
    }

    if (rejecting) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejecting = false },
            title = { Text(stringResource(R.string.wp_reject)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.wp_reject_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text(stringResource(R.string.wp_reject_reason)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    // Disabled rather than refused: the rule is the same one
                    // the repository enforces, and being told why after the
                    // fact is worse than the button waiting for the reason.
                    enabled = Assignment.canReject(reason),
                    onClick = {
                        rejecting = false
                        viewModel.move(Assignment.Status.REJECTED, reason = reason)
                    },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { rejecting = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    refusal?.let { why ->
        AlertDialog(
            onDismissRequest = { viewModel.clearRefusal() },
            text = { Text(stringResource(refusalLabel(why))) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearRefusal() }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }
}

@Composable
private fun PackageRow(
    item: AssignmentEntity,
    orgId: String,
    locale: java.util.Locale,
    onOpen: () -> Unit,
) {
    val side = WorkPackagesViewModel.sideOf(item, orgId)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        color = when (item.status) {
            Assignment.Status.REJECTED -> MaterialTheme.colorScheme.errorContainer
            Assignment.Status.OFFERED, Assignment.Status.SUBMITTED ->
                MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(item.reference, style = MaterialTheme.typography.labelMedium)
                Text(
                    stringResource(
                        if (side == Assignment.Side.PAYER) R.string.wp_given else R.string.wp_taken,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(
                Formats.money(item.amount, locale) + " · " +
                    stringResource(statusLabel(item.status)),
                style = MaterialTheme.typography.bodyMedium,
            )
            item.location?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    HorizontalDivider()
}

@StringRes
private fun statusLabel(status: String): Int = when (status) {
    Assignment.Status.DRAFT -> R.string.wp_status_draft
    Assignment.Status.OFFERED -> R.string.wp_status_offered
    Assignment.Status.ACCEPTED -> R.string.wp_status_accepted
    Assignment.Status.DECLINED -> R.string.wp_status_declined
    Assignment.Status.IN_PROGRESS -> R.string.wp_status_progress
    Assignment.Status.SUBMITTED -> R.string.wp_status_submitted
    Assignment.Status.REJECTED -> R.string.wp_status_rejected
    Assignment.Status.APPROVED -> R.string.wp_status_approved
    else -> R.string.wp_status_cancelled
}

/** What the button that reaches a state should say, which is not its name. */
@StringRes
private fun moveLabel(to: String): Int = when (to) {
    Assignment.Status.OFFERED -> R.string.wp_new
    Assignment.Status.ACCEPTED -> R.string.wp_accept
    Assignment.Status.DECLINED -> R.string.wp_decline
    Assignment.Status.IN_PROGRESS -> R.string.wp_start
    Assignment.Status.SUBMITTED -> R.string.wp_submit
    Assignment.Status.APPROVED -> R.string.wp_approve
    Assignment.Status.REJECTED -> R.string.wp_reject
    else -> R.string.action_cancel
}

@StringRes
private fun refusalLabel(refusal: EngagementRepository.Refusal): Int = when (refusal) {
    EngagementRepository.Refusal.WRONG_SIDE -> R.string.wp_refused_side
    EngagementRepository.Refusal.NOT_A_PARTY -> R.string.wp_refused_party
    EngagementRepository.Refusal.NO_EVIDENCE -> R.string.wp_need_photo
    EngagementRepository.Refusal.INCOMPLETE -> R.string.wp_reject_hint
    else -> R.string.wp_refused_state
}

@Composable
private fun EngageDialog(
    offered: List<Party>,
    onDismiss: () -> Unit,
    onEngage: (String, Party, String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf("") }
    var party by remember { mutableStateOf(offered.firstOrNull()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.eng_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.eng_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.party_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                offered.forEach { option ->
                    FilterChip(
                        selected = party == option,
                        onClick = { party = option },
                        label = { Text(stringResource(partyLabel(option.name))) },
                    )
                }
                OutlinedTextField(
                    value = scope,
                    onValueChange = { scope = it },
                    label = { Text(stringResource(R.string.eng_scope)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && party != null,
                onClick = { party?.let { onEngage(name, it, scope) } },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun CreateDialog(
    engagements: List<EngagementEntity>,
    stages: List<WorkStage>,
    scopesFor: (String?) -> List<WorkScope>,
    language: String,
    onDismiss: () -> Unit,
    onCreate: (String, Double, String, String?, String?, String?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var payee by remember { mutableStateOf(engagements.firstOrNull()) }
    var stageId by remember { mutableStateOf<String?>(null) }
    var scopeId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wp_new)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.wp_title_field)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.wp_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(stringResource(R.string.wp_payee), style = MaterialTheme.typography.labelLarge)
                engagements.forEach { row ->
                    FilterChip(
                        selected = payee?.id == row.id,
                        onClick = { payee = row },
                        label = { Text(row.orgName) },
                    )
                }

                Text(stringResource(R.string.wp_stage), style = MaterialTheme.typography.labelLarge)
                stages.forEach { stage ->
                    FilterChip(
                        selected = stageId == stage.id,
                        onClick = {
                            stageId = stage.id
                            // A scope belongs to a stage. Keeping the old one
                            // after the stage changes files the package under
                            // a combination that does not exist.
                            scopeId = null
                        },
                        label = { Text(stage.names.resolve(language)) },
                    )
                }

                if (stageId != null) {
                    Text(
                        stringResource(R.string.wp_scope),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    scopesFor(stageId).forEach { scope ->
                        FilterChip(
                            selected = scopeId == scope.id,
                            onClick = { scopeId = scope.id },
                            label = { Text(scope.names.resolve(language)) },
                        )
                    }
                }

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(stringResource(R.string.wp_location)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                // toDoubleOrNull, not a locale-aware parse: the field is typed
                // on a phone whose keyboard may offer a comma, and a comma
                // parsed as a thousands separator turns 7,5 into 75.
                enabled = title.isNotBlank() && payee != null &&
                    (amount.toDoubleOrNull() ?: -1.0) >= 0.0,
                onClick = {
                    val target = payee ?: return@TextButton
                    onCreate(
                        title,
                        amount.toDoubleOrNull() ?: 0.0,
                        target.orgId,
                        stageId,
                        scopeId,
                        location.takeIf { it.isNotBlank() },
                    )
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@StringRes
private fun partyLabel(party: String): Int = when (Party.parse(party)) {
    Party.CLIENT -> R.string.party_client
    Party.GENERAL_CONTRACTOR -> R.string.party_gc
    Party.FIRST_TIER -> R.string.party_first
    Party.SECOND_TIER -> R.string.party_second
    Party.SUPPLIER -> R.string.party_supplier
    Party.CONSULTANT -> R.string.party_consultant
}

/**
 * The first thing on a job with more than one firm: saying what you are.
 *
 * Every position is offered here and nothing is filtered, because this is a
 * firm describing itself rather than appointing anybody. The downward-only
 * rule governs who you may bring on, not what you may be.
 */
@Composable
private fun DeclareSelfCard(onDeclare: (Party) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.eng_self),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.eng_self_hint),
                style = MaterialTheme.typography.bodySmall,
            )
            Party.entries.sortedBy { it.depth }.forEach { option ->
                OutlinedButton(
                    onClick = { onDeclare(option) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(partyLabel(option.name))) }
            }
        }
    }
}
