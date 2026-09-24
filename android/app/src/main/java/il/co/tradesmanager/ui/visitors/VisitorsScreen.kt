package il.co.tradesmanager.ui.visitors

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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import il.co.tradesmanager.core.safety.Visits
import il.co.tradesmanager.data.local.entity.SiteVisitEntity
import il.co.tradesmanager.data.repository.VisitRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.SignaturePad
import il.co.tradesmanager.ui.components.currentLocale
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * One job's visitor log.
 *
 * Who is here now comes first, with a way to sign each of them out, because
 * that list is what the next roll call starts from: a visitor nobody signed
 * out is a name somebody will be shouting for at the assembly point.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitorsScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: VisitorsViewModel =
        viewModel(factory = ViewModelFactory(container) { VisitorsViewModel(it, projectId) })
    val mayWrite by viewModel.mayWrite.collectAsStateWithLifecycle()
    val here by viewModel.here.collectAsStateWithLifecycle()
    val gone by viewModel.gone.collectAsStateWithLifecycle()
    val refusal by viewModel.refusal.collectAsStateWithLifecycle()
    val locale = currentLocale()

    var signing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var organisation by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var briefed by remember { mutableStateOf(false) }
    var signature by remember { mutableStateOf("") }
    var form by remember { mutableIntStateOf(0) }

    val snackbar = remember { SnackbarHostState() }
    val signedInText = stringResource(R.string.visit_signed_in)
    LaunchedEffect(Unit) {
        viewModel.signedInEvents.collect {
            name = ""
            organisation = ""
            phone = ""
            host = ""
            briefed = false
            signature = ""
            form += 1
            signing = false
            snackbar.showSnackbar(signedInText)
        }
    }
    val refusalText = refusal?.let { stringResource(sentence(it)) }
    LaunchedEffect(refusalText) {
        if (refusalText != null) {
            snackbar.showSnackbar(refusalText)
            viewModel.clearRefusal()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.visit_title)) },
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
                    stringResource(R.string.visit_blurb),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }

            if (mayWrite && !signing) {
                item {
                    Button(onClick = { signing = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.visit_sign_in))
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            if (mayWrite && signing) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.visit_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = organisation,
                        onValueChange = { organisation = it },
                        label = { Text(stringResource(R.string.visit_organisation)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text(stringResource(R.string.visit_host)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text(stringResource(R.string.visit_phone)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = briefed, onCheckedChange = { briefed = it })
                        Text(stringResource(R.string.visit_briefed))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.visit_sign_here),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    key(form) {
                        SignaturePad(onSignatureChange = { signature = it }, height = 140.dp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        enabled = name.isNotBlank(),
                        onClick = {
                            viewModel.signIn(name, organisation, phone, host, briefed, signature)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.visit_record))
                    }
                    TextButton(onClick = { signing = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            item {
                Text(stringResource(R.string.visit_here), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
            }
            if (here.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.visit_nobody_here),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(here, key = { it.id }) { visit ->
                    HereCard(
                        visit = visit,
                        locale = locale,
                        mayWrite = mayWrite,
                        onSignOut = { viewModel.signOut(visit.id) },
                    )
                }
            }

            if (gone.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    Text(stringResource(R.string.visit_history), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                }
                items(gone, key = { "gone-" + it.id }) { visit ->
                    Text(
                        listOfNotNull(
                            visit.name,
                            visit.organisation,
                            timeOf(visit.arrivedAt, locale) + " – " +
                                (visit.leftAt?.let { timeOf(it, locale) } ?: ""),
                        ).joinToString(SEPARATOR),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * Somebody still signed in. One who has been here past a shift is drawn as
 * a warning: they have almost certainly gone, and the roll call will still
 * list them until somebody says so.
 */
@Composable
private fun HereCard(
    visit: SiteVisitEntity,
    locale: Locale,
    mayWrite: Boolean,
    onSignOut: () -> Unit,
) {
    val forgotten = Visits.forgotten(visit.arrivedAt, System.currentTimeMillis())
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = if (forgotten) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(visit.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(
                        visit.organisation,
                        visit.hostName?.let { stringResource(R.string.visit_seeing, it) },
                    ).joinToString(SEPARATOR),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    listOfNotNull(
                        stringResource(R.string.visit_since, timeOf(visit.arrivedAt, locale)),
                        stringResource(if (visit.briefed) R.string.visit_was_briefed else R.string.visit_not_briefed),
                    ).joinToString(SEPARATOR),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (visit.briefed) FontWeight.Normal else FontWeight.Bold,
                )
                if (forgotten) {
                    Text(
                        stringResource(R.string.visit_forgotten),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (mayWrite) {
                OutlinedButton(onClick = onSignOut) {
                    Text(stringResource(R.string.visit_sign_out))
                }
            }
        }
    }
}

private fun timeOf(at: Long, locale: Locale): String {
    val zoned = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    return Formats.dateTime(zoned.toLocalDate(), zoned.toLocalTime(), locale)
}

@StringRes
private fun sentence(refusal: VisitRepository.Refusal): Int = when (refusal) {
    VisitRepository.Refusal.NOT_ALLOWED -> R.string.visit_refused_not_allowed
    VisitRepository.Refusal.BLANK_NAME -> R.string.visit_refused_name
    VisitRepository.Refusal.ALREADY_LEFT -> R.string.visit_refused_left
    VisitRepository.Refusal.UNKNOWN -> R.string.write_not_saved
}

private const val SEPARATOR = " · "
