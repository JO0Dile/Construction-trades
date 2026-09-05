package il.co.tradesmanager.ui.orders

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.repository.PurchasingRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.EmptyState

/**
 * Orders placed with suppliers.
 *
 * An order is where a job's shopping list stops being an intention and becomes
 * a commitment at a price somebody agreed. That is why a draft commits nothing
 * and placing it does — and why the screen says so rather than leaving people
 * to guess which state costs money.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    container: AppContainer,
    onOpenOrder: (String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: OrdersViewModel = viewModel(
        factory = ViewModelFactory(container) { OrdersViewModel(it) },
    )
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()

    val signedIn = session as? SessionRepository.State.SignedIn
    val canEdit = signedIn?.canWrite(Lens.STUFF) != false
    var raising by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.po_title)) },
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
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(onClick = { raising = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.po_new))
                }
            }
        },
    ) { padding ->
        if (orders.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.po_empty),
                hint = stringResource(R.string.po_new),
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(orders, key = { it.id }) { order ->
                    ListItem(
                        overlineContent = { Text(order.reference) },
                        headlineContent = { Text(order.supplierName) },
                        supportingContent = {
                            val job = projects.firstOrNull { it.id == order.projectId }?.name
                                ?: stringResource(R.string.po_yard)
                            Text(job + " · " + stringResource(orderStatusLabel(order.status)))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenOrder(order.id) },
                    )
                }
            }
        }
    }

    if (raising) {
        RaiseOrderDialog(
            projects = projects,
            onDismiss = { raising = false },
            onRaise = { projectId, supplier ->
                raising = false
                viewModel.raise(projectId, supplier, onOpenOrder)
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RaiseOrderDialog(
    projects: List<ProjectEntity>,
    onDismiss: () -> Unit,
    onRaise: (projectId: String?, supplier: String) -> Unit,
) {
    var supplier by remember { mutableStateOf("") }
    var projectId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.po_new)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // A name typed here is enough. Requiring a supplier record
                // first would mean orders do not get raised at all.
                OutlinedTextField(
                    value = supplier,
                    onValueChange = { supplier = it },
                    label = { Text(stringResource(R.string.po_supplier)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.po_for_job),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Not every order is for a job; a van restock belongs to
                    // the yard, and forcing it onto a project would put the
                    // cost somewhere it does not belong.
                    FilterChip(
                        selected = projectId == null,
                        onClick = { projectId = null },
                        label = { Text(stringResource(R.string.po_yard)) },
                    )
                    projects.forEach { project ->
                        FilterChip(
                            selected = projectId == project.id,
                            onClick = { projectId = project.id },
                            label = { Text(project.name) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = supplier.isNotBlank(),
                onClick = { onRaise(projectId, supplier.trim()) },
            ) {
                Text(stringResource(R.string.acc_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

internal fun orderStatusLabel(status: String): Int = when (status) {
    PurchasingRepository.Status.ORDERED -> R.string.po_status_ordered
    PurchasingRepository.Status.PART_RECEIVED -> R.string.po_status_part
    PurchasingRepository.Status.RECEIVED -> R.string.po_status_received
    PurchasingRepository.Status.CANCELLED -> R.string.po_status_cancelled
    else -> R.string.po_status_draft
}
