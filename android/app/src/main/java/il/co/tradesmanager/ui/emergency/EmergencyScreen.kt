package il.co.tradesmanager.ui.emergency

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.safety.Emergency
import il.co.tradesmanager.core.safety.EmergencySheet
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.repository.EmergencySheetRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.currentLanguageTag
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.export.ExportDocument
import il.co.tradesmanager.ui.export.Exporter
import java.time.Instant
import java.time.ZoneId

/**
 * One job's emergency information, laid out for somebody in a hurry: the
 * national numbers first, as buttons, then the address to give, then the
 * hospital, the assembly point, the first aiders, the number on site and
 * the shut-offs. What is still missing is said in red rather than left as
 * a gap nobody notices until it is needed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: EmergencyViewModel =
        viewModel(factory = ViewModelFactory(container) { EmergencyViewModel(it, projectId) })
    val mayWrite by viewModel.mayWrite.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val missing by viewModel.missing.collectAsStateWithLifecycle()
    val job by viewModel.job.collectAsStateWithLifecycle()
    val refusal by viewModel.refusal.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val languageTag = currentLanguageTag()
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current

    var editing by remember { mutableStateOf(false) }
    var noDialer by remember { mutableStateOf(false) }
    val dial: (String) -> Unit = { number -> if (!dialNumber(context, number)) noDialer = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.es_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    // The sheet for the site office wall and the gate, gaps and all.
                    IconButton(
                        onClick = {
                            val result = Exporter.write(
                                context = context,
                                document = ExportDocument.EmergencyInformation(
                                    jobName = job?.name.orEmpty(),
                                    address = addressOf(job),
                                    sheet = sheet,
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
                },
            )
        },
        floatingActionButton = {
            if (mayWrite) {
                FloatingActionButton(onClick = { editing = true }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.es_edit))
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.muster_call_title), style = MaterialTheme.typography.titleMedium)
            Emergency.ORDER.forEach { number ->
                OutlinedButton(onClick = { dial(number) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Call, contentDescription = null)
                    Text(stringResource(nationalNumberLabel(number)), modifier = Modifier.padding(start = 8.dp))
                }
            }
            Text(
                stringResource(if (noDialer) R.string.muster_call_no_dialer else R.string.muster_call_hint),
                style = MaterialTheme.typography.bodySmall,
                color = if (noDialer) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Item(R.string.es_address, addressOf(job).ifBlank { null }, emptyHint = R.string.es_no_address)

            if (missing.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.es_missing, missing.map { stringResource(essentialLabel(it)) }.joinToString(", ")),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(if (mayWrite) R.string.es_missing_hint_writer else R.string.es_missing_hint_reader),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            HorizontalDivider()
            Item(R.string.es_hospital, listOfNotNull(sheet?.hospitalName, sheet?.hospitalAddress).joinToString("\n").ifBlank { null })
            sheet?.hospitalPhone?.let { phone -> PhoneButton(phone, onDial = dial) }
            Item(R.string.es_assembly_point, sheet?.assemblyPoint)
            Item(R.string.es_first_aiders, sheet?.firstAiders)
            Item(R.string.es_site_contact, sheet?.siteContactName)
            sheet?.siteContactPhone?.let { phone -> PhoneButton(phone, onDial = dial) }
            HorizontalDivider()
            Item(R.string.es_electricity, sheet?.electricityShutOff)
            Item(R.string.es_water, sheet?.waterShutOff)
            Item(R.string.es_gas, sheet?.gasShutOff)
            sheet?.notes?.let { Item(R.string.es_notes, it) }
            sheet?.let {
                Text(
                    stringResource(R.string.es_updated, dateOf(it.updatedAt, locale), it.updatedByName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (editing) {
        EditDialog(
            initial = EmergencySheetRepository.sheetOf(sheet) ?: EmergencySheet.Sheet(),
            onDismiss = { editing = false },
            onSave = { edited ->
                editing = false
                viewModel.save(edited)
            },
        )
    }

    refusal?.let { reason ->
        AlertDialog(
            onDismissRequest = viewModel::clearRefusal,
            text = { Text(stringResource(refusalText(reason))) },
            confirmButton = {
                TextButton(onClick = viewModel::clearRefusal) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

/** One line of the sheet: its heading, and what is recorded or that nothing is. */
@Composable
private fun Item(@StringRes heading: Int, value: String?, @StringRes emptyHint: Int = R.string.es_not_recorded) {
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(heading), style = MaterialTheme.typography.labelLarge)
        if (value.isNullOrBlank()) {
            Text(stringResource(emptyHint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun PhoneButton(phone: String, onDial: (String) -> Unit) {
    OutlinedButton(onClick = { onDial(EmergencySheet.dialString(phone)) }, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Call, contentDescription = null)
            Text(phone, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun EditDialog(
    initial: EmergencySheet.Sheet,
    onDismiss: () -> Unit,
    onSave: (EmergencySheet.Sheet) -> Unit,
) {
    var edited by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.es_edit)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Field(R.string.es_hospital_name, edited.hospitalName, hint = R.string.es_hospital_hint) { edited = edited.copy(hospitalName = it) }
                Field(R.string.es_hospital_address, edited.hospitalAddress) { edited = edited.copy(hospitalAddress = it) }
                Field(R.string.es_hospital_phone, edited.hospitalPhone) { edited = edited.copy(hospitalPhone = it) }
                Field(R.string.es_assembly_point, edited.assemblyPoint, hint = R.string.es_assembly_hint) { edited = edited.copy(assemblyPoint = it) }
                Field(R.string.es_first_aiders, edited.firstAiders, hint = R.string.es_first_aiders_hint, singleLine = false) {
                    edited = edited.copy(firstAiders = it)
                }
                Field(R.string.es_site_contact_name, edited.siteContactName) { edited = edited.copy(siteContactName = it) }
                Field(R.string.es_site_contact_phone, edited.siteContactPhone) { edited = edited.copy(siteContactPhone = it) }
                Field(R.string.es_electricity, edited.electricityShutOff) { edited = edited.copy(electricityShutOff = it) }
                Field(R.string.es_water, edited.waterShutOff) { edited = edited.copy(waterShutOff = it) }
                Field(R.string.es_gas, edited.gasShutOff) { edited = edited.copy(gasShutOff = it) }
                Field(R.string.es_notes, edited.notes, singleLine = false) { edited = edited.copy(notes = it) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(edited) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun Field(
    @StringRes label: Int,
    value: String,
    @StringRes hint: Int? = null,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    if (hint == null) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(stringResource(label)) },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 2,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(stringResource(label)) },
            supportingText = { Text(stringResource(hint)) },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 2,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * ACTION_DIAL: the dialler opens with the number in and the person presses
 * call. False when there is nothing to open it with, a tablet with no phone.
 */
private fun dialNumber(context: Context, number: String): Boolean =
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }.isSuccess

/** The job's address as somebody would read it down the phone. */
internal fun addressOf(job: ProjectEntity?): String =
    listOfNotNull(job?.street, job?.city, job?.postalCode).filter { it.isNotBlank() }.joinToString(", ")

private fun dateOf(at: Long, locale: java.util.Locale): String =
    Formats.date(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate(), locale)

@StringRes
internal fun nationalNumberLabel(number: String): Int = when (number) {
    Emergency.AMBULANCE -> R.string.muster_call_ambulance
    Emergency.FIRE_AND_RESCUE -> R.string.muster_call_fire
    else -> R.string.muster_call_police
}

@StringRes
internal fun essentialLabel(essential: EmergencySheet.Essential): Int = when (essential) {
    EmergencySheet.Essential.HOSPITAL -> R.string.es_hospital
    EmergencySheet.Essential.ASSEMBLY_POINT -> R.string.es_assembly_point
    EmergencySheet.Essential.FIRST_AIDERS -> R.string.es_first_aiders
    EmergencySheet.Essential.SITE_CONTACT -> R.string.es_site_contact
}

@StringRes
private fun refusalText(refusal: EmergencySheetRepository.Refusal): Int = when (refusal) {
    EmergencySheetRepository.Refusal.NOT_ALLOWED -> R.string.es_refused_not_allowed
    EmergencySheetRepository.Refusal.NOT_A_PHONE_NUMBER -> R.string.es_refused_phone
    EmergencySheetRepository.Refusal.UNKNOWN -> R.string.write_not_saved
}
