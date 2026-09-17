package il.co.tradesmanager.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
fun PickDate(
    initial: Long?,
    onDismiss: () -> Unit,
    onPick: (Long?) -> Unit,
    /**
     * The question, when the calendar arrives without one having been asked.
     *
     * Tapping a row that already says a date is its own question. Pressing
     * "place the order" and having a calendar appear is not, and a dialog
     * that does not say what it wants is one people cancel.
     */
    title: String? = null,
) {
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
        // Two calls rather than a nullable composable lambda built through
        // `let`: the slot is @Composable and threading one out of a lambda is
        // the sort of inference that compiles until it does not.
        if (title == null) {
            DatePicker(state = state)
        } else {
            DatePicker(
                state = state,
                title = { Text(title, modifier = Modifier.padding(DatePickerTitlePadding)) },
            )
        }
    }
}

/** What Material puts round a date picker's own title, so ours sits the same. */
private val DatePickerTitlePadding = PaddingValues(start = 24.dp, end = 12.dp, top = 16.dp)

/** An instant as the day somebody would say out loud, in their language. */
fun asDate(millis: Long, locale: Locale): String =
    Formats.date(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate(), locale)
