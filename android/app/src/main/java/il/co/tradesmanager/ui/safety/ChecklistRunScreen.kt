package il.co.tradesmanager.ui.safety

import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.resolve
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.currentLanguageTag
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.components.rememberImageAdder
import il.co.tradesmanager.ui.components.SignaturePad
import il.co.tradesmanager.ui.export.ExportDocument
import il.co.tradesmanager.ui.export.Exporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistRunScreen(container: AppContainer, templateId: String, onDone: () -> Unit) {
    val viewModel: ChecklistRunViewModel = viewModel(
        factory = ViewModelFactory(container) { ChecklistRunViewModel(it, templateId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val evidence by viewModel.evidence.collectAsStateWithLifecycle()
    val languageTag = currentLanguageTag()
    val locale = currentLocale()
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    var signature by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.template?.titles?.resolve(languageTag).orEmpty()) },
                actions = {
                    val template = state.template
                    val run = state.run
                    IconButton(
                        enabled = template != null && run != null,
                        onClick = {
                            if (template == null || run == null) return@IconButton
                            val result = Exporter.write(
                                context = context,
                                document = ExportDocument.Checklist(
                                    template = template,
                                    run = run,
                                    checks = state.checks,
                                    answers = state.answers.mapValues { it.value.state },
                                    notes = state.answers
                                        .filterValues { !it.note.isNullOrBlank() }
                                        .mapValues { it.value.note.orEmpty() },
                                ),
                                languageTag = languageTag,
                                locale = locale,
                                rightToLeft = layoutDirection == LayoutDirection.Rtl,
                            )
                            context.startActivity(Exporter.shareIntent(context, result))
                        },
                    ) {
                        Icon(
                            Icons.Filled.IosShare,
                            contentDescription = stringResource(R.string.set_export),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            state.template?.references?.takeIf { it.isNotEmpty() }?.let { references ->
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.saf_references),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        references.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            items(state.checks, key = { it.id }) { check ->
                val answer = state.answers[check.id]?.state
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            answer == ChecklistRunViewModel.FAIL && check.critical ->
                                MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surface
                        },
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        if (check.critical) {
                            Text(
                                stringResource(R.string.saf_critical),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Text(check.texts.resolve(languageTag), style = MaterialTheme.typography.bodyLarge)
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AnswerChip(R.string.saf_pass, answer == ChecklistRunViewModel.PASS) {
                                viewModel.answer(check.id, ChecklistRunViewModel.PASS)
                            }
                            AnswerChip(R.string.saf_fail, answer == ChecklistRunViewModel.FAIL) {
                                viewModel.answer(check.id, ChecklistRunViewModel.FAIL)
                            }
                            AnswerChip(R.string.saf_na, answer == ChecklistRunViewModel.NOT_APPLICABLE) {
                                viewModel.answer(check.id, ChecklistRunViewModel.NOT_APPLICABLE)
                            }
                        }

                        // Only on a failure, and that is the whole point of
                        // putting it here rather than on every row. Nobody
                        // writes a paragraph about a scaffold that was fine,
                        // and a form that asks them to is a form they stop
                        // filling in properly.
                        if (answer == ChecklistRunViewModel.FAIL) {
                            FailureEvidence(
                                note = state.answers[check.id]?.note.orEmpty(),
                                onNote = { viewModel.setNote(check.id, it) },
                                photoUri = evidence[check.id],
                                newCameraTarget = viewModel::newEvidenceTarget,
                                onCaptured = { viewModel.evidenceCaptured(check.id, it) },
                                onPicked = { viewModel.addEvidence(check.id, it) },
                            )
                        }
                    }
                }
            }

            item {
                Column(Modifier.padding(16.dp)) {
                    if (state.blocked && !state.signed) {
                        Text(
                            text = stringResource(R.string.saf_blocked),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    OutlinedTextField(
                        value = state.signerName,
                        onValueChange = viewModel::setSignerName,
                        label = { Text(stringResource(R.string.saf_signed_by)) },
                        singleLine = true,
                        enabled = !state.signed,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // A hand on the screen, not a name in a box. The column
                    // for this has been here since checklists were built and
                    // the caller passed null, so a completed checklist was a
                    // typed word -- and a typed word is what somebody writes
                    // when they did not walk the scaffold.
                    if (!state.signed) {
                        Text(
                            text = stringResource(R.string.saf_sign_here),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        SignaturePad(
                            onSignatureChange = { signature = it },
                            height = 140.dp,
                        )
                    }
                    Button(
                        onClick = {
                            viewModel.signOff(signature) { signed -> if (signed) onDone() }
                        },
                        // Disabled rather than hidden: the worker can see the
                        // sign-off exists and that a critical check is why it
                        // is not available yet.
                        enabled = !state.blocked && !state.signed &&
                            state.signerName.isNotBlank() && signature.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) {
                        Text(stringResource(R.string.saf_sign_off))
                    }
                }
            }
        }
    }
}

@Composable
private fun AnswerChip(labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(stringResource(labelRes)) })
}

/**
 * What was wrong, and the picture of it.
 *
 * `note` and the photograph were both on the table from the day checklists
 * were built. The only caller passed the note null and nothing ever wrote a
 * photograph, so a failed check on an inspection said FAIL and not one word
 * more — which is the row somebody reads six months later, in front of a
 * regulator, and cannot act on.
 *
 * The note is written as it is typed rather than behind a Save. A person
 * halfway up a scaffold does not come back to press a button, and a note that
 * needed one is a note that was not kept.
 */
@Composable
private fun FailureEvidence(
    note: String,
    onNote: (String) -> Unit,
    photoUri: String?,
    newCameraTarget: () -> Pair<String, Uri>,
    onCaptured: (String) -> Unit,
    onPicked: (Uri) -> Unit,
) {
    var text by remember(note) { mutableStateOf(note) }
    val addPhoto = rememberImageAdder(
        newCameraTarget = newCameraTarget,
        onCaptured = onCaptured,
        onPicked = onPicked,
        allowed = ActivityResultContracts.PickVisualMedia.ImageAndVideo,
    )

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onNote(it)
        },
        label = { Text(stringResource(R.string.saf_what_is_wrong)) },
        supportingText = { Text(stringResource(R.string.saf_fail_needs_words)) },
        minLines = 2,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )

    // The newest one. A check photographed three times shows the last look at
    // it; all of them are kept on the photo table and go out with the export.
    photoUri?.let { uri ->
        AsyncImage(
            model = uri,
            contentDescription = stringResource(R.string.saf_photograph),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
    }

    OutlinedButton(
        onClick = addPhoto,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Text(stringResource(R.string.saf_photograph))
    }
}
