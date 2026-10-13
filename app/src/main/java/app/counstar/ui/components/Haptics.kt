package app.counstar.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Vibrations, where YouTube's app has them: a firm buzz when holding the video for 2x, a light
 * tick for double-tap skips and for each chapter the seek bar crosses, a click on like,
 * subscribe and the bell, and a confirm when something's done (a post, a save).
 */
class Haptics(private val view: View) {
    /** Hold for 2x, opening a long-press menu. */
    fun hold() = view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

    /** A double-tap skip, a chapter crossed while scrubbing. */
    fun tick() = view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK,
    )

    /** Like, dislike, subscribe, the bell. */
    fun click() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

    /** Something went through (posted, saved, subscribed). */
    fun confirm() = view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)

    /** A scrub starting or a drag letting go. */
    fun drag() = view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.DRAG_START else HapticFeedbackConstants.VIRTUAL_KEY,
    )
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
