package pl.watchme.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.watchme.domain.DomainError
import pl.watchme.domain.Field
import pl.watchme.domain.Outcome
import pl.watchme.domain.model.Session
import pl.watchme.domain.usecase.ObserveLineupUseCase
import pl.watchme.domain.usecase.SendPasswordResetUseCase
import pl.watchme.domain.usecase.SignInUseCase
import pl.watchme.domain.usecase.SignUpUseCase

enum class AuthError {
    INVALID_EMAIL,
    PASSWORD_REQUIRED,
    PASSWORD_TOO_SHORT,
    PASSWORDS_DIFFER,
    INVALID_CREDENTIALS,
    EMAIL_TAKEN,
    WEAK_PASSWORD,
    TOO_MANY_ATTEMPTS,
    NETWORK,
    UNKNOWN,
}

sealed interface AuthEvent {
    data class SignedIn(val hasChannels: Boolean) : AuthEvent
}

data class CredentialsUiState(
    val email: String = "",
    val password: String = "",
    val confirmation: String = "",
    val emailError: AuthError? = null,
    val passwordError: AuthError? = null,
    val confirmationError: AuthError? = null,
    val error: AuthError? = null,
    val isSubmitting: Boolean = false,
)

data class ResetPasswordUiState(
    val email: String = "",
    val emailError: AuthError? = null,
    val error: AuthError? = null,
    val isSubmitting: Boolean = false,
    val sent: Boolean = false,
)

abstract class CredentialsViewModel(
    private val observeLineup: ObserveLineupUseCase,
    private val missingPasswordError: AuthError,
) : ViewModel() {

    private val mutableState = MutableStateFlow(CredentialsUiState())
    private val eventChannel = Channel<AuthEvent>(Channel.BUFFERED)

    val state: StateFlow<CredentialsUiState> = mutableState.asStateFlow()
    val events: Flow<AuthEvent> = eventChannel.receiveAsFlow()

    protected abstract suspend fun authenticate(form: CredentialsUiState): Outcome<Session>

    fun onEmailChanged(email: String) = mutableState.update { it.copy(email = email, emailError = null, error = null) }

    fun onPasswordChanged(password: String) =
        mutableState.update { it.copy(password = password, passwordError = null, confirmationError = null, error = null) }

    fun onConfirmationChanged(confirmation: String) =
        mutableState.update { it.copy(confirmation = confirmation, confirmationError = null, error = null) }

    fun onSubmit() {
        val form = mutableState.value
        if (form.isSubmitting) return
        mutableState.value = form.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            when (val outcome = authenticate(form)) {
                is Outcome.Success -> {
                    val hasChannels = observeLineup().first()?.isEmpty == false
                    mutableState.update { it.copy(isSubmitting = false) }
                    eventChannel.send(AuthEvent.SignedIn(hasChannels))
                }
                is Outcome.Failure -> mutableState.update {
                    it.copy(isSubmitting = false).withError(outcome.error, missingPasswordError)
                }
            }
        }
    }
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val signIn: SignInUseCase,
    observeLineup: ObserveLineupUseCase,
) : CredentialsViewModel(observeLineup, AuthError.PASSWORD_REQUIRED) {

    override suspend fun authenticate(form: CredentialsUiState): Outcome<Session> = signIn(form.email, form.password)
}

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val signUp: SignUpUseCase,
    observeLineup: ObserveLineupUseCase,
) : CredentialsViewModel(observeLineup, AuthError.PASSWORD_TOO_SHORT) {

    override suspend fun authenticate(form: CredentialsUiState): Outcome<Session> =
        signUp(form.email, form.password, form.confirmation)
}

@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    private val sendPasswordReset: SendPasswordResetUseCase,
) : ViewModel() {

    private val mutableState = MutableStateFlow(ResetPasswordUiState())
    val state: StateFlow<ResetPasswordUiState> = mutableState.asStateFlow()

    fun onEmailChanged(email: String) = mutableState.update { it.copy(email = email, emailError = null, error = null) }

    fun onSubmit() {
        val form = mutableState.value
        if (form.isSubmitting) return
        mutableState.value = form.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            val outcome = sendPasswordReset(form.email)
            mutableState.update { current ->
                when (outcome) {
                    is Outcome.Success -> current.copy(isSubmitting = false, sent = true)
                    is Outcome.Failure -> when (outcome.error) {
                        DomainError.Validation(Field.EMAIL) -> current.copy(isSubmitting = false, emailError = AuthError.INVALID_EMAIL)
                        else -> current.copy(isSubmitting = false, error = outcome.error.toAuthError())
                    }
                }
            }
        }
    }
}

private fun CredentialsUiState.withError(error: DomainError, missingPasswordError: AuthError): CredentialsUiState =
    when (error) {
        DomainError.Validation(Field.EMAIL) -> copy(emailError = AuthError.INVALID_EMAIL)
        DomainError.Validation(Field.PASSWORD) -> copy(passwordError = missingPasswordError)
        DomainError.Validation(Field.PASSWORD_CONFIRMATION) -> copy(confirmationError = AuthError.PASSWORDS_DIFFER)
        DomainError.EmailAlreadyUsed -> copy(emailError = AuthError.EMAIL_TAKEN)
        DomainError.WeakPassword -> copy(passwordError = AuthError.WEAK_PASSWORD)
        else -> copy(error = error.toAuthError())
    }

private fun DomainError.toAuthError(): AuthError = when (this) {
    DomainError.InvalidCredentials -> AuthError.INVALID_CREDENTIALS
    DomainError.TooManyAttempts -> AuthError.TOO_MANY_ATTEMPTS
    DomainError.Network -> AuthError.NETWORK
    else -> AuthError.UNKNOWN
}
