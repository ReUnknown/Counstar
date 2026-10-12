package app.counstar.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** YouTube red: watch progress, live rings, the seek bar. */
val YouTubeRed = Color(0xFFFF0033)

/** Counstar is dark, like YouTube, with the phone's own (Material You) colors. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CounstarTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = dynamicDarkColorScheme(LocalContext.current),
        motionScheme = MotionScheme.expressive(),
        typography = CounstarTypography,
        content = content,
    )
}
