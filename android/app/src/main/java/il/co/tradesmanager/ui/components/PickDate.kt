package il.co.tradesmanager.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.Formats
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * A calendar, which is the one kind of date this app wants one for.
 *
 * Everywhere else a date is picked from chips — now, tomorrow morning, four
 * hours — because a permit runs for an afternoon and a man in gloves should
 * not be scrolling months. A job runs until March and a delivery is due next
 * Thursday, so those get the calendar.
 *
 * Clear is offered beside Save because "no date" is a real answer. Plenty of
 * work is open-ended until somebody signs something, a supplier often has not
 * said yet, and a date put in by mistake has to be able to come back out.
 *
 * Shared rather than private to one screen: it was written for jobs, and the
 * second screen that wanted it would otherwise have got a second calendar
 * with its own idea of what Clear means.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickDate(initial: Long?, onDismiss: () -> Unit, onPick: (Long?) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(state.selectedDateMillis) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onPick(null) }) {
                    Text(stringResource(R.string.action_clear))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    ) {
        DatePicker(state = state)
    }
}

/** An instant as the day somebody would say out loud, in their language. */
fun asDate(millis: Long, locale: Locale): String =
    Formats.date(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate(), locale)
