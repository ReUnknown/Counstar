package app.counstar

import android.content.ComponentName
import android.content.Intent
import android.widget.Toast
import app.counstar.data.Links
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
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import app.counstar.data.Settings
import app.counstar.playback.CounstarPlayer
import app.counstar.playback.PlaybackService
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import app.counstar.ui.CounstarApp
import app.counstar.ui.theme.CounstarTheme
import app.counstar.ui.theme.isCounstarDark
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : ComponentActivity() {
    /** Connects to the playback service while Counstar is showing, so the video can carry on after. */
    private var controller: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG && Timber.forest().isEmpty()) Timber.plant(Timber.DebugTree())
        Settings.initialize(this)
        // Animated images (GIFs in comments) play instead of showing their first frame.
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context).components { add(AnimatedImageDecoder.Factory()) }.build()
        }
        // The screen never dims or locks while Counstar is open.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        CounstarPlayer.initialize(this)
        lifecycleScope.launch { SessionRepository.restore(applicationContext) }
        if (savedInstanceState == null) openLink(intent)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openLink(intent)
    }

    /** A YouTube link tapped somewhere, or shared to Counstar: open it in the app. */
    private fun openLink(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return
        val link = Links.find(text)
        if (link == null) {
            Toast.makeText(this, "That isn't a YouTube link", Toast.LENGTH_SHORT).show()
            return
        }
        Links.pending = link
    }

    override fun onStart() {
        super.onStart()
        controller = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
    }

    override fun onStop() {
        super.onStop()
        // With background play off, leaving the app pauses the video (like YouTube without Premium).
        if (!isChangingConfigurations && !Settings.backgroundPlay) CounstarPlayer.player.pause()
        controller?.let(MediaController::releaseFuture)
        controller = null
    }
}
