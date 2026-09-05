package il.co.tradesmanager.ui.concrete

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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import il.co.tradesmanager.core.evidence.ConcretePour
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.data.local.entity.ConcreteTicketEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.DetailRow
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.SectionPlaceholder
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.components.rememberNow
import il.co.tradesmanager.ui.evidence.pluralCount
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Concrete arriving.
 *
 * The screen is built around one number: how long each truck has left. Cement
 * does not wait, a load that has run out cannot be told apart from a good one
 * by looking, and the only moment anybody can act is before it goes down the
 * chute. So the clock ticks on screen and the truck closest to the end sits at
 * the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcreteScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: ConcreteViewModel = viewModel(
        factory = ViewModelFactory(container) { ConcreteViewModel(it, projectId) },
    )
    val pours by viewModel.pours.collectAsStateWithLifecycle()
    val openPour by viewModel.openPour.collectAsStateWithLifecycle()
    val tickets by viewModel.tickets.collectAsStateWithLifecycle()
    val placed by viewModel.placedVolume.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val locale = currentLocale()
    // A minute is the right cadence: the numbers on this screen are minutes.
    val now = rememberNow(intervalMillis = 60_000L)

    val signedIn = session as? SessionRepository.State.SignedIn
    val canEdit = signedIn?.canWrite(Lens.STUFF) != false
    var starting by remember { mutableStateOf(false) }
    var addingTruck by remember { mutableStateOf(false) }
    var rejecting by remember { mutableStateOf<ConcreteTicketEntity?>(null) }

    val pour = openPour
    // Stable sort over a list the database already returns in batching order,
    // so loads whose clock has stopped keep that order at the bottom.
    val ordered = tickets.sortedBy {
        ConcretePour.urgency(
            dispatchedAt = it.dispatchedAt,
            dischargedAt = it.dischargedAt,
            now = now,
            temperatureCelsius = pour?.temperatureCelsius,
            rejected = it.rejected,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pour?.element ?: stringResource(R.string.pour_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = { if (pour != null) viewModel.open(null) else onBack() },
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
            if (canEdit) {
                FloatingActionButton(
                    onClick = { if (pour == null) starting = true else addingTruck = true },
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(
                            if (pour == null) R.string.pour_new else R.string.tick_add,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        if (pour == null) {
            if (pours.isEmpty()) {
                EmptyState(
                    message = stringResource(R.string.pour_empty),
                    hint = stringResource(R.string.pour_new),
                    modifier = Modifier.padding(padding),
                )
            } else {
                LazyColumn(Modifier.padding(padding)) {
                    items(pours, key = { it.id }) { row ->
                        ListItem(
                            overlineContent = { Text(row.reference) },
                            headlineContent = { Text(row.element) },
                            supportingContent = {
                                Text(
                                    listOfNotNull(
                                        row.mixDesign,
                                        stringResource(
                                            if (row.completedAt != null) {
                                                R.string.pour_finished
                                            } else {
                                                R.string.pour_running
                                            },
                                        ),
                                    ).joinToString(" · "),
                                )
                            },
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.open(row.id) },
                        )
                    }
                }
            }
            return@Scaffold
        }

        LazyColumn(Modifier.padding(padding)) {
            item {
                Column {
                    pour.mixDesign?.let { DetailRow(stringResource(R.string.pour_mix), it) }
                    pour.supplierName?.let { DetailRow(stringResource(R.string.pour_supplier), it) }
                    DetailRow(
                        stringResource(R.string.pour_placed_of),
                        Formats.quantity(placed, locale) + " / " +
                            (pour.orderedVolume?.let { Formats.quantity(it, locale) } ?: "—"),
                    )
                    pour.temperatureCelsius?.let {
                        DetailRow(
                            stringResource(R.string.pour_temperature),
                            Formats.quantity(it, locale),
                        )
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.tick_title)) }
            if (ordered.isEmpty()) {
                item { SectionPlaceholder(stringResource(R.string.tick_empty)) }
            }
            items(ordered, key = { it.id }) { ticket ->
                TruckRow(
                    ticket = ticket,
                    temperatureCelsius = pour.temperatureCelsius,
                    now = now,
                    canEdit = canEdit,
                    locale = locale,
                    onPlaced = { viewModel.markPlaced(ticket) },
                    onReject = { rejecting = ticket },
                    onSlump = { viewModel.recordSlump(ticket, it) },
                )
            }

            if (canEdit && pour.completedAt == null) {
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                item {
                    OutlinedButton(
                        onClick = { viewModel.finishPour(null) },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Text(stringResource(R.string.pour_finish))
                    }
                }
            }
        }
    }

    if (starting) {
        StartPourDialog(
            onDismiss = { starting = false },
            onStart = { element, mix, volume, supplier, temperature ->
                starting = false
                viewModel.startPour(element, mix, volume, supplier, temperature)
            },
        )
    }

    if (addingTruck) {
        AddTruckDialog(
            onDismiss = { addingTruck = false },
            onAdd = { ticketNumber, truck, volume, dispatchedAt ->
                addingTruck = false
                viewModel.addTicket(ticketNumber, truck, volume, dispatchedAt)
            },
        )
    }

    rejecting?.let { ticket ->
        RejectDialog(
            onDismiss = { rejecting = null },
            onReject = { reason ->
                rejecting = null
                viewModel.reject(ticket, reason)
            },
        )
    }
}

@Composable
private fun TruckRow(
    ticket: ConcreteTicketEntity,
    temperatureCelsius: Double?,
    now: Long,
    canEdit: Boolean,
    locale: Locale,
    onPlaced: () -> Unit,
    onReject: () -> Unit,
    onSlump: (Double?) -> Unit,
) {
    val state = ConcretePour.state(
        dispatchedAt = ticket.dispatchedAt,
        arrivedAt = ticket.arrivedAt,
        dischargedAt = ticket.dischargedAt,
        now = now,
        temperatureCelsius = temperatureCelsius,
    )
    val late = ConcretePour.wasPlacedLate(ticket.dispatchedAt, ticket.dischargedAt, temperatureCelsius)
    var slump by remember(ticket.id) { mutableStateOf(ticket.slumpCm?.toString().orEmpty()) }

    val scheme = MaterialTheme.colorScheme
    val (container, content) = when {
        ticket.rejected -> scheme.surfaceVariant to scheme.onSurfaceVariant
        state == ConcretePour.State.EXPIRED -> scheme.errorContainer to scheme.onErrorContainer
        state == ConcretePour.State.EXPIRING -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        else -> scheme.surface to scheme.onSurface
    }

    Surface(color = container, contentColor = content, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val batched = Instant.ofEpochMilli(ticket.dispatchedAt).atZone(ZoneId.systemDefault())
            Text(
                text = listOfNotNull(ticket.truckNumber, ticket.ticketNumber)
                    .joinToString(" · ")
                    .ifEmpty { Formats.time(batched.toLocalTime(), locale) },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = Formats.quantity(ticket.volume, locale) + " · " +
                    stringResource(R.string.tick_dispatched) + " " +
                    Formats.time(batched.toLocalTime(), locale),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = if (ticket.rejected) {
                    stringResource(R.string.tick_rejected) +
                        (ticket.rejectionReason?.let { ": $it" } ?: "")
                } else if (state == ConcretePour.State.EXPIRING) {
                    pluralCount(
                        R.plurals.tick_minutes_left,
                        ConcretePour.minutesLeft(ticket.dispatchedAt, now, temperatureCelsius),
                    )
                } else {
                    stringResource(truckStateLabel(state))
                },
                style = MaterialTheme.typography.labelLarge,
            )
            if (late) {
                Text(
                    text = stringResource(R.string.tick_late) + " — " +
                        stringResource(R.string.tick_late_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error,
                )
            }

            if (canEdit && !ticket.rejected && ticket.dischargedAt == null) {
                OutlinedTextField(
                    value = slump,
                    onValueChange = {
                        slump = it.filter { ch -> ch.isDigit() || ch == '.' }
                        onSlump(slump.toDoubleOrNull())
                    },
                    label = { Text(stringResource(R.string.tick_slump)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPlaced, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.tick_place))
                    }
                    OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.tick_reject))
                    }
                }
            }
        }
    }
    HorizontalDivider()
}

internal fun truckStateLabel(state: ConcretePour.State): Int = when (state) {
    ConcretePour.State.EN_ROUTE -> R.string.tick_state_en_route
    ConcretePour.State.ON_SITE -> R.string.tick_state_on_site
    ConcretePour.State.EXPIRING -> R.string.tick_state_expiring
    ConcretePour.State.EXPIRED -> R.string.tick_state_expired
    ConcretePour.State.PLACED -> R.string.tick_state_placed
}

@Composable
private fun StartPourDialog(
    onDismiss: () -> Unit,
    onStart: (
        element: String,
        mixDesign: String?,
        orderedVolume: Double?,
        supplierName: String?,
        temperatureCelsius: Double?,
    ) -> Unit,
) {
    var element by remember { mutableStateOf("") }
    var mix by remember { mutableStateOf("") }
    var volume by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }
    var temperature by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pour_new)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = element,
                    onValueChange = { element = it },
                    label = { Text(stringResource(R.string.pour_element)) },
                    supportingText = { Text(stringResource(R.string.pour_element_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = mix,
                    onValueChange = { mix = it },
                    label = { Text(stringResource(R.string.pour_mix)) },
                    supportingText = { Text(stringResource(R.string.pour_mix_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = volume,
                    onValueChange = { volume = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.pour_ordered)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = supplier,
                    onValueChange = { supplier = it },
                    label = { Text(stringResource(R.string.pour_supplier)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = temperature,
                    onValueChange = { temperature = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.pour_temperature)) },
                    supportingText = { Text(stringResource(R.string.pour_temperature_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = element.isNotBlank(),
                onClick = {
                    onStart(
                        element.trim(),
                        mix.trim().takeIf { it.isNotEmpty() },
                        volume.toDoubleOrNull(),
                        supplier.trim().takeIf { it.isNotEmpty() },
                        temperature.toDoubleOrNull(),
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

/**
 * Booking a truck in.
 *
 * The batch time is offered as minutes ago rather than a clock picker: the
 * person doing this is holding a delivery note in one hand and a phone in the
 * other, and "twenty minutes ago" is both faster to tap and closer to how the
 * paper reads to somebody standing at a pump.
 */
@Composable
private fun AddTruckDialog(
    onDismiss: () -> Unit,
    onAdd: (ticketNumber: String?, truckNumber: String?, volume: Double, dispatchedAt: Long) -> Unit,
) {
    var ticketNumber by remember { mutableStateOf("") }
    var truck by remember { mutableStateOf("") }
    var volume by remember { mutableStateOf("") }
    var minutesAgo by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tick_add)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = truck,
                    onValueChange = { truck = it },
                    label = { Text(stringResource(R.string.tick_truck)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ticketNumber,
                    onValueChange = { ticketNumber = it },
                    label = { Text(stringResource(R.string.tick_number)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = volume,
                    onValueChange = { volume = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.tick_volume)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = minutesAgo,
                    onValueChange = { minutesAgo = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.tick_dispatched_ago)) },
                    supportingText = { Text(stringResource(R.string.tick_dispatched_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = (volume.toDoubleOrNull() ?: 0.0) > 0.0,
                onClick = {
                    // Counted from the tap, not from the screen's ticking
                    // clock: that one only moves once a minute, and every
                    // judgement on this screen is measured off this number.
                    val ago = (minutesAgo.toLongOrNull() ?: 0L) * 60_000L
                    onAdd(
                        ticketNumber.trim().takeIf { it.isNotEmpty() },
                        truck.trim().takeIf { it.isNotEmpty() },
                        volume.toDoubleOrNull() ?: 0.0,
                        System.currentTimeMillis() - ago,
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

@Composable
private fun RejectDialog(onDismiss: () -> Unit, onReject: (String?) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tick_reject)) },
        text = {
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text(stringResource(R.string.tick_reject_reason)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onReject(reason.trim().takeIf { it.isNotEmpty() }) }) {
                Text(stringResource(R.string.tick_reject))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
