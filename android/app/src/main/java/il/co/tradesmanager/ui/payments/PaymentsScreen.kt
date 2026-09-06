package il.co.tradesmanager.ui.payments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.money.Payments
import il.co.tradesmanager.data.local.entity.PaymentApplicationEntity
import il.co.tradesmanager.data.repository.PaymentsRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.DetailRow
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.components.rememberNow
import il.co.tradesmanager.ui.evidence.pluralCount
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Payment applications.
 *
 * The screen exists to make one arithmetic mistake impossible. An application
 * is cumulative: number three says what the work is worth in total, not what
 * was done since number two. The figure printed largest is the one nobody
 * should be paying, so here it is shown alongside what has already gone out
 * and the difference is worked out rather than typed.
 *
 * The other half is when the money actually lands. "שוטף + 30" counts from the
 * end of the month the invoice falls in, so an invoice on the 3rd of March is
 * due on the 30th of April — a month after the date anybody reading "net 30"
 * would have written in their cash flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: PaymentsViewModel = viewModel(
        factory = ViewModelFactory(container) { PaymentsViewModel(it, projectId) },
    )
    val applications by viewModel.applications.collectAsStateWithLifecycle()
    val open by viewModel.open.collectAsStateWithLifecycle()
    val contractSum by viewModel.contractSum.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val zone = ZoneId.systemDefault()
    // Overdue is counted in days; an hour is plenty often.
    val now = rememberNow(intervalMillis = 60L * 60L * 1000L)

    val signedIn = session as? SessionRepository.State.SignedIn
    val canEdit = signedIn?.canWrite(Lens.MONEY) != false
    var raising by remember { mutableStateOf(false) }
    var certifying by remember { mutableStateOf(false) }
    var editingClaim by remember { mutableStateOf(false) }
    val current = open

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.reference ?: stringResource(R.string.pay_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (current != null) viewModel.openApplication(null) else onBack()
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (canEdit && current == null) {
                FloatingActionButton(onClick = { raising = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.pay_new))
                }
            }
        },
    ) { padding ->
        if (current == null) {
            if (applications.isEmpty()) {
                EmptyState(
                    message = stringResource(R.string.pay_empty),
                    hint = stringResource(R.string.pay_new),
                    modifier = Modifier.padding(padding),
                )
                return@Scaffold
            }
            LazyColumn(Modifier.padding(padding)) {
                items(applications, key = { it.id }) { application ->
                    ApplicationRow(
                        application = application,
                        contractSum = contractSum,
                        now = now,
                        zone = zone,
                        locale = locale,
                        onOpen = { viewModel.openApplication(application.id) },
                    )
                }
            }
            return@Scaffold
        }

        val assessment = PaymentsViewModel.assess(current, contractSum)
        val capped = contractSum > 0.0 &&
            assessment.retentionHeld >= contractSum * current.retentionLimit

        LazyColumn(Modifier.padding(padding)) {
            item {
                // The four numbers, in the order somebody reads them. "Due this
                // time" last, because it is the answer and the three above it
                // are the working.
                Column(Modifier.padding(vertical = 8.dp)) {
                    DetailRow(
                        stringResource(R.string.pay_party),
                        current.partyName,
                    )
                    DetailRow(
                        stringResource(R.string.pay_number),
                        current.applicationNumber.toString(),
                    )
                    DetailRow(
                        stringResource(R.string.pay_claimed),
                        Formats.money(current.claimedGrossToDate, locale),
                    )
                    current.certifiedGrossToDate?.let {
                        DetailRow(
                            stringResource(R.string.pay_certified),
                            Formats.money(it, locale),
                        )
                    }
                    DetailRow(
                        stringResource(R.string.pay_retention),
                        Formats.money(assessment.retentionHeld, locale) +
                            if (capped) " · " + stringResource(R.string.pay_retention_capped) else "",
                    )
                    DetailRow(
                        stringResource(R.string.pay_net),
                        Formats.money(assessment.netToDate, locale),
                    )
                    DetailRow(
                        stringResource(R.string.pay_previously_paid),
                        Formats.money(current.previouslyPaidNet, locale),
                    )
                }
            }

            item { DueNowBanner(dueNow = assessment.dueNow, locale = locale) }

            item {
                Column(Modifier.padding(vertical = 8.dp)) {
                    DetailRow(
                        stringResource(R.string.pay_terms),
                        stringResource(termsLabel(PaymentsViewModel.termsOf(current))),
                    )
                    current.dueOn?.let {
                        DetailRow(
                            stringResource(R.string.pay_due_on),
                            dateOf(it, zone, locale),
                        )
                    }
                    DetailRow(
                        stringResource(R.string.pay_direction),
                        stringResource(
                            directionLabel(PaymentsViewModel.directionOf(current)),
                        ),
                    )
                    current.certifiedByName?.let {
                        DetailRow(stringResource(R.string.pay_certify), it)
                    }
                }
            }

            // What half of the retention comes back, and when. Shown because
            // the money is real and everybody forgets to ask for it.
            if (assessment.retentionHeld > 0.0) {
                item { SectionHeader(stringResource(R.string.pay_retention)) }
                item {
                    Column(Modifier.padding(bottom = 8.dp)) {
                        DetailRow(
                            stringResource(R.string.pay_release_completion),
                            Formats.money(
                                Payments.releasedAtCompletion(assessment.retentionHeld),
                                locale,
                            ),
                        )
                        DetailRow(
                            stringResource(R.string.pay_release_defects),
                            Formats.money(
                                Payments.releasedAfterDefects(assessment.retentionHeld),
                                locale,
                            ),
                        )
                    }
                }
            }

            if (canEdit) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when (current.status) {
                            Payments.Status.DRAFT -> {
                                OutlinedButton(
                                    onClick = { editingClaim = true },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.pay_claimed))
                                }
                                Button(
                                    onClick = { viewModel.submit() },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.pay_submit))
                                }
                            }
                            Payments.Status.SUBMITTED -> {
                                Button(
                                    onClick = { certifying = true },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.pay_certify))
                                }
                                OutlinedButton(
                                    onClick = { viewModel.reject(null) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.pay_reject))
                                }
                            }
                            Payments.Status.CERTIFIED -> {
                                Button(
                                    onClick = { viewModel.markPaid() },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.pay_mark_paid))
                                }
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    if (raising) {
        RaiseDialog(
            onDismiss = { raising = false },
            onRaise = { direction, party, claimed, retention, terms ->
                raising = false
                viewModel.raise(direction, party, claimed, retention, terms)
            },
        )
    }

    if (editingClaim) {
        current?.let { application ->
            AmountDialog(
                titleRes = R.string.pay_claimed,
                hintRes = R.string.pay_claimed_hint,
                initial = application.claimedGrossToDate,
                onDismiss = { editingClaim = false },
                onSave = {
                    editingClaim = false
                    viewModel.updateClaim(it)
                },
            )
        }
    }

    if (certifying) {
        current?.let { application ->
            AmountDialog(
                titleRes = R.string.pay_certify,
                hintRes = R.string.pay_certify_hint,
                // Opens on what was claimed, because agreeing in full is the
                // common case and retyping the figure invites a typo.
                initial = application.certifiedGrossToDate ?: application.claimedGrossToDate,
                onDismiss = { certifying = false },
                onSave = {
                    certifying = false
                    viewModel.certify(it)
                },
            )
        }
    }
}

/**
 * The one number the screen is for.
 *
 * A negative reads as money owed back rather than as nothing. The previous
 * applications certified more than the work turned out to be worth, and it has
 * to come back one way or another — showing a zero would hide that.
 */
@Composable
private fun DueNowBanner(dueNow: Double, locale: Locale) {
    val owedBack = dueNow < 0.0
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = if (owedBack) scheme.errorContainer else scheme.surfaceVariant,
        contentColor = if (owedBack) scheme.onErrorContainer else scheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(if (owedBack) R.string.pay_due_back else R.string.pay_due_now),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                Formats.money(if (owedBack) -dueNow else dueNow, locale),
                style = MaterialTheme.typography.headlineSmall,
            )
            if (owedBack) {
                Text(
                    stringResource(R.string.pay_due_back_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun ApplicationRow(
    application: PaymentApplicationEntity,
    contractSum: Double,
    now: Long,
    zone: ZoneId,
    locale: Locale,
    onOpen: () -> Unit,
) {
    val assessment = PaymentsViewModel.assess(application, contractSum)
    val overdueDays = application.dueOn
        ?.takeIf { application.paidAt == null }
        ?.let {
            Payments.daysOverdue(
                dueOn = Instant.ofEpochMilli(it).atZone(zone).toLocalDate(),
                today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),
            )
        }
        ?.takeIf { it > 0L }
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = when {
            application.paidAt != null -> scheme.surfaceVariant
            overdueDays != null -> scheme.errorContainer
            else -> scheme.surface
        },
        contentColor = when {
            application.paidAt != null -> scheme.onSurfaceVariant
            overdueDays != null -> scheme.onErrorContainer
            else -> scheme.onSurface
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                application.reference + " · #" + application.applicationNumber,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(application.partyName, style = MaterialTheme.typography.titleMedium)
            Text(
                Formats.money(assessment.dueNow, locale) + " · " +
                    stringResource(statusLabel(application.status)),
                style = MaterialTheme.typography.bodyMedium,
            )
            overdueDays?.let {
                Text(
                    pluralCount(R.plurals.pay_days_overdue, it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    HorizontalDivider()
}

private fun dateOf(millis: Long, zone: ZoneId, locale: Locale): String =
    Formats.date(Instant.ofEpochMilli(millis).atZone(zone).toLocalDate(), locale)

internal fun termsLabel(terms: Payments.Terms): Int = when (terms) {
    Payments.Terms.IMMEDIATE -> R.string.pay_terms_immediate
    Payments.Terms.SHOTEF_30 -> R.string.pay_terms_30
    Payments.Terms.SHOTEF_60 -> R.string.pay_terms_60
    Payments.Terms.SHOTEF_90 -> R.string.pay_terms_90
    Payments.Terms.SHOTEF_120 -> R.string.pay_terms_120
}

internal fun directionLabel(direction: PaymentsRepository.Direction): Int = when (direction) {
    PaymentsRepository.Direction.RECEIVABLE -> R.string.pay_receivable
    PaymentsRepository.Direction.PAYABLE -> R.string.pay_payable
}

internal fun statusLabel(status: String): Int = when (status) {
    Payments.Status.SUBMITTED -> R.string.pay_status_submitted
    Payments.Status.CERTIFIED -> R.string.pay_status_certified
    Payments.Status.PAID -> R.string.pay_status_paid
    Payments.Status.REJECTED -> R.string.pay_status_rejected
    else -> R.string.pay_status_draft
}

@Composable
private fun MoneyField(value: String, onChange: (String) -> Unit, labelRes: Int) {
    OutlinedTextField(
        value = value,
        // Digits and a dot only, so a phone set to a comma decimal cannot write
        // a figure the app then fails to read back.
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }) },
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RaiseDialog(
    onDismiss: () -> Unit,
    onRaise: (
        direction: PaymentsRepository.Direction,
        partyName: String,
        claimedGrossToDate: Double,
        retentionRate: Double,
        terms: Payments.Terms,
    ) -> Unit,
) {
    var direction by remember { mutableStateOf(PaymentsRepository.Direction.RECEIVABLE) }
    var party by remember { mutableStateOf("") }
    var claimed by remember { mutableStateOf("") }
    var retention by remember {
        mutableStateOf((Payments.DEFAULT_RETENTION * 100).toInt().toString())
    }
    var terms by remember { mutableStateOf(Payments.Terms.SHOTEF_30) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pay_new)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.pay_direction),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentsRepository.Direction.entries.forEach { option ->
                        FilterChip(
                            selected = direction == option,
                            onClick = { direction = option },
                            label = { Text(stringResource(directionLabel(option))) },
                        )
                    }
                }
                OutlinedTextField(
                    value = party,
                    onValueChange = { party = it },
                    label = { Text(stringResource(R.string.pay_party)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                MoneyField(claimed, { claimed = it }, R.string.pay_claimed)
                Text(
                    stringResource(R.string.pay_claimed_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = retention,
                    onValueChange = { retention = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.pay_retention_rate)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.pay_terms), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Payments.Terms.entries.forEach { option ->
                        FilterChip(
                            selected = terms == option,
                            onClick = { terms = option },
                            label = { Text(stringResource(termsLabel(option))) },
                        )
                    }
                }
                Text(
                    stringResource(R.string.pay_terms_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = party.isNotBlank() && claimed.toDoubleOrNull() != null,
                onClick = {
                    onRaise(
                        direction,
                        party.trim(),
                        claimed.toDoubleOrNull() ?: 0.0,
                        (retention.toIntOrNull() ?: 0).coerceIn(0, 100) / 100.0,
                        terms,
                    )
                },
            ) {
                Text(stringResource(R.string.acc_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun AmountDialog(
    titleRes: Int,
    hintRes: Int,
    initial: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    // Double.toString, so what is shown is what parses back whatever the
    // phone's decimal separator is.
    var amount by remember { mutableStateOf(initial.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MoneyField(amount, { amount = it }, titleRes)
                Text(
                    stringResource(hintRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount.toDoubleOrNull() != null,
                onClick = { onSave(amount.toDoubleOrNull() ?: 0.0) },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
