package app.counstar.ui.watch

import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import app.counstar.playback.CounstarPlayer
import app.counstar.playback.Storyboard
import app.counstar.ui.components.BlurredImage
import app.counstar.ui.components.PlayPauseIcon
import app.counstar.ui.theme.YouTubeRed
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The player's state, refreshed while something shows it. */
@Stable
class PlaybackState {
    var playing by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var position by mutableLongStateOf(0L)
    var buffered by mutableLongStateOf(0L)
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
            state.buffered = player.bufferedPosition.coerceAtLeast(0L)
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
            state.buffered = player.bufferedPosition.coerceAtLeast(0L)
            state.duration = player.duration.coerceAtLeast(0L)
            delay(200)
        }
    }
    return state
}

/** The latest frame of the playing video, tiny, for the ambient glow. */
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
    Box(modifier, contentAlignment = Alignment.Center) {
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
                delay(700)
            }
        }
    }
}

/**
 * The video's light, blurred far past recognition. Each new frame fades in over the last one
 * (never a hard swap), so the glow drifts with the video instead of flashing.
 */
@Composable
fun AmbientLight(fallback: String?, modifier: Modifier = Modifier, strength: Float = 0.85f, blur: Dp = 36.dp) {
    val frame = Ambient.frame
    var previous by remember { mutableStateOf<ImageBitmap?>(null) }
    var shown by remember { mutableStateOf<ImageBitmap?>(null) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(frame) {
        if (frame == null || frame === shown) return@LaunchedEffect
        previous = shown
        shown = frame
        fade.snapTo(0f)
        fade.animateTo(1f, tween(650))
    }
    Box(modifier.alpha(strength)) {
        val look = Modifier.matchParentSize().blur(blur, BlurredEdgeTreatment.Unbounded)
        val before = previous
        val now = shown
        when {
            now == null && fallback != null -> BlurredImage(fallback, Modifier.matchParentSize(), radius = 80.dp)
            now != null -> {
                if (before != null) Image(before, null, contentScale = ContentScale.Crop, modifier = look)
                Image(now, null, contentScale = ContentScale.Crop, modifier = look.alpha(fade.value))
            }
        }
    }
}

/**
 * The video with YouTube's controls: tap to show them, play/pause in the middle, skip 10 seconds
 * either side, and the seek bar along the bottom.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoPlayer(
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onMinimize: (() -> Unit)?,
    modifier: Modifier = Modifier,
    heatmap: List<Float> = emptyList(),
) {
    val player = CounstarPlayer.player
    val state = rememberPlaybackState(player)
    var controls by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    LaunchedEffect(controls, state.playing, scrubbing) {
        if (controls && state.playing && !scrubbing) {
            delay(3_000)
            controls = false
        }
    }
    Box(
        modifier
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controls = !controls },
    ) {
        // Full screen: the glow fills the black bars around the picture, softly.
        if (fullscreen) AmbientLight(CounstarPlayer.current?.thumbnail, Modifier.fillMaxSize(), strength = 0.45f, blur = 48.dp)
        VideoSurface(Modifier.fillMaxSize(), sampleAmbient = true)

        val loading = CounstarPlayer.loading || state.buffering
        if (loading && CounstarPlayer.error == null && !scrubbing) {
            LoadingIndicator(Modifier.align(Alignment.Center).size(64.dp), color = Color.White)
        }
        CounstarPlayer.error?.let {
            Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
        }

        AnimatedVisibility(controls || scrubbing, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (scrubbing) 0.15f else 0.35f))) {
                if (!scrubbing) {
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
                }
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = if (fullscreen) 20.dp else 2.dp),
                ) {
                    if (!scrubbing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${formatTime(state.position)} / ${formatTime(state.duration)}",
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
                    }
                    SeekBar(state, heatmap, onScrubbing = { scrubbing = it })
                }
            }
        }
    }
}

/**
 * YouTube's seek bar, nicer: thin until you touch it, buffered in gray, played in red. Drag
 * anywhere along it; while you do, the frame you're on, its time and the "most replayed" wave
 * rise above it.
 */
@Composable
private fun SeekBar(state: PlaybackState, heatmap: List<Float>, onScrubbing: (Boolean) -> Unit) {
    val player = CounstarPlayer.player
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(0f) }
    val duration = state.duration.coerceAtLeast(1L)
    val shown = if (dragging) fraction else state.position.toFloat() / duration
    val buffered = state.buffered.toFloat() / duration
    val track by animateDpAsState(if (dragging) 6.dp else 3.dp, label = "track")
    val thumb by animateDpAsState(if (dragging) 18.dp else 12.dp, label = "thumb")

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth.toFloat()
        if (dragging) {
            ScrubPreview(
                positionMs = (fraction * duration).toLong(),
                durationMs = duration,
                storyboard = CounstarPlayer.storyboard,
                heatmap = heatmap,
                anchorX = fraction * widthPx,
                barWidthPx = widthPx,
            )
        }
        Box(
            Modifier
                .padding(top = if (dragging) PREVIEW_HEIGHT else 0.dp)
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(duration) {
                    detectTapGestures { offset ->
                        player.seekTo(((offset.x / size.width).coerceIn(0f, 1f) * duration).toLong())
                    }
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            onScrubbing(true)
                            fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            player.seekTo((fraction * duration).toLong())
                            scope.launch {
                                delay(150)
                                dragging = false
                                onScrubbing(false)
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            onScrubbing(false)
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            fraction = (fraction + amount / size.width).coerceIn(0f, 1f)
                        },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(Modifier.fillMaxWidth().height(thumb)) {
                val h = track.toPx()
                val y = (size.height - h) / 2
                val r = CornerRadius(h / 2, h / 2)
                drawRoundRect(Color.White.copy(alpha = 0.28f), Offset(0f, y), Size(size.width, h), r)
                drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(0f, y), Size(size.width * buffered.coerceIn(0f, 1f), h), r)
                drawRoundRect(YouTubeRed, Offset(0f, y), Size(size.width * shown.coerceIn(0f, 1f), h), r)
                val t = thumb.toPx()
                drawCircle(YouTubeRed, radius = t / 2, center = Offset((size.width * shown).coerceIn(t / 2, size.width - t / 2), size.height / 2))
            }
        }
    }
}

private val PREVIEW_HEIGHT = 156.dp

/** The frame you're scrubbing to (from YouTube's storyboard), its time, and the replay wave. */
@Composable
private fun ScrubPreview(positionMs: Long, durationMs: Long, storyboard: Storyboard?, heatmap: List<Float>, anchorX: Float, barWidthPx: Float) {
    val previewWidth = 160.dp
    val previewPx = with(LocalDensity.current) { previewWidth.toPx() }
    val left = (anchorX - previewPx / 2).coerceIn(0f, (barWidthPx - previewPx).coerceAtLeast(0f))
    Box(Modifier.fillMaxWidth().height(PREVIEW_HEIGHT)) {
        // "Most replayed": a soft wave across the whole bar, just above it.
        if (heatmap.size > 2) {
            Canvas(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(36.dp)) {
                val path = Path()
                val step = size.width / (heatmap.size - 1)
                fun yOf(v: Float) = size.height - v.coerceIn(0f, 1f) * size.height
                path.moveTo(0f, size.height)
                path.lineTo(0f, yOf(heatmap[0]))
                for (i in 1 until heatmap.size) {
                    val px = (i - 1) * step
                    val x = i * step
                    path.cubicTo(px + step / 2, yOf(heatmap[i - 1]), x - step / 2, yOf(heatmap[i]), x, yOf(heatmap[i]))
                }
                path.lineTo(size.width, size.height)
                path.close()
                drawPath(path, Color.White.copy(alpha = 0.3f))
            }
        }
        Column(
            Modifier.offset { IntOffset(left.roundToInt(), 0) }.width(previewWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .width(previewWidth)
                    .height(90.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black),
            ) {
                if (storyboard != null) StoryboardFrame(storyboard, positionMs, durationMs)
            }
            Text(
                formatTime(positionMs),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

/** One cell of a storyboard sheet, scaled to fill its box. */
@Composable
private fun StoryboardFrame(storyboard: Storyboard, positionMs: Long, durationMs: Long) {
    val frame = storyboard.frameAt(positionMs, durationMs)
    val painter = rememberAsyncImagePainter(frame.sheetUrl)
    val loaded by painter.state.collectAsState()
    val bitmap = remember(loaded) {
        (loaded as? AsyncImagePainter.State.Success)?.result?.image?.toBitmap()?.asImageBitmap()
    }
    Canvas(Modifier.fillMaxSize()) {
        val sheet = bitmap ?: return@Canvas
        val cellW = sheet.width / storyboard.columnsPerSheet
        val cellH = sheet.height / storyboard.rowsPerSheet
        drawImage(
            sheet,
            srcOffset = IntOffset(frame.column * cellW, frame.row * cellH),
            srcSize = IntSize(cellW, cellH),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        )
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
