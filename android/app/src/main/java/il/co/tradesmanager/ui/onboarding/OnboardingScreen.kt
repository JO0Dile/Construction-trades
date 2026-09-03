package il.co.tradesmanager.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.AppLanguages
import il.co.tradesmanager.core.i18n.LocaleController
import il.co.tradesmanager.core.i18n.resolve
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.LoadingState
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.currentLanguageTag

@Composable
fun OnboardingScreen(container: AppContainer, onDone: () -> Unit) {
    val viewModel: OnboardingViewModel = viewModel(
        factory = ViewModelFactory(container) { OnboardingViewModel(it) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val trades by viewModel.trades.collectAsStateWithLifecycle()
    val languageTag = currentLanguageTag()
    val context = LocalContext.current
    // Read once per configuration: the list comes from locales_config.xml, so
    // it grows when a translation is added and never when code changes.
    val languages = remember(languageTag) { AppLanguages.supported(context) }

    if (state.isWorking) {
        LoadingState(stringResource(R.string.onboarding_seeding))
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.onboarding_welcome_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.onboarding_welcome_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        item { SectionHeader(stringResource(R.string.onboarding_language_title)) }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                languages.forEach { language ->
                    FilterChip(
                        selected = languageTag.startsWith(language.tag),
                        onClick = { viewModel.chooseLanguage(language.tag) },
                        label = { Text(language.endonym) },
                    )
                }
            }
        }

        item { SectionHeader(stringResource(R.string.onboarding_trade_title)) }
        item {
            Text(
                text = stringResource(R.string.onboarding_trade_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        items(trades, key = { it.id }) { trade ->
            val selected = trade.id in state.selectedTrades
            Card(
                onClick = { viewModel.toggleTrade(trade.id) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = trade.names.resolve(languageTag),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Checkbox(checked = selected, onCheckedChange = { viewModel.toggleTrade(trade.id) })
                }
            }
        }

        item {
            Column(Modifier.padding(16.dp)) {
                if (state.selectedTrades.isEmpty()) {
                    Text(
                        text = stringResource(R.string.onboarding_select_one),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Button(
                    onClick = { viewModel.finish(onDone) },
                    enabled = state.selectedTrades.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_continue))
                }
            }
        }
    }
}
