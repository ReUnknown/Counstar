package app.counstar.ui.watch

import android.view.TextureView
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import app.counstar.data.Chapter
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import app.counstar.data.Settings
import app.counstar.playback.CounstarPlayer
import app.counstar.playback.Storyboard
import app.counstar.ui.components.BlurredImage
import app.counstar.ui.components.rememberHaptics
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
    /** Width / height of the picture (16:9 until the video says otherwise). */
    var ratio by mutableFloatStateOf(16f / 9f)
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
            val size = player.videoSize
            if (size.width > 0 && size.height > 0) state.ratio = size.width * size.pixelWidthHeightRatio / size.height
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
                if (view.isAvailable && player.videoSize.width > 0 && player.isPlaying) {
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
    if (!Settings.ambientGlow) return
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
        // Rectangle edges: the blur holds solid to the border, so overlapping frames never
        // brighten or dim at the edges while one fades in.
        val look = Modifier.matchParentSize().blur(blur, BlurredEdgeTreatment.Rectangle)
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
 * The video with YouTube's controls. Tap to show them; double-tap either side to skip 10
 * seconds; hold for 2x while your finger stays down. Swipe down to leave full screen, or (not
 * in full screen) to minimize: [onSwipe] follows the finger and [onSwipeEnd] says how far it got.
 * In full screen everything (controls included) sits on the picture itself, the title on top.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoPlayer(
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onMinimize: (() -> Unit)?,
    modifier: Modifier = Modifier,
    heatmap: List<Float> = emptyList(),
    chapters: List<Chapter> = emptyList(),
    title: String? = null,
    onSwipe: (Float) -> Unit = {},
    onSwipeEnd: (Float) -> Unit = {},
) {
    val player = CounstarPlayer.player
    val state = rememberPlaybackState(player)
    val context = LocalContext.current
    val density = LocalDensity.current
    var controls by remember { mutableStateOf(true) }
    var scrubAt by remember { mutableStateOf<Long?>(null) }
    val haptics = rememberHaptics()
    var settingsOpen by remember { mutableStateOf(false) }
    var captionsOpen by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    // Double-tap skips: which side, and how many seconds so far in this burst.
    var skipSide by remember { mutableIntStateOf(0) }
    var skipSeconds by remember { mutableIntStateOf(0) }
    var skipBurst by remember { mutableIntStateOf(0) }
    LaunchedEffect(skipBurst) {
        if (skipBurst == 0) return@LaunchedEffect
        delay(700)
        skipSide = 0
        skipSeconds = 0
    }
    LaunchedEffect(controls, state.playing, scrubAt) {
        if (controls && state.playing && scrubAt == null) {
            delay(3_000)
            controls = false
        }
    }
    fun toggleCaptions() {
        val tracks = CounstarPlayer.captionTracks
        when {
            tracks.isEmpty() -> Toast.makeText(context, "This video has no captions", Toast.LENGTH_SHORT).show()
            CounstarPlayer.caption != null -> CounstarPlayer.showCaptions(null)
            else -> CounstarPlayer.showCaptions(
                tracks.firstOrNull { it.language.startsWith("en") && !it.auto } ?: tracks.firstOrNull { !it.auto } ?: tracks.first(),
            )
        }
    }
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (fullscreen) AmbientLight(CounstarPlayer.current?.thumbnail, Modifier.fillMaxSize(), strength = 0.45f, blur = 48.dp)
        Box(
            (if (fullscreen) Modifier.fillMaxHeight().aspectRatio(state.ratio, matchHeightConstraintsFirst = true) else Modifier.fillMaxSize())
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controls = !controls },
                        onDoubleTap = { at ->
                            val side = when {
                                at.x < size.width * 0.4f -> -1
                                at.x > size.width * 0.6f -> 1
                                else -> 0
                            }
                            if (side == 0) {
                                if (player.isPlaying) player.pause() else player.play()
                            } else {
                                haptics.tick()
                                player.seekTo((player.currentPosition + side * 10_000L).coerceIn(0L, player.duration.coerceAtLeast(0L)))
                                if (skipSide != side) skipSeconds = 0
                                skipSide = side
                                skipSeconds += 10
                                skipBurst++
                            }
                        },
                        onLongPress = {
                            haptics.hold()
                            holding = true
                            controls = false
                            CounstarPlayer.holdFast(true)
                        },
                        onPress = {
                            tryAwaitRelease()
                            if (holding) {
                                holding = false
                                CounstarPlayer.holdFast(false)
                            }
                        },
                    )
                }
                .pointerInput(fullscreen) {
                    var total = 0f
                    // In full screen, a drag from the very top edge is the status bar's, not ours.
                    var counts = true
                    detectVerticalDragGestures(
                        onDragStart = { at ->
                            total = 0f
                            counts = !fullscreen || at.y > 56.dp.toPx()
                        },
                        onDragEnd = {
                            if (fullscreen) {
                                // A long, deliberate pull: looking near the top shouldn't leave full screen.
                                if (counts && total > with(density) { 200.dp.toPx() }) onToggleFullscreen()
                            } else {
                                onSwipeEnd(total)
                            }
                            total = 0f
                        },
                        onDragCancel = {
                            if (!fullscreen) onSwipeEnd(0f)
                            total = 0f
                        },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            total = (total + dy).coerceAtLeast(0f)
                            if (!fullscreen) onSwipe(total)
                        },
                    )
                },
        ) {
            VideoSurface(Modifier.fillMaxSize(), sampleAmbient = true)

            // Scrubbing: the frame you're on takes the video's place, full size, like newer YouTube.
            val target = scrubAt
            val storyboard = CounstarPlayer.storyboard
            if (target != null && storyboard != null) {
                StoryboardFrame(storyboard, target, state.duration.coerceAtLeast(1L))
            }

            Captions(state.position, fullscreen, lifted = controls || target != null)

            val loading = CounstarPlayer.loading || state.buffering
            if (loading && CounstarPlayer.error == null && target == null) {
                LoadingIndicator(Modifier.align(Alignment.Center).size(64.dp), color = Color.White)
            }
            CounstarPlayer.error?.let {
                Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
            }

            SkipIndicator(skipSide, skipSeconds)
            if (holding) {
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 14.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("2x", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Icon(Icons.Rounded.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            AnimatedVisibility(controls || target != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (target != null) 0.1f else 0.35f))) {
                    if (target == null) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = if (fullscreen) 8.dp else 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (fullscreen) {
                                IconButton(onClick = onToggleFullscreen) {
                                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Exit full screen", tint = Color.White)
                                }
                                Text(
                                    title.orEmpty(),
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(start = 4.dp, end = 8.dp),
                                )
                            } else {
                                if (onMinimize != null) {
                                    IconButton(onClick = onMinimize) {
                                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Minimize", tint = Color.White)
                                    }
                                }
                                Spacer(Modifier.weight(1f))
                            }
                            // Captions: tap to turn on or off, hold for languages.
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .combinedClickable(onLongClick = { captionsOpen = true }, onClick = ::toggleCaptions),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (CounstarPlayer.caption != null) Icons.Rounded.ClosedCaption else Icons.Outlined.ClosedCaption,
                                    contentDescription = "Captions",
                                    tint = if (CounstarPlayer.captionTracks.isEmpty()) Color.White.copy(alpha = 0.45f) else Color.White,
                                )
                            }
                            IconButton(onClick = { settingsOpen = true }) {
                                Icon(Icons.Outlined.Settings, contentDescription = "Video settings", tint = Color.White)
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
                            .padding(bottom = if (fullscreen) 10.dp else 2.dp),
                    ) {
                        if (target == null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${formatTime(state.position)} / ${formatTime(state.duration)}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                                chapterAt(chapters, state.position)?.let {
                                    Text(
                                        "  •  ${it.title}",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                }
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
                        SeekBar(state, heatmap, chapters, onScrub = { scrubAt = it })
                    }
                }
            }
        }
    }
    if (settingsOpen) VideoSettingsSheet(onDismiss = { settingsOpen = false }, onCaptions = { settingsOpen = false; captionsOpen = true })
    if (captionsOpen) CaptionsSheet(onDismiss = { captionsOpen = false })
}

/** The chapter playing at [positionMs], if the video has chapters. */
fun chapterAt(chapters: List<Chapter>, positionMs: Long): Chapter? = chapters.lastOrNull { it.startMs <= positionMs }

/** The caption line for now, over the bottom of the picture (above the controls when they show). */
@Composable
private fun BoxScope.Captions(positionMs: Long, fullscreen: Boolean, lifted: Boolean) {
    if (CounstarPlayer.caption == null) return
    val cue = CounstarPlayer.cues.lastOrNull { it.startMs <= positionMs && positionMs < it.endMs } ?: return
    val lift by animateDpAsState(if (lifted) 64.dp else 12.dp, label = "captions")
    Text(
        cue.text,
        color = Color.White,
        fontSize = if (fullscreen) 19.sp else 15.sp,
        lineHeight = if (fullscreen) 24.sp else 19.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = lift, start = 24.dp, end = 24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** YouTube's double-tap feedback: a soft half-circle on that side, with the seconds skipped. */
@Composable
private fun BoxScope.SkipIndicator(side: Int, seconds: Int) {
    androidx.compose.animation.AnimatedVisibility(
        visible = side != 0,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(300)),
        modifier = Modifier.align(if (side < 0) Alignment.CenterStart else Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.38f),
    ) {
        val shape = if (side < 0) RoundedCornerShape(topEndPercent = 100, bottomEndPercent = 100) else RoundedCornerShape(topStartPercent = 100, bottomStartPercent = 100)
        Box(Modifier.fillMaxSize().clip(shape).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (side < 0) Icons.Rounded.FastRewind else Icons.Rounded.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
                Text("${seconds.coerceAtLeast(10)} seconds", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * The seek bar: a red line with a line-shaped handle (Material's expressive slider), buffered
 * in gray, split at chapters. Tap to jump. Drag anywhere along it and the handle moves by as
 * much as your finger does, from where it was (it doesn't jump to your finger), like YouTube's.
 * While you drag it thickens, the time and chapter follow, and the "most replayed" wave rises.
 */
@Composable
private fun SeekBar(state: PlaybackState, heatmap: List<Float>, chapters: List<Chapter>, onScrub: (Long?) -> Unit) {
    val player = CounstarPlayer.player
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(0f) }
    // Silent while scrubbing, like YouTube: the video holds still and picks up again once you let go.
    var resumeAfter by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    // The chapter under the handle while scrubbing: a tick each time it changes.
    var chapterIndex by remember { mutableIntStateOf(-1) }
    val duration = state.duration.coerceAtLeast(1L)
    val shown = if (dragging) fraction else state.position.toFloat() / duration
    val buffered = state.buffered.toFloat() / duration
    val track by animateDpAsState(if (dragging) 7.dp else 4.dp, label = "track")
    val handle by animateDpAsState(if (dragging) 26.dp else 18.dp, label = "handle")
    val breaks = chapters.drop(1).map { it.startMs.toFloat() / duration }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth.toFloat()
        Column {
            if (dragging) {
                val at = (fraction * duration).toLong()
                ScrubInfo(at, chapterAt(chapters, at)?.title, heatmap, fraction * widthPx, widthPx)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .pointerInput(duration) {
                        detectTapGestures { offset ->
                            player.seekTo(((offset.x / size.width).coerceIn(0f, 1f) * duration).toLong())
                        }
                    }
                    .pointerInput(duration) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                resumeAfter = player.isPlaying || player.playWhenReady
                                player.pause()
                                // From where the video is, not where the finger landed.
                                fraction = (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
                                chapterIndex = chapters.indexOfLast { it.startMs <= fraction * duration }
                                haptics.drag()
                                dragging = true
                                onScrub((fraction * duration).toLong())
                            },
                            onDragEnd = {
                                player.seekTo((fraction * duration).toLong())
                                if (resumeAfter) player.play()
                                scope.launch {
                                    delay(120)
                                    dragging = false
                                    onScrub(null)
                                }
                            },
                            onDragCancel = {
                                if (resumeAfter) player.play()
                                dragging = false
                                onScrub(null)
                            },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                fraction = (fraction + amount / size.width).coerceIn(0f, 1f)
                                val now = chapters.indexOfLast { it.startMs <= fraction * duration }
                                if (now != chapterIndex) {
                                    chapterIndex = now
                                    haptics.tick()
                                }
                                onScrub((fraction * duration).toLong())
                            },
                        )
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Canvas(Modifier.fillMaxWidth().height(handle)) {
                    val h = track.toPx()
                    val y = (size.height - h) / 2
                    val r = CornerRadius(h / 2, h / 2)
                    val handleW = 4.dp.toPx()
                    val gap = 3.dp.toPx()
                    val chapterGap = 2.dp.toPx()
                    val cuts = breaks.map { it * size.width }
                    // A stretch of the bar, broken where chapters begin.
                    fun piece(from: Float, to: Float, color: Color) {
                        var start = from
                        for (c in cuts) {
                            if (c <= start || c >= to) continue
                            if (c - chapterGap / 2 > start) drawRoundRect(color, Offset(start, y), Size(c - chapterGap / 2 - start, h), r)
                            start = c + chapterGap / 2
                        }
                        if (to > start) drawRoundRect(color, Offset(start, y), Size(to - start, h), r)
                    }
                    val x = (size.width * shown.coerceIn(0f, 1f)).coerceIn(handleW / 2, size.width - handleW / 2)
                    // Inactive, then buffered, to the right of the handle; played to its left.
                    val restStart = x + handleW / 2 + gap
                    if (restStart < size.width) {
                        piece(restStart, size.width, Color.White.copy(alpha = 0.28f))
                        val bufferedEnd = size.width * buffered.coerceIn(0f, 1f)
                        if (bufferedEnd > restStart) piece(restStart, bufferedEnd, Color.White.copy(alpha = 0.55f))
                    }
                    val playedEnd = x - handleW / 2 - gap
                    if (playedEnd > 0f) piece(0f, playedEnd, YouTubeRed)
                    drawRoundRect(
                        YouTubeRed,
                        Offset(x - handleW / 2, 0f),
                        Size(handleW, size.height),
                        CornerRadius(handleW / 2, handleW / 2),
                    )
                }
            }
        }
    }
}

/** While scrubbing: the time (and chapter) over the handle, and the "most replayed" wave. */
@Composable
private fun ScrubInfo(positionMs: Long, chapter: String?, heatmap: List<Float>, anchorX: Float, barWidthPx: Float) {
    val bubbleWidth = if (chapter != null) 180.dp else 76.dp
    val bubblePx = with(LocalDensity.current) { bubbleWidth.toPx() }
    val left = (anchorX - bubblePx / 2).coerceIn(0f, (barWidthPx - bubblePx).coerceAtLeast(0f))
    Box(Modifier.fillMaxWidth().height(if (chapter != null) 80.dp else 64.dp)) {
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
            Modifier
                .offset { IntOffset(left.roundToInt(), 0) }
                .width(bubbleWidth)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(vertical = 4.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            chapter?.let { Text(it, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text(formatTime(positionMs), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
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
