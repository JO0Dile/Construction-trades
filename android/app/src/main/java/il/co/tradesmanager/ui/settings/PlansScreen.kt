package il.co.tradesmanager.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.money.Plans
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.currentLocale

/**
 * What the plans are, what each one costs, and which one this firm is on.
 *
 * The safety card comes first and stays first. It is the part of the pricing
 * that is a promise rather than an offer — admission, the induction, permits,
 * incidents, clocking in, and reading or exporting anything already recorded
 * are free on every plan for ever, including after a subscription runs out.
 * A safety officer deciding whether to stand behind this app reads that
 * paragraph, and putting it under the prices would say the opposite of what
 * it says.
 *
 * Nothing here sells anything, and the page says so rather than showing three
 * buttons that do nothing. Until a subscription can be checked against
 * something other than the phone it was bought on, a limit is only a limit for
 * people who choose not to lift it, and a paywall like that teaches its own
 * customers that the plan is free.
 *
 * The count of people is real and is the one figure that will still be true
 * on the day this starts charging: it is what [Plans.seats] is counted
 * against.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: PlansViewModel = viewModel(
        factory = ViewModelFactory(container) { PlansViewModel(it) },
    )
    val onTheBooks by viewModel.onTheBooks.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val entitlement = viewModel.entitlement

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.set_plans)) },
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
                Text(
                    text = stringResource(R.string.plans_lead),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.plans_safety_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(R.string.plans_safety_body),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            // Where the firm stands today: the plan, and the number the seats
            // are counted against.
            item {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.plans_current),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(planName(entitlement.effective)),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.plans_on_the_books),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = Formats.quantity(onTheBooks.toDouble(), locale),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }

            item { HorizontalDivider() }

            items(PLANS.size) { index ->
                val plan = PLANS[index]
                PlanCard(plan = plan, current = plan.plan == entitlement.effective)
            }

            item {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.plans_year_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.plans_vat),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // Said plainly rather than left for somebody to discover by
            // pressing a button that does nothing.
            item {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                    Text(
                        text = stringResource(R.string.plans_not_yet_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.plans_not_yet_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanCard(plan: PlanCopy, current: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = if (current) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(plan.name),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(plan.price),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Text(
                text = stringResource(plan.seats),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = stringResource(plan.forWhom),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** One plan, as it reads on the page. */
private data class PlanCopy(
    val plan: Plans.Plan,
    @StringRes val name: Int,
    @StringRes val price: Int,
    @StringRes val seats: Int,
    @StringRes val forWhom: Int,
)

private val PLANS: List<PlanCopy> = listOf(
    PlanCopy(
        plan = Plans.Plan.FREE,
        name = R.string.plans_free,
        price = R.string.plans_free_price,
        seats = R.string.plans_seats_free,
        forWhom = R.string.plans_free_for,
    ),
    PlanCopy(
        plan = Plans.Plan.PRO,
        name = R.string.plans_pro,
        price = R.string.plans_pro_price,
        seats = R.string.plans_seats_pro,
        forWhom = R.string.plans_pro_for,
    ),
    PlanCopy(
        plan = Plans.Plan.SITE,
        name = R.string.plans_site,
        price = R.string.plans_site_price,
        seats = R.string.plans_seats_site,
        forWhom = R.string.plans_site_for,
    ),
)

@StringRes
private fun planName(plan: Plans.Plan): Int = when (plan) {
    Plans.Plan.FREE -> R.string.plans_free
    Plans.Plan.PRO -> R.string.plans_pro
    Plans.Plan.SITE -> R.string.plans_site
}
