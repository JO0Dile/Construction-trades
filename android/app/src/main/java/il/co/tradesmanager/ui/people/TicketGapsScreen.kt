package il.co.tradesmanager.ui.people

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.resolve
import il.co.tradesmanager.core.people.TicketGaps
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.currentLanguageTag

/**
 * The tickets people's trades usually need, and who does not hold them in date.
 *
 * Grouped by trade, because that is how the question is asked at the gate in
 * the morning: are all the electricians licensed. The screen says "usually",
 * because the job decides what it calls for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketGapsScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: TicketGapsViewModel =
        viewModel(factory = ViewModelFactory(container) { TicketGapsViewModel(it) })
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val checked by viewModel.checked.collectAsStateWithLifecycle()
    val trades by viewModel.trades.collectAsStateWithLifecycle()
    val kinds by viewModel.kinds.collectAsStateWithLifecycle()
    val languageTag = currentLanguageTag()
    val tradeNames = trades.associate { it.id to it.names.resolve(languageTag) }
    val kindNames = kinds.associate { it.id to it.names.resolve(languageTag) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gaps_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (rows.isEmpty()) {
            EmptyState(
                message = stringResource(if (checked == 0) R.string.gaps_none_checked else R.string.gaps_none),
                hint = stringResource(R.string.gaps_blurb),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding)) {
            item {
                Text(
                    stringResource(R.string.gaps_blurb),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            rows.groupBy { it.tradeId }.forEach { (tradeId, inTrade) ->
                item(key = "trade-$tradeId") { SectionHeader(tradeNames[tradeId] ?: tradeId) }
                items(inTrade, key = { it.account.id }) { row ->
                    ListItem(
                        headlineContent = { Text(row.account.displayName) },
                        supportingContent = {
                            Text(
                                // map, not joinToString: map is inline, so the
                                // strings can be looked up inside it.
                                row.gaps.map { gap ->
                                    val kind = kindNames[gap.kindId] ?: gap.kindId
                                    when (gap.state) {
                                        TicketGaps.State.MISSING -> stringResource(R.string.gaps_missing, kind)
                                        TicketGaps.State.EXPIRED -> stringResource(R.string.gaps_expired, kind)
                                    }
                                }.joinToString("\n"),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
