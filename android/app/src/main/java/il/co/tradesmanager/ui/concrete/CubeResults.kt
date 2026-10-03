package il.co.tradesmanager.ui.concrete

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import il.co.tradesmanager.R
import il.co.tradesmanager.core.evidence.CubeTests
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.i18n.Numbers
import il.co.tradesmanager.data.local.entity.ConcreteCubeSetEntity
import il.co.tradesmanager.data.repository.ConcreteRepository
import java.util.Locale

/**
 * One set of cube results, and what they say against the mix.
 *
 * The verdict is the first line, in bold when the engineer has to see it,
 * because that is the only thing somebody glancing at the pour wants to know.
 */
@Composable
internal fun CubeSetRow(set: ConcreteCubeSetEntity, mixDesign: String?, locale: Locale) {
    val result = CubeTests.judgeStored(set.ageDays, set.strengthsMpa, mixDesign)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            listOfNotNull(
                pluralStringResource(R.plurals.cube_age_days, set.ageDays, set.ageDays),
                set.laboratory,
                set.reportNumber,
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (result != null) {
            Text(
                stringResource(verdictLabel(result.verdict)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (result.needsEngineer) FontWeight.Bold else FontWeight.Normal,
                color = if (result.needsEngineer) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                listOfNotNull(
                    stringResource(
                        R.string.cube_mean,
                        Formats.quantity(result.meanMpa, locale),
                        Formats.quantity(result.lowestMpa, locale),
                    ),
                    result.specifiedMpa?.let { stringResource(R.string.cube_specified, Formats.quantity(it, locale)) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Typing a lab report in.
 *
 * Three cube fields because three is a set; any left blank are ignored, and
 * more than three is a second result. The age is picked, because it is
 * almost always seven or twenty-eight, with a field for the day it is not.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CubeDialog(
    onDismiss: () -> Unit,
    onRecord: (ageDays: Int, laboratory: String?, reportNumber: String?, strengths: List<Double>) -> Unit,
) {
    var age by remember { mutableStateOf<Int?>(CubeTests.JUDGED_AT_DAYS) }
    var otherAge by remember { mutableStateOf("") }
    var laboratory by remember { mutableStateOf("") }
    var report by remember { mutableStateOf("") }
    var cubes by remember { mutableStateOf(List(CUBE_FIELDS) { "" }) }

    val ageDays = age ?: Numbers.parseWhole(otherAge)?.coerceIn(0L, 10_000L)?.toInt()
    val strengths = cubes.mapNotNull { Numbers.parseDecimal(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cube_add)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.cube_age), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, CubeTests.JUDGED_AT_DAYS).forEach { days ->
                        FilterChip(
                            selected = age == days,
                            onClick = { age = days },
                            label = { Text(pluralStringResource(R.plurals.cube_age_days, days, days)) },
                        )
                    }
                    FilterChip(
                        selected = age == null,
                        onClick = { age = null },
                        label = { Text(stringResource(R.string.cube_other_age)) },
                    )
                }
                if (age == null) {
                    OutlinedTextField(
                        value = otherAge,
                        onValueChange = { otherAge = Numbers.typingDecimal(it).filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.cube_other_age)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = laboratory,
                    onValueChange = { laboratory = it },
                    label = { Text(stringResource(R.string.cube_lab)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = report,
                    onValueChange = { report = it },
                    label = { Text(stringResource(R.string.cube_report)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                cubes.forEachIndexed { index, value ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { typed ->
                            cubes = cubes.toMutableList().also { it[index] = Numbers.typingDecimal(typed) }
                        },
                        label = { Text(stringResource(R.string.cube_strength, index + 1)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    stringResource(R.string.cube_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = ageDays != null && strengths.isNotEmpty(),
                onClick = {
                    onRecord(ageDays ?: 0, laboratory, report, strengths)
                },
            ) { Text(stringResource(R.string.cube_record)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@StringRes
internal fun verdictLabel(verdict: CubeTests.Verdict): Int = when (verdict) {
    CubeTests.Verdict.EARLY -> R.string.cube_verdict_early
    CubeTests.Verdict.NO_SPECIFICATION -> R.string.cube_verdict_no_spec
    CubeTests.Verdict.MEAN_BELOW -> R.string.cube_verdict_mean_below
    CubeTests.Verdict.CUBE_BELOW -> R.string.cube_verdict_cube_below
    CubeTests.Verdict.MEETS -> R.string.cube_verdict_meets
}

@StringRes
internal fun cubeRefusalLabel(refusal: ConcreteRepository.CubeRefusal): Int = when (refusal) {
    ConcreteRepository.CubeRefusal.NOT_ALLOWED -> R.string.cube_refused_not_allowed
    ConcreteRepository.CubeRefusal.NO_CUBES -> R.string.cube_refused_no_cubes
    ConcreteRepository.CubeRefusal.IMPLAUSIBLE_STRENGTH -> R.string.cube_refused_strength
    ConcreteRepository.CubeRefusal.BAD_AGE -> R.string.cube_refused_age
    ConcreteRepository.CubeRefusal.UNKNOWN -> R.string.write_not_saved
}

private const val CUBE_FIELDS = 3
