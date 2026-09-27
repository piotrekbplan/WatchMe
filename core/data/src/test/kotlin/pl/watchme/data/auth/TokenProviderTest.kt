package pl.watchme.data.auth

import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import pl.watchme.data.fakes.FakeAuthRemoteSource
import pl.watchme.data.fakes.FakeSessionStore
import pl.watchme.testing.MutableClock

class TokenProviderTest {

    private val now = Instant.parse("2026-09-28T10:00:00Z")
    private val remote = FakeAuthRemoteSource()

    private fun session(idToken: String = "id-1", expiresInSeconds: Long) =
        StoredSession("uid-1", "jan@example.com", idToken, "refresh-1", now.plusSeconds(expiresInSeconds).toEpochMilli())

    private fun provider(store: FakeSessionStore) = TokenProvider(store, remote, MutableClock(now))

    @Test
    fun `no session means no token`() = runTest {
        assertThat(provider(FakeSessionStore()).validToken()).isNull()
    }

    @Test
    fun `token valid for longer than a minute is reused`() = runTest {
        val store = FakeSessionStore(session(expiresInSeconds = 600))

        assertThat(provider(store).validToken()).isEqualTo("id-1")
        assertThat(remote.refreshCalls).isEqualTo(0)
    }

    @Test
    fun `token about to expire is refreshed and stored`() = runTest {
        val store = FakeSessionStore(session(expiresInSeconds = 30))

        assertThat(provider(store).validToken()).isEqualTo("id-2")
        assertThat(store.session.value).isNotNull().isEqualTo(
            StoredSession("uid-1", "jan@example.com", "id-2", "refresh-2", now.plusSeconds(3600).toEpochMilli()),
        )
    }

    @Test
    fun `concurrent callers share a single refresh`() = runTest {
        remote.refreshLatencyMillis = 100
        val tokens = provider(FakeSessionStore(session(expiresInSeconds = -10)))

        val results = (1..5).map { async { tokens.validToken() } }.awaitAll()

        assertThat(remote.refreshCalls).isEqualTo(1)
        assertThat(results).each { it.isEqualTo("id-2") }
    }

    @Test
    fun `unauthorized response with the current token triggers a refresh`() = runTest {
        val store = FakeSessionStore(session(expiresInSeconds = 600))

        assertThat(provider(store).refreshAfterUnauthorized("id-1")).isEqualTo("id-2")
        assertThat(remote.refreshCalls).isEqualTo(1)
    }

    @Test
    fun `unauthorized response with an already replaced token reuses the new one`() = runTest {
        val store = FakeSessionStore(session(idToken = "id-2", expiresInSeconds = 600))

        assertThat(provider(store).refreshAfterUnauthorized("id-1")).isEqualTo("id-2")
        assertThat(remote.refreshCalls).isEqualTo(0)
    }

    @Test
    fun `rejected refresh signs the user out`() = runTest {
        remote.failure = FakeAuthRemoteSource.rejected("INVALID_REFRESH_TOKEN")
        val store = FakeSessionStore(session(expiresInSeconds = -10))

        assertThat(provider(store).validToken()).isNull()
        assertThat(store.session.value).isNull()
    }

    @Test
    fun `offline refresh keeps the session`() = runTest {
        remote.failure = FakeAuthRemoteSource.offline()
        val store = FakeSessionStore(session(expiresInSeconds = -10))

        assertThat(provider(store).validToken()).isNull()
        assertThat(store.session.value).isNotNull()
    }
}
