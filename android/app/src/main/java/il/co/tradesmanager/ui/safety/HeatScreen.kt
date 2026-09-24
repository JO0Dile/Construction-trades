package il.co.tradesmanager.ui.safety

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.i18n.Numbers
import il.co.tradesmanager.core.safety.Heat
import il.co.tradesmanager.data.local.entity.HeatCheckEntity
import il.co.tradesmanager.data.repository.HeatRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.currentLocale
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The heat check.
 *
 * The reading is worked out as it is typed, so the level and what it calls
 * for are on the screen before anybody presses anything. What is recorded is
 * what was done, ticked by the person who did it -- the list of what the level
 * calls for is a prompt, not a form that fills itself in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: HeatViewModel =
        viewModel(factory = ViewModelFactory(container) { HeatViewModel(it) })
    val mayRecord by viewModel.mayRecord.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val jobNames by viewModel.jobNames.collectAsStateWithLifecycle()
    val refusal by viewModel.refusal.collectAsStateWithLifecycle()
    val locale = currentLocale()

    var temperature by remember { mutableStateOf("") }
    var humidity by remember { mutableStateOf("") }
    var inSun by remember { mutableStateOf(false) }
    var measures by remember { mutableStateOf(emptySet<Heat.Measure>()) }
    var note by remember { mutableStateOf("") }

    val snackbar = remember { SnackbarHostState() }
    val recordedText = stringResource(R.string.heat_recorded)
    LaunchedEffect(Unit) {
        viewModel.recorded.collect {
            temperature = ""
            humidity = ""
            inSun = false
            measures = emptySet()
            note = ""
            snackbar.showSnackbar(recordedText)
        }
    }
    val refusalText = refusal?.let { stringResource(sentence(it)) }
    LaunchedEffect(refusalText) {
        if (refusalText != null) {
            snackbar.showSnackbar(refusalText)
            viewModel.clearRefusal()
        }
    }

    // Worked out as it is typed. Null until both numbers are there.
    val typedTemperature = Numbers.parseDecimal(temperature)
    val typedHumidity = Numbers.parseDecimal(humidity)
    val outcome = if (typedTemperature != null && typedHumidity != null) {
        Heat.record(typedTemperature, typedHumidity, inSun)
    } else {
        null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.heat_title)) },
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
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.heat_blurb),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }

            if (mayRecord) {
                item {
                    OutlinedTextField(
                        value = temperature,
                        onValueChange = { temperature = Numbers.typingDecimal(it, allowNegative = true) },
                        label = { Text(stringResource(R.string.heat_temperature)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = humidity,
                        onValueChange = { humidity = Numbers.typingDecimal(it) },
                        label = { Text(stringResource(R.string.heat_humidity)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.heat_in_sun),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = inSun, onCheckedChange = { inSun = it })
                    }
                    Text(
                        stringResource(R.string.heat_in_sun_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                }

                when (outcome) {
                    is Heat.Outcome.Accepted -> {
                        val reading = outcome.reading
                        item {
                            LevelCard(band = reading.band, judgedOnC = reading.judgedOnC)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.heat_measures_title),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        // Every measure, with the ones this level calls for in
                        // bold. All of them, because somebody may have put up
                        // shade on a cool morning and that is worth recording.
                        val called = Heat.recommended(reading.band)
                        items(Heat.Measure.entries.toList(), key = { it.name }) { measure ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = measure in measures,
                                    onCheckedChange = { on ->
                                        measures = if (on) measures + measure else measures - measure
                                    },
                                )
                                Text(
                                    stringResource(measureLabel(measure)),
                                    fontWeight = if (measure in called) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                        item {
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                label = { Text(stringResource(R.string.heat_note_hint)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    viewModel.record(
                                        temperatureC = reading.temperatureC,
                                        humidityPercent = reading.humidityPercent,
                                        inSun = inSun,
                                        measures = measures,
                                        note = note,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.heat_record))
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                    is Heat.Outcome.Refused -> item {
                        Text(
                            stringResource(
                                when (outcome.reason) {
                                    Heat.Refusal.IMPLAUSIBLE_TEMPERATURE -> R.string.heat_refused_temperature
                                    Heat.Refusal.IMPLAUSIBLE_HUMIDITY -> R.string.heat_refused_humidity
                                },
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    null -> Unit
                }
            }

            item {
                Text(
                    stringResource(R.string.heat_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.heat_history),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
            }
            if (history.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.heat_history_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(history, key = { it.id }) { check ->
                    PastCheck(check, check.projectId?.let(jobNames::get), locale)
                }
            }
        }
    }
}

/** The level, as big as the roll call's count. It is the thing somebody acts on. */
@Composable
private fun LevelCard(band: Heat.Band, judgedOnC: Double) {
    Card(Modifier.fillMaxWidth(), colors = colorsFor(band)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(bandLabel(band)),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.heat_feels_like, judgedOnC.roundToInt()),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(bandMeaning(band)), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * One past check.
 *
 * What it calls for and was not recorded is spelled out on the record itself,
 * because that is the line somebody reading a hot August afterwards is
 * looking for, and a register that made them work it out would not be read.
 */
@Composable
private fun PastCheck(check: HeatCheckEntity, jobName: String?, locale: Locale) {
    val judged = check.heatIndexC + if (check.inSun) Heat.SUN_ALLOWANCE_C else 0.0
    // The stored level is the record. Only a value nobody recognises is
    // worked out again, and then from the stored index rather than guessed.
    val band = runCatching { Heat.Band.valueOf(check.band) }.getOrElse { Heat.bandOf(judged) }
    val done = Heat.Measure.entries.filter { it.name in check.measures }
    val missing = Heat.recommended(band).filterNot { it in done }
    val at = Instant.ofEpochMilli(check.checkedAt).atZone(ZoneId.systemDefault())

    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = colorsFor(band),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(bandLabel(band)) + SEPARATOR +
                    stringResource(R.string.heat_feels_like, judged.roundToInt()),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                listOfNotNull(
                    Formats.dateTime(at.toLocalDate(), at.toLocalTime(), locale),
                    stringResource(
                        R.string.heat_reading,
                        Formats.quantity(check.temperatureC, locale),
                        Formats.percent(check.humidityPercent / 100.0, locale),
                    ),
                    if (check.inSun) stringResource(R.string.heat_sun_badge) else null,
                    jobName,
                ).joinToString(SEPARATOR),
                style = MaterialTheme.typography.bodySmall,
            )
            if (done.isNotEmpty()) {
                Text(
                    done.map { stringResource(measureLabel(it)) }.joinToString(SEPARATOR),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (band != Heat.Band.NONE) {
                Text(
                    stringResource(R.string.heat_nothing_done),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (missing.isNotEmpty() && done.isNotEmpty()) {
                Text(
                    stringResource(
                        R.string.heat_not_done,
                        missing.map { stringResource(measureLabel(it)) }.joinToString(SEPARATOR),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            check.note?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun colorsFor(band: Heat.Band): CardColors = when (band) {
    Heat.Band.NONE -> CardDefaults.cardColors()
    Heat.Band.CAUTION -> CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
    Heat.Band.EXTREME_CAUTION -> CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    )
    Heat.Band.DANGER, Heat.Band.EXTREME_DANGER -> CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    )
}

@StringRes
private fun bandLabel(band: Heat.Band): Int = when (band) {
    Heat.Band.NONE -> R.string.heat_band_none
    Heat.Band.CAUTION -> R.string.heat_band_caution
    Heat.Band.EXTREME_CAUTION -> R.string.heat_band_extreme_caution
    Heat.Band.DANGER -> R.string.heat_band_danger
    Heat.Band.EXTREME_DANGER -> R.string.heat_band_extreme_danger
}

@StringRes
private fun bandMeaning(band: Heat.Band): Int = when (band) {
    Heat.Band.NONE -> R.string.heat_band_none_meaning
    Heat.Band.CAUTION -> R.string.heat_band_caution_meaning
    Heat.Band.EXTREME_CAUTION -> R.string.heat_band_extreme_caution_meaning
    Heat.Band.DANGER -> R.string.heat_band_danger_meaning
    Heat.Band.EXTREME_DANGER -> R.string.heat_band_extreme_danger_meaning
}

@StringRes
private fun measureLabel(measure: Heat.Measure): Int = when (measure) {
    Heat.Measure.WATER -> R.string.heat_measure_water
    Heat.Measure.SHADE -> R.string.heat_measure_shade
    Heat.Measure.SCHEDULED_BREAKS -> R.string.heat_measure_scheduled_breaks
    Heat.Measure.BUDDY_SYSTEM -> R.string.heat_measure_buddy_system
    Heat.Measure.NEW_WORKERS_EASED_IN -> R.string.heat_measure_new_workers_eased_in
    Heat.Measure.HEAVY_WORK_MOVED -> R.string.heat_measure_heavy_work_moved
    Heat.Measure.HEAVY_WORK_STOPPED -> R.string.heat_measure_heavy_work_stopped
}

@StringRes
private fun sentence(refusal: HeatRepository.Refusal): Int = when (refusal) {
    HeatRepository.Refusal.NOT_ALLOWED -> R.string.heat_not_allowed
    HeatRepository.Refusal.IMPLAUSIBLE_TEMPERATURE -> R.string.heat_refused_temperature
    HeatRepository.Refusal.IMPLAUSIBLE_HUMIDITY -> R.string.heat_refused_humidity
    HeatRepository.Refusal.UNKNOWN -> R.string.heat_refused_generic
}

private const val SEPARATOR = " · "
