package pl.watchme.data.auth

import java.io.IOException
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import org.slf4j.LoggerFactory
import pl.watchme.domain.DomainError

@Singleton
class TokenProvider @Inject constructor(
    private val sessions: SessionStore,
    private val remote: AuthRemoteSource,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(TokenProvider::class.java)
    private val mutex = Mutex()

    suspend fun validToken(): String? {
        val session = sessions.current() ?: return null
        return if (session.expiresSoon()) refreshUnlessReplaced(session.idToken) else session.idToken
    }

    suspend fun refreshAfterUnauthorized(staleToken: String): String? = refreshUnlessReplaced(staleToken)

    private suspend fun refreshUnlessReplaced(staleToken: String): String? = mutex.withLock {
        val session = sessions.current() ?: return null
        if (session.idToken != staleToken && !session.expiresSoon()) return session.idToken
        try {
            val tokens = remote.refresh(session.refreshToken)
            val renewed = session.copy(
                idToken = tokens.idToken,
                refreshToken = tokens.refreshToken,
                expiresAtMillis = clock.millis() + Duration.ofSeconds(tokens.expiresInSeconds).toMillis(),
            )
            sessions.save(renewed)
            renewed.idToken
        } catch (e: AuthRemoteException) {
            if (FirebaseAuthErrors.map(e.code, AuthOperation.REFRESH) == DomainError.Unauthorized) {
                log.warn("Session for uid {} was revoked, signing out", session.uid)
                sessions.clear()
            }
            null
        } catch (e: IOException) {
            log.warn("Token refresh failed", e)
            null
        }
    }

    private fun StoredSession.expiresSoon(): Boolean = expiresAtMillis - clock.millis() < REFRESH_MARGIN_MILLIS

    private companion object {
        val REFRESH_MARGIN_MILLIS = Duration.ofSeconds(60).toMillis()
    }
}

class AuthInterceptor @Inject constructor(private val tokens: TokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokens.validToken() }
        val request = token?.let { chain.request().withBearer(it) } ?: chain.request()
        return chain.proceed(request)
    }
}

class TokenAuthenticator @Inject constructor(private val tokens: TokenProvider) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null
        val stale = response.request.header(AUTHORIZATION)?.removePrefix(BEARER) ?: return null
        val fresh = runBlocking { tokens.refreshAfterUnauthorized(stale) } ?: return null
        return response.request.withBearer(fresh)
    }
}

private const val AUTHORIZATION = "Authorization"
private const val BEARER = "Bearer "

private fun Request.withBearer(token: String): Request = newBuilder().header(AUTHORIZATION, BEARER + token).build()
