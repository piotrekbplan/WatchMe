package pl.watchme.data.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import pl.watchme.data.local.CatalogDao
import pl.watchme.data.local.GuideSyncDao
import pl.watchme.data.local.LineupDao
import pl.watchme.data.local.ProgrammeDao
import pl.watchme.data.local.RoomTransactionRunner
import pl.watchme.data.local.TransactionRunner
import pl.watchme.data.local.WatchMeDatabase
import pl.watchme.data.remote.EpgApi
import pl.watchme.data.remote.EpgRemoteSource
import pl.watchme.data.remote.RetrofitEpgRemoteSource
import pl.watchme.data.repository.CatalogRepositoryImpl
import pl.watchme.data.repository.GuideRepositoryImpl
import pl.watchme.data.repository.LineupRepositoryImpl
import pl.watchme.domain.repository.CatalogRepository
import pl.watchme.domain.repository.GuideRepository
import pl.watchme.domain.repository.LineupRepository
import pl.watchme.epg.contract.EpgContract
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EpgBaseUrl

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FirebaseApiKey

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FirebaseProjectId

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun catalogRepository(impl: CatalogRepositoryImpl): CatalogRepository

    @Binds
    abstract fun lineupRepository(impl: LineupRepositoryImpl): LineupRepository

    @Binds
    abstract fun guideRepository(impl: GuideRepositoryImpl): GuideRepository

    @Binds
    abstract fun epgRemoteSource(impl: RetrofitEpgRemoteSource): EpgRemoteSource

    @Binds
    abstract fun transactionRunner(impl: RoomTransactionRunner): TransactionRunner

    companion object {

        @Provides
        @Singleton
        fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(15))
            .readTimeout(Duration.ofSeconds(30))
            .build()

        @Provides
        @Singleton
        fun epgApi(client: OkHttpClient, @EpgBaseUrl baseUrl: String): EpgApi = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(EpgContract.json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create()

        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): WatchMeDatabase =
            Room.databaseBuilder(context, WatchMeDatabase::class.java, "watchme.db").build()

        @Provides
        fun programmeDao(database: WatchMeDatabase): ProgrammeDao = database.programmeDao()

        @Provides
        fun guideSyncDao(database: WatchMeDatabase): GuideSyncDao = database.guideSyncDao()

        @Provides
        fun lineupDao(database: WatchMeDatabase): LineupDao = database.lineupDao()

        @Provides
        fun catalogDao(database: WatchMeDatabase): CatalogDao = database.catalogDao()
    }
}
