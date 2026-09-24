package il.co.tradesmanager.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import il.co.tradesmanager.R

/**
 * What the app does with what you put into it.
 *
 * Both stores require this and the app did not have it: `set_privacy` was
 * written and translated into all three languages and no screen showed it,
 * which is the same fault as everything else found this week.
 *
 * Written as what is true rather than as a template. The honest version is
 * unusually short, because the app has no server: there is no collection to
 * disclose, no third party to name and no retention period to state. Saying
 * that plainly is worth more than a page of clauses covering things that do
 * not happen — and the day a server exists, this is the page that has to
 * change before it does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.set_privacy)) },
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
                    text = stringResource(R.string.priv_lead),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(SECTIONS.size) { index ->
                val (title, body) = SECTIONS[index]
                Section(title = title, body = body)
            }
        }
    }
}

@Composable
private fun Section(@StringRes title: Int, @StringRes body: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        Text(
            text = stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * In the order somebody asks them.
 *
 * Where is my work, what leaves the phone, what are you not doing, why did
 * you ask for the camera, who else can see it, how do I get rid of it, and
 * what happens when this changes.
 */
private val SECTIONS: List<Pair<Int, Int>> = listOf(
    R.string.priv_where_title to R.string.priv_where_body,
    R.string.priv_network_title to R.string.priv_network_body,
    R.string.priv_none_title to R.string.priv_none_body,
    R.string.priv_perm_title to R.string.priv_perm_body,
    R.string.priv_crew_title to R.string.priv_crew_body,
    R.string.priv_delete_title to R.string.priv_delete_body,
    R.string.priv_future_title to R.string.priv_future_body,
)
