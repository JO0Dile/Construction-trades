package il.co.tradesmanager.ui.company

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import il.co.tradesmanager.R
import il.co.tradesmanager.core.access.CompanyProfile
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.rememberImageAdder

/**
 * The firm's own details, and a switch beside each one.
 *
 * The switches are the reason this screen is not three text fields in
 * settings. A company signing up wants its name and mark where the crew sees
 * them; whether the owner's mobile number goes there too is a decision that
 * belongs to the owner and is asked field by field, off by default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyProfileScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: CompanyProfileViewModel = viewModel(
        factory = ViewModelFactory(container) { CompanyProfileViewModel(it) },
    )
    val company by viewModel.company.collectAsStateWithLifecycle()
    val audience by viewModel.audience.collectAsStateWithLifecycle()
    val canEdit = audience == CompanyProfile.Audience.INSIDE

    // Keyed on the company id so switching firms reloads the fields rather
    // than editing one firm's details into another's row.
    val key = company?.id
    var name by remember(key) { mutableStateOf(company?.name.orEmpty()) }
    var registration by remember(key) { mutableStateOf(company?.registrationNumber.orEmpty()) }
    var email by remember(key) { mutableStateOf(company?.email.orEmpty()) }
    var phone by remember(key) { mutableStateOf(company?.phone.orEmpty()) }
    var website by remember(key) { mutableStateOf(company?.website.orEmpty()) }
    var address by remember(key) { mutableStateOf(company?.addressLine.orEmpty()) }
    var licence by remember(key) { mutableStateOf(company?.contractorLicenceNumber.orEmpty()) }
    var classification by remember(key) {
        mutableStateOf(company?.contractorClassification.orEmpty())
    }
    val published = remember(key) {
        mutableStateMapOf<CompanyProfile.Field, Boolean>().apply {
            val chosen = CompanyProfileViewModel.publishedOf(company)
            CompanyProfile.Field.entries.forEach { put(it, it in chosen) }
        }
    }

    val pickLogo = rememberImageAdder(
        newCameraTarget = viewModel::newCameraTarget,
        onCaptured = viewModel::captureLogo,
        onPicked = viewModel::setLogo,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.co_title)) },
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    company?.logoUri?.let { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = stringResource(R.string.co_logo),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
                        )
                    }
                    if (canEdit) {
                        OutlinedButton(onClick = pickLogo) {
                            Text(stringResource(R.string.co_logo))
                        }
                    }
                }
            }

            // Name and registration have no switch. A firm with a hidden name
            // is not a firm anybody can work for, and the registration number
            // is on every invoice it issues already.
            item {
                Field(R.string.co_name, name, canEdit) { name = it }
            }
            item {
                Field(R.string.co_reg, registration, canEdit) { registration = it }
            }

            item { SectionHeader(stringResource(R.string.co_visible_to_workers)) }
            item {
                Text(
                    text = stringResource(R.string.co_visible_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            publishable.forEach { (field, label, keyboard) ->
                item(key = field.name) {
                    val value = when (field) {
                        CompanyProfile.Field.EMAIL -> email
                        CompanyProfile.Field.PHONE -> phone
                        CompanyProfile.Field.WEBSITE -> website
                        CompanyProfile.Field.ADDRESS -> address
                        else -> ""
                    }
                    PublishableField(
                        label = label,
                        value = value,
                        keyboard = keyboard,
                        enabled = canEdit,
                        shown = published[field] == true,
                        onValueChange = {
                            when (field) {
                                CompanyProfile.Field.EMAIL -> email = it
                                CompanyProfile.Field.PHONE -> phone = it
                                CompanyProfile.Field.WEBSITE -> website = it
                                CompanyProfile.Field.ADDRESS -> address = it
                                else -> Unit
                            }
                        },
                        onShownChange = { published[field] = it },
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.co_licence)) }
            item {
                Text(
                    text = stringResource(R.string.co_verified_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                PublishableField(
                    label = R.string.co_licence,
                    value = licence,
                    keyboard = KeyboardType.Text,
                    enabled = canEdit,
                    shown = published[CompanyProfile.Field.LICENCE] == true,
                    onValueChange = { licence = it },
                    onShownChange = { published[CompanyProfile.Field.LICENCE] = it },
                )
            }
            item {
                Field(R.string.co_classification, classification, canEdit) { classification = it }
            }

            if (canEdit) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        Button(
                            onClick = {
                                viewModel.save(
                                    name = name,
                                    registrationNumber = registration,
                                    email = email,
                                    phone = phone,
                                    website = website,
                                    addressLine = address,
                                    licenceNumber = licence,
                                    classification = classification,
                                    published = published
                                        .filterValues { it }
                                        .keys
                                        .toSet(),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.action_save)) }
                    }
                }
            }
        }
    }
}

/** The fields that carry a "show to the crew" switch, in the order they read. */
private val publishable = listOf(
    Triple(CompanyProfile.Field.PHONE, R.string.co_phone, KeyboardType.Phone),
    Triple(CompanyProfile.Field.EMAIL, R.string.co_email, KeyboardType.Email),
    Triple(CompanyProfile.Field.WEBSITE, R.string.co_website, KeyboardType.Uri),
    Triple(CompanyProfile.Field.ADDRESS, R.string.co_address, KeyboardType.Text),
)

@Composable
private fun Field(
    @StringRes label: Int,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(stringResource(label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/**
 * A field and the question about it, together.
 *
 * The switch sits with the value rather than in a list of privacy settings
 * three screens away, because somebody typing a mobile number is the person
 * who knows whether that number should be on forty phones.
 */
@Composable
private fun PublishableField(
    @StringRes label: Int,
    value: String,
    keyboard: KeyboardType,
    enabled: Boolean,
    shown: Boolean,
    onValueChange: (String) -> Unit,
    onShownChange: (Boolean) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            label = { Text(stringResource(label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.co_visible_to_workers),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Switch(checked = shown, onCheckedChange = onShownChange, enabled = enabled)
        }
    }
}
