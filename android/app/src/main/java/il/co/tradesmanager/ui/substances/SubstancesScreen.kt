package il.co.tradesmanager.ui.substances

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.safety.Substances
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.repository.SubstanceRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.PhotoViewer
import il.co.tradesmanager.ui.components.PickDate
import il.co.tradesmanager.ui.components.currentLanguageTag
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.components.rememberImageAdder
import il.co.tradesmanager.ui.export.ExportDocument
import il.co.tradesmanager.ui.export.Exporter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/**
 * One job's hazardous substances register.
 *
 * The ones with no safety data sheet on file first and in red, because the
 * moment somebody needs the sheet is the moment there is no time to find it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubstancesScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: SubstancesViewModel =
        viewModel(factory = ViewModelFactory(container) { SubstancesViewModel(it, projectId) })
    val mayWrite by viewModel.mayWrite.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val open by viewModel.open.collectAsStateWithLifecycle()
    val openPhotos by viewModel.openPhotos.collectAsStateWithLifecycle()
    val refusal by viewModel.refusal.collectAsStateWithLifecycle()
    val jobName by viewModel.jobName.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val languageTag = currentLanguageTag()
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current

    var adding by remember { mutableStateOf(false) }
    var pickingSheet by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<PhotoEntity?>(null) }

    val addSheet = rememberImageAdder(
        newCameraTarget = { viewModel.newSheetTarget() },
        onCaptured = { viewModel.sheetCaptured(it) },
        onPicked = { viewModel.sheetPicked(it) },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hs_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    // The register as a document: a PDF for the store's door or the inspector, and a CSV.
                    if (rows.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val result = Exporter.write(
                                    context = context,
                                    document = ExportDocument.SubstanceRegister(
                                        jobName = jobName,
                                        substances = rows.map { it.substance },
                                        today = LocalDate.now(),
                                    ),
                                    languageTag = languageTag,
                                    locale = locale,
                                    rightToLeft = layoutDirection == LayoutDirection.Rtl,
                                )
                                context.startActivity(Exporter.shareIntent(context, result))
                            },
                        ) {
                            Icon(Icons.Filled.IosShare, contentDescription = stringResource(R.string.set_export))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (mayWrite) {
                FloatingActionButton(onClick = { adding = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.hs_add))
                }
            }
        },
    ) { padding ->
        if (rows.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.hs_empty),
                hint = stringResource(R.string.hs_blurb),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding)) {
            items(rows, key = { it.substance.id }) { row ->
                val substance = row.substance
                ListItem(
                    overlineContent = {
                        Text(substance.reference + SEPARATOR + hazardsLine(row.hazards))
                    },
                    headlineContent = { Text(substance.name, maxLines = 2) },
                    supportingContent = {
                        Text(
                            stateLine(row, locale),
                            color = stateColour(row.state),
                            fontWeight = if (row.state == Substances.State.NO_SHEET) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.openSubstance(substance.id) },
                )
            }
        }
    }

    open?.let { row ->
        DetailDialog(
            row = row,
            photos = openPhotos,
            locale = locale,
            mayWrite = mayWrite,
            onDismiss = { viewModel.openSubstance(null) },
            onNewSheet = { pickingSheet = true },
            onRemove = { removing = true },
            onAddSheetPhoto = addSheet,
            onViewPhoto = { viewing = it },
        )
    }

    if (adding) {
        AddDialog(
            onDismiss = { adding = false },
            onAdd = { arrival ->
                adding = false
                viewModel.add(arrival)
            },
        )
    }

    val sheetRow = open
    if (pickingSheet && sheetRow != null) {
        PickDate(
            initial = sheetRow.sheetOn?.let { pickerMillis(it) },
            title = stringResource(R.string.hs_sheet_date_question),
            onDismiss = { pickingSheet = false },
            onPick = { millis ->
                pickingSheet = false
                if (millis != null) viewModel.newSheet(sheetRow.substance.id, millis)
            },
        )
    }

    val removingRow = open
    if (removing && removingRow != null) {
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text(stringResource(R.string.hs_remove_question, removingRow.substance.name)) },
            text = { Text(stringResource(R.string.hs_remove_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        removing = false
                        viewModel.remove(removingRow.substance.id)
                    },
                ) { Text(stringResource(R.string.hs_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { removing = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    viewing?.let { photo ->
        PhotoViewer(
            photo = photo,
            // "Set as plan" belongs to a job's own plan, not to one data sheet.
            isPlan = true,
            onSetAsPlan = {},
            onDelete = {
                viewModel.deletePhoto(photo)
                viewing = null
            }.takeIf { mayWrite },
            onDismiss = { viewing = null },
        )
    }

    refusal?.let { reason ->
        AlertDialog(
            onDismissRequest = viewModel::clearRefusal,
            text = { Text(stringResource(substanceRefusalText(reason))) },
            confirmButton = {
                TextButton(onClick = viewModel::clearRefusal) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

@Composable
private fun hazardsLine(hazards: Set<Substances.Hazard>): String =
    hazards.sortedBy { it.ordinal }.map { stringResource(hazardLabel(it)) }.joinToString(", ")

@Composable
private fun stateColour(state: Substances.State): Color = when (state) {
    Substances.State.NO_SHEET, Substances.State.SHEET_OLD -> MaterialTheme.colorScheme.error
    Substances.State.ON_SITE, Substances.State.REMOVED -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun stateLine(row: SubstancesViewModel.Row, locale: Locale): String {
    val substance = row.substance
    return when (row.state) {
        Substances.State.NO_SHEET -> stringResource(R.string.hs_no_sheet)
        Substances.State.SHEET_OLD -> stringResource(R.string.hs_sheet_old, dayText(row.sheetOn ?: LocalDate.now(), locale))
        Substances.State.ON_SITE -> stringResource(R.string.hs_kept_at, substance.keptWhere)
        Substances.State.REMOVED -> stringResource(R.string.hs_removed_on, dateOf(substance.removedAt ?: substance.addedAt, locale))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailDialog(
    row: SubstancesViewModel.Row,
    photos: List<PhotoEntity>,
    locale: Locale,
    mayWrite: Boolean,
    onDismiss: () -> Unit,
    onNewSheet: () -> Unit,
    onRemove: () -> Unit,
    onAddSheetPhoto: () -> Unit,
    onViewPhoto: (PhotoEntity) -> Unit,
) {
    val substance = row.substance
    val onSite = substance.removedAt == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(substance.reference + SEPARATOR + substance.name) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(hazardsLine(row.hazards), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                Text(
                    listOfNotNull(
                        stringResource(R.string.hs_kept_at, substance.keptWhere),
                        substance.quantity,
                        substance.supplier,
                    ).joinToString(SEPARATOR),
                    style = MaterialTheme.typography.bodyMedium,
                )
                substance.precautions?.let {
                    Text(stringResource(R.string.hs_precautions), style = MaterialTheme.typography.labelLarge)
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                substance.firstAid?.let {
                    Text(stringResource(R.string.hs_first_aid), style = MaterialTheme.typography.labelLarge)
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                Text(stringResource(R.string.hs_sheet), style = MaterialTheme.typography.labelLarge)
                Text(
                    row.sheetOn?.let { stringResource(R.string.hs_sheet_dated, dayText(it, locale)) } ?: stringResource(R.string.hs_no_sheet),
                    color = stateColour(row.state),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (row.state == Substances.State.SHEET_OLD) {
                    Text(stringResource(R.string.hs_sheet_old_hint), style = MaterialTheme.typography.bodySmall)
                }
                if (photos.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        photos.forEach { photo ->
                            AsyncImage(
                                model = photo.uri,
                                contentDescription = stringResource(R.string.hs_sheet),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(72.dp).clickable { onViewPhoto(photo) },
                            )
                        }
                    }
                }
                Text(
                    listOfNotNull(
                        stringResource(R.string.hs_added_by, dateOf(substance.addedAt, locale), substance.addedByName),
                        substance.removedAt?.let { stringResource(R.string.hs_removed_on, dateOf(it, locale)) },
                        substance.removedByName,
                    ).joinToString(SEPARATOR),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (mayWrite && onSite) {
                    OutlinedButton(onClick = onNewSheet, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.hs_record_sheet))
                    }
                    OutlinedButton(onClick = onAddSheetPhoto, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.hs_add_sheet_photo))
                    }
                    OutlinedButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.hs_remove))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

/**
 * A substance coming onto the site. The name, at least one hazard and where
 * it is kept are asked for; the rest is what the data sheet says, and the
 * sheet's own date if there is one to hand.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddDialog(
    onDismiss: () -> Unit,
    onAdd: (SubstanceRepository.Arrival) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }
    var hazards by remember { mutableStateOf(emptySet<Substances.Hazard>()) }
    var keptWhere by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var precautions by remember { mutableStateOf("") }
    var firstAid by remember { mutableStateOf("") }
    var sheetOn by remember { mutableStateOf<LocalDate?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    val locale = currentLocale()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hs_add)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.hs_name)) },
                    supportingText = { Text(stringResource(R.string.hs_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.hs_hazards), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Substances.Hazard.entries.forEach { option ->
                        FilterChip(
                            selected = option in hazards,
                            onClick = { hazards = if (option in hazards) hazards - option else hazards + option },
                            label = { Text(stringResource(hazardLabel(option))) },
                        )
                    }
                }
                OutlinedTextField(
                    value = keptWhere,
                    onValueChange = { keptWhere = it },
                    label = { Text(stringResource(R.string.hs_kept_where)) },
                    supportingText = { Text(stringResource(R.string.hs_kept_where_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text(stringResource(R.string.hs_quantity)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = supplier,
                    onValueChange = { supplier = it },
                    label = { Text(stringResource(R.string.hs_supplier)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = precautions,
                    onValueChange = { precautions = it },
                    label = { Text(stringResource(R.string.hs_precautions)) },
                    supportingText = { Text(stringResource(R.string.hs_precautions_hint)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = firstAid,
                    onValueChange = { firstAid = it },
                    label = { Text(stringResource(R.string.hs_first_aid)) },
                    supportingText = { Text(stringResource(R.string.hs_first_aid_hint)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.hs_sheet), style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(sheetOn?.let { stringResource(R.string.hs_sheet_dated, dayText(it, locale)) } ?: stringResource(R.string.hs_sheet_pick))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && hazards.isNotEmpty() && keptWhere.isNotBlank(),
                onClick = {
                    onAdd(
                        SubstanceRepository.Arrival(
                            name = name,
                            supplier = supplier,
                            hazards = hazards,
                            keptWhere = keptWhere,
                            quantity = quantity,
                            precautions = precautions,
                            firstAid = firstAid,
                            sheetOn = sheetOn,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.hs_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    if (pickingDate) {
        PickDate(
            initial = sheetOn?.let { pickerMillis(it) },
            title = stringResource(R.string.hs_sheet_date_question),
            onDismiss = { pickingDate = false },
            onPick = { millis ->
                pickingDate = false
                sheetOn = millis?.let { SubstancesViewModel.dayOf(it) }
            },
        )
    }
}

/** The calendar works in midnight UTC, whatever the phone's zone. */
private fun pickerMillis(day: LocalDate): Long = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun dayText(day: LocalDate, locale: Locale): String = Formats.date(day, locale)

private fun dateOf(at: Long, locale: Locale): String =
    Formats.date(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate(), locale)

@StringRes
internal fun hazardLabel(hazard: Substances.Hazard): Int = when (hazard) {
    Substances.Hazard.EXPLOSIVE -> R.string.hs_hazard_explosive
    Substances.Hazard.FLAMMABLE -> R.string.hs_hazard_flammable
    Substances.Hazard.OXIDISING -> R.string.hs_hazard_oxidising
    Substances.Hazard.GAS_UNDER_PRESSURE -> R.string.hs_hazard_gas
    Substances.Hazard.CORROSIVE -> R.string.hs_hazard_corrosive
    Substances.Hazard.TOXIC -> R.string.hs_hazard_toxic
    Substances.Hazard.HEALTH_HAZARD -> R.string.hs_hazard_health
    Substances.Hazard.HARMFUL -> R.string.hs_hazard_harmful
    Substances.Hazard.ENVIRONMENT -> R.string.hs_hazard_environment
}

@StringRes
private fun substanceRefusalText(refusal: SubstanceRepository.Refusal): Int = when (refusal) {
    SubstanceRepository.Refusal.NOT_ALLOWED -> R.string.hs_refused_not_allowed
    SubstanceRepository.Refusal.BLANK_NAME -> R.string.hs_refused_name
    SubstanceRepository.Refusal.NO_HAZARD -> R.string.hs_refused_hazard
    SubstanceRepository.Refusal.BLANK_KEPT_WHERE -> R.string.hs_refused_kept_where
    SubstanceRepository.Refusal.SHEET_IN_FUTURE -> R.string.hs_refused_sheet_future
    SubstanceRepository.Refusal.SHEET_NOT_NEWER -> R.string.hs_refused_sheet_not_newer
    SubstanceRepository.Refusal.ALREADY_REMOVED -> R.string.hs_refused_removed
    SubstanceRepository.Refusal.UNKNOWN -> R.string.write_not_saved
}

private const val SEPARATOR = " · "
