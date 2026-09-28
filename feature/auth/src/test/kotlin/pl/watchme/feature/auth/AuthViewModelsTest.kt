package pl.watchme.feature.auth

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.watchme.domain.DomainError
import pl.watchme.domain.Outcome
import pl.watchme.domain.model.ChannelLineup
import pl.watchme.domain.usecase.ObserveLineupUseCase
import pl.watchme.domain.usecase.SendPasswordResetUseCase
import pl.watchme.domain.usecase.SignInUseCase
import pl.watchme.domain.usecase.SignUpUseCase
import pl.watchme.testing.FakeAuthRepository
import pl.watchme.testing.FakeLineupRepository
import pl.watchme.testing.MainDispatcherExtension
import pl.watchme.testing.TestData

class AuthViewModelsTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val auth = FakeAuthRepository()
    private val lineups = FakeLineupRepository()

    private fun login() = LoginViewModel(SignInUseCase(auth, lineups), ObserveLineupUseCase(lineups))

    private fun register() = RegisterViewModel(SignUpUseCase(auth, lineups), ObserveLineupUseCase(lineups))

    private fun reset() = ResetPasswordViewModel(SendPasswordResetUseCase(auth))

    @Test
    fun `invalid email is shown next to the field`() {
        val viewModel = login()
        viewModel.onEmailChanged("jan")
        viewModel.onPasswordChanged("sekret1")

        viewModel.onSubmit()

        assertThat(viewModel.state.value.emailError).isEqualTo(AuthError.INVALID_EMAIL)
        assertThat(auth.signInCalls.size).isEqualTo(0)
    }

    @Test
    fun `typing clears the field error`() {
        val viewModel = login()
        viewModel.onSubmit()

        viewModel.onEmailChanged("jan@example.com")

        assertThat(viewModel.state.value.emailError).isNull()
    }

    @Test
    fun `wrong credentials are shown as a general error`() {
        auth.nextOutcome = Outcome.Failure(DomainError.InvalidCredentials)
        val viewModel = login().filled()

        viewModel.onSubmit()

        assertThat(viewModel.state.value.error).isEqualTo(AuthError.INVALID_CREDENTIALS)
        assertThat(viewModel.state.value.isSubmitting).isFalse()
    }

    @Test
    fun `offline sign in is reported`() {
        auth.nextOutcome = Outcome.Failure(DomainError.Network)
        val viewModel = login().filled()

        viewModel.onSubmit()

        assertThat(viewModel.state.value.error).isEqualTo(AuthError.NETWORK)
    }

    @Test
    fun `successful sign in without channels goes to channel selection`() = runTest {
        val viewModel = login().filled()

        viewModel.events.test {
            viewModel.onSubmit()

            assertThat(awaitItem()).isEqualTo(AuthEvent.SignedIn(hasChannels = false))
        }
    }

    @Test
    fun `successful sign in with synced channels goes to the ranking`() = runTest {
        lineups.lineup.value = ChannelLineup(setOf(TestData.tvp.id), null, Instant.EPOCH)
        val viewModel = login().filled()

        viewModel.events.test {
            viewModel.onSubmit()

            assertThat(awaitItem()).isEqualTo(AuthEvent.SignedIn(hasChannels = true))
        }
    }

    @Test
    fun `registration checks password length and confirmation`() {
        val viewModel = register()
        viewModel.onEmailChanged("jan@example.com")
        viewModel.onPasswordChanged("123")
        viewModel.onConfirmationChanged("123")

        viewModel.onSubmit()
        assertThat(viewModel.state.value.passwordError).isEqualTo(AuthError.PASSWORD_TOO_SHORT)

        viewModel.onPasswordChanged("sekret1")
        viewModel.onConfirmationChanged("sekret2")
        viewModel.onSubmit()
        assertThat(viewModel.state.value.confirmationError).isEqualTo(AuthError.PASSWORDS_DIFFER)
    }

    @Test
    fun `taken email is shown next to the email field`() {
        auth.nextOutcome = Outcome.Failure(DomainError.EmailAlreadyUsed)
        val viewModel = register()
        viewModel.onEmailChanged("jan@example.com")
        viewModel.onPasswordChanged("sekret1")
        viewModel.onConfirmationChanged("sekret1")

        viewModel.onSubmit()

        assertThat(viewModel.state.value.emailError).isEqualTo(AuthError.EMAIL_TAKEN)
    }

    @Test
    fun `successful registration signs the user in`() = runTest {
        val viewModel = register()
        viewModel.onEmailChanged("jan@example.com")
        viewModel.onPasswordChanged("sekret1")
        viewModel.onConfirmationChanged("sekret1")

        viewModel.events.test {
            viewModel.onSubmit()

            assertThat(awaitItem()).isEqualTo(AuthEvent.SignedIn(hasChannels = false))
        }
    }

    @Test
    fun `password reset confirms sending even for unknown accounts`() {
        auth.resetOutcome = Outcome.Failure(DomainError.NotFound)
        val viewModel = reset()
        viewModel.onEmailChanged("jan@example.com")

        viewModel.onSubmit()

        assertThat(viewModel.state.value.sent).isTrue()
    }

    @Test
    fun `password reset validates email and reports throttling`() {
        val viewModel = reset()
        viewModel.onEmailChanged("jan")
        viewModel.onSubmit()
        assertThat(viewModel.state.value.emailError).isEqualTo(AuthError.INVALID_EMAIL)

        auth.resetOutcome = Outcome.Failure(DomainError.TooManyAttempts)
        viewModel.onEmailChanged("jan@example.com")
        viewModel.onSubmit()
        assertThat(viewModel.state.value.error).isEqualTo(AuthError.TOO_MANY_ATTEMPTS)
        assertThat(viewModel.state.value.sent).isFalse()
    }

    private fun LoginViewModel.filled() = apply {
        onEmailChanged("jan@example.com")
        onPasswordChanged("sekret1")
    }
}
