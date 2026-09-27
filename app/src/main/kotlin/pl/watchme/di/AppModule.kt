package pl.watchme.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import pl.watchme.BuildConfig
import pl.watchme.data.di.EpgBaseUrl

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
}
