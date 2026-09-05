package il.co.tradesmanager.ui.evidence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import il.co.tradesmanager.R
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.evidence.Snags
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.repository.PhotoRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.DetailRow
import il.co.tradesmanager.ui.components.LoadingState
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.SectionPlaceholder
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.components.rememberImageAdder
import il.co.tradesmanager.ui.components.rememberNow
import java.time.Instant
import java.time.ZoneId

/**
 * One defect, and the two pictures that bracket it.
 *
 * The page is laid out as the job runs: what is wrong and a photograph of it,
 * then what was done and a photograph of that, then somebody else's verdict.
 * The claim and the check are deliberately two different buttons pressed by
 * two different people — a list where the person who did the work also signs
 * it off is worth nothing at handover.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnagDetailScreen(
    container: AppContainer,
    snagId: String,
    onBack: () -> Unit,
) {
    val viewModel: SnagDetailViewModel = viewModel(
        factory = ViewModelFactory(container) { SnagDetailViewModel(it, snagId) },
    )
    val snag by viewModel.snag.collectAsStateWithLifecycle()
    val raised by viewModel.raisedPhotos.collectAsStateWithLifecycle()
    val fixed by viewModel.fixedPhotos.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val now = rememberNow(intervalMillis = 5 * 60_000L)

    val signedIn = session as? SessionRepository.State.SignedIn
    val canEdit = signedIn?.canWrite(Lens.EVIDENCE) != false
    var verifying by remember { mutableStateOf(false) }

    val current = snag

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.reference ?: stringResource(R.string.snag_title)) },
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
        if (current == null) {
            LoadingState(
                message = stringResource(R.string.snag_title),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        val state = Snags.state(current.status)
        val overdue = Snags.isOverdue(current.status, current.dueOn, now)

        LazyColumn(Modifier.padding(padding)) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(current.title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = stringResource(snagStateLabel(state)),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (state == Snags.State.REJECTED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (overdue) {
                        Text(
                            text = pluralCount(
                                R.plurals.snag_overdue_days,
                                Snags.daysOverdue(current.dueOn, now),
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // The sentence that stops a claim reading as a completion.
            if (state == Snags.State.AWAITING_CHECK) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.snag_claim_note),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            item {
                Column {
                    current.location?.let { DetailRow(stringResource(R.string.snag_where), it) }
                    current.assignedToName?.let {
                        DetailRow(stringResource(R.string.snag_who), it)
                    }
                    current.dueOn?.let {
                        val due = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                        DetailRow(
                            stringResource(R.string.snag_due),
                            Formats.date(due.toLocalDate(), locale),
                        )
                    }
                    DetailRow(stringResource(R.string.snag_raised_by), current.raisedByName)
                    current.verifiedByName?.let {
                        DetailRow(stringResource(R.string.snag_checked_by), it)
                    }
                    current.verifyNotes?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    if (current.blocksHandover) {
                        Text(
                            text = stringResource(R.string.snag_blocks),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.snag_photo_raised)) }
            item {
                PhotoStrip(
                    photos = raised,
                    canAdd = canEdit,
                    newCameraTarget = viewModel::newCameraTarget,
                    onCaptured = {
                        viewModel.recordCameraPhoto(it, PhotoRepository.Owner.SNAG_RAISED)
                    },
                    onPicked = { viewModel.importPhoto(it, PhotoRepository.Owner.SNAG_RAISED) },
                )
            }

            item { SectionHeader(stringResource(R.string.snag_photo_fixed)) }
            item {
                PhotoStrip(
                    photos = fixed,
                    canAdd = canEdit,
                    newCameraTarget = viewModel::newCameraTarget,
                    onCaptured = {
                        viewModel.recordCameraPhoto(it, PhotoRepository.Owner.SNAG_FIXED)
                    },
                    onPicked = { viewModel.importPhoto(it, PhotoRepository.Owner.SNAG_FIXED) },
                )
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (canEdit && Snags.canMarkFixed(current.status)) {
                        Button(
                            onClick = { viewModel.markFixed() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.snag_mark_fixed))
                        }
                    }
                    if (canEdit && Snags.canVerify(current.status)) {
                        Button(
                            onClick = { verifying = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.snag_accept))
                        }
                        OutlinedButton(
                            onClick = { viewModel.verify(accepted = false, notes = null) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.snag_reject))
                        }
                    }
                }
            }
        }
    }

    if (verifying) {
        VerifyDialog(
            onDismiss = { verifying = false },
            onAccept = { notes ->
                verifying = false
                viewModel.verify(accepted = true, notes = notes)
            },
        )
    }
}

@Composable
private fun PhotoStrip(
    photos: List<PhotoEntity>,
    canAdd: Boolean,
    newCameraTarget: () -> Pair<String, android.net.Uri>,
    onCaptured: (String) -> Unit,
    onPicked: (android.net.Uri) -> Unit,
) {
    val add = rememberImageAdder(
        newCameraTarget = newCameraTarget,
        onCaptured = onCaptured,
        onPicked = onPicked,
    )
    if (photos.isEmpty() && !canAdd) {
        SectionPlaceholder(stringResource(R.string.photo_empty))
        return
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(photos, key = { it.id }) { photo ->
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                AsyncImage(
                    model = photo.uri,
                    contentDescription = photo.note,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (canAdd) {
            item {
                OutlinedButton(onClick = add, modifier = Modifier.size(110.dp)) {
                    Text(stringResource(R.string.photo_add))
                }
            }
        }
    }
}

@Composable
private fun VerifyDialog(onDismiss: () -> Unit, onAccept: (String?) -> Unit) {
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.snag_accept)) },
        text = {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.snag_verify_notes)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onAccept(notes.trim().takeIf { it.isNotEmpty() }) }) {
                Text(stringResource(R.string.snag_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
