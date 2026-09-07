package il.co.tradesmanager.ui.safety

import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.safety.Violations
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.PersonCard
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.SectionPlaceholder
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.components.rememberImageAdder

/**
 * The safety officer's register: who, what, what it cost, and the proof.
 *
 * The whole screen is built around one sequence — type an ID, see a face,
 * write it down, attach the proof, stand behind it. The face is the point of
 * the ID step: five men on a site in the same hi-vis are not told apart by a
 * name, and a violation against the wrong one is worse than none.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViolationsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenCrew: () -> Unit,
) {
    val viewModel: ViolationsViewModel = viewModel(
        factory = ViewModelFactory(container) { ViolationsViewModel(it) },
    )
    val violations by viewModel.violations.collectAsStateWithLifecycle()
    val found by viewModel.found.collectAsStateWithLifecycle()
    val face by viewModel.foundFace.collectAsStateWithLifecycle()
    val inVain by viewModel.searchedInVain.collectAsStateWithLifecycle()
    val open by viewModel.open.collectAsStateWithLifecycle()
    val evidence by viewModel.evidence.collectAsStateWithLifecycle()
    val openFace by viewModel.openFace.collectAsStateWithLifecycle()
    val locale = currentLocale()
    var idNumber by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vio_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    // The other way of finding somebody, next to the ID field
                    // because it is the same job. You type the number off a
                    // card when you have the card; you look down a column of
                    // faces when the man has walked off and all you have is
                    // that he was on the electrics.
                    IconButton(onClick = onOpenCrew) {
                        Icon(
                            Icons.Filled.Groups,
                            contentDescription = stringResource(R.string.crew_open),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val draft = open
        if (draft != null) {
            DraftForm(
                viewModel = viewModel,
                description = draft.description,
                cost = draft.costAmount,
                againstName = draft.againstName,
                againstIdNumber = draft.againstIdNumber,
                againstFace = openFace,
                evidenceCount = evidence.size,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(Modifier.padding(padding)) {
            item { SectionHeader(stringResource(R.string.vio_new)) }
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = idNumber,
                        onValueChange = {
                            idNumber = it
                            viewModel.clearSearch()
                        },
                        label = { Text(stringResource(R.string.find_by_id)) },
                        supportingText = { Text(stringResource(R.string.find_by_id_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = { viewModel.search(idNumber) },
                    enabled = idNumber.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Text(stringResource(R.string.action_search))
                }
            }

            if (inVain) {
                item { SectionPlaceholder(stringResource(R.string.find_nobody)) }
            }

            found?.let { person ->
                item {
                    Card(Modifier.fillMaxWidth().padding(16.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            PersonCard(
                                name = person.displayName,
                                idNumber = person.idNumber,
                                photoUri = face,
                            )
                            Button(
                                onClick = {
                                    viewModel.startAgainstFound()
                                    idNumber = ""
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            ) {
                                Text(stringResource(R.string.vio_new))
                            }
                        }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.vio_title)) }
            if (violations.isEmpty()) {
                item { SectionPlaceholder(stringResource(R.string.vio_none)) }
            }
            items(violations, key = { it.id }) { violation ->
                val status = Violations.Status.valueOf(violation.status)
                ListItem(
                    overlineContent = { Text(stringResource(statusLabel(status))) },
                    headlineContent = { Text(violation.againstName) },
                    supportingContent = {
                        Column {
                            Text(violation.description)
                            violation.costAmount?.let {
                                Text(Formats.money(it, locale))
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * The draft, and the two ways out of it.
 *
 * Cancel is a plain button with no confirmation: the officer asked to be able
 * to drop one freely, and a draft nobody has been told about is exactly the
 * thing that should be cheap to throw away. Confirm is the one that warns,
 * because that is the irreversible half.
 */
@Composable
private fun DraftForm(
    viewModel: ViolationsViewModel,
    description: String,
    cost: Double?,
    againstName: String,
    againstIdNumber: String,
    againstFace: String?,
    evidenceCount: Int,
    modifier: Modifier = Modifier,
) {
    var text by remember(description) { mutableStateOf(description) }
    var costText by remember(cost) { mutableStateOf(cost?.toString().orEmpty()) }
    // Locale-independent: what is typed is what is stored, and a decimal comma
    // must not silently become a different number.
    val parsedCost = costText.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    val costOk = costText.isBlank() || (parsedCost != null && parsedCost >= 0.0)

    val addEvidence = rememberImageAdder(
        newCameraTarget = { viewModel.newEvidenceTarget() },
        onCaptured = { viewModel.evidenceCaptured(it) },
        onPicked = { viewModel.addEvidence(it) },
        allowed = ActivityResultContracts.PickVisualMedia.ImageAndVideo,
    )

    LazyColumn(modifier) {
        item {
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(12.dp)) {
                    PersonCard(
                        name = againstName,
                        idNumber = againstIdNumber,
                        photoUri = againstFace,
                    )
                }
            }
        }
        item {
            Text(
                text = stringResource(R.string.vio_draft),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    viewModel.edit(it, parsedCost)
                },
                label = { Text(stringResource(R.string.vio_what)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
        item {
            OutlinedTextField(
                value = costText,
                onValueChange = {
                    costText = it
                    val parsed = it.trim().takeIf { v -> v.isNotEmpty() }?.toDoubleOrNull()
                    if (it.isBlank() || parsed != null) viewModel.edit(text, parsed)
                },
                label = { Text(stringResource(R.string.vio_cost)) },
                supportingText = { Text(stringResource(R.string.vio_cost_hint)) },
                isError = !costOk,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        item {
            OutlinedButton(
                onClick = addEvidence,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text(stringResource(R.string.vio_evidence) + " ($evidenceCount)")
            }
        }

        // Says which thing is missing rather than greying out Confirm with no
        // explanation, which is how somebody decides the app is broken.
        viewModel.blocker()?.let { blocker ->
            item {
                Text(
                    text = stringResource(blockerLabel(blocker)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.vio_confirm_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { viewModel.cancel() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = { viewModel.confirm() },
                    enabled = viewModel.blocker() == null && costOk,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.vio_confirm))
                }
            }
        }
    }
}

private fun statusLabel(status: Violations.Status): Int = when (status) {
    Violations.Status.DRAFT -> R.string.vio_draft
    Violations.Status.CONFIRMED -> R.string.vio_confirmed
    Violations.Status.CANCELLED -> R.string.vio_cancelled
}

private fun blockerLabel(blocker: Violations.Blocker): Int = when (blocker) {
    Violations.Blocker.NOT_A_DRAFT -> R.string.vio_confirmed
    Violations.Blocker.NOBODY_NAMED -> R.string.vio_need_person
    Violations.Blocker.NO_EVIDENCE -> R.string.vio_need_evidence
    Violations.Blocker.NOT_DESCRIBED -> R.string.vio_need_description
}
