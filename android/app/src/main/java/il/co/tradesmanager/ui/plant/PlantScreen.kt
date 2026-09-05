package il.co.tradesmanager.ui.plant

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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.money.HireCost
import il.co.tradesmanager.core.people.Expiry
import il.co.tradesmanager.data.local.entity.EquipmentEntity
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.repository.EquipmentRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.currentLocale
import java.time.ZoneId

/**
 * The plant register: what the firm owns or hires, where it is, and whether it
 * is legal to use.
 *
 * Kept out of Inventory because the two answer different questions. Stock asks
 * "have we got enough"; plant asks "where is the breaker and when was it last
 * serviced". A machine overdue a service is the same shape of problem as a
 * person with a lapsed ticket, so it is warned about the same way and on the
 * same thirty-day window.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: PlantViewModel = viewModel(
        factory = ViewModelFactory(container) { PlantViewModel(it) },
    )
    val equipment by viewModel.equipment.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()

    val signedIn = session as? SessionRepository.State.SignedIn
    val canEdit = signedIn?.canWrite(Lens.STUFF) != false

    var adding by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<EquipmentEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plant_title)) },
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
                FloatingActionButton(onClick = { adding = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.plant_add))
                }
            }
        },
    ) { padding ->
        if (equipment.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.plant_empty),
                hint = stringResource(R.string.plant_add),
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(equipment, key = { it.id }) { machine ->
                    PlantRow(
                        machine = machine,
                        projectName = projects.firstOrNull {
                            it.id == machine.assignedProjectId
                        }?.name,
                        onClick = { if (canEdit) chosen = machine },
                    )
                }
            }
        }
    }

    if (adding) {
        AddPlantDialog(
            onDismiss = { adding = false },
            onAdd = { name, ownership, serial, rate, serviceDue ->
                viewModel.add(name, ownership, serial, rate, serviceDue)
                adding = false
            },
        )
    }

    chosen?.let { machine ->
        PlantActionsDialog(
            machine = machine,
            projects = projects,
            onDismiss = { chosen = null },
            onAssign = {
                viewModel.assign(machine, it)
                chosen = null
            },
            onStatus = {
                viewModel.setStatus(machine, it)
                chosen = null
            },
            onServiced = {
                viewModel.recordService(machine, null)
                chosen = null
            },
            onRemove = {
                viewModel.remove(machine)
                chosen = null
            },
        )
    }
}

@Composable
private fun PlantRow(machine: EquipmentEntity, projectName: String?, onClick: () -> Unit) {
    val now = System.currentTimeMillis()
    val service = Expiry.state(machine.serviceDueOn, now)
    val trailing: (@Composable () -> Unit)? = machine.serialNumber?.let { serial ->
        { Text(serial) }
    }

    ListItem(
        overlineContent = {
            Text(
                stringResource(
                    if (machine.ownership == EquipmentRepository.Ownership.HIRED) {
                        R.string.plant_hired
                    } else {
                        R.string.plant_owned
                    },
                ),
            )
        },
        headlineContent = { Text(machine.name) },
        supportingContent = {
            val where = projectName ?: stringResource(statusLabel(machine.status))
            Text(
                text = when (service) {
                    Expiry.State.EXPIRED ->
                        where + " · " + stringResource(R.string.plant_overdue)
                    Expiry.State.EXPIRING_SOON ->
                        where + " · " + stringResource(R.string.plant_service_due)
                    else -> where
                },
                color = when (service) {
                    Expiry.State.EXPIRED -> MaterialTheme.colorScheme.error
                    Expiry.State.EXPIRING_SOON -> Amber
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        trailingContent = trailing,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddPlantDialog(
    onDismiss: () -> Unit,
    onAdd: (
        name: String,
        ownership: String,
        serial: String?,
        rate: Double?,
        serviceDue: Long?,
    ) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var ownership by remember { mutableStateOf(EquipmentRepository.Ownership.OWNED) }
    var serial by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var serviceDue by remember { mutableStateOf("") }

    val hired = ownership == EquipmentRepository.Ownership.HIRED
    val parsedRate = rate.trim().replace(',', '.').toDoubleOrNull()
    val parsedDue = if (serviceDue.isBlank()) null else Formats.parseDate(serviceDue)
    val dueOk = serviceDue.isBlank() || parsedDue != null
    // A hire with no rate is a machine that silently costs nothing, which is
    // the one thing a hired machine never does.
    val rateOk = !hired || parsedRate != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plant_add)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.plant_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !hired,
                        onClick = { ownership = EquipmentRepository.Ownership.OWNED },
                        label = { Text(stringResource(R.string.plant_owned)) },
                    )
                    FilterChip(
                        selected = hired,
                        onClick = { ownership = EquipmentRepository.Ownership.HIRED },
                        label = { Text(stringResource(R.string.plant_hired)) },
                    )
                }
                if (hired) {
                    OutlinedTextField(
                        value = rate,
                        onValueChange = { rate = it },
                        label = { Text(stringResource(R.string.plant_rate)) },
                        supportingText = { Text(stringResource(R.string.money_ex_vat)) },
                        isError = !rateOk,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = serial,
                    onValueChange = { serial = it },
                    label = { Text(stringResource(R.string.plant_serial)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = serviceDue,
                    onValueChange = { serviceDue = it },
                    label = { Text(stringResource(R.string.plant_service)) },
                    isError = !dueOk,
                    supportingText = {
                        Text(
                            stringResource(
                                if (dueOk) R.string.cert_expires_hint else R.string.cert_date_bad,
                            ),
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && dueOk && rateOk,
                onClick = {
                    onAdd(
                        name.trim(),
                        ownership,
                        serial.takeIf { it.isNotBlank() },
                        parsedRate.takeIf { hired },
                        parsedDue?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
                    )
                },
            ) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlantActionsDialog(
    machine: EquipmentEntity,
    projects: List<ProjectEntity>,
    onDismiss: () -> Unit,
    onAssign: (String?) -> Unit,
    onStatus: (String) -> Unit,
    onServiced: () -> Unit,
    onRemove: () -> Unit,
) {
    val locale = currentLocale()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(machine.name) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // What this machine has cost so far, from the same rule the
                // Money lens sums — a foreman deciding whether to send it back
                // wants the number, not a reason to go and work it out.
                if (machine.ownership == EquipmentRepository.Ownership.HIRED) {
                    Text(
                        text = stringResource(R.string.plant_accrued) + ": " + Formats.money(
                            HireCost.accrued(
                                machine.hireRatePerDay,
                                machine.hireStartedOn,
                                machine.hireEndsOn,
                                System.currentTimeMillis(),
                            ),
                            locale,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Text(
                    text = stringResource(R.string.plant_assign),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = machine.assignedProjectId == null,
                        onClick = { onAssign(null) },
                        label = { Text(stringResource(R.string.plant_unassign)) },
                    )
                    projects.forEach { project ->
                        FilterChip(
                            selected = machine.assignedProjectId == project.id,
                            onClick = { onAssign(project.id) },
                            label = { Text(project.name) },
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.proj_status),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EquipmentRepository.Status.all.forEach { status ->
                        FilterChip(
                            selected = machine.status == status,
                            onClick = { onStatus(status) },
                            label = { Text(stringResource(statusLabel(status))) },
                        )
                    }
                }

                TextButton(onClick = onServiced) {
                    Text(stringResource(R.string.plant_serviced))
                }
                TextButton(onClick = onRemove) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

internal fun statusLabel(status: String): Int = when (status) {
    EquipmentRepository.Status.ON_SITE -> R.string.plant_on_site
    EquipmentRepository.Status.MAINTENANCE -> R.string.plant_maintenance
    EquipmentRepository.Status.OFF_HIRE -> R.string.plant_off_hire
    else -> R.string.plant_available
}

/** Amber: still usable, but book the service. Not an error yet. */
private val Amber = Color(0xFFB9770E)
