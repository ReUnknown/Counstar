package app.counstar.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

/**
 * Frosted glass, like YouTube's bars: what's behind, blurred just enough that shapes and colors
 * (text too, not only thumbnails) still glow through, under a wash of the page color. [tint]
 * is how much of the page color lies over it.
 */
@Composable
fun frost(tint: Float = FROSTED_TINT): HazeStyle {
    val page = MaterialTheme.colorScheme.background
    return HazeStyle(
        backgroundColor = page,
        tint = HazeTint(page.copy(alpha = tint)),
        blurRadius = 22.dp,
        noiseFactor = 0.03f,
    )
}
