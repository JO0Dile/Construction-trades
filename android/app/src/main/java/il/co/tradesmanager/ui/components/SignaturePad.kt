package il.co.tradesmanager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import il.co.tradesmanager.R
import il.co.tradesmanager.core.security.Signature

/**
 * Somewhere to sign with a finger.
 *
 * Points are captured normalised to the pad, so what is stored redraws
 * correctly on a different phone, in a report, or on whatever somebody opens
 * the record with years from now — see [Signature].
 *
 * The strokes are kept in a snapshot list rather than rebuilt from the encoded
 * string on every touch. A finger drawing a name generates a point every few
 * milliseconds, and re-parsing a growing string at that rate is how a
 * signature pad ends up lagging behind the finger drawing on it.
 */
@Composable
fun SignaturePad(
    onSignatureChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
) {
    val strokes = remember { mutableStateListOf<MutableList<Signature.Point>>() }
    var revision by remember { mutableStateOf(0) }
    val onChange by rememberUpdatedState(onSignatureChange)

    // Encode off the drawing path: the caller hears about a finished stroke,
    // not about every pixel of an unfinished one.
    LaunchedEffect(strokes) {
        snapshotFlow { revision }.collect {
            onChange(Signature.encode(strokes.map { stroke -> stroke.toList() }))
        }
    }

    Column(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val ink = MaterialTheme.colorScheme.onSurface
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { start ->
                                strokes.add(mutableListOf(normalise(start, size.width, size.height)))
                                revision++
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                strokes.lastOrNull()
                                    ?.add(normalise(change.position, size.width, size.height))
                                revision++
                            },
                            onDragEnd = { revision++ },
                        )
                    },
            ) {
                strokes.forEach { stroke ->
                    if (stroke.size < 2) return@forEach
                    val path = Path()
                    stroke.forEachIndexed { index, point ->
                        val x = point.x * size.width
                        val y = point.y * size.height
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path = path, color = ink, style = Stroke(width = 4f))
                }
            }

            if (strokes.isEmpty()) {
                Text(
                    text = stringResource(R.string.sign_here),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        TextButton(
            onClick = {
                strokes.clear()
                revision++
            },
            modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
        ) {
            Text(stringResource(R.string.sign_clear))
        }
    }
}

/**
 * Screen pixels to a fraction of the pad.
 *
 * Clamped, because a finger that slides off the edge mid-stroke keeps
 * reporting positions outside the box and would otherwise store a signature
 * with a tail hanging into nowhere.
 */
private fun normalise(offset: Offset, width: Int, height: Int): Signature.Point =
    Signature.Point(
        x = if (width == 0) 0f else (offset.x / width).coerceIn(0f, 1f),
        y = if (height == 0) 0f else (offset.y / height).coerceIn(0f, 1f),
    )
