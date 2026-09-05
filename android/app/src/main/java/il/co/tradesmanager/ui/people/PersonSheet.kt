package il.co.tradesmanager.ui.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import il.co.tradesmanager.R
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.i18n.resolve
import il.co.tradesmanager.core.people.Expiry
import il.co.tradesmanager.data.catalog.ProjectKind
import il.co.tradesmanager.data.local.entity.AccountEntity
import il.co.tradesmanager.data.local.entity.CertificationEntity
import il.co.tradesmanager.ui.account.roleHint
import il.co.tradesmanager.ui.account.roleLabel
import il.co.tradesmanager.ui.components.SectionHeaderWithAdd
import il.co.tradesmanager.ui.components.SectionPlaceholder
import il.co.tradesmanager.ui.components.currentLanguageTag
import il.co.tradesmanager.ui.components.currentLocale
import java.time.ZoneId

/**
 * One person: what they do, and what they are ticketed for.
 *
 * Both in one place because they are the same question on a site — "can Yossi
 * go up on the scaffold" is half a role and half a certificate, and answering
 * it from two screens is how the wrong person ends up on the scaffold.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonSheet(
    person: AccountEntity,
    certifications: List<CertificationEntity>,
    suggestedKinds: List<ProjectKind>,
    role: Role,
    canManage: Boolean,
    onDismiss: () -> Unit,
    onSetRole: (Role) -> Unit,
    onAddCertification: (title: String, reference: String?, expiresOn: Long?) -> Unit,
    onRemoveCertification: (CertificationEntity) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val locale = currentLocale()
    val languageTag = currentLanguageTag()
    var adding by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text(person.displayName, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = stringResource(roleHint(role)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            if (canManage) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.people_role),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Role.assignable.forEach { option ->
                        FilterChip(
                            selected = role == option,
                            onClick = { onSetRole(option) },
                            label = { Text(stringResource(roleLabel(option))) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            SectionHeaderWithAdd(
                title = stringResource(R.string.cert_title),
                contentDescription = stringResource(R.string.cert_add),
                onAdd = if (canManage) ({ adding = true }) else null,
            )
            if (certifications.isEmpty()) {
                SectionPlaceholder(stringResource(R.string.cert_empty))
            }
            certifications.forEach { certification ->
                CertificationRow(
                    certification = certification,
                    locale = locale,
                    canManage = canManage,
                    onRemove = { onRemoveCertification(certification) },
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (adding) {
        CertificationDialog(
            suggestedKinds = suggestedKinds,
            languageTag = languageTag,
            onDismiss = { adding = false },
            onAdd = { title, reference, expiresOn ->
                onAddCertification(title, reference, expiresOn)
                adding = false
            },
        )
    }
}

@Composable
private fun CertificationRow(
    certification: CertificationEntity,
    locale: java.util.Locale,
    canManage: Boolean,
    onRemove: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val state = Expiry.state(certification.expiresOn, now)
    val overline: (@Composable () -> Unit)? = certification.reference?.let { ref ->
        { Text(ref) }
    }

    ListItem(
        headlineContent = { Text(certification.title) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = expiryText(certification, state, now, locale),
                    style = MaterialTheme.typography.bodySmall,
                    color = when (state) {
                        Expiry.State.EXPIRED -> MaterialTheme.colorScheme.error
                        Expiry.State.EXPIRING_SOON -> WarningColour
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        overlineContent = overline,
        trailingContent = {
            if (canManage) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_delete),
                    )
                }
            }
        },
    )
}

@Composable
private fun expiryText(
    certification: CertificationEntity,
    state: Expiry.State,
    now: Long,
    locale: java.util.Locale,
): String = when (state) {
    Expiry.State.NO_EXPIRY -> stringResource(R.string.cert_no_expiry)
    Expiry.State.EXPIRED -> stringResource(R.string.cert_expired)
    Expiry.State.EXPIRING_SOON -> {
        val days = Expiry.daysUntil(certification.expiresOn ?: now, now).toInt().coerceAtLeast(0)
        pluralStringResource(R.plurals.cert_expiring_days, days, days)
    }
    Expiry.State.VALID -> {
        val date = java.time.Instant.ofEpochMilli(certification.expiresOn ?: now)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        stringResource(R.string.cert_expires) + " " + Formats.date(date, locale)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CertificationDialog(
    suggestedKinds: List<ProjectKind>,
    languageTag: String,
    onDismiss: () -> Unit,
    onAdd: (title: String, reference: String?, expiresOn: Long?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var expires by remember { mutableStateOf("") }

    // Empty is legitimate — a trade licence may not expire, and inventing a
    // date for one produces a warning nobody can ever clear.
    val parsed = if (expires.isBlank()) null else Formats.parseDate(expires)
    val dateOk = expires.isBlank() || parsed != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cert_add)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.cert_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Suggestions, not a fixed set: a ticket the list does not name
                // is still typed straight into the field above.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestedKinds.forEach { kind ->
                        val name = kind.names.resolve(languageTag)
                        FilterChip(
                            selected = title == name,
                            onClick = { title = name },
                            label = { Text(name) },
                        )
                    }
                }
                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    label = { Text(stringResource(R.string.cert_reference)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = expires,
                    onValueChange = { expires = it },
                    label = { Text(stringResource(R.string.cert_expires)) },
                    isError = !dateOk,
                    supportingText = {
                        Text(
                            stringResource(
                                if (dateOk) R.string.cert_expires_hint else R.string.cert_date_bad,
                            ),
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && dateOk,
                onClick = {
                    onAdd(
                        title.trim(),
                        reference.takeIf { it.isNotBlank() },
                        parsed?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
                    )
                },
            ) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Amber: still legal, but book the renewal course. Not an error yet. */
private val WarningColour = androidx.compose.ui.graphics.Color(0xFFB9770E)
