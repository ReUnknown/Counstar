package app.counstar.ui.watch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import android.view.TextureView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import app.counstar.playback.CounstarPlayer
import app.counstar.ui.components.PlayPauseIcon
import app.counstar.ui.theme.YouTubeRed
import kotlinx.coroutines.delay

/** The player's state, refreshed while something shows it. */
@Stable
class PlaybackState {
    var playing by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
}

@Composable
fun rememberPlaybackState(player: Player): PlaybackState {
    val state = remember(player) { PlaybackState() }
    DisposableEffect(player) {
        fun sync() {
            state.playing = player.isPlaying
            state.buffering = player.playbackState == Player.STATE_BUFFERING
            state.duration = player.duration.coerceAtLeast(0L)
            state.position = player.currentPosition.coerceAtLeast(0L)
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = sync()
        }
        sync()
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            state.position = player.currentPosition.coerceAtLeast(0L)
            state.duration = player.duration.coerceAtLeast(0L)
            delay(250)
        }
    }
    return state
}

/** The latest frame of the playing video, tiny, for the watch page's ambient glow. */
object Ambient {
    var frame by mutableStateOf<ImageBitmap?>(null)
}

/** Just the picture, fitted into its box. [sampleAmbient] keeps [Ambient.frame] up to date. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoSurface(modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit, sampleAmbient: Boolean = false) {
    val player = CounstarPlayer.player
    val presentation = rememberPresentationState(player)
    var texture by remember { mutableStateOf<TextureView?>(null) }
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { context -> TextureView(context).also { texture = it; player.setVideoTextureView(it) } },
            onRelease = { player.clearVideoTextureView(it) },
            modifier = Modifier.resizeWithContentScale(contentScale, presentation.videoSizeDp),
        )
        if (presentation.coverSurface) Box(Modifier.fillMaxSize().background(Color.Black))
    }
    if (sampleAmbient) {
        LaunchedEffect(texture) {
            val view = texture ?: return@LaunchedEffect
            while (true) {
                if (view.isAvailable && player.videoSize.width > 0) {
                    view.getBitmap(48, 27)?.let { Ambient.frame = it.asImageBitmap() }
                }
                delay(500)
            }
        }
    }
}

/**
 * The video with YouTube's controls: tap to show them, play/pause in the middle, skip 10 seconds
 * either side, the red seek bar and full screen along the bottom.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayer(
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onMinimize: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val player = CounstarPlayer.player
    val state = rememberPlaybackState(player)
    var controls by remember { mutableStateOf(true) }
    var seeking by remember { mutableStateOf(false) }
    var seekTo by remember { mutableFloatStateOf(0f) }
    // Controls hide themselves while the video plays.
    LaunchedEffect(controls, state.playing, seeking) {
        if (controls && state.playing && !seeking) {
            delay(3_000)
            controls = false
        }
    }
    Box(
        modifier
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controls = !controls },
    ) {
        VideoSurface(Modifier.fillMaxSize(), sampleAmbient = true)

        val loading = CounstarPlayer.loading || state.buffering
        if (loading && CounstarPlayer.error == null) {
            LoadingIndicator(Modifier.align(Alignment.Center).size(64.dp), color = Color.White)
        }
        CounstarPlayer.error?.let {
            Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
        }

        AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
                Row(Modifier.fillMaxWidth().padding(4.dp)) {
                    if (onMinimize != null) {
                        IconButton(onClick = onMinimize) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Minimize", tint = Color.White)
                        }
                    }
                }
                if (!loading) {
                    Row(
                        Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        GlassButton(onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) }) {
                            Icon(Icons.Rounded.Replay10, contentDescription = "Back 10 seconds", tint = Color.White)
                        }
                        Box(
                            Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.White.copy(alpha = 0.92f))
                                .clickable { if (player.isPlaying) player.pause() else player.play() },
                            contentAlignment = Alignment.Center,
                        ) {
                            PlayPauseIcon(playing = state.playing, tint = Color(0xFF111318), size = 30.dp)
                        }
                        GlassButton(onClick = { player.seekTo(player.currentPosition + 10_000) }) {
                            Icon(Icons.Rounded.Forward10, contentDescription = "Forward 10 seconds", tint = Color.White)
                        }
                    }
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = if (fullscreen) 16.dp else 0.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val shown = if (seeking) (seekTo * state.duration).toLong() else state.position
                        Text(
                            "${formatTime(shown)} / ${formatTime(state.duration)}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onToggleFullscreen) {
                            Icon(
                                if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                contentDescription = if (fullscreen) "Exit full screen" else "Full screen",
                                tint = Color.White,
                            )
                        }
                    }
                    val fraction = if (state.duration > 0) state.position.toFloat() / state.duration else 0f
                    Slider(
                        value = if (seeking) seekTo else fraction,
                        onValueChange = {
                            seeking = true
                            seekTo = it
                        },
                        onValueChangeFinished = {
                            player.seekTo((seekTo * state.duration).toLong())
                            seeking = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = YouTubeRed,
                            activeTrackColor = YouTubeRed,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
