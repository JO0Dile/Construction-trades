package il.co.tradesmanager.ui.components

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import il.co.tradesmanager.R

/**
 * Asks for a permission only after saying, in the user's language, what it is
 * for — which is what both stores expect and what stops a site worker being
 * handed a bare system dialog mid-job.
 *
 * The caller's action runs either way where the feature degrades gracefully:
 * a refused location permission costs the GPS stamp on a check-in, not the
 * check-in.
 */
@Composable
fun rememberPermissionRequest(
    permission: String,
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    onResult: (granted: Boolean) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = onResult,
    )

    if (showRationale) {
        AlertDialog(
            onDismissRequest = {
                showRationale = false
                onResult(false)
            },
            title = { Text(stringResource(titleRes)) },
            text = { Text(stringResource(bodyRes)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRationale = false
                        launcher.launch(permission)
                    },
                ) {
                    Text(stringResource(R.string.perm_allow))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRationale = false
                        onResult(false)
                    },
                ) {
                    Text(stringResource(R.string.perm_not_now))
                }
            },
        )
    }

    return {
        val alreadyGranted = ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) onResult(true) else showRationale = true
    }
}
