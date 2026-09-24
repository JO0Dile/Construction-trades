package il.co.tradesmanager.ui.update

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import il.co.tradesmanager.BuildConfig
import il.co.tradesmanager.R
import il.co.tradesmanager.core.update.ReleaseNotes
import il.co.tradesmanager.data.update.ReleaseNotesFile
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.components.currentLanguageTag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Versions and what each of them does, newest first, in the phone's language.
 *
 * The same list in three places: under an update that is on offer, in the
 * dialog after one has been installed, and in Settings for the whole history.
 */
@Composable
fun ChangesList(entries: List<ReleaseNotes.Entry>, modifier: Modifier = Modifier) {
    val languageTag = currentLanguageTag()
    Column(modifier) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.whats_new_version, entry.version),
                style = MaterialTheme.typography.titleSmall,
            )
            ReleaseNotes.points(entry, languageTag).forEach { point ->
                Text(
                    "• $point",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
fun WhatsNewDialog(entries: List<ReleaseNotes.Entry>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.whats_new_title)) },
        text = { ChangesList(entries, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        },
    )
}

/**
 * Shows what changed, once, on the first start after an update.
 *
 * Everything since the last version this phone was shown, so skipping from
 * 0.17 to 0.20 lists 0.18, 0.19 and 0.20. Nothing on a first install. The
 * version is recorded as seen when the dialog is closed -- or straight away
 * when there is nothing to show -- so the next update stacks from here.
 */
@Composable
fun WhatsNewGate(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<ReleaseNotes.Entry>>(emptyList()) }
    val current = BuildConfig.VERSION_NAME

    LaunchedEffect(Unit) {
        val lastSeen = container.settings.settings.first().whatsNewSeen
        val show = ReleaseNotes.shouldShow(lastSeen, current, freshInstall(context))
        val due = if (show) ReleaseNotes.between(ReleaseNotesFile.bundled(context), lastSeen, current) else emptyList()
        if (due.isEmpty()) {
            if (lastSeen != current) container.settings.setWhatsNewSeen(current)
        } else {
            entries = due
        }
    }

    if (entries.isNotEmpty()) {
        WhatsNewDialog(
            entries = entries,
            onDismiss = {
                entries = emptyList()
                scope.launch { container.settings.setWhatsNewSeen(current) }
            },
        )
    }
}

/** Installed and never updated. Android records both times; they are equal until an update. */
@Suppress("DEPRECATION")
private fun freshInstall(context: Context): Boolean = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    info.firstInstallTime == info.lastUpdateTime
}.getOrDefault(false)
