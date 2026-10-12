package app.counstar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.counstar.data.VideoItem
import app.counstar.playback.CounstarPlayer

enum class Tab { Home, Subscriptions, You }

/** Where you are in the app. Search, notifications and watch open over the tabs. */
object Nav {
    var tab by mutableStateOf(Tab.Home)
    var searchOpen by mutableStateOf(false)
    var notificationsOpen by mutableStateOf(false)
    var watchOpen by mutableStateOf(false)

    fun goTo(tab: Tab) {
        searchOpen = false
        notificationsOpen = false
        this.tab = tab
    }

    fun watch(item: VideoItem) {
        CounstarPlayer.play(item)
        watchOpen = true
    }

    fun reset() {
        tab = Tab.Home
        searchOpen = false
        notificationsOpen = false
        watchOpen = false
    }
}
