package il.co.tradesmanager.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.security.Passcode
import il.co.tradesmanager.data.local.entity.AccountEntity
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory

/**
 * The door.
 *
 * Two jobs in one screen because they are the same moment from the user's
 * side: either this device has nobody on it yet and someone is setting it up,
 * or it has people on it and one of them is arriving. Nothing here talks to a
 * server — an account is a row on this phone, so a crew in a basement car park
 * can still sign in and record what they did.
 */
@Composable
fun AccountGateScreen(container: AppContainer, needsSetup: Boolean) {
    val viewModel: AccountViewModel = viewModel(
        factory = ViewModelFactory(container) { AccountViewModel(it) },
    )
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val wrongPasscode by viewModel.wrongPasscode.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize()) {
        if (needsSetup) {
            FirstRun(
                onPersonal = viewModel::createPersonal,
                onCompany = viewModel::createCompany,
            )
        } else {
            SignIn(
                accounts = accounts,
                wrongPasscode = wrongPasscode,
                onTyping = viewModel::clearError,
                onSignIn = viewModel::signIn,
            )
        }
    }
}

/* ---------------------------------------------------------------- first run */

private enum class Setup { CHOOSE, PERSONAL, COMPANY }

@Composable
private fun FirstRun(
    onPersonal: (name: String, passcode: String?) -> Unit,
    onCompany: (company: String, registration: String?, owner: String, passcode: String?) -> Unit,
) {
    var step by remember { mutableStateOf(Setup.CHOOSE) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        if (step != Setup.CHOOSE) {
            TextButton(onClick = { step = Setup.CHOOSE }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Text(" " + stringResource(R.string.action_back))
            }
        }

        Text(
            text = stringResource(R.string.acc_welcome),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.acc_welcome_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )

        when (step) {
            Setup.CHOOSE -> {
                Choice(
                    title = stringResource(R.string.acc_personal),
                    hint = stringResource(R.string.acc_personal_hint),
                    onClick = { step = Setup.PERSONAL },
                )
                Spacer(Modifier.height(12.dp))
                Choice(
                    title = stringResource(R.string.acc_company),
                    hint = stringResource(R.string.acc_company_hint),
                    onClick = { step = Setup.COMPANY },
                )
            }

            Setup.PERSONAL -> PersonalForm(onPersonal)
            Setup.COMPANY -> CompanyForm(onCompany)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Choice(title: String, hint: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun PersonalForm(onCreate: (name: String, passcode: String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var passcode by remember { mutableStateOf("") }
    val passcodeOk = passcode.isEmpty() || Passcode.isAcceptable(passcode)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.acc_your_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        PasscodeField(passcode, { passcode = it }, passcodeOk)
        Button(
            onClick = { onCreate(name.trim(), passcode.takeIf { it.isNotEmpty() }) },
            enabled = name.isNotBlank() && passcodeOk,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.acc_create))
        }
    }
}

@Composable
private fun CompanyForm(
    onCreate: (company: String, registration: String?, owner: String, passcode: String?) -> Unit,
) {
    var companyName by remember { mutableStateOf("") }
    var registration by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var passcode by remember { mutableStateOf("") }
    val passcodeOk = passcode.isEmpty() || Passcode.isAcceptable(passcode)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = companyName,
            onValueChange = { companyName = it },
            label = { Text(stringResource(R.string.acc_company_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = registration,
            onValueChange = { registration = it },
            label = { Text(stringResource(R.string.acc_reg_number)) },
            supportingText = { Text(stringResource(R.string.acc_reg_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = ownerName,
            onValueChange = { ownerName = it },
            label = { Text(stringResource(R.string.acc_your_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        PasscodeField(passcode, { passcode = it }, passcodeOk)
        Button(
            onClick = {
                onCreate(
                    companyName.trim(),
                    registration.trim().takeIf { it.isNotEmpty() },
                    ownerName.trim(),
                    passcode.takeIf { it.isNotEmpty() },
                )
            },
            enabled = companyName.isNotBlank() && ownerName.isNotBlank() && passcodeOk,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.acc_create))
        }
    }
}

/* ------------------------------------------------------------------ sign in */

@Composable
private fun SignIn(
    accounts: List<AccountEntity>,
    wrongPasscode: Boolean,
    onTyping: () -> Unit,
    onSignIn: (String, String) -> Unit,
) {
    var chosen by remember { mutableStateOf<AccountEntity?>(null) }
    var passcode by remember { mutableStateOf("") }

    // One account with no passcode is not a sign-in screen, it is a doorway
    // someone taps through for no reason — go straight in. In an effect, not
    // in composition: composition runs again for all sorts of reasons and this
    // must happen once.
    val single = accounts.singleOrNull()
    LaunchedEffect(single?.id) {
        if (single != null && single.passcodeHash == null) onSignIn(single.id, "")
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.acc_sign_in_as),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        accounts.forEach { account ->
            val selected = chosen?.id == account.id
            ListItem(
                headlineContent = { Text(account.displayName) },
                supportingContent = { Text(stringResource(roleLabel(Role.parse(account.role)))) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        chosen = account
                        passcode = ""
                        onTyping()
                    },
                colors = ListItemDefaults.colors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
            )
        }

        val account = chosen
        if (account != null) {
            Spacer(Modifier.height(20.dp))
            if (account.passcodeHash == null) {
                Button(
                    onClick = { onSignIn(account.id, "") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.acc_sign_in))
                }
            } else {
                OutlinedTextField(
                    value = passcode,
                    onValueChange = {
                        passcode = it
                        onTyping()
                    },
                    label = { Text(stringResource(R.string.acc_passcode)) },
                    isError = wrongPasscode,
                    supportingText = {
                        if (wrongPasscode) Text(stringResource(R.string.acc_wrong_passcode))
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onSignIn(account.id, passcode) },
                    enabled = passcode.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Text(stringResource(R.string.acc_sign_in))
                }
            }
        }
    }
}

/* ------------------------------------------------------------------- shared */

@Composable
private fun PasscodeField(value: String, onChange: (String) -> Unit, acceptable: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.acc_passcode)) },
        isError = !acceptable,
        supportingText = {
            Text(
                stringResource(
                    if (acceptable) R.string.acc_passcode_hint else R.string.acc_passcode_short,
                ),
            )
        },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Role -> the words a site actually uses for it. */
fun roleLabel(role: Role): Int = when (role) {
    Role.OWNER -> R.string.role_owner
    Role.MANAGER -> R.string.role_manager
    Role.FINANCE -> R.string.role_finance
    Role.HR -> R.string.role_hr
    Role.WORKER -> R.string.role_worker
}

/** The one-line explanation under a role, for anyone choosing one. */
fun roleHint(role: Role): Int = when (role) {
    Role.OWNER -> R.string.role_owner_hint
    Role.MANAGER -> R.string.role_manager_hint
    Role.FINANCE -> R.string.role_finance_hint
    Role.HR -> R.string.role_hr_hint
    Role.WORKER -> R.string.role_worker_hint
}
