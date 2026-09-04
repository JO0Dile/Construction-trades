package il.co.tradesmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import il.co.tradesmanager.R
import il.co.tradesmanager.data.local.entity.PhotoEntity

/**
 * Full-screen image with pinch-zoom and pan.
 *
 * Zoom is the whole point for a site plan: a floor plan photographed off a
 * wall is unreadable at thumbnail size, and reading a dimension off it is
 * exactly what someone standing in the room needs to do.
 */
@Composable
fun PhotoViewer(
    photo: PhotoEntity,
    isPlan: Boolean,
    onSetAsPlan: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }
        var confirmDelete by remember { mutableStateOf(false) }

        val transform = rememberTransformableState { zoomChange, panChange, _ ->
            // Never below 1x — letting it shrink past the frame just loses the
            // image off the edge of a screen someone is squinting at.
            scale = (scale * zoomChange).coerceIn(1f, 8f)
            if (scale > 1f) {
                offsetX += panChange.x
                offsetY += panChange.y
            } else {
                offsetX = 0f
                offsetY = 0f
            }
        }

        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = photo.uri,
                contentDescription = photo.note,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    )
                    .transformable(transform),
            )

            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(8.dp),
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = Color.White,
                    )
                }
                Box(Modifier.weight(1f))
                if (!isPlan) {
                    TextButton(onClick = onSetAsPlan) {
                        Icon(Icons.Filled.Map, contentDescription = null, tint = Color.White)
                        Text(stringResource(R.string.photo_set_plan), color = Color.White)
                    }
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = Color.White,
                    )
                }
            }
        }

        if (confirmDelete) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text(stringResource(R.string.action_delete)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmDelete = false
                            onDelete()
                        },
                    ) {
                        Text(stringResource(R.string.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}
