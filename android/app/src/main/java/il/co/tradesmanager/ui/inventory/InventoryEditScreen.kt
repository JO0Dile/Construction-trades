package il.co.tradesmanager.ui.inventory

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import coil.compose.AsyncImage
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.PhotoViewer
import il.co.tradesmanager.ui.components.currentLanguageTag
import il.co.tradesmanager.ui.components.rememberImageAdder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryEditScreen(
    container: AppContainer,
    itemId: String?,
    onDone: (savedItemId: String?) -> Unit,
) {
    val languageTag = currentLanguageTag()
    val viewModel: InventoryEditViewModel = viewModel(
        factory = ViewModelFactory(container) { InventoryEditViewModel(it, itemId, languageTag) },
    )
    val form by viewModel.form.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    var viewing by remember { mutableStateOf<PhotoEntity?>(null) }
    val addImage = rememberImageAdder(
        newCameraTarget = viewModel::newCameraTarget,
        onCaptured = viewModel::onCaptured,
        onPicked = viewModel::onPicked,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (itemId == null) R.string.inv_add_item else R.string.inv_edit_item,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onDone(null) }) {
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // A photograph of the actual item beats any stock image: it is
            // this user's own stock, on their own van, in the state it is in.
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                photos.take(4).forEach { photo ->
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = stringResource(R.string.item_photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { viewing = photo },
                    )
                }
                OutlinedButton(onClick = addImage, modifier = Modifier.size(64.dp)) {
                    Icon(
                        Icons.Filled.AddAPhoto,
                        contentDescription = stringResource(R.string.item_photo),
                    )
                }
            }

            Field(form.name, viewModel::setName, R.string.inv_name, isError = form.nameError)
            Field(form.spec, viewModel::setSpec, R.string.inv_spec, singleLine = false)
            Field(form.category, viewModel::setCategory, R.string.inv_category)

            Row(Modifier.fillMaxWidth()) {
                Field(
                    value = form.quantity,
                    onValueChange = viewModel::setQuantity,
                    labelRes = R.string.inv_quantity,
                    numeric = true,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                Field(
                    value = form.minStock,
                    onValueChange = viewModel::setMinStock,
                    labelRes = R.string.inv_min_stock,
                    numeric = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Field(form.unit, viewModel::setUnit, R.string.inv_unit)
            Field(form.barcode, viewModel::setBarcode, R.string.inv_barcode)
            Field(form.price, viewModel::setPrice, R.string.inv_price, numeric = true)
            Field(form.tags, viewModel::setTags, R.string.inv_tags)

            Button(
                onClick = { viewModel.save { id -> onDone(id) } },
                enabled = !form.nameError,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Text(stringResource(R.string.action_save))
            }

            if (itemId != null) {
                OutlinedButton(
                    onClick = { viewModel.delete { onDone(null) } },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            }
        }
    }

    viewing?.let { photo ->
        PhotoViewer(
            photo = photo,
            // "Set as plan" is meaningless for a tool; only a project has one.
            isPlan = true,
            onSetAsPlan = {},
            onDelete = {
                viewModel.deletePhoto(photo)
                viewing = null
            },
            onDismiss = { viewing = null },
        )
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    singleLine: Boolean = true,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = singleLine,
        isError = isError,
        supportingText = if (isError) {
            { Text(stringResource(R.string.error_required)) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
        ),
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}