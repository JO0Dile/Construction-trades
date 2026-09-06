package il.co.tradesmanager.ui.handover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.currentLanguageTag
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.export.ExportDocument
import il.co.tradesmanager.ui.export.Exporter
import il.co.tradesmanager.ui.export.handoverItemLabel
import java.time.LocalDate

/**
 * What is still open on a job, and the pack that goes out.
 *
 * Nine registers have been recording evidence, and until now every one of them
 * could only be read inside the app. Evidence that cannot leave is evidence of
 * nothing — the same argument the photograph watermarks are built on.
 *
 * The screen does not refuse to produce a pack while things are outstanding. An
 * interim pack is a real thing. What it does is say plainly which kind this one
 * is, and put that on the first row of the document, so a pack that gets
 * skimmed rather than read still says whether the job was finished when it was
 * printed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandoverScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: HandoverViewModel = viewModel(
        factory = ViewModelFactory(container) { HandoverViewModel(it, projectId) },
    )
    val project by viewModel.project.collectAsStateWithLifecycle()
    val readiness by viewModel.readiness.collectAsStateWithLifecycle()
    val producedBy by viewModel.producedBy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locale = currentLocale()
    val languageTag = currentLanguageTag()
    val layoutDirection = LocalLayoutDirection.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hv_title)) },
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
                val complete = readiness.isComplete
                val scheme = MaterialTheme.colorScheme
                Surface(
                    color = if (complete) scheme.surfaceVariant else scheme.tertiaryContainer,
                    contentColor = if (complete) {
                        scheme.onSurfaceVariant
                    } else {
                        scheme.onTertiaryContainer
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            stringResource(
                                if (complete) R.string.hv_complete else R.string.hv_interim,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(
                                if (complete) {
                                    R.string.hv_complete_hint
                                } else {
                                    R.string.hv_interim_hint
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                HorizontalDivider()
            }

            items(readiness.outstanding, key = { it.item.name }) { outstanding ->
                ListItem(
                    headlineContent = { Text(stringResource(handoverItemLabel(outstanding.item))) },
                    trailingContent = {
                        Text(
                            outstanding.count.toString(),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                )
                HorizontalDivider()
            }

            item {
                val current = project
                Button(
                    enabled = current != null,
                    onClick = {
                        if (current == null) return@Button
                        val result = Exporter.write(
                            context = context,
                            document = ExportDocument.Handover(
                                project = current,
                                readiness = readiness,
                                producedByName = producedBy,
                                producedOn = LocalDate.now(),
                            ),
                            languageTag = languageTag,
                            locale = locale,
                            rightToLeft = layoutDirection == LayoutDirection.Rtl,
                        )
                        context.startActivity(Exporter.shareIntent(context, result))
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text(stringResource(R.string.hv_export))
                }
            }
        }
    }
}
