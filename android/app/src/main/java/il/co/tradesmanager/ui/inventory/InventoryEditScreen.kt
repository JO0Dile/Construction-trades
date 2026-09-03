package il.co.tradesmanager.ui.inventory

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.currentLanguageTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryEditScreen(
    container: AppContainer,
    itemId: String?,
    onDone: () -> Unit,
) {
    val languageTag = currentLanguageTag()
    val viewModel: InventoryEditViewModel = viewModel(
        factory = ViewModelFactory(container) { InventoryEditViewModel(it, itemId, languageTag) },
    )
    val form by viewModel.form.collectAsStateWithLifecycle()

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
                    IconButton(onClick = onDone) {
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
                onClick = { viewModel.save(onDone) },
                enabled = !form.nameError,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Text(stringResource(R.string.action_save))
            }

            if (itemId != null) {
                OutlinedButton(
                    onClick = { viewModel.delete(onDone) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            }
        }
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
