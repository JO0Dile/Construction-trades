package il.co.tradesmanager.ui.components

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import il.co.tradesmanager.R

/**
 * Adding an image, from either source, wherever the app needs one.
 *
 * Returns a lambda that opens the chooser. Two sources, because they answer
 * different needs on a site: the drawing taped to the wall wants the camera,
 * and the plan the architect emailed is already in the gallery.
 *
 * The gallery route uses the system photo picker, which grants access to the
 * single chosen image and needs no storage permission at all — so the app
 * never has to ask for the user's whole photo library.
 */
@Composable
fun rememberImageAdder(
    newCameraTarget: () -> Pair<String, Uri>,
    onCaptured: (photoId: String) -> Unit,
    onPicked: (Uri) -> Unit,
): () -> Unit {

    var showChooser by remember { mutableStateOf(false) }
    var pendingId by remember { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val id = pendingId
        pendingId = null
        // A cancelled camera returns false and leaves an empty file, which the
        // repository cleans up. Nothing is recorded either way.
        if (success && id != null) onCaptured(id)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(onPicked) }

    val requestCamera = rememberPermissionRequest(
        permission = Manifest.permission.CAMERA,
        titleRes = R.string.perm_camera_title,
        bodyRes = R.string.perm_camera_body,
        onResult = { granted ->
            if (granted) {
                val (id, uri) = newCameraTarget()
                pendingId = id
                cameraLauncher.launch(uri)
            }
        },
    )

    if (showChooser) {
        AlertDialog(
            onDismissRequest = { showChooser = false },
            title = { Text(stringResource(R.string.photo_add)) },
            text = { Text(stringResource(R.string.photo_empty_hint)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showChooser = false
                        requestCamera()
                    },
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                    Text(stringResource(R.string.photo_take))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showChooser = false
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Text(stringResource(R.string.photo_choose))
                }
            },
        )
    }

    return { showChooser = true }
}
