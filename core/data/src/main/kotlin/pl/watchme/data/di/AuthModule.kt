package pl.watchme.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import pl.watchme.data.auth.AuthInterceptor
import pl.watchme.data.auth.AuthRemoteSource
import pl.watchme.data.auth.AuthRepositoryImpl
import pl.watchme.data.auth.BlobStore
import pl.watchme.data.auth.DataStoreBlobStore
import pl.watchme.data.auth.EncryptedSessionStore
import pl.watchme.data.auth.IdentityToolkitApi
import pl.watchme.data.auth.RetrofitAuthRemoteSource
import pl.watchme.data.auth.SecureTokenApi
import pl.watchme.data.auth.SessionCipher
import pl.watchme.data.auth.SessionStore
import pl.watchme.data.auth.TinkSessionCipher
import pl.watchme.data.auth.TokenAuthenticator
import pl.watchme.data.sync.FirestoreApi
import pl.watchme.data.sync.FirestoreJson
import pl.watchme.data.sync.FirestoreLineupRemoteSource
import pl.watchme.data.sync.LineupRemoteSource
import pl.watchme.data.sync.LineupSyncScheduler
import pl.watchme.data.sync.WorkManagerLineupSyncScheduler
import pl.watchme.domain.repository.AuthRepository
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthorizedClient

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    abstract fun authRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    abstract fun authRemoteSource(impl: RetrofitAuthRemoteSource): AuthRemoteSource

    @Binds
    abstract fun sessionStore(impl: EncryptedSessionStore): SessionStore

    @Binds
    abstract fun blobStore(impl: DataStoreBlobStore): BlobStore

    @Binds
    abstract fun sessionCipher(impl: TinkSessionCipher): SessionCipher

    @Binds
    abstract fun lineupRemoteSource(impl: FirestoreLineupRemoteSource): LineupRemoteSource

    @Binds
    abstract fun lineupSyncScheduler(impl: WorkManagerLineupSyncScheduler): LineupSyncScheduler

    companion object {

        private val authJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
        private val jsonType = "application/json".toMediaType()

        @Provides
        @Singleton
        fun identityToolkitApi(client: OkHttpClient): IdentityToolkitApi = Retrofit.Builder()
            .baseUrl("https://identitytoolkit.googleapis.com/")
            .client(client)
            .addConverterFactory(authJson.asConverterFactory(jsonType))
            .build()
            .create()

        @Provides
        @Singleton
        fun secureTokenApi(client: OkHttpClient): SecureTokenApi = Retrofit.Builder()
            .baseUrl("https://securetoken.googleapis.com/")
            .client(client)
            .addConverterFactory(authJson.asConverterFactory(jsonType))
            .build()
            .create()

        @Provides
        @Singleton
        @AuthorizedClient
        fun authorizedClient(
            client: OkHttpClient,
            interceptor: AuthInterceptor,
            authenticator: TokenAuthenticator,
        ): OkHttpClient = client.newBuilder()
            .addInterceptor(interceptor)
            .authenticator(authenticator)
            .build()

        @Provides
        @Singleton
        fun firestoreApi(@AuthorizedClient client: OkHttpClient): FirestoreApi = Retrofit.Builder()
            .baseUrl("https://firestore.googleapis.com/")
            .client(client)
            .addConverterFactory(FirestoreJson.asConverterFactory(jsonType))
            .build()
            .create()
    }
}
