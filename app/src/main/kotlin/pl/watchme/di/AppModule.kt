package pl.watchme.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import pl.watchme.BuildConfig
import pl.watchme.data.di.EpgBaseUrl
import pl.watchme.data.di.FirebaseApiKey
import pl.watchme.data.di.FirebaseProjectId

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    fun clock(): Clock = Clock.systemUTC()

    @Provides
    fun zone(): ZoneId = ZoneId.systemDefault()

    @Provides
    @EpgBaseUrl
    fun epgBaseUrl(): String = BuildConfig.EPG_BASE_URL

    @Provides
    @FirebaseApiKey
    fun firebaseApiKey(): String = BuildConfig.FIREBASE_API_KEY

    @Provides
    @FirebaseProjectId
    fun firebaseProjectId(): String = BuildConfig.FIREBASE_PROJECT_ID
}
