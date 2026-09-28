package pl.watchme.data.fakes

import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import pl.watchme.data.auth.AuthRemoteException
import pl.watchme.data.auth.AuthRemoteSource
import pl.watchme.data.auth.AuthTokens
import pl.watchme.data.auth.SessionStore
import pl.watchme.data.auth.StoredSession

class FakeSessionStore(initial: StoredSession? = null) : SessionStore {
    val session = MutableStateFlow(initial)
    var saveFailure: Exception? = null

    override fun observe(): Flow<StoredSession?> = session

    override suspend fun current(): StoredSession? = session.value

    override suspend fun save(session: StoredSession) {
        saveFailure?.let { throw it }
        this.session.value = session
    }

    override suspend fun clear() {
        session.value = null
    }
}

class FakeAuthRemoteSource : AuthRemoteSource {
    var tokens = AuthTokens("uid-1", "jan@example.com", "id-1", "refresh-1", 3600)
    var refreshedTokens = AuthTokens("uid-1", null, "id-2", "refresh-2", 3600)
    var failure: Exception? = null
    var verificationFailure: Exception? = null
    var refreshLatencyMillis = 0L
    var refreshCalls = 0
    val verificationsSent = mutableListOf<String>()
    val resetsSent = mutableListOf<String>()

    override suspend fun signIn(email: String, password: String): AuthTokens = tokensOrThrow()

    override suspend fun signUp(email: String, password: String): AuthTokens = tokensOrThrow()

    override suspend fun sendPasswordReset(email: String) {
        failure?.let { throw it }
        resetsSent += email
    }

    override suspend fun sendEmailVerification(idToken: String) {
        verificationFailure?.let { throw it }
        verificationsSent += idToken
    }

    override suspend fun refresh(refreshToken: String): AuthTokens {
        refreshCalls++
        if (refreshLatencyMillis > 0) delay(refreshLatencyMillis)
        failure?.let { throw it }
        return refreshedTokens
    }

    private fun tokensOrThrow(): AuthTokens {
        failure?.let { throw it }
        return tokens
    }

    companion object {
        fun rejected(code: String) = AuthRemoteException(code)
        fun offline() = IOException("offline")
    }
}
