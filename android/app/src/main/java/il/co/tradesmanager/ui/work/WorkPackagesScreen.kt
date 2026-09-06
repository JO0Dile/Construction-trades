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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.work.Assignment
import il.co.tradesmanager.data.local.entity.AssignmentEntity
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
    val locale = currentLocale()

    var rejecting by remember { mutableStateOf(false) }
    val current = open

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.reference ?: stringResource(R.string.wp_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = { if (current != null) viewModel.openPackage(null) else onBack() },
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
            if (packages.isEmpty()) {
                EmptyState(
                    message = stringResource(R.string.wp_empty),
                    modifier = Modifier.padding(padding),
                )
                return@Scaffold
            }
            LazyColumn(Modifier.padding(padding)) {
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
                    current.location?.let { DetailRow(stringResource(R.string.wp_location), it) }
                    DetailRow(
                        stringResource(R.string.proj_status),
                        stringResource(statusLabel(current.status)),
                    )
                    DetailRow(
                        stringResource(R.string.party_title),
                        stringResource(
                            if (side == Assignment.Side.PAYER) R.string.wp_given else R.string.wp_taken,
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
