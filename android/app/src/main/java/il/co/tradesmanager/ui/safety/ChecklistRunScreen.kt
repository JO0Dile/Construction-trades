package il.co.tradesmanager.ui.safety

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.resolve
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.currentLanguageTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistRunScreen(container: AppContainer, templateId: String, onDone: () -> Unit) {
    val viewModel: ChecklistRunViewModel = viewModel(
        factory = ViewModelFactory(container) { ChecklistRunViewModel(it, templateId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val languageTag = currentLanguageTag()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.template?.titles?.resolve(languageTag).orEmpty()) },
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
                    Button(
                        onClick = { viewModel.signOff { signed -> if (signed) onDone() } },
                        // Disabled rather than hidden: the worker can see the
                        // sign-off exists and that a critical check is why it
                        // is not available yet.
                        enabled = !state.blocked && !state.signed && state.signerName.isNotBlank(),
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
