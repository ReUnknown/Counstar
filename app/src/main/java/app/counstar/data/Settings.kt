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
        theme = runCatching { Theme.valueOf(p.getString("theme", null) ?: "System") }.getOrDefault(Theme.System)
    }

    fun saveAmbientGlow(on: Boolean) {
        ambientGlow = on
        prefs?.edit()?.putBoolean("ambient_glow", on)?.apply()
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
