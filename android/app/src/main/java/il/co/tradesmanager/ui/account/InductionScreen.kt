package il.co.tradesmanager.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import il.co.tradesmanager.R
import il.co.tradesmanager.core.safety.Induction
import il.co.tradesmanager.data.repository.PhotoRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.SignaturePad
import il.co.tradesmanager.ui.components.rememberImageAdder

/**
 * The safety induction. The last thing between a person and the app.
 *
 * There is no skip, no "later", and no back. Somebody who has not been told
 * what to wear and who to tell has no business recording work on a site, and
 * an induction with a dismiss button is a tick-box that protects nobody —
 * least of all the person dismissing it.
 *
 * The continue button stays disabled until they have scrolled to the bottom
 * *and* signed. Scrolling is not proof anybody read it, but it is proof the
 * words went past their eyes, and it is the difference between a page and a
 * pop-up.
 */
@Composable
fun InductionScreen(
    container: AppContainer,
    accountId: String,
    level: Induction.Level,
) {
    val viewModel: InductionViewModel = viewModel(
        factory = ViewModelFactory(container) { InductionViewModel(it, accountId) },
    )
    val photo by viewModel.photo.collectAsStateWithLifecycle()
    val idDocument by viewModel.idDocument.collectAsStateWithLifecycle()

    var signature by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val sections = remember(level) { inductionSections(level) }

    // "Read to the bottom" means the last item has actually been laid out on
    // screen, not that the scrollbar happens to be near the end.
    val reachedEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            last != null && last.index >= listState.layoutInfo.totalItemsCount - 1
        }
    }
    val canContinue = reachedEnd && Induction.isSignatureAcceptable(signature)

    Surface(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column {
                    Text(
                        text = stringResource(R.string.ind_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = stringResource(inductionIntro(level)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            sections.forEach { section ->
                item {
                    Text(
                        text = stringResource(section.headingRes),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(section.points.size) { index ->
                    val point = section.points[index]
                    Column {
                        Text(
                            text = stringResource(point.titleRes),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(point.bodyRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { HorizontalDivider() }

            item {
                IdentityPicture(
                    titleRes = R.string.ind_photo,
                    hintRes = R.string.ind_photo_hint,
                    uri = photo.firstOrNull()?.uri,
                    newCameraTarget = viewModel::newCameraTarget,
                    onCaptured = {
                        viewModel.recordCameraPhoto(it, PhotoRepository.Owner.ACCOUNT_PHOTO)
                    },
                    onPicked = {
                        viewModel.importPhoto(it, PhotoRepository.Owner.ACCOUNT_PHOTO)
                    },
                )
            }
            item {
                IdentityPicture(
                    titleRes = R.string.ind_id_photo,
                    hintRes = R.string.ind_id_photo_hint,
                    uri = idDocument.firstOrNull()?.uri,
                    newCameraTarget = viewModel::newCameraTarget,
                    onCaptured = {
                        viewModel.recordCameraPhoto(it, PhotoRepository.Owner.ACCOUNT_ID_DOCUMENT)
                    },
                    onPicked = {
                        viewModel.importPhoto(it, PhotoRepository.Owner.ACCOUNT_ID_DOCUMENT)
                    },
                )
            }

            item { HorizontalDivider() }

            item {
                Column {
                    Text(
                        text = stringResource(R.string.ind_sign_heading),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    SignaturePad(
                        onSignatureChange = { signature = it },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            item {
                Column {
                    if (!canContinue) {
                        Text(
                            text = stringResource(
                                if (!reachedEnd) R.string.ind_must_read else R.string.ind_must_sign,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Button(
                        onClick = { viewModel.sign(signature) },
                        enabled = canContinue,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.ind_confirm))
                    }
                }
            }
        }
    }
}

/**
 * A picture somebody adds of themselves or their ID.
 *
 * Optional, deliberately. Making the induction depend on a working camera
 * would mean a cracked lens or a refused permission locks somebody out of the
 * app they need to do their job, and the site office can chase a photograph
 * later — it cannot chase a briefing that never happened.
 */
@Composable
private fun IdentityPicture(
    titleRes: Int,
    hintRes: Int,
    uri: String?,
    newCameraTarget: () -> Pair<String, android.net.Uri>,
    onCaptured: (String) -> Unit,
    onPicked: (android.net.Uri) -> Unit,
) {
    val add = rememberImageAdder(
        newCameraTarget = newCameraTarget,
        onCaptured = onCaptured,
        onPicked = onPicked,
    )
    // The trailing button used to carry its own label, and "Add a plan or
    // photo" next to a 72dp thumbnail left the title about two characters
    // wide — "A photograp / h of you". The whole row is the button now, and
    // the icon says what it does without spending any width on saying it.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = add)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            if (uri != null) {
                AsyncImage(
                    model = uri,
                    contentDescription = stringResource(titleRes),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        Icons.Filled.AddAPhoto,
                        contentDescription = null,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(hintRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = add) {
            Icon(
                Icons.Filled.AddAPhoto,
                contentDescription = stringResource(titleRes),
            )
        }
    }
}
