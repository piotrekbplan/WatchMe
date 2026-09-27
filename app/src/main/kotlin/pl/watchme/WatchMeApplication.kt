package pl.watchme

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import pl.watchme.logging.CrashLogger

@HiltAndroidApp
class WatchMeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install()
    }
}
