package il.co.tradesmanager.ui.waste

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.evidence.Waste
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.i18n.Numbers
import il.co.tradesmanager.data.local.entity.WasteLoadEntity
import il.co.tradesmanager.data.repository.WasteRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.components.rememberImageAdder
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * One job's waste register.
 *
 * What it adds up to comes first -- how much, how much of it was kept out of
 * landfill, and how many loads nothing yet shows went anywhere -- because that
 * is the paragraph somebody at handover wants. The loads follow, newest first,
 * each saying whether it is proven and offering the way to prove it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WasteScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: WasteViewModel =
        viewModel(factory = ViewModelFactory(container) { WasteViewModel(it, projectId) })
    val loads by viewModel.loads.collectAsStateWithLifecycle()
    val photos by viewModel.ticketPhotos.collectAsStateWithLifecycle()
    val totals by viewModel.totals.collectAsStateWithLifecycle()
    val mayRecord by viewModel.mayRecord.collectAsStateWithLifecycle()
    val refusal by viewModel.refusal.collectAsStateWithLifecycle()
    val locale = currentLocale()

    var recording by remember { mutableStateOf(false) }
    var numbering by remember { mutableStateOf<WasteLoadEntity?>(null) }

    val addPhoto = rememberImageAdder(
        newCameraTarget = { viewModel.newTicketTarget() },
        onCaptured = { viewModel.ticketCaptured(it) },
        onPicked = { viewModel.ticketPicked(it) },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.waste_title)) },
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
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.waste_blurb),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                totals.forEach { total -> TotalsLine(total, locale) }
                val unproven = totals.sumOf { it.unproven }
                if (unproven > 0) {
                    Text(
                        pluralStringResource(R.plurals.waste_unproven, unproven, unproven),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (mayRecord) {
                    Button(onClick = { recording = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.waste_add))
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            if (loads.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.waste_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(loads, key = { it.id }) { load ->
                LoadCard(
                    load = load,
                    photos = photos[load.id] ?: 0,
                    locale = locale,
                    mayRecord = mayRecord,
                    onAddNumber = { numbering = load },
                    onPhotograph = {
                        viewModel.photographingFor(load.id)
                        addPhoto()
                    },
                )
            }
        }
    }

    if (recording) {
        RecordLoadDialog(
            refusal = refusal,
            onDismiss = {
                recording = false
                viewModel.clearRefusal()
            },
            onRecord = { stream, quantity, unit, destination, facility, hauler, ticket, notes ->
                viewModel.clearRefusal()
                viewModel.record(stream, quantity, unit, destination, facility, hauler, ticket, notes) {
                    recording = false
                }
            },
        )
    }

    numbering?.let { load ->
        TicketNumberDialog(
            onDismiss = { numbering = null },
            onSave = { number ->
                viewModel.addTicketNumber(load.id, number)
                numbering = null
            },
        )
    }

    // A refusal outside the record dialog -- adding a ticket number -- still
    // says something rather than closing as though it worked.
    if (!recording) {
        refusal?.let {
            AlertDialog(
                onDismissRequest = viewModel::clearRefusal,
                text = { Text(stringResource(refusalLabel(it))) },
                confirmButton = {
                    TextButton(onClick = viewModel::clearRefusal) { Text(stringResource(R.string.action_ok)) }
                },
            )
        }
    }
}

@Composable
private fun TotalsLine(total: Waste.Totals, locale: Locale) {
    Text(
        stringResource(
            R.string.waste_total,
            Formats.quantity(total.total, locale),
            stringResource(unitLabel(total.unit)),
            total.diversionRate?.let { Formats.percent(it, locale) } ?: "—",
        ),
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun LoadCard(
    load: WasteLoadEntity,
    photos: Int,
    locale: Locale,
    mayRecord: Boolean,
    onAddNumber: () -> Unit,
    onPhotograph: () -> Unit,
) {
    val asLoad = WasteRepository.asLoad(load, photos)
    val proven = asLoad?.proven == true
    val at = Instant.ofEpochMilli(load.removedAt).atZone(ZoneId.systemDefault())
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = if (proven) {
            CardDefaults.cardColors()
        } else {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        },
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                listOfNotNull(
                    Formats.quantity(load.quantity, locale) + " " +
                        (asLoad?.let { stringResource(unitLabel(it.unit)) } ?: load.unit),
                    asLoad?.let { stringResource(streamLabel(it.stream)) } ?: load.stream,
                ).joinToString(SEPARATOR),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                listOfNotNull(
                    asLoad?.let { stringResource(destinationLabel(it.destination)) },
                    load.facility,
                    Formats.dateTime(at.toLocalDate(), at.toLocalTime(), locale),
                    load.hauler,
                ).joinToString(SEPARATOR),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                when {
                    !load.ticketNumber.isNullOrBlank() -> stringResource(R.string.waste_ticket) + ": " + load.ticketNumber
                    photos > 0 -> stringResource(R.string.waste_ticket_photo) + " ✓"
                    else -> stringResource(R.string.waste_no_ticket)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (proven) FontWeight.Normal else FontWeight.Bold,
            )
            load.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (mayRecord) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (load.ticketNumber.isNullOrBlank()) {
                        TextButton(onClick = onAddNumber) { Text(stringResource(R.string.waste_add_ticket)) }
                    }
                    TextButton(onClick = onPhotograph) { Text(stringResource(R.string.waste_ticket_photo)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordLoadDialog(
    refusal: WasteRepository.Refusal?,
    onDismiss: () -> Unit,
    onRecord: (
        Waste.Stream,
        Double?,
        Waste.Measurement,
        Waste.Destination,
        String,
        String,
        String,
        String,
    ) -> Unit,
) {
    var stream by remember { mutableStateOf(Waste.Stream.CONCRETE_AND_MASONRY) }
    var quantity by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(Waste.Measurement.TONNES) }
    var destination by remember { mutableStateOf(Waste.Destination.RECYCLING) }
    var facility by remember { mutableStateOf("") }
    var hauler by remember { mutableStateOf("") }
    var ticket by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.waste_add)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.waste_stream), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Waste.Stream.entries.forEach { option ->
                        FilterChip(
                            selected = stream == option,
                            onClick = { stream = option },
                            label = { Text(stringResource(streamLabel(option))) },
                        )
                    }
                }
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = Numbers.typingDecimal(it) },
                    label = { Text(stringResource(R.string.waste_quantity)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Waste.Measurement.entries.forEach { option ->
                        FilterChip(
                            selected = unit == option,
                            onClick = { unit = option },
                            label = { Text(stringResource(unitLabel(option))) },
                        )
                    }
                }
                Text(stringResource(R.string.waste_destination), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Waste.Destination.entries.forEach { option ->
                        FilterChip(
                            selected = destination == option,
                            onClick = { destination = option },
                            label = { Text(stringResource(destinationLabel(option))) },
                        )
                    }
                }
                OutlinedTextField(
                    value = facility,
                    onValueChange = { facility = it },
                    label = { Text(stringResource(R.string.waste_facility)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = hauler,
                    onValueChange = { hauler = it },
                    label = { Text(stringResource(R.string.waste_hauler)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ticket,
                    onValueChange = { ticket = it },
                    label = { Text(stringResource(R.string.waste_ticket)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.waste_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                refusal?.let {
                    Text(stringResource(refusalLabel(it)), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onRecord(
                        stream,
                        Numbers.parseDecimal(quantity),
                        unit,
                        destination,
                        facility,
                        hauler,
                        ticket,
                        notes,
                    )
                },
            ) { Text(stringResource(R.string.waste_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun TicketNumberDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var number by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.waste_add_ticket)) },
        text = {
            OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                label = { Text(stringResource(R.string.waste_ticket)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(number) }, enabled = number.isNotBlank()) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun streamLabel(stream: Waste.Stream): Int = when (stream) {
    Waste.Stream.CONCRETE_AND_MASONRY -> R.string.waste_stream_concrete_and_masonry
    Waste.Stream.WOOD -> R.string.waste_stream_wood
    Waste.Stream.METAL -> R.string.waste_stream_metal
    Waste.Stream.PLASTIC -> R.string.waste_stream_plastic
    Waste.Stream.CARDBOARD_AND_PAPER -> R.string.waste_stream_cardboard_and_paper
    Waste.Stream.SOIL -> R.string.waste_stream_soil
    Waste.Stream.MIXED -> R.string.waste_stream_mixed
    Waste.Stream.HAZARDOUS -> R.string.waste_stream_hazardous
}

private fun unitLabel(unit: Waste.Measurement): Int = when (unit) {
    Waste.Measurement.TONNES -> R.string.waste_unit_tonnes
    Waste.Measurement.CUBIC_METRES -> R.string.waste_unit_cubic_metres
}

private fun destinationLabel(destination: Waste.Destination): Int = when (destination) {
    Waste.Destination.RECYCLING -> R.string.waste_dest_recycling
    Waste.Destination.REUSED -> R.string.waste_dest_reused
    Waste.Destination.TRANSFER_STATION -> R.string.waste_dest_transfer_station
    Waste.Destination.LANDFILL -> R.string.waste_dest_landfill
}

private fun refusalLabel(refusal: WasteRepository.Refusal): Int = when (refusal) {
    WasteRepository.Refusal.NOT_ALLOWED -> R.string.waste_not_allowed
    WasteRepository.Refusal.NO_QUANTITY -> R.string.waste_refused_quantity
    WasteRepository.Refusal.NO_DESTINATION -> R.string.waste_refused_destination
    WasteRepository.Refusal.HAZARDOUS_WITHOUT_TICKET -> R.string.waste_refused_hazardous
    WasteRepository.Refusal.UNKNOWN -> R.string.write_not_saved
}

private const val SEPARATOR = " · "
