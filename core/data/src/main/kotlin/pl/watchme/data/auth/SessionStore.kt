package pl.watchme.data.auth

import java.io.IOException
import java.security.GeneralSecurityException
import java.util.Base64
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class StoredSession(
    val uid: String,
    val email: String,
    val idToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
)

interface SessionStore {
    fun observe(): Flow<StoredSession?>
    suspend fun current(): StoredSession?
    suspend fun save(session: StoredSession)
    suspend fun clear()
}

interface SessionCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(encrypted: ByteArray): ByteArray
}

interface BlobStore {
    val data: Flow<String?>
    suspend fun write(value: String?)
}

class EncryptedSessionStore @Inject constructor(
    private val blobs: BlobStore,
    private val cipher: SessionCipher,
) : SessionStore {

    private val json = Json { ignoreUnknownKeys = true }

    override fun observe(): Flow<StoredSession?> =
        blobs.data.map(::readOrWipe).distinctUntilChanged().flowOn(Dispatchers.Default)

    override suspend fun current(): StoredSession? = readOrWipe(blobs.data.first())

    override suspend fun save(session: StoredSession) {
        val plain = json.encodeToString(StoredSession.serializer(), session).encodeToByteArray()
        val blob = withContext(Dispatchers.Default) { Base64.getEncoder().encodeToString(cipher.encrypt(plain)) }
        blobs.write(blob)
    }

    override suspend fun clear() = blobs.write(null)

    private suspend fun readOrWipe(blob: String?): StoredSession? {
        if (blob == null) return null
        return withContext(Dispatchers.Default) { decode(blob) } ?: null.also { blobs.write(null) }
    }

    private fun decode(blob: String): StoredSession? =
        try {
            val plain = cipher.decrypt(Base64.getDecoder().decode(blob)).decodeToString()
            json.decodeFromString(StoredSession.serializer(), plain)
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            null
        }
}
