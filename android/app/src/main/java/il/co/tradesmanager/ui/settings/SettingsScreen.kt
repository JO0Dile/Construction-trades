package il.co.tradesmanager.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.AppLanguages
import il.co.tradesmanager.core.i18n.resolve
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.data.repository.SettingsRepository.ThemeMode
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.account.roleLabel
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.SectionHeaderWithAdd
import il.co.tradesmanager.ui.components.currentLanguageTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel(
        factory = ViewModelFactory(container) { SettingsViewModel(it) },
    )
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val trades by viewModel.trades.collectAsStateWithLifecycle()
    var addingTrade by remember { mutableStateOf(false) }
    val updateState by viewModel.update.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val languageTag = currentLanguageTag()
    val context = LocalContext.current
    val languages = remember(languageTag) { AppLanguages.supported(context) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.set_title)) },
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

            item { SectionHeader(stringResource(R.string.set_language)) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    languages.forEach { language ->
                        FilterChip(
                            selected = languageTag.startsWith(language.tag),
                            onClick = { viewModel.setLanguage(language.tag) },
                            label = { Text(language.endonym) },
                        )
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.set_theme)) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeChip(ThemeMode.SYSTEM, R.string.set_theme_system, settings.themeMode, viewModel::setThemeMode)
                    ThemeChip(ThemeMode.LIGHT, R.string.set_theme_light, settings.themeMode, viewModel::setThemeMode)
                    ThemeChip(ThemeMode.DARK, R.string.set_theme_dark, settings.themeMode, viewModel::setThemeMode)
                }
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.set_large_text)) },
                    trailingContent = {
                        Switch(checked = settings.largeText, onCheckedChange = viewModel::setLargeText)
                    },
                )
            }

            (session as? SessionRepository.State.SignedIn)?.let { signedIn ->
                item {
                    ListItem(
                        overlineContent = { Text(stringResource(R.string.acc_signed_in_as)) },
                        headlineContent = { Text(signedIn.account.displayName) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    signedIn.company?.name,
                                    stringResource(roleLabel(signedIn.role)),
                                ).joinToString(" · "),
                            )
                        },
                        trailingContent = {
                            TextButton(onClick = viewModel::signOut) {
                                Text(stringResource(R.string.acc_sign_out))
                            }
                        },
                    )
                }

                item {
                    IdNumberRow(
                        idNumber = signedIn.account.idNumber,
                        onSet = { viewModel.setIdNumber(signedIn.account.id, it) },
                    )
                }

                // Only worth a section when there is a choice to make. One
                // membership is not a switcher, it is a row saying where you
                // already are.
                if (signedIn.switchable.size > 1) {
                    item { SectionHeader(stringResource(R.string.comp_switch)) }
                    item {
                        Text(
                            text = stringResource(R.string.comp_switch_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    items(signedIn.switchable, key = { it.id }) { membership ->
                        val company = signedIn.companyNamed(membership.companyId)
                        ListItem(
                            headlineContent = {
                                Text(company?.name ?: stringResource(R.string.comp_own))
                            },
                            supportingContent = {
                                Text(stringResource(roleLabel(membership.role)))
                            },
                            trailingContent = {
                                RadioButton(
                                    selected = membership.id == signedIn.active?.id,
                                    onClick = { viewModel.switchCompany(membership.companyId) },
                                )
                            },
                            modifier = Modifier.clickable {
                                viewModel.switchCompany(membership.companyId)
                            },
                        )
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.update_title)) }
            item {
                UpdateSection(
                    state = updateState,
                    onCheck = viewModel::checkForUpdate,
                    onDownload = viewModel::downloadUpdate,
                    installIntent = viewModel::installIntent,
                )
            }

            item {
                SectionHeaderWithAdd(
                    title = stringResource(R.string.set_trades),
                    contentDescription = stringResource(R.string.trade_add),
                    onAdd = { addingTrade = true },
                )
            }
            items(trades, key = { it.id }) { trade ->
                val isCustom = viewModel.isCustom(trade)
                val badge: (@Composable () -> Unit)? = if (isCustom) {
                    { Text(stringResource(R.string.trade_custom)) }
                } else {
                    null
                }
                ListItem(
                    headlineContent = { Text(trade.names.resolve(languageTag)) },
                    supportingContent = badge,
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = trade.isSelected,
                                onCheckedChange = { viewModel.toggleTrade(trade.id, it) },
                            )
                            // Only the user's own trades can go. A shipped one
                            // would be back on the next catalogue load, so
                            // offering to delete it would be a lie.
                            if (isCustom) {
                                IconButton(onClick = { viewModel.deleteTrade(trade) }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.action_delete),
                                    )
                                }
                            }
                        }
                    },
                )
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    OutlinedButton(onClick = { viewModel.reseed() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.set_reseed))
                    }
                    Text(
                        text = stringResource(R.string.set_reseed_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.set_security)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.set_encrypt)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (viewModel.databaseIsEncrypted) R.string.state_saved else R.string.error_generic,
                            ),
                        )
                    },
                )
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.set_delete_data))
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.set_catalog_version) + ": " +
                        settings.seededCatalogVersion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    if (addingTrade) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingTrade = false },
            title = { Text(stringResource(R.string.trade_add)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.trade_add_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.trade_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        viewModel.addTrade(name, languageTag)
                        addingTrade = false
                    },
                ) {
                    Text(stringResource(R.string.action_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { addingTrade = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.set_delete_data)) },
            text = { Text(stringResource(R.string.set_reseed_note)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteEverything(onBack)
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ThemeChip(
    mode: ThemeMode,
    labelRes: Int,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    FilterChip(
        selected = current == mode,
        onClick = { onSelect(mode) },
        label = { Text(stringResource(labelRes)) },
    )
}

/**
 * The ID number, or a way to add one.
 *
 * Somebody who signed up before this field existed has none, and cannot sign in
 * with it — which is exactly the complaint that put this row here. Once it is
 * set the field goes away rather than turning read-only-ish: a box you can type
 * in but that ignores you is worse than no box.
 */
@Composable
private fun IdNumberRow(idNumber: String?, onSet: (String) -> Unit) {
    if (!idNumber.isNullOrBlank()) {
        ListItem(
            overlineContent = { Text(stringResource(R.string.acc_id_number)) },
            headlineContent = { Text(idNumber) },
            supportingContent = { Text(stringResource(R.string.acc_id_locked)) },
        )
        return
    }

    var typed by remember { mutableStateOf("") }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.acc_id_missing),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text(stringResource(R.string.acc_id_number)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                enabled = typed.isNotBlank(),
                onClick = {
                    onSet(typed.trim())
                    typed = ""
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}
