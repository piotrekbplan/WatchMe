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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREFERENCES)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    override fun encrypt(plain: ByteArray): ByteArray = aead.encrypt(plain, ASSOCIATED_DATA)

    override fun decrypt(encrypted: ByteArray): ByteArray = aead.decrypt(encrypted, ASSOCIATED_DATA)

    private companion object {
        const val KEYSET_NAME = "watchme_session_keyset"
        const val KEYSET_PREFERENCES = "watchme_session_keyset_prefs"
        const val MASTER_KEY_URI = "android-keystore://watchme_session_master_key"
        val ASSOCIATED_DATA = "watchme-session".encodeToByteArray()
    }
}
