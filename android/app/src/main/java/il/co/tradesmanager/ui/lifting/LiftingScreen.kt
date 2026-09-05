package il.co.tradesmanager.ui.lifting

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
import androidx.compose.material3.Switch
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
import il.co.tradesmanager.core.people.Expiry
import il.co.tradesmanager.core.safety.Lifting
import il.co.tradesmanager.data.local.entity.CertificationEntity
import il.co.tradesmanager.data.local.entity.LiftCrewEntity
import il.co.tradesmanager.data.local.entity.LiftPlanEntity
import il.co.tradesmanager.data.repository.LiftingRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.DetailRow
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.components.rememberNow
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Lift plans.
 *
 * Everything that stops a lift is known before the load leaves the ground and
 * forgotten while it is in the air. A crane at ninety-six per cent of its chart
 * at thirty metres is a different machine from the same crane at forty per
 * cent, and nobody recalculates that with a load swinging. A slinger whose
 * ticket ran out last week is exactly as good at slinging as he was a fortnight
 * ago and exactly as illegal to use.
 *
 * So the top of the plan is a verdict and the reasons for it — all of them at
 * once, not the first one, because a plan that fails four checks and reports
 * one is a plan somebody fixes four times.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiftingScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: LiftingViewModel = viewModel(
        factory = ViewModelFactory(container) { LiftingViewModel(it, projectId) },
    )
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    val open by viewModel.open.collectAsStateWithLifecycle()
    val crew by viewModel.crew.collectAsStateWithLifecycle()
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val zone = ZoneId.systemDefault()
    // Tickets and certificates are counted in days, so an hour is often enough.
    val now = rememberNow(intervalMillis = 60L * 60L * 1000L)

    val signedIn = session as? SessionRepository.State.SignedIn
    val canEdit = signedIn?.canWrite(Lens.EVIDENCE) != false
    // Approving a lift is not the same as writing the plan. It is the thing a
    // supervisor does, and it is the last blocker to clear.
    val canApprove = signedIn == null || signedIn.role.canManageJobs
    var adding by remember { mutableStateOf(false) }
    var pickingRole by remember { mutableStateOf<LiftingRepository.Role?>(null) }

    val current = open

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.reference ?: stringResource(R.string.lift_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = { if (current != null) viewModel.openPlan(null) else onBack() },
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
                FloatingActionButton(onClick = { adding = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.lift_new))
                }
            }
        },
    ) { padding ->
        if (current == null) {
            if (plans.isEmpty()) {
                EmptyState(
                    message = stringResource(R.string.lift_empty),
                    hint = stringResource(R.string.lift_new),
                    modifier = Modifier.padding(padding),
                )
                return@Scaffold
            }
            LazyColumn(Modifier.padding(padding)) {
                items(plans, key = { it.id }) { plan ->
                    PlanRow(
                        plan = plan,
                        locale = locale,
                        zone = zone,
                        onOpen = { viewModel.openPlan(plan.id) },
                    )
                }
            }
            return@Scaffold
        }

        val blockers = LiftingViewModel.blockersFor(current, crew, now)
        val gross = Lifting.grossWeightKg(current.loadWeightKg, current.riggingWeightKg)
        val share = Lifting.utilisation(gross, current.capacityAtRadiusKg)

        LazyColumn(Modifier.padding(padding)) {
            item { Verdict(blockers = blockers, share = share, locale = locale) }

            item {
                Column {
                    DetailRow(stringResource(R.string.lift_what), current.description)
                    gross?.let {
                        DetailRow(
                            stringResource(R.string.lift_gross),
                            Formats.quantity(it, locale) + " kg",
                        )
                    }
                    current.capacityAtRadiusKg?.let {
                        DetailRow(
                            stringResource(R.string.lift_capacity),
                            Formats.quantity(it, locale) + " kg",
                        )
                    }
                    current.applianceName?.let {
                        DetailRow(stringResource(R.string.lift_appliance), it)
                    }
                    current.approvedByName?.let {
                        DetailRow(stringResource(R.string.lift_approved_by), it)
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.lift_crew)) }
            items(LiftingRepository.Role.entries, key = { it.name }) { role ->
                CrewRow(
                    role = role,
                    member = crew.firstOrNull { it.role == role.name },
                    now = now,
                    locale = locale,
                    zone = zone,
                    onPick = if (canEdit && current.completedAt == null) {
                        { pickingRole = role }
                    } else {
                        null
                    },
                )
            }

            if (canEdit && current.completedAt == null) {
                item {
                    NumbersForm(
                        plan = current,
                        locale = locale,
                        onSave = viewModel::save,
                    )
                }
            }

            if (current.completedAt == null) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (canApprove && current.approvedAt == null) {
                            Button(
                                onClick = { viewModel.approve() },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.lift_approve))
                            }
                        }
                        if (canEdit) {
                            OutlinedButton(
                                // The one place the gate bites: the button that
                                // says the load went up will not move while
                                // anything is wrong with the plan.
                                enabled = Lifting.mayLift(blockers),
                                onClick = { viewModel.complete() },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.lift_complete))
                            }
                        }
                    }
                }
            } else {
                item {
                    Text(
                        stringResource(R.string.lift_completed),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    if (adding) {
        AddPlanDialog(
            onDismiss = { adding = false },
            onAdd = {
                adding = false
                viewModel.createPlan(it)
            },
        )
    }

    pickingRole?.let { role ->
        CrewPickerDialog(
            role = role,
            candidates = candidates,
            now = now,
            locale = locale,
            zone = zone,
            onDismiss = { pickingRole = null },
            onClear = {
                pickingRole = null
                viewModel.clearCrew(role)
            },
            onPick = { accountId, name, certification ->
                pickingRole = null
                viewModel.setCrew(role, accountId, name, certification)
            },
        )
    }
}

/**
 * The verdict, and every reason for it.
 *
 * All of them at once. A plan that fails four checks and reports one is a plan
 * somebody fixes four times, walking back to the crane in between.
 */
@Composable
private fun Verdict(blockers: Set<Lifting.Blocker>, share: Double?, locale: Locale) {
    val clear = Lifting.mayLift(blockers)
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = if (clear) scheme.surfaceVariant else scheme.errorContainer,
        contentColor = if (clear) scheme.onSurfaceVariant else scheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(if (clear) R.string.lift_may_go else R.string.lift_blocked),
                style = MaterialTheme.typography.titleMedium,
            )
            share?.let {
                Text(
                    Formats.percent(it, locale) + " " + stringResource(R.string.lift_utilisation),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            blockers.forEach { Text("· " + stringResource(blockerLabel(it))) }
            if (Lifting.isHeavy(share)) {
                Text(
                    stringResource(R.string.lift_heavy),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PlanRow(
    plan: LiftPlanEntity,
    locale: Locale,
    zone: ZoneId,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(plan.reference, style = MaterialTheme.typography.labelMedium)
            Text(plan.description, style = MaterialTheme.typography.titleMedium)
            Text(
                text = listOfNotNull(
                    plan.applianceName,
                    plan.plannedFor?.let { dateOf(it, zone, locale) },
                    if (plan.completedAt != null) stringResource(R.string.lift_completed) else null,
                ).joinToString(" · ").ifEmpty { stringResource(R.string.lift_what) },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun CrewRow(
    role: LiftingRepository.Role,
    member: LiftCrewEntity?,
    now: Long,
    locale: Locale,
    zone: ZoneId,
    onPick: (() -> Unit)?,
) {
    val state = member?.let { Expiry.state(it.certificateExpiresOn, now) }
    val lapsed = state == Expiry.State.EXPIRED
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = if (lapsed) scheme.errorContainer else scheme.surface,
        contentColor = if (lapsed) scheme.onErrorContainer else scheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onPick != null) Modifier.clickable(onClick = onPick) else Modifier),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(roleLabel(role)), style = MaterialTheme.typography.labelMedium)
            Text(
                member?.name ?: stringResource(R.string.lift_crew_none),
                style = MaterialTheme.typography.titleMedium,
            )
            if (member != null) {
                Text(
                    text = listOfNotNull(
                        member.certificateReference,
                        member.certificateExpiresOn
                            ?.let { dateOf(it, zone, locale) }
                            ?: stringResource(R.string.lift_crew_no_expiry),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    HorizontalDivider()
}

/**
 * The numbers, saved together.
 *
 * One Save rather than a field that writes on every keystroke, because saving
 * clears the approval: a supervisor signed off a lift of four tonnes at twenty
 * metres, and a plan that quietly keeps the signature while the numbers change
 * underneath it is a plan nobody actually approved.
 */
@Composable
private fun NumbersForm(
    plan: LiftPlanEntity,
    locale: Locale,
    onSave: (LiftPlanEntity) -> Unit,
) {
    var appliance by remember(plan.id) { mutableStateOf(plan.applianceName.orEmpty()) }
    var load by remember(plan.id) { mutableStateOf(plan.loadWeightKg.asField()) }
    var rigging by remember(plan.id) { mutableStateOf(plan.riggingWeightKg.asField()) }
    var radius by remember(plan.id) { mutableStateOf(plan.radiusMetres.asField()) }
    var capacity by remember(plan.id) { mutableStateOf(plan.capacityAtRadiusKg.asField()) }
    var windLimit by remember(plan.id) { mutableStateOf(plan.windLimitKmh.toString()) }
    var wind by remember(plan.id) { mutableStateOf(plan.windSpeedKmh.asField()) }
    var certRequired by remember(plan.id) {
        mutableStateOf(plan.applianceCertificateRequired)
    }
    var certExpiry by remember(plan.id) {
        mutableStateOf(
            plan.applianceCertificateExpiresOn
                ?.let { dateOf(it, ZoneId.systemDefault(), locale) }
                .orEmpty(),
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = appliance,
            onValueChange = { appliance = it },
            label = { Text(stringResource(R.string.lift_appliance)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        NumberField(load, { load = it }, R.string.lift_load_weight)
        NumberField(rigging, { rigging = it }, R.string.lift_rigging_weight)
        Text(
            stringResource(R.string.lift_rigging_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NumberField(radius, { radius = it }, R.string.lift_radius)
        NumberField(capacity, { capacity = it }, R.string.lift_capacity)
        Text(
            stringResource(R.string.lift_capacity_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NumberField(windLimit, { windLimit = it }, R.string.lift_wind_limit)
        NumberField(wind, { wind = it }, R.string.lift_wind)
        if (wind.isBlank()) {
            Text(
                stringResource(R.string.lift_wind_unmeasured),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.lift_appliance_cert_needed),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = certRequired, onCheckedChange = { certRequired = it })
        }
        if (certRequired) {
            OutlinedTextField(
                value = certExpiry,
                onValueChange = { certExpiry = it },
                label = { Text(stringResource(R.string.lift_appliance_cert)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(
            onClick = {
                onSave(
                    plan.copy(
                        applianceName = appliance.trim().takeIf { it.isNotEmpty() },
                        loadWeightKg = load.toDoubleOrNull(),
                        riggingWeightKg = rigging.toDoubleOrNull(),
                        radiusMetres = radius.toDoubleOrNull(),
                        capacityAtRadiusKg = capacity.toDoubleOrNull(),
                        windLimitKmh = windLimit.toDoubleOrNull()
                            ?: Lifting.DEFAULT_WIND_LIMIT_KMH,
                        windSpeedKmh = wind.toDoubleOrNull(),
                        applianceCertificateRequired = certRequired,
                        applianceCertificateExpiresOn = Formats.parseDate(certExpiry)
                            ?.atStartOfDay(ZoneId.systemDefault())
                            ?.toInstant()
                            ?.toEpochMilli(),
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_save))
        }
    }
    HorizontalDivider()
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, labelRes: Int) {
    OutlinedTextField(
        value = value,
        // Digits and a full stop only. Double.toString and toDoubleOrNull both
        // speak the same dot-decimal regardless of locale, so a phone set to a
        // comma decimal cannot write a number the app then fails to read back.
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }) },
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Locale-independent, so what is typed is what is stored. */
private fun Double?.asField(): String = this?.toString().orEmpty()

private fun dateOf(millis: Long, zone: ZoneId, locale: Locale): String =
    Formats.date(Instant.ofEpochMilli(millis).atZone(zone).toLocalDate(), locale)

internal fun roleLabel(role: LiftingRepository.Role): Int = when (role) {
    LiftingRepository.Role.OPERATOR -> R.string.lift_role_operator
    LiftingRepository.Role.SLINGER -> R.string.lift_role_slinger
    LiftingRepository.Role.BANKSMAN -> R.string.lift_role_banksman
}

internal fun blockerLabel(blocker: Lifting.Blocker): Int = when (blocker) {
    Lifting.Blocker.NO_WEIGHT -> R.string.lift_block_weight
    Lifting.Blocker.NO_CAPACITY -> R.string.lift_block_capacity
    Lifting.Blocker.OVERLOADED -> R.string.lift_block_overloaded
    Lifting.Blocker.NO_OPERATOR -> R.string.lift_block_operator
    Lifting.Blocker.NO_SLINGER -> R.string.lift_block_slinger
    Lifting.Blocker.NO_BANKSMAN -> R.string.lift_block_banksman
    Lifting.Blocker.TICKET_EXPIRED -> R.string.lift_block_ticket
    Lifting.Blocker.APPLIANCE_UNCERTIFIED -> R.string.lift_block_appliance
    Lifting.Blocker.TOO_WINDY -> R.string.lift_block_wind
    Lifting.Blocker.NOT_APPROVED -> R.string.lift_block_approval
}

@Composable
private fun AddPlanDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lift_new)) },
        text = {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.lift_what)) },
                supportingText = { Text(stringResource(R.string.lift_what_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = description.isNotBlank(),
                onClick = { onAdd(description.trim()) },
            ) {
                Text(stringResource(R.string.acc_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Choosing who does a job, and on which ticket.
 *
 * A person holds several certificates and only one of them is the ticket for
 * this role. Picking the person and then the ticket is a click more than
 * picking a name, and it is the click that makes the lapsed-ticket check mean
 * anything: blocking a lift because somebody's first-aid card ran out would be
 * wrong, and blocking it because their slinging ticket ran out is the point.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CrewPickerDialog(
    role: LiftingRepository.Role,
    candidates: List<LiftingViewModel.Candidate>,
    now: Long,
    locale: Locale,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onPick: (accountId: String?, name: String, certification: CertificationEntity?) -> Unit,
) {
    var chosen by remember { mutableStateOf<LiftingViewModel.Candidate?>(null) }
    var ticket by remember { mutableStateOf<CertificationEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(roleLabel(role))) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.lift_crew_name),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    candidates.forEach { candidate ->
                        FilterChip(
                            selected = chosen?.account?.id == candidate.account.id,
                            onClick = {
                                chosen = candidate
                                ticket = null
                            },
                            label = { Text(candidate.account.displayName) },
                        )
                    }
                }
                chosen?.let { candidate ->
                    Text(
                        stringResource(R.string.lift_crew_ticket),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (candidate.certifications.isEmpty()) {
                        Text(
                            stringResource(R.string.lift_crew_no_expiry),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        candidate.certifications.forEach { certification ->
                            val lapsed =
                                Expiry.state(certification.expiresOn, now) == Expiry.State.EXPIRED
                            FilterChip(
                                selected = ticket?.id == certification.id,
                                onClick = { ticket = certification },
                                label = {
                                    Text(
                                        certification.title +
                                            (
                                                certification.expiresOn
                                                    ?.let { " · " + dateOf(it, zone, locale) }
                                                    .orEmpty()
                                                ),
                                    )
                                },
                                // Shown, not hidden. A lapsed ticket is
                                // pickable so the plan can say out loud why
                                // the lift is refused, rather than leaving
                                // somebody to wonder where the name went.
                                leadingIcon = if (lapsed) {
                                    { Text("!") }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = chosen != null,
                onClick = {
                    val candidate = chosen ?: return@TextButton
                    onPick(candidate.account.id, candidate.account.displayName, ticket)
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.lift_crew_none)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
