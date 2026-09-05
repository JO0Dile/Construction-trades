package il.co.tradesmanager.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.currentLocale
import java.time.LocalDate
import java.time.LocalTime

/**
 * The screen the day starts on.
 *
 * Ordered by what someone standing in a van at 06:50 needs: what am I doing
 * today, what is about to stop me, and which jobs are open. Every tile is a
 * way in, because a dashboard that only reports is a dashboard people stop
 * opening.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenInventory: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenSafety: () -> Unit,
    onOpenPeople: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(factory = ViewModelFactory(container) { HomeViewModel(it) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val portfolio by viewModel.portfolio.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val locale = currentLocale()

    // Each tile belongs to a lens, so the dashboard is different work for
    // different people rather than the same wall of numbers with some greyed
    // out. A foreman never sees the book; the finance clerk never sees stock.
    val signedIn = session as? SessionRepository.State.SignedIn
    val seesStuff = signedIn?.canRead(Lens.STUFF) != false
    val seesPlan = signedIn?.canRead(Lens.PLAN) != false
    val seesEvidence = signedIn?.canRead(Lens.EVIDENCE) != false
    val seesMoney = signedIn?.canRead(Lens.MONEY) != false
    val seesPeople = signedIn?.canRead(Lens.PEOPLE) != false

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name))
                        Text(
                            text = Formats.date(LocalDate.now(), locale),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.set_title))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        ) {

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (seesStuff) {
                        StatTile(
                            value = state.lowStock.size.toString(),
                            label = stringResource(R.string.home_low_stock),
                            icon = Icons.Filled.Inventory2,
                            accent = if (state.lowStock.isEmpty()) Neutral else Warning,
                            onClick = onOpenInventory,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (seesPlan) {
                        StatTile(
                            value = state.activeProjects.size.toString(),
                            label = stringResource(R.string.home_active_projects),
                            icon = Icons.Filled.Work,
                            accent = Neutral,
                            onClick = onOpenProjects,
                            modifier = Modifier.weight(1f),
                        )
                        // Only once something is actually late: a permanent
                        // zero teaches people to stop reading the row.
                        if (state.overdue.isNotEmpty()) {
                            StatTile(
                                value = state.overdue.size.toString(),
                                label = stringResource(R.string.home_overdue),
                                icon = Icons.Filled.Warning,
                                accent = Danger,
                                onClick = onOpenProjects,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (seesEvidence) {
                        StatTile(
                            value = state.openChecklists.toString(),
                            label = stringResource(R.string.home_safety_due),
                            icon = Icons.Filled.HealthAndSafety,
                            accent = if (state.openChecklists == 0) Neutral else Danger,
                            onClick = onOpenSafety,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (seesMoney || seesPeople) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (seesMoney) {
                            StatTile(
                                value = Formats.moneyRounded(portfolio.money.outstanding, locale),
                                label = stringResource(R.string.home_owed),
                                icon = Icons.Filled.RequestQuote,
                                accent = if (portfolio.money.outstanding > 0.0) Warning else Neutral,
                                onClick = onOpenProjects,
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                value = Formats.moneyRounded(portfolio.money.margin, locale),
                                label = stringResource(R.string.home_margin) + " · " +
                                    stringResource(R.string.home_book),
                                icon = Icons.Filled.TrendingUp,
                                accent = if (portfolio.money.margin < 0.0) Danger else Neutral,
                                onClick = onOpenProjects,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (seesPeople && portfolio.ticketsNeedingAttention.isNotEmpty()) {
                            StatTile(
                                value = portfolio.ticketsNeedingAttention.size.toString(),
                                label = stringResource(R.string.home_tickets),
                                icon = Icons.Filled.Badge,
                                accent = if (viewModel.anyExpired(portfolio.ticketsNeedingAttention)) {
                                    Danger
                                } else {
                                    Warning
                                },
                                onClick = onOpenPeople,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.home_today)) }

            if (state.today.isEmpty()) {
                item {
                    Card(
                        onClick = onOpenSchedule,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.home_no_tasks_today),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            } else {
                items(state.today, key = { it.id }) { block ->
                    Card(
                        onClick = onOpenSchedule,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // The time is what the eye goes to first on a plan
                            // for the day, so it leads and it is monospaced.
                            Text(
                                text = Formats.time(
                                    LocalTime.ofSecondOfDay(block.startMinute * 60L),
                                    locale,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Column(Modifier.padding(start = 14.dp)) {
                                Text(block.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = Formats.time(
                                        LocalTime.ofSecondOfDay(block.endMinute * 60L),
                                        locale,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            if (state.lowStock.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.home_low_stock)) }
                item {
                    Card(
                        onClick = onOpenInventory,
                        colors = CardDefaults.cardColors(containerColor = Warning.copy(alpha = 0.10f)),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.low_stock_count,
                                state.lowStock.size,
                                state.lowStock.size,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            if (state.activeProjects.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.home_active_projects)) }
                items(state.activeProjects, key = { it.id }) { project ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenProjects)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Neutral, RoundedCornerShape(4.dp)),
                        )
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(project.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = listOfNotNull(project.kindLabel, project.city)
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

private val Warning = Color(0xFFB9770E)
private val Danger = Color(0xFFC0392B)
private val Neutral = Color(0xFF2E86C1)

/**
 * A number, what it counts, and a way in. Deliberately large: this is read at
 * arm's length, often through a scratched screen protector.
 */
@Composable
private fun StatTile(
    value: String,
    label: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.10f)),
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Text(
                text = value,
                // A count fits at headline size; "₪257,240" does not, and a
                // clipped number on a dashboard is worse than a smaller one.
                style = if (value.length <= 4) {
                    MaterialTheme.typography.headlineMedium
                } else {
                    MaterialTheme.typography.titleMedium
                },
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}
