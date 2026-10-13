package app.counstar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.counstar.data.Settings

/** YouTube red: watch progress, live rings, the seek bar. */
val YouTubeRed = Color(0xFFFF0033)

/** Whether Counstar is dark right now: the phone's setting, unless Settings picks one. */
@Composable
fun isCounstarDark(): Boolean = when (Settings.theme) {
    Settings.Theme.Dark -> true
    Settings.Theme.Light -> false
    Settings.Theme.System -> isSystemInDarkTheme()
}

/** Counstar in the phone's own (Material You) colors, dark or light. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CounstarTheme(dark: Boolean = isCounstarDark(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    MaterialExpressiveTheme(
        colorScheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
        motionScheme = MotionScheme.expressive(),
        typography = CounstarTypography,
        content = content,
    )
}
