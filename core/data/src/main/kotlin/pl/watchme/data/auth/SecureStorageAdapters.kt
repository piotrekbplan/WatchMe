package pl.watchme.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.slf4j.LoggerFactory

private val Context.sessionDataStore by preferencesDataStore(name = "session")

@Singleton
class DataStoreBlobStore @Inject constructor(@param:ApplicationContext private val context: Context) : BlobStore {

    private val key = stringPreferencesKey("encrypted_session")

    override val data: Flow<String?> = context.sessionDataStore.data.map { it[key] }

    override suspend fun write(value: String?) {
        context.sessionDataStore.edit { preferences ->
            if (value == null) preferences.remove(key) else preferences[key] = value
        }
    }
}

@Singleton
class TinkSessionCipher @Inject constructor(@param:ApplicationContext private val context: Context) : SessionCipher {

    private val log = LoggerFactory.getLogger(TinkSessionCipher::class.java)

    private val aead: Aead by lazy {
        AeadConfig.register()
        try {
            buildAead()
        } catch (e: GeneralSecurityException) {
            recreateAfter(e)
        } catch (e: IOException) {
            recreateAfter(e)
        }
    }

    override fun encrypt(plain: ByteArray): ByteArray = aead.encrypt(plain, ASSOCIATED_DATA)

    override fun decrypt(encrypted: ByteArray): ByteArray = aead.decrypt(encrypted, ASSOCIATED_DATA)

    private fun buildAead(): Aead = AndroidKeysetManager.Builder()
        .withSharedPref(context, KEYSET_NAME, KEYSET_PREFERENCES)
        .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
        .withMasterKeyUri(MASTER_KEY_URI_PREFIX + MASTER_KEY_ALIAS)
        .build()
        .keysetHandle
        .getPrimitive(RegistryConfiguration.get(), Aead::class.java)

    private fun recreateAfter(error: Exception): Aead {
        log.warn("Session keyset is unusable, creating a new one", error)
        context.deleteSharedPreferences(KEYSET_PREFERENCES)
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(MASTER_KEY_ALIAS)
        return buildAead()
    }

    private companion object {
        const val KEYSET_NAME = "watchme_session_keyset"
        const val KEYSET_PREFERENCES = "watchme_session_keyset_prefs"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val MASTER_KEY_URI_PREFIX = "android-keystore://"
        const val MASTER_KEY_ALIAS = "watchme_session_master_key"
        val ASSOCIATED_DATA = "watchme-session".encodeToByteArray()
    }
}
