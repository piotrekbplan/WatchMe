package pl.watchme.data.auth

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import java.security.GeneralSecurityException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class EncryptedSessionStoreTest {

    private val blobs = InMemoryBlobStore()
    private val cipher = ReversingCipher()
    private val store = EncryptedSessionStore(blobs, cipher)
    private val session = StoredSession("uid-1", "jan@example.com", "id-token-123", "refresh-456", 1_000L)

    @Test
    fun `saved session is read back`() = runTest {
        store.save(session)

        assertThat(store.current()).isEqualTo(session)
    }

    @Test
    fun `stored blob does not reveal tokens or email`() = runTest {
        store.save(session)

        val blob = blobs.data.value
        assertThat(blob).isNotNull().doesNotContain("id-token-123")
        assertThat(blob).isNotNull().doesNotContain("jan@example.com")
    }

    @Test
    fun `observers see saves and clears`() = runTest {
        store.observe().test {
            assertThat(awaitItem()).isNull()

            store.save(session)
            assertThat(awaitItem()).isEqualTo(session)

            store.clear()
            assertThat(awaitItem()).isNull()
        }
    }

    @Test
    fun `undecryptable session is wiped`() = runTest {
        store.save(session)
        cipher.broken = true

        assertThat(store.current()).isNull()
        assertThat(blobs.data.value).isNull()
    }

    @Test
    fun `garbage blob is wiped`() = runTest {
        blobs.data.value = "%%% not base64 %%%"

        assertThat(store.current()).isNull()
        assertThat(blobs.data.value).isNull()
    }

    private class InMemoryBlobStore : BlobStore {
        override val data = MutableStateFlow<String?>(null)

        override suspend fun write(value: String?) {
            data.value = value
        }
    }

    private class ReversingCipher : SessionCipher {
        var broken = false

        override fun encrypt(plain: ByteArray): ByteArray = plain.reversedArray().map { (it + 1).toByte() }.toByteArray()

        override fun decrypt(encrypted: ByteArray): ByteArray {
            if (broken) throw GeneralSecurityException("key lost")
            return encrypted.map { (it - 1).toByte() }.toByteArray().reversedArray()
        }
    }
}

