package app.counstar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.counstar.data.Playlist
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
    var watchOpen by mutableStateOf(false)

    /** Something full screen is open over the tab (not the watch page). */
    val overlay: Boolean get() = searchOpen || notificationsOpen || historyOpen || playlist != null

    fun goTo(tab: Tab) {
        searchOpen = false
        notificationsOpen = false
        historyOpen = false
        playlist = null
        this.tab = tab
    }

    fun watch(item: VideoItem) {
        CounstarPlayer.play(item)
        watchOpen = true
    }

    fun reset() {
        goTo(Tab.Home)
        settingsOpen = false
        watchOpen = false
    }
}
