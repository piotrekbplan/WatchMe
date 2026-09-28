package pl.watchme.data.auth

import java.io.IOException
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.slf4j.LoggerFactory
import pl.watchme.domain.DomainError
import pl.watchme.domain.Outcome
import pl.watchme.domain.model.Email
import pl.watchme.domain.model.Password
import pl.watchme.domain.model.Session
import pl.watchme.domain.model.UserId
import pl.watchme.domain.repository.AuthRepository

class AuthRepositoryImpl @Inject constructor(
    private val remote: AuthRemoteSource,
    private val sessions: SessionStore,
    private val clock: Clock,
) : AuthRepository {

    private val log = LoggerFactory.getLogger(AuthRepositoryImpl::class.java)

    override fun observeSession(): Flow<Session?> = sessions.observe().map { it?.toDomain() }

    override suspend fun signIn(email: Email, password: String): Outcome<Session> =
        authenticate(email, AuthOperation.SIGN_IN) { remote.signIn(email.value, password) }

    override suspend fun signUp(email: Email, password: Password): Outcome<Session> =
        authenticate(email, AuthOperation.SIGN_UP) { remote.signUp(email.value, password.value) }
            .also { outcome -> if (outcome is Outcome.Success) sendVerification() }

    override suspend fun sendPasswordReset(email: Email): Outcome<Unit> =
        catching(AuthOperation.RESET) { remote.sendPasswordReset(email.value) }

    override suspend fun signOut() = sessions.clear()

    private suspend fun authenticate(
        email: Email,
        operation: AuthOperation,
        call: suspend () -> AuthTokens,
    ): Outcome<Session> = catching(operation) {
        val tokens = call()
        val stored = StoredSession(
            uid = tokens.uid,
            email = tokens.email ?: email.value,
            idToken = tokens.idToken,
            refreshToken = tokens.refreshToken,
            expiresAtMillis = clock.millis() + Duration.ofSeconds(tokens.expiresInSeconds).toMillis(),
        )
        sessions.save(stored)
        Session(UserId(stored.uid), email)
    }

    private suspend fun sendVerification() {
        val idToken = sessions.current()?.idToken ?: return
        try {
            remote.sendEmailVerification(idToken)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Verification email was not sent", e)
        }
    }

    private suspend fun <T> catching(operation: AuthOperation, block: suspend () -> T): Outcome<T> =
        try {
            Outcome.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthRemoteException) {
            Outcome.Failure(FirebaseAuthErrors.map(e.code, operation))
        } catch (e: IOException) {
            log.warn("Firebase Auth is unreachable", e)
            Outcome.Failure(DomainError.Network)
        } catch (e: Exception) {
            log.error("Unexpected authentication failure", e)
            Outcome.Failure(DomainError.Unknown(e::class.simpleName))
        }

    private fun StoredSession.toDomain(): Session? = Email.of(email)?.let { Session(UserId(uid), it) }
}
