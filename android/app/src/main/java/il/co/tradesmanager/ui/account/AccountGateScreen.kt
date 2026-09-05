package il.co.tradesmanager.ui.account

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    val wrongCredentials by viewModel.wrongCredentials.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize()) {
        if (needsSetup) {
            FirstRun(
                onPersonal = viewModel::createPersonal,
                onCompany = viewModel::createCompany,
            )
        } else {
            SignIn(
                wrongCredentials = wrongCredentials,
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
    onPersonal: (name: String, username: String?, idNumber: String?, passcode: String?) -> Unit,
    onCompany: (
        company: String,
        registration: String?,
        owner: String,
        username: String?,
        idNumber: String?,
        passcode: String?,
    ) -> Unit,
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
private fun PersonalForm(
    onCreate: (name: String, username: String?, idNumber: String?, passcode: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var idNumber by remember { mutableStateOf("") }
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
        IdentityFields(
            username = username,
            onUsername = { username = it },
            idNumber = idNumber,
            onIdNumber = { idNumber = it },
        )
        PasscodeField(passcode, { passcode = it }, passcodeOk)
        Button(
            onClick = {
                onCreate(
                    name.trim(),
                    username.trim().takeIf { it.isNotEmpty() },
                    idNumber.trim().takeIf { it.isNotEmpty() },
                    passcode.takeIf { it.isNotEmpty() },
                )
            },
            enabled = name.isNotBlank() && passcodeOk,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.acc_create))
        }
    }
}

/**
 * The two things that identify one person out of five on a site.
 *
 * Both optional. A sole trader setting the app up on a Tuesday morning should
 * not be stopped at a field they have to go and look up, and a manager filling
 * in a crew has every reason to fill both in.
 */
@Composable
private fun IdentityFields(
    username: String,
    onUsername: (String) -> Unit,
    idNumber: String,
    onIdNumber: (String) -> Unit,
) {
    OutlinedTextField(
        value = username,
        onValueChange = onUsername,
        label = { Text(stringResource(R.string.acc_identifier)) },
        supportingText = { Text(stringResource(R.string.acc_identifier_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = idNumber,
        onValueChange = onIdNumber,
        label = { Text(stringResource(R.string.acc_id_number)) },
        supportingText = { Text(stringResource(R.string.acc_id_number_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CompanyForm(
    onCreate: (
        company: String,
        registration: String?,
        owner: String,
        username: String?,
        idNumber: String?,
        passcode: String?,
    ) -> Unit,
) {
    var companyName by remember { mutableStateOf("") }
    var registration by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var idNumber by remember { mutableStateOf("") }
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
        IdentityFields(
            username = username,
            onUsername = { username = it },
            idNumber = idNumber,
            onIdNumber = { idNumber = it },
        )
        PasscodeField(passcode, { passcode = it }, passcodeOk)
        Button(
            onClick = {
                onCreate(
                    companyName.trim(),
                    registration.trim().takeIf { it.isNotEmpty() },
                    ownerName.trim(),
                    username.trim().takeIf { it.isNotEmpty() },
                    idNumber.trim().takeIf { it.isNotEmpty() },
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

/**
 * Typing who you are, rather than picking yourself off a list.
 *
 * The list was wrong twice over. It showed every account on the device to
 * whoever picked the phone up — a crew roster handed to a stranger — and it did
 * not match how people are actually told who they are on a site: somebody in
 * the office gives you a username and a password.
 *
 * One error message covers both a name nobody has and a password that does not
 * match. Telling somebody which half was wrong tells them which of their
 * colleagues to keep guessing at, and the person genuinely signing in learns
 * nothing from the distinction — they retype both either way.
 */
@Composable
private fun SignIn(
    wrongCredentials: Boolean,
    onTyping: () -> Unit,
    onSignIn: (identifier: String, password: String) -> Unit,
) {
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.acc_sign_in_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        OutlinedTextField(
            value = identifier,
            onValueChange = {
                identifier = it
                onTyping()
            },
            label = { Text(stringResource(R.string.acc_identifier)) },
            supportingText = { Text(stringResource(R.string.acc_identifier_hint)) },
            isError = wrongCredentials,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                onTyping()
            },
            label = { Text(stringResource(R.string.acc_password)) },
            supportingText = {
                Text(
                    stringResource(
                        if (wrongCredentials) {
                            R.string.acc_wrong_credentials
                        } else {
                            R.string.acc_password_blank_hint
                        },
                    ),
                )
            },
            isError = wrongCredentials,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { onSignIn(identifier, password) },
            enabled = identifier.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.acc_sign_in))
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
