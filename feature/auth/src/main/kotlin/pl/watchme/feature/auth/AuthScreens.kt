package pl.watchme.feature.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.watchme.designsystem.theme.WatchMeTheme

@Composable
fun LoginRoute(
    onSignedIn: (hasChannels: Boolean) -> Unit,
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectSignIn(viewModel, onSignedIn)
    LoginScreen(
        state = state,
        onEmailChanged = viewModel::onEmailChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onSubmit = viewModel::onSubmit,
        onCreateAccount = onCreateAccount,
        onForgotPassword = onForgotPassword,
    )
}

@Composable
fun RegisterRoute(
    onSignedIn: (hasChannels: Boolean) -> Unit,
    onBack: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectSignIn(viewModel, onSignedIn)
    RegisterScreen(
        state = state,
        onEmailChanged = viewModel::onEmailChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onConfirmationChanged = viewModel::onConfirmationChanged,
        onSubmit = viewModel::onSubmit,
        onBack = onBack,
    )
}

@Composable
fun ResetPasswordRoute(onBack: () -> Unit, viewModel: ResetPasswordViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ResetPasswordScreen(
        state = state,
        onEmailChanged = viewModel::onEmailChanged,
        onSubmit = viewModel::onSubmit,
        onBack = onBack,
    )
}

@Composable
private fun CollectSignIn(viewModel: CredentialsViewModel, onSignedIn: (Boolean) -> Unit) {
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AuthEvent.SignedIn -> onSignedIn(event.hasChannels)
            }
        }
    }
}

@Composable
fun LoginScreen(
    state: CredentialsUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    Scaffold { padding ->
        FormColumn(Modifier.padding(padding)) {
            Spacer(Modifier.height(48.dp))
            Text(
                stringResource(R.string.auth_brand),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.auth_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            EmailField(state.email, state.emailError, onEmailChanged)
            PasswordField(state.password, R.string.auth_password, state.passwordError, onPasswordChanged, ImeAction.Done)
            GeneralError(state.error)
            SubmitButton(R.string.auth_sign_in, state.isSubmitting, onSubmit)
            TextButton(onClick = onCreateAccount) { Text(stringResource(R.string.auth_create_account)) }
            TextButton(onClick = onForgotPassword) { Text(stringResource(R.string.auth_forgot_password)) }
        }
    }
}

@Composable
fun RegisterScreen(
    state: CredentialsUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirmationChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    FormScaffold(R.string.auth_register_title, onBack) { modifier ->
        FormColumn(modifier) {
            Text(
                stringResource(R.string.auth_register_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EmailField(state.email, state.emailError, onEmailChanged)
            PasswordField(state.password, R.string.auth_password, state.passwordError, onPasswordChanged, ImeAction.Next)
            PasswordField(
                state.confirmation,
                R.string.auth_confirmation,
                state.confirmationError,
                onConfirmationChanged,
                ImeAction.Done,
            )
            GeneralError(state.error)
            SubmitButton(R.string.auth_create_account, state.isSubmitting, onSubmit)
        }
    }
}

@Composable
fun ResetPasswordScreen(
    state: ResetPasswordUiState,
    onEmailChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    FormScaffold(R.string.auth_reset_title, onBack) { modifier ->
        FormColumn(modifier) {
            if (state.sent) {
                Text(stringResource(R.string.auth_reset_sent), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.auth_back)) }
            } else {
                Text(
                    stringResource(R.string.auth_reset_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EmailField(state.email, state.emailError, onEmailChanged)
                GeneralError(state.error)
                SubmitButton(R.string.auth_reset_send, state.isSubmitting, onSubmit)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormScaffold(@StringRes title: Int, onBack: () -> Unit, content: @Composable (Modifier) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.auth_back))
                    }
                },
            )
        },
    ) { padding -> content(Modifier.padding(padding)) }
}

@Composable
private fun FormColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun EmailField(value: String, error: AuthError?, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.auth_email)) },
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it.message)) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordField(
    value: String,
    @StringRes label: Int,
    error: AuthError?,
    onChange: (String) -> Unit,
    imeAction: ImeAction,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it.message)) } },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun GeneralError(error: AuthError?) {
    error?.let {
        Text(
            stringResource(it.message),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SubmitButton(@StringRes label: Int, isSubmitting: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        if (isSubmitting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(stringResource(label))
        }
    }
}

@get:StringRes
private val AuthError.message: Int
    get() = when (this) {
        AuthError.INVALID_EMAIL -> R.string.auth_error_invalid_email
        AuthError.PASSWORD_REQUIRED -> R.string.auth_error_password_required
        AuthError.PASSWORD_TOO_SHORT -> R.string.auth_error_password_too_short
        AuthError.PASSWORDS_DIFFER -> R.string.auth_error_passwords_differ
        AuthError.INVALID_CREDENTIALS -> R.string.auth_error_invalid_credentials
        AuthError.EMAIL_TAKEN -> R.string.auth_error_email_taken
        AuthError.WEAK_PASSWORD -> R.string.auth_error_weak_password
        AuthError.TOO_MANY_ATTEMPTS -> R.string.auth_error_too_many_attempts
        AuthError.NETWORK -> R.string.auth_error_network
        AuthError.UNKNOWN -> R.string.auth_error_unknown
    }

@Preview
@Composable
private fun LoginPreview() {
    WatchMeTheme(darkTheme = true) {
        LoginScreen(
            state = CredentialsUiState(email = "jan@example.com", error = AuthError.INVALID_CREDENTIALS),
            onEmailChanged = {},
            onPasswordChanged = {},
            onSubmit = {},
            onCreateAccount = {},
            onForgotPassword = {},
        )
    }
}
