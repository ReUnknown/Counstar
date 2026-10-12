package app.counstar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.lifecycleScope
import app.counstar.data.SessionRepository
import app.counstar.playback.CounstarPlayer
import app.counstar.ui.CounstarApp
import app.counstar.ui.theme.CounstarTheme
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG && Timber.forest().isEmpty()) Timber.plant(Timber.DebugTree())
        CounstarPlayer.initialize(this)
        lifecycleScope.launch { SessionRepository.restore(applicationContext) }
        setContent {
            CounstarTheme {
                Surface(color = MaterialTheme.colorScheme.background) { CounstarApp() }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Like YouTube: leaving the app pauses the video.
        if (!isChangingConfigurations) CounstarPlayer.player.pause()
    }
}
