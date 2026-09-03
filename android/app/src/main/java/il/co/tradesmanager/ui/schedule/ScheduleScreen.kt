package il.co.tradesmanager.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.time.TimeOfDay
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.rememberPermissionRequest
import il.co.tradesmanager.ui.components.currentLocale
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(container: AppContainer) {
    val viewModel: ScheduleViewModel = viewModel(
        factory = ViewModelFactory(container) { ScheduleViewModel(it) },
    )
    val date by viewModel.date.collectAsStateWithLifecycle()
    val blocks by viewModel.blocks.collectAsStateWithLifecycle()
    val openEntry by viewModel.openTimeEntry.collectAsStateWithLifecycle()
    val locale = currentLocale()
    var showAdd by remember { mutableStateOf(false) }

    // Location is asked for at the moment of a check-in, with a reason, and a
    // refusal still records the check-in — just without the GPS stamp.
    val requestLocation = rememberPermissionRequest(
        permission = android.Manifest.permission.ACCESS_COARSE_LOCATION,
        titleRes = R.string.perm_location_title,
        bodyRes = R.string.perm_location_body,
        onResult = { viewModel.toggleCheckIn(null, null) },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Formats.date(date, locale)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.shiftDay(-1) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.sch_previous_day),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.shiftDay(1) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.sch_next_day),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.sch_new_block))
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { if (openEntry == null) requestLocation() else viewModel.toggleCheckIn(null, null) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            stringResource(
                                if (openEntry == null) R.string.sch_check_in else R.string.sch_check_out,
                            ),
                        )
                    }
                    if (blocks.isNotEmpty()) {
                        TextButton(onClick = { viewModel.copyToTomorrow() }) {
                            Text(stringResource(R.string.sch_next_day))
                        }
                    }
                }
            }

            if (blocks.isEmpty()) {
                item { EmptyState(stringResource(R.string.sch_empty)) }
            } else {
                items(blocks, key = { it.id }) { block ->
                    ListItem(
                        headlineContent = { Text(block.title) },
                        supportingContent = {
                            Text(
                                Formats.time(LocalTime.ofSecondOfDay(block.startMinute * 60L), locale) +
                                    " – " +
                                    Formats.time(LocalTime.ofSecondOfDay(block.endMinute * 60L), locale),
                            )
                        },
                        leadingContent = {
                            Checkbox(
                                checked = block.isDone,
                                onCheckedChange = { viewModel.setDone(block.id, it) },
                            )
                        },
                        trailingContent = {
                            TextButton(onClick = { viewModel.delete(block.id) }) {
                                Text(stringResource(R.string.action_delete))
                            }
                        },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddBlockDialog(
            onDismiss = { showAdd = false },
            onConfirm = { title, start, end ->
                viewModel.addBlock(title, start, end)
                showAdd = false
            },
        )
    }
}

@Composable
private fun AddBlockDialog(onDismiss: () -> Unit, onConfirm: (String, Int, Int) -> Unit) {
    var title by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("07:00") }
    var end by remember { mutableStateOf("12:00") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sch_new_block)) },
        text = {
            androidx.compose.foundation.layout.Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.sch_task)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = start,
                        onValueChange = { start = it },
                        label = { Text(stringResource(R.string.sch_start)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    OutlinedTextField(
                        value = end,
                        onValueChange = { end = it },
                        label = { Text(stringResource(R.string.sch_end)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title, TimeOfDay.parse(start, 7 * 60), TimeOfDay.parse(end, 12 * 60)) },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

