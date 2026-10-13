package app.counstar.ui.watch

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.counstar.playback.CounstarPlayer
import app.counstar.ui.Nav
import app.counstar.ui.components.CounstarMotion
import app.counstar.ui.components.PlayPauseIcon
import app.counstar.ui.components.rememberArtColorScheme
import app.counstar.ui.components.pressable
import app.counstar.ui.theme.YouTubeRed
import kotlinx.coroutines.launch
import kotlin.math.abs

val MiniPlayerHeight = 78.dp

/**
 * The mini player, docked into the bottom bar's frosted glass: the video playing on the left,
 * title and channel, play/pause and close. Tap to go back to the video; flick it sideways to
 * close it.
 */
@Composable
fun MiniPlayer(modifier: Modifier = Modifier) {
    val item = CounstarPlayer.current ?: return
    val player = CounstarPlayer.player
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val dismissAt = with(LocalDensity.current) { 140.dp.toPx() }
    val state = rememberPlaybackState(player)
    // Play/pause in the channel's color, like its Subscribe button.
    val accent = rememberArtColorScheme(item.channelAvatar ?: item.thumbnail)
    Box(
        modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - (abs(offset.value) / (dismissAt * 1.6f)).coerceIn(0f, 0.9f)
            }
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
            // Swipe up to go back to the video.
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -40.dp.toPx()) Nav.watchOpen = true },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        total += dy
                    },
                )
            }
            .clickable { Nav.watchOpen = true },
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VideoSurface(
                Modifier.size(width = 114.dp, height = 64.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.weight(1f)) {
                Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                item.channelName?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(
                Modifier
                    .size(44.dp)
                    .pressable(RoundedCornerShape(15.dp), color = accent.primary) {
                        if (player.isPlaying) player.pause() else player.play()
                    },
                contentAlignment = Alignment.Center,
            ) { PlayPauseIcon(playing = state.playing, tint = accent.onPrimary, size = 22.dp) }
            IconButton(onClick = { CounstarPlayer.stop() }) {
                Icon(Icons.Rounded.Close, contentDescription = "Close")
            }
        }
        val fraction = if (state.duration > 0) state.position.toFloat() / state.duration else 0f
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.18f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(YouTubeRed))
        }
    }
}
