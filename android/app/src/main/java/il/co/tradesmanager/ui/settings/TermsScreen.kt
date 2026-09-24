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
 * What the app is, what it is not, and who is answerable for what.
 *
 * `set_terms` was written and translated into all three languages and no
 * screen showed it — the same fault the privacy notice had, found the same
 * way. It was left that way deliberately for a while, on the grounds that
 * terms are the half of this which is genuinely a legal document rather than
 * a description of behaviour. Two things changed that. Subscriptions mean
 * Play asks for terms rather than suggesting them. And a document that exists
 * as a draft in a folder protects nobody: the disclaimer that matters here —
 * that a checklist is not a safe job and a recorded contractor number is not
 * a licence — only does its work if somebody can read it.
 *
 * So it says plainly what is true, and it says on its own last section that it
 * has not been through a lawyer and should before the app is sold. Better an
 * honest page that admits what it is than a confident one nobody checked.
 *
 * Same shape as [PrivacyScreen], and the same reason: the sections come from
 * the shared string catalogue, so this screen and the published page at
 * docs/TERMS.md are generated from one source and cannot drift apart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.set_terms)) },
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
                    text = stringResource(R.string.terms_lead),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(SECTIONS.size) { index ->
                val (title, body) = SECTIONS[index]
                TermsSection(title = title, body = body)
            }
        }
    }
}

@Composable
private fun TermsSection(@StringRes title: Int, @StringRes body: Int) {
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
 * In the order somebody reads them, which is not the order a lawyer would
 * write them: what it is, then the two disclaimers that actually matter on a
 * site, then money, then the rest.
 */
private val SECTIONS: List<Pair<Int, Int>> = listOf(
    R.string.terms_what_title to R.string.terms_what_body,
    R.string.terms_not_title to R.string.terms_not_body,
    R.string.terms_licence_title to R.string.terms_licence_body,
    R.string.terms_money_title to R.string.terms_money_body,
    R.string.terms_data_title to R.string.terms_data_body,
    R.string.terms_availability_title to R.string.terms_availability_body,
    R.string.terms_liability_title to R.string.terms_liability_body,
    R.string.terms_law_title to R.string.terms_law_body,
    R.string.terms_review_title to R.string.terms_review_body,
)
