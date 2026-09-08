package il.co.tradesmanager.ui.work

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.work.Amendment
import il.co.tradesmanager.data.local.entity.ContractAmendmentEntity
import il.co.tradesmanager.data.local.entity.ContractEntity
import il.co.tradesmanager.data.repository.EngagementRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.DetailRow
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.currentLocale
import java.util.Locale

/**
 * What this firm agreed, and with whom.
 *
 * Only contracts it is a party to appear, and that filtering happens in the
 * query rather than here — a screen that received every contract and drew four
 * of them would still have had the other two in memory on the phone.
 *
 * The margin at the top is the number this whole model exists to protect. It
 * is the difference between two contracts, it is shown only to the firm
 * sitting between them, and it is on nobody else's screen at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContractsScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: ContractsViewModel = viewModel(
        factory = ViewModelFactory(container) { ContractsViewModel(it, projectId) },
    )
    val contracts by viewModel.contracts.collectAsStateWithLifecycle()
    val open by viewModel.open.collectAsStateWithLifecycle()
    val amendments by viewModel.amendments.collectAsStateWithLifecycle()
    val margin by viewModel.margin.collectAsStateWithLifecycle()
    val orgId by viewModel.orgId.collectAsStateWithLifecycle()
    val refusal by viewModel.refusal.collectAsStateWithLifecycle()
    val locale = currentLocale()

    var proposing by remember { mutableStateOf(false) }
    val current = open

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.reference ?: stringResource(R.string.ct_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (current != null) viewModel.openContract(null) else onBack()
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
            if (contracts.isEmpty()) {
                EmptyState(
                    message = stringResource(R.string.ct_empty),
                    modifier = Modifier.padding(padding),
                )
                return@Scaffold
            }
            LazyColumn(Modifier.padding(padding)) {
                margin?.let { kept ->
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    stringResource(R.string.ct_margin),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Text(
                                    Formats.money(kept, locale),
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                Text(
                                    stringResource(R.string.ct_margin_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                items(contracts, key = { it.id }) { contract ->
                    ContractRow(
                        contract = contract,
                        orgId = orgId,
                        locale = locale,
                        onOpen = { viewModel.openContract(contract.id) },
                    )
                }
            }
            return@Scaffold
        }

        val inForce = ContractsViewModel.currentAmount(current, amendments)
        val pending = ContractsViewModel.openAmendment(amendments)
        val signed = current.signedAt != null

        LazyColumn(Modifier.padding(padding)) {
            item {
                Column(Modifier.padding(vertical = 8.dp)) {
                    DetailRow(stringResource(R.string.ct_scope), current.title)
                    DetailRow(
                        stringResource(
                            if (current.payeeOrgId == orgId) {
                                R.string.ct_receivable
                            } else {
                                R.string.ct_payable
                            },
                        ),
                        Formats.money(inForce, locale),
                    )
                    // Shown only once it has moved, because "original" and
                    // "in force" being the same number twice is noise.
                    if (inForce != current.amount) {
                        DetailRow(
                            stringResource(R.string.ct_original),
                            Formats.money(current.amount, locale),
                        )
                    }
                    DetailRow(
                        stringResource(R.string.pay_terms),
                        ContractsViewModel.termsOf(current).name,
                    )
                    DetailRow(
                        stringResource(R.string.ct_signed_by),
                        listOfNotNull(current.signedByPayerName, current.signedByPayeeName)
                            .joinToString(" · ")
                            .ifEmpty { stringResource(R.string.ct_unsigned) },
                    )
                }
            }

            if (!signed) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        Button(
                            onClick = { viewModel.sign() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.ct_sign)) }
                    }
                }
            }

            if (pending != null) {
                item {
                    PendingAmendment(
                        amendment = pending,
                        // The proposer never decides their own. Showing them
                        // the buttons and refusing the tap would be worse than
                        // telling them who it is waiting on.
                        mine = pending.proposedByOrgId == orgId,
                        locale = locale,
                        onDecide = { accept -> viewModel.decide(pending, accept) },
                    )
                }
            } else if (signed) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        OutlinedButton(
                            onClick = { proposing = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.ct_amend)) }
                    }
                }
            }

            val history = ContractsViewModel.history(current, amendments)
            if (history.size > 1) {
                item { SectionHeader(stringResource(R.string.ct_amend_history)) }
                item {
                    Text(
                        text = history.joinToString(" → ") { Formats.money(it, locale) },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            items(
                amendments.filter { it.status != Amendment.Status.PROPOSED },
                key = { it.id },
            ) { past ->
                DetailRow(
                    "v${past.version} · ${past.status}",
                    Formats.money(past.newAmount, locale) + " · " + past.reason,
                )
            }
        }
    }

    if (proposing && current != null) {
        var amount by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { proposing = false },
            title = { Text(stringResource(R.string.ct_amend)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text(stringResource(R.string.ct_amend_amount)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.ct_amend_reason_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text(stringResource(R.string.ct_amend_reason)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    // The same completeness rule the repository applies, so
                    // the button waits rather than refusing after the tap.
                    enabled = Amendment.isComplete(amount.toDoubleOrNull(), reason),
                    onClick = {
                        proposing = false
                        viewModel.propose(amount.toDoubleOrNull(), reason)
                    },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { proposing = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    refusal?.let { why ->
        AlertDialog(
            onDismissRequest = { viewModel.clearRefusal() },
            text = { Text(stringResource(contractRefusalLabel(why))) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearRefusal() }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }
}

@Composable
private fun ContractRow(
    contract: ContractEntity,
    orgId: String,
    locale: Locale,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        color = if (contract.signedAt == null) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(contract.reference, style = MaterialTheme.typography.labelMedium)
            Text(contract.title, style = MaterialTheme.typography.titleMedium)
            Text(
                Formats.money(contract.amount, locale) + " · " +
                    stringResource(
                        if (contract.payeeOrgId == orgId) {
                            R.string.ct_receivable
                        } else {
                            R.string.ct_payable
                        },
                    ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (contract.signedAt == null) {
                Text(
                    stringResource(R.string.ct_unsigned),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun PendingAmendment(
    amendment: ContractAmendmentEntity,
    mine: Boolean,
    locale: Locale,
    onDecide: (Boolean) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                Formats.money(amendment.previousAmount, locale) + " → " +
                    Formats.money(amendment.newAmount, locale),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(amendment.reason, style = MaterialTheme.typography.bodyMedium)
            Text(
                amendment.proposedByName,
                style = MaterialTheme.typography.bodySmall,
            )
            if (mine) {
                Text(
                    stringResource(R.string.ct_amend_yours),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Button(
                    onClick = { onDecide(true) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.ct_amend_accept)) }
                OutlinedButton(
                    onClick = { onDecide(false) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.ct_amend_reject)) }
            }
        }
    }
}

@StringRes
private fun contractRefusalLabel(refusal: EngagementRepository.Refusal): Int = when (refusal) {
    EngagementRepository.Refusal.NOT_A_PARTY -> R.string.wp_refused_party
    EngagementRepository.Refusal.WRONG_SIDE -> R.string.wp_refused_side
    EngagementRepository.Refusal.INCOMPLETE -> R.string.ct_amend_reason_hint
    EngagementRepository.Refusal.ALREADY_OPEN -> R.string.ct_amend_pending
    else -> R.string.wp_refused_state
}
