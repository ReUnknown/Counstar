package app.counstar.ui.watch

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.counstar.playback.CounstarPlayer
import app.counstar.ui.Nav
import app.counstar.ui.components.CounstarMotion
import app.counstar.ui.theme.YouTubeRed
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The mini player is just the video, floating in the corner. Tap it to go back to the watch page;
 * flick it sideways to close it.
 */
@Composable
fun MiniPlayer(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val dismissAt = with(LocalDensity.current) { 120.dp.toPx() }
    val state = rememberPlaybackState(CounstarPlayer.player)
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .size(width = 208.dp, height = 117.dp)
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - (abs(offset.value) / (dismissAt * 1.6f)).coerceIn(0f, 0.9f)
            }
            .shadow(16.dp, shape)
            .clip(shape)
            .background(Color.Black)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> scope.launch { offset.snapTo(offset.value + delta) } },
                onDragStopped = { velocity ->
                    if (abs(offset.value) > dismissAt || abs(velocity) > 2_000f) {
                        val target = if ((offset.value + velocity / 10f) > 0) dismissAt * 3 else -dismissAt * 3
                        offset.animateTo(target, CounstarMotion.settle())
                        CounstarPlayer.stop()
                        offset.snapTo(0f)
                    } else {
                        offset.animateTo(0f, CounstarMotion.bouncy())
                    }
                },
            )
            .clickable { Nav.watchOpen = true },
    ) {
        VideoSurface(Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        val fraction = if (state.duration > 0) state.position.toFloat() / state.duration else 0f
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.3f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(YouTubeRed))
        }
    }
}
