package il.co.tradesmanager.ui.people

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.people.Contact
import il.co.tradesmanager.core.i18n.resolve
import il.co.tradesmanager.core.people.Corrections
import il.co.tradesmanager.core.people.Expiry
import il.co.tradesmanager.data.local.entity.TradeEntity
import il.co.tradesmanager.data.repository.AccountRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.account.roleLabel
import il.co.tradesmanager.ui.components.ContactFields
import il.co.tradesmanager.ui.components.PersonCard
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.SectionPlaceholder
import il.co.tradesmanager.ui.components.currentLanguageTag
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.components.phoneMessage
import java.time.Instant
import java.time.ZoneId

/**
 * The crew, by trade, with faces.
 *
 * Built for one moment on a site: a man walks away from a safety officer, the
 * officer saw his face and knows he was on the electrics. There is no name to
 * search for — not knowing it is the entire difficulty — so the screen is a
 * trade and then a column of faces to look down.
 *
 * What a profile shows is what the viewer is entitled to. There is no money on
 * it for a safety officer because [il.co.tradesmanager.core.access.Chain] says
 * so, not because this file remembers not to draw it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: CrewViewModel = viewModel(
        factory = ViewModelFactory(container) { CrewViewModel(it) },
    )
    val trades by viewModel.trades.collectAsStateWithLifecycle()
    val tradeId by viewModel.tradeId.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val withoutTrade by viewModel.withoutTrade.collectAsStateWithLifecycle()
    val open by viewModel.open.collectAsStateWithLifecycle()
    val languageTag = currentLanguageTag()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crew_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    trades.forEach { trade ->
                        FilterChip(
                            selected = tradeId == trade.id,
                            onClick = { viewModel.setTrade(trade.id) },
                            label = { Text(trade.names.resolve(languageTag)) },
                        )
                    }
                }
            }

            if (people.isEmpty()) {
                item { SectionPlaceholder(stringResource(R.string.crew_none)) }
            }

            items(people, key = { it.membership.id }) { person ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openProfile(person.membership.id) }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    PersonCard(
                        name = person.account.displayName,
                        idNumber = person.account.idNumber,
                        photoUri = person.faceUri,
                        subtitle = stringResource(roleLabel(person.role)),
                    )
                }
                HorizontalDivider()
            }

            // Said once, at the bottom, rather than by quietly leaving people
            // off every list. Somebody hunting a face needs to know the list
            // they are looking at is not everybody.
            if (withoutTrade > 0 && tradeId != null) {
                item {
                    Text(
                        text = pluralStringResource(R.plurals.crew_no_trade, withoutTrade, withoutTrade),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    open?.let { person ->
        CrewProfileSheet(
            viewModel = viewModel,
            person = person,
            trades = trades,
            onDismiss = { viewModel.openProfile(null) },
        )
    }
}

/**
 * One person, as the viewer in front of the phone is entitled to see them.
 *
 * Face, number, trade, rank, who they answer to, their tickets, and what has
 * been confirmed against them. Money appears only if the chain says this
 * viewer may be shown it, which for a safety officer is never — the role has
 * no Money lens, and that is the point of the role rather than an omission
 * from this screen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CrewProfileSheet(
    viewModel: CrewViewModel,
    person: CrewViewModel.Person,
    trades: List<TradeEntity>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tickets by viewModel.openTickets.collectAsStateWithLifecycle()
    val violations by viewModel.openViolations.collectAsStateWithLifecycle()
    val reportsTo by viewModel.openReportsTo.collectAsStateWithLifecycle()
    val mayPrice by viewModel.mayPrice.collectAsStateWithLifecycle()
    val notCorrected by viewModel.notCorrected.collectAsStateWithLifecycle()
    var correcting by remember(person.account.id) { mutableStateOf(false) }
    val locale = currentLocale()
    val languageTag = currentLanguageTag()
    val zone = ZoneId.systemDefault()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            PersonCard(
                name = person.account.displayName,
                idNumber = person.account.idNumber,
                photoUri = person.faceUri,
                subtitle = stringResource(roleLabel(person.role)),
            )

            person.membership.tradeId
                ?.let { id -> trades.firstOrNull { it.id == id } }
                ?.let { trade ->
                    DetailLine(stringResource(R.string.crew_trade), trade.names.resolve(languageTag))
                }

            DetailLine(
                stringResource(R.string.people_reports_to),
                reportsTo ?: stringResource(R.string.people_reports_to_nobody),
            )

            person.account.inductedAt?.let { at ->
                val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
                DetailLine(stringResource(R.string.crew_inducted), Formats.date(day, locale))
            }

            ContactSection(phone = person.account.phone, email = person.account.email)

            // Most people on a site never open this app. They were put on the
            // books at the gate by whoever was standing on it, with a name
            // heard once and a number read off a scrap of paper, and until
            // now there was nowhere at all to fix either.
            if (viewModel.mayCorrect(person)) {
                OutlinedButton(
                    onClick = { correcting = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text(stringResource(R.string.crew_correct)) }
            }

            // Said out loud rather than left as an absence. An officer looking
            // at a profile with no wages on it should know that is the rule
            // working, not the record being incomplete.
            if (!mayPrice) {
                Text(
                    text = stringResource(R.string.crew_no_money),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            SectionHeader(stringResource(R.string.cert_title))
            if (tickets.isEmpty()) {
                SectionPlaceholder(stringResource(R.string.cert_empty))
            }
            tickets.forEach { ticket ->
                val state = Expiry.state(ticket.expiresOn, System.currentTimeMillis())
                DetailLine(
                    label = ticket.title,
                    value = when (state) {
                        Expiry.State.EXPIRED -> stringResource(R.string.cert_expired)
                        Expiry.State.EXPIRING_SOON -> stringResource(R.string.cert_attention)
                        else -> ticket.expiresOn
                            ?.let { Formats.date(Instant.ofEpochMilli(it).atZone(zone).toLocalDate(), locale) }
                            ?: stringResource(R.string.cert_no_expiry)
                    },
                    emphasise = state == Expiry.State.EXPIRED,
                )
            }

            SectionHeader(stringResource(R.string.vio_title))
            if (violations.isEmpty()) {
                SectionPlaceholder(stringResource(R.string.crew_no_violations))
            }
            violations.forEach { violation ->
                DetailLine(
                    label = violation.description,
                    value = violation.costAmount?.let { Formats.money(it, locale) }.orEmpty(),
                )
            }

            // Only somebody above them may say what they do. A safety officer
            // reads this screen; they do not staff it.
            if (viewModel.mayName(person)) {
                SectionHeader(stringResource(R.string.crew_trade))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    trades.forEach { trade ->
                        FilterChip(
                            selected = person.membership.tradeId == trade.id,
                            onClick = { viewModel.setTradeOf(person, trade.id) },
                            label = { Text(trade.names.resolve(languageTag)) },
                        )
                    }
                }
            }
        }
    }

    if (correcting) {
        CorrectDetailsDialog(
            person = person,
            onDismiss = { correcting = false },
            onSave = { name, phone, email, idNumber ->
                viewModel.correct(person, name, phone, email, idNumber)
                correcting = false
            },
        )
    }

    notCorrected?.let { reason ->
        AlertDialog(
            onDismissRequest = viewModel::clearNotCorrected,
            text = { Text(stringResource(notCorrectedLabel(reason))) },
            confirmButton = {
                TextButton(onClick = viewModel::clearNotCorrected) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }
}

/**
 * The office correcting what the books say.
 *
 * Name, number and address, which are the three that go wrong and the three
 * that matter when somebody has to be reached. The trade is a chip row on the
 * sheet already and the role belongs on the People screen, where the chain
 * and the last-administrator rule live.
 *
 * The ID number is the exception, and deliberately: it can be filled in when
 * it is missing and not changed once it is there. It goes on the gate list
 * and on the induction record, so a quiet edit is how one person ends up
 * standing behind another person's paperwork. The screen says that out loud
 * rather than showing a field that silently does nothing.
 */
@Composable
private fun CorrectDetailsDialog(
    person: CrewViewModel.Person,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, email: String, idNumber: String) -> Unit,
) {
    var name by remember { mutableStateOf(person.account.displayName) }
    var phone by remember { mutableStateOf(person.account.phone.orEmpty()) }
    var email by remember { mutableStateOf(person.account.email.orEmpty()) }
    var idNumber by remember { mutableStateOf(person.account.idNumber.orEmpty()) }
    val idIsSet = !person.account.idNumber.isNullOrBlank()

    // The same rules the repository will apply, asked here so the button can
    // be grey for a reason the person can read rather than for none.
    val phoneOk = phone.isBlank() || Contact.blocksPhone(phone) == null
    val emailOk = Contact.blocksEmail(email) == null
    val canSave = name.isNotBlank() && phoneOk && emailOk

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crew_correct)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.acc_your_name)) },
                    isError = name.isBlank(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                ContactFields(phone, { phone = it }, email, { email = it })

                if (idIsSet) {
                    DetailLine(stringResource(R.string.acc_id_number), idNumber)
                    Text(
                        text = stringResource(R.string.crew_id_locked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedTextField(
                        value = idNumber,
                        onValueChange = { idNumber = it },
                        label = { Text(stringResource(R.string.acc_id_number)) },
                        supportingText = { Text(stringResource(R.string.acc_id_number_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, phone, email, idNumber) },
                enabled = canSave,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * The sentence for each way a correction can be refused. Exhaustive with no
 * `else`, so a reason added to the repository cannot arrive as a blank dialog.
 */
@StringRes
private fun notCorrectedLabel(reason: AccountRepository.NotCorrected): Int = when (reason) {
    AccountRepository.NotCorrected.NotPermitted -> R.string.lens_locked
    AccountRepository.NotCorrected.NoSuchPerson -> R.string.crew_no_such_person
    AccountRepository.NotCorrected.NotOnTheseBooks -> R.string.crew_not_on_books
    AccountRepository.NotCorrected.IdNumberTaken -> R.string.acc_id_taken
    AccountRepository.NotCorrected.Unknown -> R.string.crew_correct_failed
    is AccountRepository.NotCorrected.Rejected -> faultLabel(reason.fault)
}

/** The sentence for each way the details themselves are wrong. */
@StringRes
private fun faultLabel(fault: Corrections.Fault): Int = when (fault) {
    Corrections.Fault.NameIsBlank -> R.string.crew_need_name
    is Corrections.Fault.BadPhone -> phoneMessage(fault.fault)
    is Corrections.Fault.BadEmail -> R.string.acc_email_bad
    Corrections.Fault.IdNumberIsSet -> R.string.crew_id_locked
}

/**
 * Ringing somebody, which is the reason the number was asked for.
 *
 * A number collected and never shown is a field people learn to lie in. The
 * question on a site is nearly always "where is he" and the answer is nearly
 * always a phone call, so the call is a button rather than a number to copy
 * out with a glove on.
 *
 * An account made before the number was asked for has none, and says so.
 * Blank would read as a person with no phone rather than a record made before
 * the app asked.
 */
@Composable
private fun ContactSection(phone: String?, email: String?) {
    val context = LocalContext.current
    SectionHeader(stringResource(R.string.crew_contact))

    if (phone.isNullOrBlank()) {
        SectionPlaceholder(stringResource(R.string.crew_no_phone))
    } else {
        DetailLine(stringResource(R.string.acc_phone), phone)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // ACTION_DIAL, not ACTION_CALL. It puts the number in the dialler
            // and lets the person press the button themselves: no CALL_PHONE
            // permission to ask for, and no call placed by a mis-tap on a
            // screen being scrolled with a glove on.
            FilledTonalButton(
                onClick = {
                    val number = Contact.dialable(phone)
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
                    }
                },
            ) { Text(stringResource(R.string.crew_call)) }

            // Only when the number carries its own country code. See
            // Contact.international: guessing the country would open a
            // conversation with whoever holds that number somewhere else.
            Contact.international(phone)?.let { international ->
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$international")),
                            )
                        }
                    },
                ) { Text(stringResource(R.string.crew_whatsapp)) }
            }
        }
    }

    if (!email.isNullOrBlank()) {
        DetailLine(stringResource(R.string.crew_email), email)
    }
}

@Composable
private fun DetailLine(label: String, value: String, emphasise: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (emphasise) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
