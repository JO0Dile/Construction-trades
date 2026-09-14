package il.co.tradesmanager.ui.gate

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.access.Admission
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.account.roleLabel
import il.co.tradesmanager.ui.components.PersonCard
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.SectionPlaceholder
import il.co.tradesmanager.ui.components.SignaturePad

/**
 * The gate: the first thing anybody meets on a site, and the first thing they
 * meet in this app.
 *
 * Nobody is added by being typed in. A number off a card finds an account that
 * already exists, that account's photograph comes back, and the person standing
 * there signs before anything is written. Three separate acts, in that order,
 * because each one is a chance to notice that the man in front of you is not
 * the man on the screen.
 *
 * There is no role picker, and its absence is the design. Whoever is on the
 * gate signs people onto the site; who somebody answers to is settled
 * afterwards by the firm that engaged them. A gate that could hand out ranks
 * would be a gate that could hand out the wrong one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GateScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: GateViewModel = viewModel(
        factory = ViewModelFactory(container) { GateViewModel(it) },
    )
    val found by viewModel.found.collectAsStateWithLifecycle()
    val face by viewModel.foundFace.collectAsStateWithLifecycle()
    val inVain by viewModel.searchedInVain.collectAsStateWithLifecycle()
    val alreadyIn by viewModel.alreadyIn.collectAsStateWithLifecycle()
    val signature by viewModel.signature.collectAsStateWithLifecycle()
    val admitted by viewModel.admitted.collectAsStateWithLifecycle()
    val refusal by viewModel.refusal.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    var idNumber by remember { mutableStateOf("") }

    // Worked out here, from state Compose is watching, rather than by asking
    // the view model. A method reading MutableStateFlow.value is invisible to
    // recomposition: the button would stay dead after somebody signed, because
    // nothing the composable read had changed. It is still the one rule in
    // Admission -- the repository asks it again and is what actually decides.
    val role = (session as? SessionRepository.State.SignedIn)?.role ?: Role.WORKER
    val blocker = Admission.blocksAdmission(
        Admission.AtTheGate(
            gateKeeperRole = role,
            foundAccountId = found?.id.orEmpty(),
            signed = signature.isNotBlank(),
            alreadyIn = alreadyIn,
        ),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gate_title)) },
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
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            // Somebody who may not work the gate is told so and shown
            // nothing else. The repository refuses them too; this is so they
            // are not left tapping at a search that was never going to end in
            // an admission.
            if (!Admission.mayWorkTheGate(role)) {
                item { SectionPlaceholder(stringResource(R.string.gate_not_yours)) }
                return@LazyColumn
            }

            item { SectionHeader(stringResource(R.string.gate_find)) }
            item {
                OutlinedTextField(
                    value = idNumber,
                    onValueChange = {
                        idNumber = it
                        viewModel.clear()
                    },
                    label = { Text(stringResource(R.string.find_by_id)) },
                    supportingText = { Text(stringResource(R.string.gate_find_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            item {
                OutlinedButton(
                    onClick = { viewModel.search(idNumber) },
                    enabled = idNumber.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text(stringResource(R.string.action_search))
                }
            }

            // Nobody with that number. The gate cannot invent an account: a
            // stranger's ID number must not create a person who never agreed
            // to exist, so they sign up first and come back.
            if (inVain) {
                item { SectionPlaceholder(stringResource(R.string.gate_not_found)) }
            }

            admitted?.let { name ->
                item {
                    SectionPlaceholder(stringResource(R.string.gate_admitted, name))
                }
            }

            found?.let { person ->
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            PersonCard(
                                name = person.displayName,
                                idNumber = person.idNumber,
                                photoUri = face,
                            )
                            Text(
                                text = stringResource(
                                    R.string.gate_grants,
                                    stringResource(roleLabel(Admission.grantedRole())),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }

                if (alreadyIn) {
                    item { SectionPlaceholder(stringResource(R.string.gate_already_in)) }
                } else {
                    item { SectionHeader(stringResource(R.string.gate_sign)) }
                    item {
                        Text(
                            text = stringResource(R.string.gate_sign_body),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    item {
                        SignaturePad(
                            onSignatureChange = viewModel::signed,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    item {
                        Button(
                            onClick = { viewModel.admit() },
                            enabled = blocker == null,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        ) {
                            Text(stringResource(R.string.gate_admit))
                        }
                    }
                    // Says which thing is outstanding rather than leaving a
                    // dead button and no explanation. Recomputed from the same
                    // rule the repository will apply.
                    blocker?.let { outstanding ->
                        item {
                            Text(
                                text = stringResource(blockerText(outstanding)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                item {
                    TextButton(
                        onClick = {
                            idNumber = ""
                            viewModel.clear()
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }

            // The repository refused after the screen thought it would not.
            // Shown rather than swallowed: the two disagreeing means something
            // changed underneath, and the person at the gate needs to know it
            // did not go through.
            refusal?.let { refused ->
                item {
                    Card(Modifier.fillMaxWidth().padding(16.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(blockerText(refused)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(onClick = viewModel::clearRefusal) {
                                Text(stringResource(R.string.action_ok))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The sentence for each refusal. Exhaustive on purpose, with no `else`: the
 * next blocker somebody adds must not silently become a blank line at the
 * gate.
 */
private fun blockerText(blocker: Admission.Blocker): Int = when (blocker) {
    Admission.Blocker.NOT_ON_THE_GATE -> R.string.gate_not_yours
    Admission.Blocker.NOT_FOUND -> R.string.gate_not_found
    Admission.Blocker.ALREADY_IN -> R.string.gate_already_in
    Admission.Blocker.NOT_SIGNED -> R.string.gate_need_signature
}
