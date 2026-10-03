package il.co.tradesmanager.ui.components

import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import il.co.tradesmanager.R

/**
 * Says that a write was refused.
 *
 * Twelve writes across seven screens returned false when the rules said no --
 * a permit already issued from another phone, a snag verified twice, an ID
 * number already on somebody else's account -- and every one of them threw the
 * answer away. The button did nothing, which is indistinguishable from a
 * broken app. tools/check-ignored-results.py now fails the build on a write
 * whose answer nothing reads.
 */
@Composable
fun NotSavedDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    @StringRes message: Int = R.string.write_not_saved,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(message)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        },
    )
}
