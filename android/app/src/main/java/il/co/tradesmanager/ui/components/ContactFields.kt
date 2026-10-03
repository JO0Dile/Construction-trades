package il.co.tradesmanager.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import il.co.tradesmanager.R
import il.co.tradesmanager.core.people.Contact

/**
 * How the site reaches this person.
 *
 * The phone number is required and the email address is not, which is the way
 * round a site works rather than the way round a form usually is. Everybody
 * on a site has a phone and is reached on it within the hour; plenty of them
 * have never had an email address, and requiring one would be requiring the
 * part that is optional in real life.
 *
 * The error appears only once something has been typed. A box somebody has
 * not reached yet is not a mistake they have made, and a form that is already
 * shouting when it opens teaches people to ignore it.
 *
 * Shared rather than private to the sign-up screen, because the office
 * correcting a mistyped number on the crew list is asking the same question
 * of the same rules. Two copies would be two answers, and the one nobody was
 * looking at would be the one that went wrong.
 */
@Composable
fun ContactFields(
    phone: String,
    onPhone: (String) -> Unit,
    email: String,
    onEmail: (String) -> Unit,
) {
    val phoneFault = Contact.blocksPhone(phone).takeIf { phone.isNotBlank() }
    val emailFault = Contact.blocksEmail(email)

    OutlinedTextField(
        value = phone,
        onValueChange = onPhone,
        label = { Text(stringResource(R.string.acc_phone)) },
        isError = phoneFault != null,
        supportingText = {
            Text(stringResource(phoneFault?.let(::phoneMessage) ?: R.string.acc_phone_hint))
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = email,
        onValueChange = onEmail,
        label = { Text(stringResource(R.string.acc_email)) },
        isError = emailFault != null,
        supportingText = {
            if (emailFault != null) Text(stringResource(R.string.acc_email_bad))
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * One message per fault, and no `else`.
 *
 * A fault added later has to be given words here or this stops compiling,
 * which is the point: the alternative is a new kind of wrong number showing
 * a blank line under the box.
 */
@StringRes
fun phoneMessage(fault: Contact.PhoneFault): Int = when (fault) {
    Contact.PhoneFault.MISSING -> R.string.acc_phone_missing
    Contact.PhoneFault.NOT_A_NUMBER -> R.string.acc_phone_not_a_number
    Contact.PhoneFault.TOO_SHORT -> R.string.acc_phone_too_short
    Contact.PhoneFault.TOO_LONG -> R.string.acc_phone_too_long
    Contact.PhoneFault.ONE_DIGIT_REPEATED -> R.string.acc_phone_repeated
}
