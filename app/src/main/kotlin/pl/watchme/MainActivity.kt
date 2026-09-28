package pl.watchme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import pl.watchme.designsystem.theme.WatchMeTheme
import pl.watchme.navigation.StartViewModel
import pl.watchme.navigation.WatchMeNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val startViewModel: StartViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WatchMeTheme {
                val start by startViewModel.destination.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    start?.let {
                        WatchMeNavHost(
                            start = it,
                            sessionEnded = startViewModel.sessionEnded,
                            appVersion = BuildConfig.VERSION_NAME,
                        )
                    }
                }
            }
        }
    }
}
