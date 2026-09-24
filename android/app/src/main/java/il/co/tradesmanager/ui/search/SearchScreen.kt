package il.co.tradesmanager.ui.search

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.find.Search
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.currentLanguageTag

/**
 * The one box that looks everywhere.
 *
 * Deliberately plain. There are no filter chips, no tabs across the top and no
 * recent-searches list: somebody opening this has a word in their head and
 * wants the thing, and every control between the keyboard and the answer is a
 * decision they did not want to make. What the row came out of is written on
 * the row.
 *
 * The rows say which register they came from in words rather than with an
 * icon. A glyph for "permit" that reads as a permit to three languages and
 * every trade does not exist, and a wrong guess at one is worse than a word.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpen: (Search.Hit) -> Unit,
) {
    val viewModel: SearchViewModel = viewModel(
        factory = ViewModelFactory(container) { SearchViewModel(it) },
    )
    val query by viewModel.query.collectAsStateWithLifecycle()
    val answer by viewModel.results.collectAsStateWithLifecycle()
    val languageTag = currentLanguageTag()

    // Which language an item's name is shown in. Never which languages are
    // matched: an item is matched on every name it has.
    LaunchedEffect(languageTag) { viewModel.setLanguage(languageTag) }

    // The keyboard is the point of this screen, so it opens with it.
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { field.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_search)) },
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
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                label = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .focusRequester(field),
            )

            when {
                query.isBlank() -> Note(stringResource(R.string.search_start))

                // Folded, so that a single letter with a vowel point under it
                // is still a single letter and still says so.
                Search.terms(query).isEmpty() -> Note(stringResource(R.string.search_short))

                // Only once the answer has caught up with what is typed does
                // an empty list mean nothing matched rather than not yet.
                answer.forQuery == query && answer.hits.isEmpty() ->
                    Note(stringResource(R.string.search_nothing, query.trim()))

                else -> Results(
                    hits = answer.hits,
                    onOpen = onOpen,
                    // weight, not fillMaxSize: the list gets what is left
                    // below the box, and fillMaxSize in a Column asks for
                    // the whole height and hangs off the bottom.
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Results(
    hits: List<Search.Hit>,
    onOpen: (Search.Hit) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxWidth()) {
        items(hits, key = { hit -> "${hit.kind}:${hit.id}" }) { hit ->
            Result(hit = hit, onOpen = onOpen)
            HorizontalDivider()
        }
        // An empty-ish list on a full database is otherwise read as the app
        // having lost something, when it is the role grid doing its job. Said
        // once, at the bottom, rather than over the results — and only when
        // there are results, so that the moment between the last letter and
        // the answer is blank rather than a footnote on nothing.
        if (hits.isNotEmpty()) {
            item { Note(stringResource(R.string.search_only_yours)) }
        }
    }
}

@Composable
private fun Result(hit: Search.Hit, onOpen: (Search.Hit) -> Unit) {
    // Typed explicitly, which is what makes the null legal: Kotlin will not
    // infer @Composable through a let and a null check. Same shape as
    // MoneyScreen's CostRow, for the same reason.
    val supporting: (@Composable () -> Unit)? =
        hit.detail.takeIf { it.isNotBlank() }?.let { detail ->
            { Text(text = detail, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }

    ListItem(
        overlineContent = { Text(stringResource(kindLabel(hit.kind))) },
        headlineContent = { Text(hit.title) },
        supportingContent = supporting,
        modifier = Modifier.clickable { onOpen(hit) },
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@StringRes
private fun kindLabel(kind: Search.Kind): Int = when (kind) {
    Search.Kind.JOB -> R.string.search_kind_job
    Search.Kind.PERSON -> R.string.search_kind_person
    Search.Kind.ITEM -> R.string.search_kind_item
    Search.Kind.ORDER -> R.string.search_kind_order
    Search.Kind.PERMIT -> R.string.search_kind_permit
    Search.Kind.SNAG -> R.string.search_kind_snag
    Search.Kind.PLANT -> R.string.search_kind_plant
    Search.Kind.DRAWING -> R.string.search_kind_drawing
    Search.Kind.QUERY -> R.string.search_kind_query
    Search.Kind.VISITOR -> R.string.search_kind_visitor
    Search.Kind.INSPECTION -> R.string.search_kind_inspection
}
