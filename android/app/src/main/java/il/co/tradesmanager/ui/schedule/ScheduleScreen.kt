package il.co.tradesmanager.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import il.co.tradesmanager.data.local.entity.AccountEntity
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
    val crew by viewModel.crew.collectAsStateWithLifecycle()
    val locale = currentLocale()
    var showAdd by remember { mutableStateOf(false) }
    var assigning by remember { mutableStateOf<String?>(null) }

    // There is no location prompt here any more, and there was never a GPS
    // stamp. The prompt passed null coordinates to the check-in whatever the
    // answer was, so it asked a worker for their position every time they
    // started a shift and did nothing with the reply — the comment that used
    // to sit here claimed a refusal cost you the stamp, when accepting cost
    // you it too. Nothing in the app reads a location.
    //
    // The columns on the time entry stay, so a real GPS stamp can be added
    // later with the permission it needs. Asking first and using it never is
    // the wrong order.

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
                        onClick = { viewModel.toggleCheckIn(null, null) },
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
                item {
                    EmptyState(
                        message = stringResource(R.string.sch_empty),
                        icon = Icons.Filled.CalendarMonth,
                        actionLabel = stringResource(R.string.sch_new_block),
                        onAction = { showAdd = true },
                    )
                }
            } else {
                items(blocks, key = { it.id }) { block ->
                    val assignee = crew.firstOrNull { it.id == block.assigneeId }
                    ListItem(
                        headlineContent = { Text(block.title) },
                        supportingContent = {
                            val hours = Formats.time(LocalTime.ofSecondOfDay(block.startMinute * 60L), locale) +
                                " – " +
                                Formats.time(LocalTime.ofSecondOfDay(block.endMinute * 60L), locale)
                            // Named on the row rather than a tap away. The
                            // question at seven in the morning is who is doing
                            // it, and an answer you have to open something to
                            // read is not on the list at all.
                            Text(
                                if (crew.isEmpty()) {
                                    hours
                                } else {
                                    hours + " · " + (
                                        assignee?.displayName
                                            ?: stringResource(R.string.sch_nobody)
                                        )
                                },
                            )
                        },
                        leadingContent = {
                            Checkbox(
                                checked = block.isDone,
                                onCheckedChange = { viewModel.setDone(block.id, it) },
                            )
                        },
                        trailingContent = {
                            Row {
                                // Offered only where there is somebody to hand
                                // it to. A sole trader has no crew, and a
                                // button that opens an empty list is a button
                                // that teaches people not to press buttons.
                                if (crew.isNotEmpty()) {
                                    TextButton(onClick = { assigning = block.id }) {
                                        Text(stringResource(R.string.sch_assign))
                                    }
                                }
                                TextButton(onClick = { viewModel.delete(block.id) }) {
                                    Text(stringResource(R.string.action_delete))
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    assigning?.let { blockId ->
        AssignDialog(
            crew = crew,
            current = blocks.firstOrNull { it.id == blockId }?.assigneeId,
            onDismiss = { assigning = null },
            onPick = { accountId ->
                viewModel.setAssignee(blockId, accountId)
                assigning = null
            },
        )
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

/**
 * Who is doing this block.
 *
 * "Nobody" is the first row rather than a missing option. Work gets handed
 * back as often as it gets handed out -- somebody is off sick, somebody is
 * needed on the other floor -- and a picker you can only add to leaves the
 * wrong name against the job until somebody deletes the block and retypes it.
 */
@Composable
private fun AssignDialog(
    crew: List<AccountEntity>,
    current: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sch_assignee)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.sch_unassign)) },
                    leadingContent = {
                        RadioButton(selected = current == null, onClick = { onPick(null) })
                    },
                    modifier = Modifier.clickable { onPick(null) },
                )
                crew.forEach { person ->
                    ListItem(
                        headlineContent = { Text(person.displayName) },
                        leadingContent = {
                            RadioButton(
                                selected = current == person.id,
                                onClick = { onPick(person.id) },
                            )
                        },
                        modifier = Modifier.clickable { onPick(person.id) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
