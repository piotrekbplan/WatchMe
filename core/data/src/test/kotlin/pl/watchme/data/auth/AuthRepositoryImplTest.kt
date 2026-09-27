package pl.watchme.data.auth

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import pl.watchme.data.fakes.FakeAuthRemoteSource
import pl.watchme.data.fakes.FakeSessionStore
import pl.watchme.domain.DomainError
import pl.watchme.domain.Outcome
import pl.watchme.domain.model.Email
import pl.watchme.domain.model.Password
import pl.watchme.domain.model.Session
import pl.watchme.domain.model.UserId
import pl.watchme.testing.MutableClock

class AuthRepositoryImplTest {

    private val now = Instant.parse("2026-09-28T10:00:00Z")
    private val remote = FakeAuthRemoteSource()
    private val sessions = FakeSessionStore()
    private val repository = AuthRepositoryImpl(remote, sessions, MutableClock(now))
    private val email = checkNotNull(Email.of("jan@example.com"))
    private val password = checkNotNull(Password.of("sekret1"))
    private val session = Session(UserId("uid-1"), email)

    @Test
    fun `sign in stores the tokens with their expiry`() = runTest {
        val outcome = repository.signIn(email, "sekret1")

        assertThat(outcome).isEqualTo(Outcome.Success(session))
        assertThat(sessions.session.value).isEqualTo(
            StoredSession("uid-1", "jan@example.com", "id-1", "refresh-1", now.plusSeconds(3600).toEpochMilli()),
        )
    }

    @Test
    fun `rejected credentials store nothing`() = runTest {
        remote.failure = FakeAuthRemoteSource.rejected("INVALID_LOGIN_CREDENTIALS")

        assertThat(repository.signIn(email, "zle")).isEqualTo(Outcome.Failure(DomainError.InvalidCredentials))
        assertThat(sessions.session.value).isNull()
    }

    @Test
    fun `offline sign in is a network error`() = runTest {
        remote.failure = FakeAuthRemoteSource.offline()

        assertThat(repository.signIn(email, "sekret1")).isEqualTo(Outcome.Failure(DomainError.Network))
    }

    @Test
    fun `sign up stores the session and sends a verification email`() = runTest {
        assertThat(repository.signUp(email, password)).isEqualTo(Outcome.Success(session))
        assertThat(remote.verificationsSent).containsExactly("id-1")
    }

    @Test
    fun `failed verification email does not fail the sign up`() = runTest {
        remote.verificationFailure = FakeAuthRemoteSource.offline()

        assertThat(repository.signUp(email, password)).isEqualTo(Outcome.Success(session))
    }

    @Test
    fun `taken email is reported`() = runTest {
        remote.failure = FakeAuthRemoteSource.rejected("EMAIL_EXISTS")

        assertThat(repository.signUp(email, password)).isEqualTo(Outcome.Failure(DomainError.EmailAlreadyUsed))
        assertThat(remote.verificationsSent).isEmpty()
    }

    @Test
    fun `password reset reports unknown accounts as not found`() = runTest {
        assertThat(repository.sendPasswordReset(email)).isEqualTo(Outcome.Success(Unit))
        assertThat(remote.resetsSent).containsExactly("jan@example.com")

        remote.failure = FakeAuthRemoteSource.rejected("EMAIL_NOT_FOUND")
        assertThat(repository.sendPasswordReset(email)).isEqualTo(Outcome.Failure(DomainError.NotFound))
    }

    @Test
    fun `session is observed until sign out`() = runTest {
        repository.observeSession().test {
            assertThat(awaitItem()).isNull()

            repository.signIn(email, "sekret1")
            assertThat(awaitItem()).isEqualTo(session)

            repository.signOut()
            assertThat(awaitItem()).isNull()
        }
    }
}
