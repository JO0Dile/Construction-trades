package il.co.tradesmanager.ui.plant

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.safety.Examinations
import il.co.tradesmanager.data.local.entity.EquipmentEntity
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.local.entity.PlantExaminationEntity
import il.co.tradesmanager.data.repository.PlantExaminationRepository
import java.time.LocalDate
import java.util.Locale

/** How a machine's certificate stands, for its row: NONE when none is recorded, which is silence, not an alarm. */
fun examinationState(latest: PlantExaminationEntity?, today: LocalDate = LocalDate.now()): Examinations.State =
    Examinations.state(
        latestResult = latest?.let { Examinations.resultOf(it.result) },
        nextDueOn = latest?.nextDueDay?.let(LocalDate::ofEpochDay),
        today = today,
    )

/** One line under the machine's name; nothing at all when no certificate has ever been recorded. */
@Composable
fun ExaminationLine(latest: PlantExaminationEntity?, locale: Locale, amber: Color) {
    val state = examinationState(latest)
    if (latest == null || state == Examinations.State.NONE) return
    val examined = Formats.date(LocalDate.ofEpochDay(latest.examinedOnDay), locale)
    val due = latest.nextDueDay?.let { Formats.date(LocalDate.ofEpochDay(it), locale) }.orEmpty()
    val (text, colour) = when (state) {
        Examinations.State.CURRENT -> stringResource(R.string.pe_current, due) to MaterialTheme.colorScheme.onSurfaceVariant
        Examinations.State.NO_NEXT_DATE -> stringResource(R.string.pe_no_next, examined) to MaterialTheme.colorScheme.onSurfaceVariant
        Examinations.State.DUE_SOON -> stringResource(R.string.pe_due_soon, due) to amber
        Examinations.State.OVERDUE -> stringResource(R.string.pe_overdue, due) to MaterialTheme.colorScheme.error
        Examinations.State.FAILED -> stringResource(R.string.pe_failed_on, examined) to MaterialTheme.colorScheme.error
        Examinations.State.NONE -> return
    }
    Text(
        text = text,
        color = colour,
        fontWeight = if (Examinations.needsAttention(state)) FontWeight.Bold else FontWeight.Normal,
    )
}

/** A machine's certificates: the newest with its photographs, then the ones before it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExaminationsDialog(
    machine: EquipmentEntity,
    examinations: List<PlantExaminationEntity>,
    photos: List<PhotoEntity>,
    locale: Locale,
    mayRecord: Boolean,
    onRecord: () -> Unit,
    onAddPhoto: () -> Unit,
    onViewPhoto: (PhotoEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pe_title) + " · " + machine.name) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (examinations.isEmpty()) {
                    Text(stringResource(R.string.pe_none), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.pe_blurb), style = MaterialTheme.typography.bodySmall)
                }
                examinations.forEachIndexed { index, exam ->
                    val result = Examinations.resultOf(exam.result)
                    Text(
                        listOfNotNull(
                            Formats.date(LocalDate.ofEpochDay(exam.examinedOnDay), locale),
                            result?.let { stringResource(resultLabel(it)) },
                            exam.examinerName,
                            exam.certificateNumber,
                        ).joinToString(" · "),
                        style = if (index == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                        color = if (result == Examinations.Result.FAILED) MaterialTheme.colorScheme.error else Color.Unspecified,
                    )
                    exam.nextDueDay?.let {
                        Text(
                            stringResource(R.string.pe_next_due_on, Formats.date(LocalDate.ofEpochDay(it), locale)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    exam.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (index == 0) {
                        Text(stringResource(R.string.pe_certificate), style = MaterialTheme.typography.labelLarge)
                        if (photos.isEmpty()) {
                            Text(stringResource(R.string.pe_no_photo), style = MaterialTheme.typography.bodySmall)
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                photos.forEach { photo ->
                                    AsyncImage(
                                        model = photo.uri,
                                        contentDescription = stringResource(R.string.pe_certificate),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(72.dp).clickable { onViewPhoto(photo) },
                                    )
                                }
                            }
                        }
                        if (mayRecord) {
                            OutlinedButton(onClick = onAddPhoto, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.pe_add_photo))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (mayRecord) {
                TextButton(onClick = onRecord) { Text(stringResource(R.string.pe_record)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        },
        dismissButton = {
            if (mayRecord) TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

/**
 * Typing a certificate in. The next date is offered in months from the day
 * examined, which is how certificates are written; which interval applies is
 * the examiner's to say, so none is chosen for them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordExaminationDialog(
    onDismiss: () -> Unit,
    onRecord: (
        examinedDaysAgo: Int,
        examiner: String,
        certificate: String,
        result: Examinations.Result,
        nextDueInMonths: Int?,
        notes: String,
    ) -> Unit,
) {
    var daysAgo by remember { mutableStateOf(0) }
    var examiner by remember { mutableStateOf("") }
    var certificate by remember { mutableStateOf("") }
    var result by remember { mutableStateOf(Examinations.Result.PASSED) }
    var months by remember { mutableStateOf<Int?>(null) }
    var notes by remember { mutableStateOf("") }
    val failed = result == Examinations.Result.FAILED

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pe_record)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.pe_examined), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EXAMINED_OFFERS.forEach { (days, label) ->
                        FilterChip(selected = daysAgo == days, onClick = { daysAgo = days }, label = { Text(stringResource(label)) })
                    }
                }
                OutlinedTextField(
                    value = examiner,
                    onValueChange = { examiner = it },
                    label = { Text(stringResource(R.string.pe_examiner)) },
                    supportingText = { Text(stringResource(R.string.pe_examiner_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = certificate,
                    onValueChange = { certificate = it },
                    label = { Text(stringResource(R.string.pe_certificate_number)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Examinations.Result.entries.forEach { option ->
                        FilterChip(
                            selected = result == option,
                            onClick = { result = option },
                            label = { Text(stringResource(resultLabel(option))) },
                        )
                    }
                }
                if (!failed) {
                    Text(stringResource(R.string.pe_next_due), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = months == null,
                            onClick = { months = null },
                            label = { Text(stringResource(R.string.pe_no_date)) },
                        )
                        NEXT_DUE_OFFERS.forEach { (count, label) ->
                            FilterChip(selected = months == count, onClick = { months = count }, label = { Text(stringResource(label)) })
                        }
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(if (failed) R.string.pe_why_failed else R.string.pe_conditions)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = examiner.isNotBlank() && (!failed || notes.isNotBlank()),
                onClick = { onRecord(daysAgo, examiner, certificate, result, if (failed) null else months, notes) },
            ) { Text(stringResource(R.string.pe_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@StringRes
private fun resultLabel(result: Examinations.Result): Int = when (result) {
    Examinations.Result.PASSED -> R.string.pe_result_passed
    Examinations.Result.FAILED -> R.string.pe_result_failed
}

@StringRes
fun examinationRefusalText(refusal: PlantExaminationRepository.Refusal): Int = when (refusal) {
    PlantExaminationRepository.Refusal.NOT_ALLOWED -> R.string.pe_refused_not_allowed
    PlantExaminationRepository.Refusal.NO_EXAMINER -> R.string.pe_refused_examiner
    PlantExaminationRepository.Refusal.EXAMINED_IN_FUTURE -> R.string.pe_refused_future
    PlantExaminationRepository.Refusal.DUE_BEFORE_EXAMINED -> R.string.pe_refused_due
    PlantExaminationRepository.Refusal.FAILED_WITHOUT_REASON -> R.string.pe_refused_reason
    PlantExaminationRepository.Refusal.UNKNOWN -> R.string.write_not_saved
}

private val EXAMINED_OFFERS = listOf(
    0 to R.string.de_today,
    1 to R.string.de_yesterday,
    2 to R.string.de_2_days_ago,
    7 to R.string.de_a_week_ago,
)

private val NEXT_DUE_OFFERS = listOf(
    3 to R.string.pe_in_3_months,
    6 to R.string.pe_in_6_months,
    12 to R.string.pe_in_12_months,
)
