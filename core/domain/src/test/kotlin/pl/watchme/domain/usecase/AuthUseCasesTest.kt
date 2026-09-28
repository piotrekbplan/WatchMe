package pl.watchme.domain.usecase

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import pl.watchme.domain.DomainError
import pl.watchme.domain.Field
import pl.watchme.domain.Outcome
import pl.watchme.domain.model.ChannelLineup
import pl.watchme.testing.FakeAuthRepository
import pl.watchme.testing.FakeLineupRepository
import pl.watchme.testing.TestData

class AuthUseCasesTest {

    private val auth = FakeAuthRepository()
    private val lineups = FakeLineupRepository(ChannelLineup(setOf(TestData.tvp.id), null, Instant.EPOCH))

    @Test
    fun `sign in validates email before calling the backend`() = runTest {
        val outcome = SignInUseCase(auth, lineups)("nie-mail", "sekret1")

        assertThat(outcome).isEqualTo(Outcome.Failure(DomainError.Validation(Field.EMAIL)))
        assertThat(auth.signInCalls).isEmpty()
    }

    @Test
    fun `sign in requires a password`() = runTest {
        val outcome = SignInUseCase(auth, lineups)("jan@example.com", "  ")

        assertThat(outcome).isEqualTo(Outcome.Failure(DomainError.Validation(Field.PASSWORD)))
    }

    @Test
    fun `successful sign in syncs the lineup`() = runTest {
        val outcome = SignInUseCase(auth, lineups)(" jan@example.com ", "sekret1")

        assertThat(outcome).isEqualTo(Outcome.Success(TestData.session))
        assertThat(auth.signInCalls.single().first.value).isEqualTo("jan@example.com")
        assertThat(lineups.syncCalls).isEqualTo(1)
    }

    @Test
    fun `failed sign in does not sync`() = runTest {
        auth.nextOutcome = Outcome.Failure(DomainError.InvalidCredentials)

        val outcome = SignInUseCase(auth, lineups)("jan@example.com", "zlehaslo")

        assertThat(outcome).isEqualTo(Outcome.Failure(DomainError.InvalidCredentials))
        assertThat(lineups.syncCalls).isEqualTo(0)
    }

    @Test
    fun `sign up validates password length and confirmation`() = runTest {
        val signUp = SignUpUseCase(auth, lineups)

        assertThat(signUp("jan@example.com", "123", "123"))
            .isEqualTo(Outcome.Failure(DomainError.Validation(Field.PASSWORD)))
        assertThat(signUp("jan@example.com", "sekret1", "sekret2"))
            .isEqualTo(Outcome.Failure(DomainError.Validation(Field.PASSWORD_CONFIRMATION)))
        assertThat(auth.signUpCalls).isEmpty()
    }

    @Test
    fun `successful sign up syncs the local lineup`() = runTest {
        val outcome = SignUpUseCase(auth, lineups)("jan@example.com", "sekret1", "sekret1")

        assertThat(outcome).isEqualTo(Outcome.Success(TestData.session))
        assertThat(lineups.syncCalls).isEqualTo(1)
    }

    @Test
    fun `password reset for unknown account still reports success`() = runTest {
        auth.resetOutcome = Outcome.Failure(DomainError.NotFound)

        assertThat(SendPasswordResetUseCase(auth)("jan@example.com")).isEqualTo(Outcome.Success(Unit))
    }

    @Test
    fun `password reset validates email and passes other errors`() = runTest {
        val reset = SendPasswordResetUseCase(auth)

        assertThat(reset("jan")).isEqualTo(Outcome.Failure(DomainError.Validation(Field.EMAIL)))

        auth.resetOutcome = Outcome.Failure(DomainError.TooManyAttempts)
        assertThat(reset("jan@example.com")).isEqualTo(Outcome.Failure(DomainError.TooManyAttempts))
    }

    @Test
    fun `sign out clears the session and the local lineup`() = runTest {
        auth.session.value = TestData.session

        SignOutUseCase(auth, lineups)()

        assertThat(auth.session.value).isNull()
        assertThat(lineups.lineup.value).isNull()
        assertThat(lineups.clearCalls).isEqualTo(1)
    }
}
