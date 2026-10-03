package il.co.tradesmanager.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import il.co.tradesmanager.R
import il.co.tradesmanager.data.local.entity.PhotoEntity

/**
 * A row of photographs with a way to add one.
 *
 * Lives here rather than beside its first caller because it now has two, and
 * a second copy of a camera flow is a second place for the permission
 * handling and the file-provider target to drift.
 */
@Composable
fun PhotoStrip(
    photos: List<PhotoEntity>,
    canAdd: Boolean,
    newCameraTarget: () -> Pair<String, Uri>,
    onCaptured: (String) -> Unit,
    onPicked: (Uri) -> Unit,
    modifier: Modifier = Modifier,
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
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(photos, key = { it.id }) { photo ->
            Box(Modifier.size(110.dp).clip(RoundedCornerShape(12.dp))) {
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
