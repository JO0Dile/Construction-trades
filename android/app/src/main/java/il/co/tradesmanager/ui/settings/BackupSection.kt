package il.co.tradesmanager.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.security.Backup
import il.co.tradesmanager.data.backup.StagedRestore
import il.co.tradesmanager.ui.components.currentLocale
import java.time.Instant
import java.time.ZoneId

/**
 * Taking the site's record off the phone, and putting it back.
 *
 * Everything is on this device and nothing is on a server, which is the point
 * on a site with no signal and has one consequence nobody notices until it
 * happens: a lost phone is a lost site diary. This is the answer, and it is
 * deliberately a thing the person does to a file they choose rather than
 * something uploaded on their behalf — the database holds ID numbers, faces,
 * signatures and wages, and that is not a decision to make for somebody.
 *
 * The passphrase is asked for twice on the way out and once on the way in, and
 * the screen says plainly that nobody can reset it. There is no recovery here
 * and pretending otherwise would be the cruellest possible lie to tell
 * somebody on the day they need this.
 */
@Composable
fun BackupSection(
    state: SettingsViewModel.BackupState,
    restoreOutcome: StagedRestore.Outcome,
    suggestedName: String,
    onBackUp: (android.net.Uri, String) -> Unit,
    onRestore: (android.net.Uri, String) -> Unit,
    onCancelRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = currentLocale()
    var pending by remember { mutableStateOf<Pair<Asking, android.net.Uri>?>(null) }

    val create = rememberLauncherForActivityResult(
        // Any type: a .tmbak is nothing the system knows, and claiming a MIME
        // type it does not have makes some file pickers refuse to save it.
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> uri?.let { pending = Asking.BACK_UP to it } }

    val open = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { pending = Asking.RESTORE to it } }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.backup_why),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        // Said once, at the top, and only when it is true. A person who has
        // just restored wants to know it worked before they go looking for
        // whether their jobs came back.
        when (restoreOutcome) {
            StagedRestore.Outcome.RESTORED ->
                Note(stringResource(R.string.backup_restored), error = false)
            StagedRestore.Outcome.REFUSED ->
                Note(stringResource(R.string.backup_refused_file), error = true)
            StagedRestore.Outcome.ROLLED_BACK ->
                Note(stringResource(R.string.backup_rolled_back), error = true)
            StagedRestore.Outcome.NOTHING_STAGED -> Unit
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            OutlinedButton(
                onClick = { create.launch(suggestedName) },
                enabled = state !is SettingsViewModel.BackupState.Working,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.backup_take))
            }
            Spacer(Modifier.padding(horizontal = 4.dp))
            OutlinedButton(
                onClick = { open.launch(arrayOf("*/*")) },
                enabled = state !is SettingsViewModel.BackupState.Working,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.backup_restore))
            }
        }

        when (state) {
            SettingsViewModel.BackupState.Idle -> Unit
            SettingsViewModel.BackupState.Working ->
                Note(stringResource(R.string.backup_working), error = false)
            is SettingsViewModel.BackupState.BackedUp ->
                Note(
                    stringResource(R.string.backup_done, state.mediaCount),
                    error = false,
                )
            is SettingsViewModel.BackupState.Staged -> {
                val at = Instant.ofEpochMilli(state.takenAt).atZone(ZoneId.systemDefault())
                Note(
                    stringResource(
                        R.string.backup_staged,
                        Formats.dateTime(at.toLocalDate(), at.toLocalTime(), locale),
                    ),
                    error = false,
                )
                if (state.needsMigrating) {
                    Note(stringResource(R.string.backup_older_version), error = false)
                }
                TextButton(onClick = onCancelRestore) {
                    Text(stringResource(R.string.backup_cancel_restore))
                }
            }
            is SettingsViewModel.BackupState.Refused ->
                Note(stringResource(blockerText(state.blocker)), error = true)
            SettingsViewModel.BackupState.Failed ->
                Note(stringResource(R.string.backup_failed), error = true)
        }
    }

    pending?.let { (what, uri) ->
        PassphraseDialog(
            asking = what,
            onDismiss = { pending = null },
            onConfirm = { passphrase ->
                pending = null
                onDismiss()
                when (what) {
                    Asking.BACK_UP -> onBackUp(uri, passphrase)
                    Asking.RESTORE -> onRestore(uri, passphrase)
                }
            },
        )
    }
}

private enum class Asking { BACK_UP, RESTORE }

/**
 * Asks for the passphrase — twice when making a backup, once when opening one.
 *
 * Twice on the way out because there is nothing to check it against later and
 * nobody to reset it: a typo is discovered on the day the phone went into the
 * concrete. Once on the way in because a wrong one simply fails to open the
 * file, which is a mistake that costs a second rather than a year.
 */
@Composable
private fun PassphraseDialog(
    asking: Asking,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    val making = asking == Asking.BACK_UP
    val fault = if (making) Backup.blocksPassphrase(passphrase, repeated) else null
    val ready = if (making) fault == null else passphrase.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (making) R.string.backup_passphrase else R.string.backup_passphrase_open,
                ),
            )
        },
        text = {
            Column {
                if (making) {
                    Text(
                        text = stringResource(R.string.backup_passphrase_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.backup_passphrase)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (making) {
                    OutlinedTextField(
                        value = repeated,
                        onValueChange = { repeated = it },
                        label = { Text(stringResource(R.string.backup_passphrase_again)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    // Says which thing is wrong rather than leaving a dead
                    // button, and says nothing at all until something has been
                    // typed — a red line under an empty box is a telling-off.
                    if (passphrase.isNotEmpty() && fault != null) {
                        Text(
                            text = stringResource(faultText(fault)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(passphrase) }, enabled = ready) {
                Text(stringResource(if (making) R.string.backup_take else R.string.backup_restore))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun Note(text: String, error: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/** Exhaustive with no `else`, so a new refusal cannot become a blank line. */
private fun blockerText(blocker: Backup.Blocker): Int = when (blocker) {
    Backup.Blocker.NOT_A_BACKUP -> R.string.backup_refused_file
    Backup.Blocker.FROM_A_NEWER_APP -> R.string.backup_newer_app
}

private fun faultText(fault: Backup.PassphraseFault): Int = when (fault) {
    Backup.PassphraseFault.TOO_SHORT -> R.string.backup_too_short
    Backup.PassphraseFault.DOES_NOT_MATCH -> R.string.backup_no_match
    Backup.PassphraseFault.TOO_FEW_CHARACTERS -> R.string.backup_too_plain
}
