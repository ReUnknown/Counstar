package app.counstar.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Counstar's settings, saved on the phone. Reading one in a composable follows its changes. */
object Settings {
    enum class Theme(val title: String) { System("System"), Dark("Dark"), Light("Light") }

    /** Highest quality to stream, as video height. */
    val qualities = listOf(1080, 720, 480, 360)

    private var prefs: SharedPreferences? = null

    /** The video's own light glowing around the player. */
    var ambientGlow by mutableStateOf(true)
        private set
    var maxHeight by mutableIntStateOf(1080)
        private set
    /** Creators' posts and polls: in feeds, search and on channels (off: never shown). */
    var showPosts by mutableStateOf(true)
        private set
    /** Shorts: the tab, shelves in feeds and search, and opening Shorts in the Shorts player. */
    var showShorts by mutableStateOf(true)
        private set
    /** In the Shorts player: go on to the next Short when one ends (instead of looping). */
    var shortsAutoScroll by mutableStateOf(false)
        private set
    /** Searches you've made, newest first. */
    var searchHistory by mutableStateOf<List<String>>(emptyList())
        private set
    /** Keep playing (as sound) when you leave Counstar or turn the screen off. */
    var backgroundPlay by mutableStateOf(true)
        private set
    var theme by mutableStateOf(Theme.System)
        private set

    fun initialize(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs = p
        ambientGlow = p.getBoolean("ambient_glow", true)
        maxHeight = p.getInt("max_height", 1080)
        backgroundPlay = p.getBoolean("background_play", true)
        showPosts = p.getBoolean("show_posts", true)
        showShorts = p.getBoolean("show_shorts", true)
        shortsAutoScroll = p.getBoolean("shorts_auto_scroll", false)
        searchHistory = p.getString("search_history", null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
        theme = runCatching { Theme.valueOf(p.getString("theme", null) ?: "System") }.getOrDefault(Theme.System)
    }

    fun saveAmbientGlow(on: Boolean) {
        ambientGlow = on
        prefs?.edit()?.putBoolean("ambient_glow", on)?.apply()
    }

    fun saveShowShorts(on: Boolean) {
        showShorts = on
        prefs?.edit()?.putBoolean("show_shorts", on)?.apply()
    }

    fun saveShortsAutoScroll(on: Boolean) {
        shortsAutoScroll = on
        prefs?.edit()?.putBoolean("shorts_auto_scroll", on)?.apply()
    }

    fun saveShowPosts(on: Boolean) {
        showPosts = on
        prefs?.edit()?.putBoolean("show_posts", on)?.apply()
    }

    /** Puts [query] at the top of your search history (once), keeping the last 30. */
    fun rememberSearch(query: String) {
        val q = query.trim().replace('\n', ' ')
        if (q.isEmpty()) return
        saveHistory(listOf(q) + searchHistory.filterNot { it.equals(q, ignoreCase = true) })
    }

    fun forgetSearch(query: String) = saveHistory(searchHistory - query)

    private fun saveHistory(list: List<String>) {
        searchHistory = list.take(30)
        prefs?.edit()?.putString("search_history", searchHistory.joinToString("\n"))?.apply()
    }

    fun saveBackgroundPlay(on: Boolean) {
        backgroundPlay = on
        prefs?.edit()?.putBoolean("background_play", on)?.apply()
    }

    fun saveMaxHeight(height: Int) {
        maxHeight = height
        prefs?.edit()?.putInt("max_height", height)?.apply()
    }

    fun saveTheme(value: Theme) {
        theme = value
        prefs?.edit()?.putString("theme", value.name)?.apply()
    }
}
