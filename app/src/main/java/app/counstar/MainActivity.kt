package app.counstar

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import app.counstar.data.SessionRepository
import app.counstar.data.Settings
import app.counstar.playback.CounstarPlayer
import app.counstar.ui.CounstarApp
import app.counstar.ui.theme.CounstarTheme
import app.counstar.ui.theme.isCounstarDark
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG && Timber.forest().isEmpty()) Timber.plant(Timber.DebugTree())
        Settings.initialize(this)
        // The screen never dims or locks while Counstar is open.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        CounstarPlayer.initialize(this)
        lifecycleScope.launch { SessionRepository.restore(applicationContext) }
        setContent {
            val dark = isCounstarDark()
            // Status and navigation bar icons follow the theme: light on dark, dark on light.
            LaunchedEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            CounstarTheme(dark) {
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
