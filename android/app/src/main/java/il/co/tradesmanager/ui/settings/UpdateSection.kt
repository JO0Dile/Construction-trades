package il.co.tradesmanager.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import il.co.tradesmanager.BuildConfig
import il.co.tradesmanager.R
import il.co.tradesmanager.data.update.UpdateRepository
import java.io.File

/**
 * Updates, for a copy of the app that came from a download rather than a
 * store.
 *
 * Nothing here exists in a store build — [BuildConfig.SELF_UPDATE] is false
 * there and the whole section is skipped, because a Play build that offers to
 * install its own APK is a policy violation, and on iOS the platform simply
 * does not allow it.
 */
@Composable
fun UpdateSection(
    state: SettingsViewModel.UpdateState,
    onCheck: () -> Unit,
    onDownload: (release: UpdateRepository.Release) -> Unit,
    installIntent: (File) -> Intent,
) {
    if (!BuildConfig.SELF_UPDATE) return
    val context = LocalContext.current

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.update_installed) + ": " + BuildConfig.VERSION_NAME,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))

        when (state) {
            is SettingsViewModel.UpdateState.Checking -> {
                Text(
                    text = stringResource(R.string.update_checking),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is SettingsViewModel.UpdateState.UpToDate -> {
                StatusLine(stringResource(R.string.update_current))
                CheckButton(onCheck)
            }

            is SettingsViewModel.UpdateState.Failed -> {
                StatusLine(stringResource(R.string.update_failed))
                CheckButton(onCheck)
            }

            is SettingsViewModel.UpdateState.Available -> {
                StatusLine(
                    stringResource(R.string.update_available) + ": " + state.release.versionName,
                )
                if (state.release.notes.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.update_notes),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = state.release.notes.take(NOTES_LIMIT),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.update_permission),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(
                    onClick = { onDownload(state.release) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.update_download))
                }
            }

            is SettingsViewModel.UpdateState.Downloading -> {
                StatusLine(stringResource(R.string.update_downloading))
                // A negative fraction means the server sent no length: show
                // movement rather than a bar frozen at zero.
                if (state.fraction >= 0f) {
                    LinearProgressIndicator(
                        progress = { state.fraction },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }

            is SettingsViewModel.UpdateState.Ready -> {
                Button(
                    onClick = {
                        // Android 8 and up asks per app, not once per device.
                        if (context.packageManager.canRequestPackageInstalls()) {
                            context.startActivity(installIntent(state.apk))
                        } else {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.update_install))
                }
            }

            SettingsViewModel.UpdateState.Idle -> CheckButton(onCheck)
        }
    }
}

@Composable
private fun StatusLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CheckButton(onCheck: () -> Unit) {
    OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(stringResource(R.string.update_check))
    }
}

private const val NOTES_LIMIT = 600
