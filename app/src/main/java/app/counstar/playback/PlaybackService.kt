package app.counstar.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.counstar.MainActivity
import app.counstar.data.Settings

/**
 * Keeps the video playing with Counstar in the background or the screen off, and gives it
 * Android's media controls: the notification, the lock screen, headphone buttons. Tapping the
 * notification opens Counstar again.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        Settings.initialize(this)
        CounstarPlayer.initialize(this)
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, CounstarPlayer.player).setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiping Counstar away from recent apps stops the video too. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        CounstarPlayer.player.pause()
        stopSelf()
    }

    override fun onDestroy() {
        // The player is the app's one player: only the session goes.
        session?.release()
        session = null
        super.onDestroy()
    }
}
