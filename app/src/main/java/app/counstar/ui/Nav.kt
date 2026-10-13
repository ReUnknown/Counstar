package app.counstar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.counstar.data.Playlist
import app.counstar.data.Post
import app.counstar.data.VideoItem
import app.counstar.playback.CounstarPlayer

enum class Tab { Home, Subscriptions, You }

/** Where you are in the app. Search, notifications, pages and watch open over the tabs. */
object Nav {
    var tab by mutableStateOf(Tab.Home)
    var searchOpen by mutableStateOf(false)
    var notificationsOpen by mutableStateOf(false)
    var historyOpen by mutableStateOf(false)
    /** A playlist opened from your library. */
    var playlist by mutableStateOf<Playlist?>(null)
    var settingsOpen by mutableStateOf(false)
    /** Channel pages open, the top one showing (back goes to the one before). */
    var channels by mutableStateOf<List<String>>(emptyList())
    /** Every subscription, as a list. */
    var allSubscriptionsOpen by mutableStateOf(false)
    var watchOpen by mutableStateOf(false)
    /** A Short, opened from a link (Shorts aren't in feeds). */
    var short by mutableStateOf<VideoItem?>(null)
    /** A creator's post, opened with its comments. */
    var post by mutableStateOf<Post?>(null)

    /** Something full screen is open over the tab (not the watch page). */
    val overlay: Boolean get() = searchOpen || notificationsOpen || historyOpen || playlist != null || channels.isNotEmpty() || allSubscriptionsOpen || post != null

    /** Opens a channel's page over everything (the video playing shrinks to the mini player). */
    fun openChannel(id: String?) {
        if (id.isNullOrBlank()) return
        watchOpen = false
        playlist = null
        post = null
        if (channels.lastOrNull() != id) channels = channels + id
    }

    fun closeChannel() {
        channels = channels.dropLast(1)
    }

    fun goTo(tab: Tab) {
        searchOpen = false
        notificationsOpen = false
        historyOpen = false
        playlist = null
        channels = emptyList()
        allSubscriptionsOpen = false
        post = null
        this.tab = tab
    }

    fun watch(item: VideoItem, startAtMs: Long = 0L) {
        if (short != null) {
            short = null
            CounstarPlayer.changeLoop(false)
        }
        CounstarPlayer.play(item, startAtMs)
        watchOpen = true
    }

    /** Plays a Short full screen, looping, over everything. */
    fun openShort(item: VideoItem) {
        watchOpen = false
        short = item
        CounstarPlayer.changeLoop(true)
        CounstarPlayer.play(item)
    }

    /** Leaves the Short; it stops (it never goes to the mini player). */
    fun closeShort() {
        if (short == null) return
        short = null
        CounstarPlayer.changeLoop(false)
        CounstarPlayer.stop()
    }

    fun openPost(p: Post) {
        watchOpen = false
        post = p
    }

    fun reset() {
        goTo(Tab.Home)
        settingsOpen = false
        watchOpen = false
        short = null
    }
}
