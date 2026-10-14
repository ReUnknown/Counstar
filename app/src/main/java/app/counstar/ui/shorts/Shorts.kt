package app.counstar.ui.shorts

import android.content.Intent
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import app.counstar.ui.components.backGesture
import app.counstar.ui.components.predictiveBack
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import app.counstar.ui.components.BlurredImage
import app.counstar.ui.components.SaveToPlaylistSheet
import app.counstar.ui.watch.Ambient
import app.counstar.ui.watch.formatTime
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import app.counstar.data.Settings
import app.counstar.data.ShortsQueue
import app.counstar.data.VideoItem
import app.counstar.data.WatchInfo
import app.counstar.data.YouTubeParser
import app.counstar.playback.CounstarPlayer
import app.counstar.ui.Nav
import app.counstar.ui.components.Avatar
import app.counstar.ui.components.blockTouches
import app.counstar.ui.components.rememberArtColorScheme
import app.counstar.ui.components.rememberHaptics
import app.counstar.ui.watch.Captions
import app.counstar.ui.watch.CaptionsSheet
import app.counstar.ui.watch.CommentsPanel
import app.counstar.ui.watch.CommentsState
import app.counstar.ui.watch.SubscribeButton
import app.counstar.ui.watch.VideoSettingsSheet
import app.counstar.ui.watch.VideoSurface
import app.counstar.ui.watch.compact
import app.counstar.ui.watch.rememberPlaybackState
import coil3.compose.AsyncImage
import com.metrolist.innertube.WebApi
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random

/** Whether the playing Short's comments are up (the Shorts don't swipe while they are). */
private object ShortsUi {
    var commentsOpen by mutableStateOf(false)
}

/** Your likes on Shorts, kept while the app runs so the heart and double taps agree. */
private object ShortLikes {
    val liked = mutableStateMapOf<String, Boolean>()
}

/**
 * Shorts, one screen each: swipe up for the next. The one you stop on plays (looping, or on to
 * the next by itself with Auto scroll on). [active] false: something's open over it, so it
 * stays still. [onClose] null: it's the Shorts tab (back goes home, not away).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShortsPlayer(queue: ShortsQueue, onClose: (() -> Unit)?, active: Boolean = true, bottomInset: Dp = 0.dp) {
    LaunchedEffect(queue) { queue.start() }
    // Back, the system's predictive way like every page: the Shorts shrink as you swipe and
    // close when you let go. On the Shorts tab, back goes home.
    val back = predictiveBack(enabled = active) { onClose?.invoke() ?: Nav.goTo(app.counstar.ui.Tab.Home) }
    val items = queue.items
    Box(Modifier.fillMaxSize().backGesture(back).background(Color.Black).blockTouches()) {
        if (items.isEmpty()) {
            if (queue.failed) {
                Text("Couldn't load Shorts.", color = Color.White, modifier = Modifier.align(Alignment.Center).padding(32.dp))
            } else {
                LoadingIndicator(Modifier.align(Alignment.Center).size(64.dp), color = Color.White)
            }
            return@Box
        }
        val pager = rememberPagerState { items.size }
        val scope = rememberCoroutineScope()
        val current = items.getOrNull(pager.settledPage)
        // Play the Short you've stopped on; keep still while something's over it.
        LaunchedEffect(current?.id, active) {
            if (current == null) return@LaunchedEffect
            if (!active) {
                if (Nav.shorts == null && CounstarPlayer.current?.isShort == true) CounstarPlayer.player.pause()
                return@LaunchedEffect
            }
            CounstarPlayer.changeLoop(!Settings.shortsAutoScroll)
            if (CounstarPlayer.current?.id == current.id) CounstarPlayer.player.play() else CounstarPlayer.play(current)
        }
        LaunchedEffect(Settings.shortsAutoScroll) { if (active) CounstarPlayer.changeLoop(!Settings.shortsAutoScroll) }
        LaunchedEffect(pager.settledPage, items.size) {
            if (pager.settledPage >= items.size - 3) queue.loadMore()
            // The next two get their streams ready now, so swiping to them plays at once.
            items.getOrNull(pager.settledPage + 1)?.let(CounstarPlayer::prefetch)
            items.getOrNull(pager.settledPage + 2)?.let(CounstarPlayer::prefetch)
        }
        // Auto scroll: on to the next Short when one ends. Leaving Shorts stops them.
        DisposableEffect(queue) {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED && Settings.shortsAutoScroll && CounstarPlayer.current?.isShort == true) {
                        scope.launch { pager.animateScrollToPage((pager.settledPage + 1).coerceAtMost(items.size - 1)) }
                    }
                }
            }
            CounstarPlayer.player.addListener(listener)
            onDispose {
                CounstarPlayer.player.removeListener(listener)
                if (CounstarPlayer.current?.isShort == true) {
                    CounstarPlayer.changeLoop(false)
                    CounstarPlayer.stop()
                }
            }
        }
        VerticalPager(pager, beyondViewportPageCount = 1, userScrollEnabled = !ShortsUi.commentsOpen, key = { items[it].id }) { page ->
            ShortPage(items[page], playing = active && page == pager.settledPage, onClose = onClose, bottomInset = bottomInset)
        }
    }
}

private class Heart(val id: Long, val at: Offset, val tilt: Float)

/** Text over a video: a small, soft shadow keeps it readable on anything. */
private val shadowed = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.75f), Offset(0f, 1.5f), 6f))

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ShortPage(item: VideoItem, playing: Boolean, onClose: (() -> Unit)?, bottomInset: Dp) {
    var info by remember(item.id) { mutableStateOf<WatchInfo?>(null) }
    LaunchedEffect(item.id) {
        withContext(Dispatchers.IO) { YouTube.rawNext(videoId = item.id).mapCatching(YouTubeParser::watch) }.onSuccess { info = it }
    }
    val details = info
    val comments = remember(details) { details?.commentsToken?.let { CommentsState(it, details.commentSorts, details.commentCount) } }
    var commentsOpen by remember(item.id) { mutableStateOf(false) }
    var menuOpen by remember(item.id) { mutableStateOf(false) }
    var captionsOpen by remember(item.id) { mutableStateOf(false) }
    var descriptionOpen by remember(item.id) { mutableStateOf(false) }
    var saveOpen by remember(item.id) { mutableStateOf(false) }
    var fast by remember(item.id) { mutableStateOf(false) }
    var hearts by remember(item.id) { mutableStateOf<List<Heart>>(emptyList()) }
    /** The play / pause flash after a tap: when, and whether it now plays. */
    var flash by remember(item.id) { mutableStateOf<Pair<Long, Boolean>?>(null) }
    BackHandler(commentsOpen) { commentsOpen = false }
    LaunchedEffect(commentsOpen, playing) { if (playing) ShortsUi.commentsOpen = commentsOpen }
    DisposableEffect(item.id) { onDispose { if (playing) ShortsUi.commentsOpen = false } }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val liked = ShortLikes.liked[item.id] ?: (details?.liked == true)
    // The count without your like, so yours can be added or taken away as you tap.
    val baseLikes = details?.likeCount?.let { it - if (details.liked) 1 else 0 }

    fun setLiked(want: Boolean) {
        if (YouTube.cookie.isNullOrBlank()) {
            Toast.makeText(context, "Sign in to like", Toast.LENGTH_SHORT).show()
            return
        }
        // Read fresh: the gesture handler that calls this outlives the composition it came from.
        val was = ShortLikes.liked[item.id] ?: (info?.liked == true)
        if (want == was) return
        ShortLikes.liked[item.id] = want
        scope.launch {
            withContext(Dispatchers.IO) { if (want) WebApi.like(item.id) else WebApi.removeLike(item.id) }
                .onFailure { ShortLikes.liked[item.id] = was }
        }
    }

    MaterialTheme(colorScheme = rememberArtColorScheme(details?.channelAvatar ?: item.channelAvatar ?: item.thumbnail)) {
        val colors = MaterialTheme.colorScheme
        val state = rememberPlaybackState(CounstarPlayer.player)
        val mine = playing && CounstarPlayer.current?.id == item.id
        val frame = if (mine) Ambient.frame else null
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // Acrylic bars: the Short's own picture, blurred, fills what the video doesn't
            // (it's never cropped to fit).
            if (frame != null) {
                Image(frame, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(48.dp, BlurredEdgeTreatment.Rectangle))
            } else {
                BlurredImage(item.thumbnail, Modifier.fillMaxSize(), radius = 64.dp)
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)))
            if (mine) {
                VideoSurface(Modifier.fillMaxSize(), contentScale = ContentScale.Fit, sampleAmbient = true)
            } else {
                AsyncImage(item.thumbnail, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            Box(Modifier.fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))))
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(300.dp + bottomInset)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))),
            )

            // Every gesture: tap to pause, double tap to like (a heart pops where you tapped),
            // hold either side for 2x, hold the middle for the menu.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(item.id) {
                        detectTapGestures(
                            onPress = {
                                tryAwaitRelease()
                                if (fast) {
                                    fast = false
                                    CounstarPlayer.holdFast(false)
                                }
                            },
                            onLongPress = { at ->
                                haptics.hold()
                                val third = size.width / 3f
                                if (at.x < third || at.x > size.width - third) {
                                    fast = true
                                    CounstarPlayer.holdFast(true)
                                } else {
                                    menuOpen = true
                                }
                            },
                            onDoubleTap = { at ->
                                haptics.tick()
                                hearts = hearts + Heart(System.nanoTime(), at, Random.nextInt(-22, 22).toFloat())
                                setLiked(true)
                            },
                            onTap = {
                                val player = CounstarPlayer.player
                                val play = !player.isPlaying
                                if (play) player.play() else player.pause()
                                flash = System.nanoTime() to play
                            },
                        )
                    },
            )

            if (mine) {
                if (CounstarPlayer.loading || (state.buffering && !state.playing)) {
                    LoadingIndicator(Modifier.align(Alignment.Center).size(64.dp), color = Color.White)
                } else if (!state.playing && CounstarPlayer.error == null) {
                    // Paused: a quiet play sign stays until you tap again.
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.align(Alignment.Center).size(84.dp))
                }
                CounstarPlayer.error?.let { Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(32.dp)) }
                Box(Modifier.fillMaxSize().padding(bottom = bottomInset + 170.dp)) { Captions(state.position, fullscreen = true, lifted = false) }
            }
            flash?.let { f -> key(f.first) { PlayFlash(f.second, Modifier.align(Alignment.Center)) } }

            Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White) }
                Text("Shorts", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, style = shadowed, modifier = Modifier.padding(start = if (onClose == null) 16.dp else 0.dp))
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = Color.White) }
            }

            // 2x while held.
            AnimatedVisibility(fast, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp), enter = fadeIn(), exit = fadeOut()) {
                Text(
                    "2x ▸▸",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }

            // Like (a heart), comments, save, share.
            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = bottomInset + 128.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val pop by animateFloatAsState(if (liked) 1f else 0f, spring(dampingRatio = 0.4f, stiffness = 500f), label = "pop")
                Rail(
                    if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    baseLikes?.let { compact(it + if (liked) 1 else 0) } ?: details?.likes ?: "Like",
                    tint = if (liked) Color(0xFFFF2D55) else Color.White,
                    scale = 1f + 0.18f * pop * (1f - pop) * 4f,
                ) {
                    haptics.tick()
                    setLiked(!liked)
                }
                Rail(Icons.Outlined.ChatBubbleOutline, details?.commentCount ?: "Comments") { if (comments != null) commentsOpen = true }
                Rail(Icons.Outlined.BookmarkBorder, "Save") {
                    if (YouTube.cookie.isNullOrBlank()) Toast.makeText(context, "Sign in to save", Toast.LENGTH_SHORT).show() else saveOpen = true
                }
                Rail(Icons.Rounded.Share, "Share") {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "https://youtube.com/shorts/${item.id}")
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }
            }

            // Who and what, above the bottom bar; then the scrub bar.
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom = bottomInset + 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.padding(start = 16.dp, end = 84.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val channelId = details?.channelId ?: item.channelId
                    Row(
                        Modifier.weight(1f, fill = false).clip(RoundedCornerShape(20.dp)).clickable { Nav.openChannel(channelId) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Avatar(details?.channelAvatar ?: item.channelAvatar, 36.dp)
                        Text(
                            details?.channelName ?: item.channelName.orEmpty(),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            style = shadowed,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (details?.channelId != null) {
                        SubscribeButton(details.channelId, details.subscribed, details.bellOptions, accent = colors.primary, onAccent = colors.onPrimary)
                    }
                }
                Text(
                    details?.title?.ifBlank { null } ?: item.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    style = shadowed,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp, end = 84.dp).clip(RoundedCornerShape(8.dp)).clickable { descriptionOpen = true },
                )
                Scrubber(if (mine) state.position else 0L, if (mine) state.duration else 0L, colors.primary, enabled = mine)
            }

            hearts.forEach { h -> key(h.id) { FloatingHeart(h) { hearts = hearts - h } } }

            AnimatedVisibility(
                commentsOpen && comments != null,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(180)),
            ) {
                comments?.let {
                    SwipeDownToClose(onClose = { commentsOpen = false }) {
                        CommentsPanel(
                            it,
                            onClose = { commentsOpen = false },
                            modifier = Modifier.fillMaxHeight(0.68f).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).padding(bottom = bottomInset),
                        )
                    }
                }
            }
        }

        if (menuOpen) {
            VideoSettingsSheet(
                onDismiss = { menuOpen = false },
                onCaptions = {
                    menuOpen = false
                    captionsOpen = true
                },
                onDescription = {
                    menuOpen = false
                    descriptionOpen = true
                },
            )
        }
        if (captionsOpen) CaptionsSheet(onDismiss = { captionsOpen = false })
        if (saveOpen) SaveToPlaylistSheet(item.id, onDismiss = { saveOpen = false })
        if (descriptionOpen) {
            ModalBottomSheet(onDismissRequest = { descriptionOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.background) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(details?.title?.ifBlank { null } ?: item.title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 25.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOfNotNull(
                            (baseLikes?.let { compact(it + if (liked) 1 else 0) } ?: details?.likes)?.let { it to "Likes" },
                            (details?.exactViews ?: details?.views)?.let { it to "Views" },
                            details?.date?.let { it to "Posted" },
                        ).forEach { (value, label) ->
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(colors.surfaceContainerHigh).padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(label, fontSize = 12.sp, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                    Text(details?.description ?: "No description.", fontSize = 14.sp, lineHeight = 20.sp, color = colors.onBackground)
                }
            }
        }
    }
}

/**
 * Comments that close when you pull them down: from the top of the list, or by the header.
 * Let go far enough (or flick) and they close; otherwise they spring back.
 */
@Composable
private fun SwipeDownToClose(onClose: () -> Unit, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    var drag by remember { mutableFloatStateOf(0f) }
    val far = with(androidx.compose.ui.platform.LocalDensity.current) { 120.dp.toPx() }
    fun settle(velocity: Float) {
        if (drag > far || (drag > 0f && velocity > 1800f)) onClose()
        else scope.launch { androidx.compose.animation.core.animate(drag, 0f, animationSpec = spring(stiffness = 600f)) { v, _ -> drag = v } }
    }
    val connection = remember {
        object : NestedScrollConnection {
            // Pulled down past the top of the list: the panel follows your finger.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    drag += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            // Pushed back up: the panel first, then the list.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (drag > 0f && available.y < 0f) {
                    val take = maxOf(available.y, -drag)
                    drag += take
                    return Offset(0f, take)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drag <= 0f) return Velocity.Zero
                settle(available.y)
                return available
            }
        }
    }
    Box(
        Modifier
            .offset { IntOffset(0, drag.roundToInt()) }
            .nestedScroll(connection)
            .draggable(
                rememberDraggableState { d -> drag = (drag + d).coerceAtLeast(0f) },
                Orientation.Vertical,
                onDragStopped = { v -> settle(v) },
            ),
    ) { content() }
}

/** After a tap: play or pause, big in the middle, popping in and fading away. */
@Composable
private fun PlayFlash(playing: Boolean, modifier: Modifier) {
    val scale = remember { Animatable(0.6f) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1.1f, spring(dampingRatio = 0.5f, stiffness = 500f)) }
        alpha.animateTo(0f, tween(550, delayMillis = 250))
    }
    Box(
        modifier
            .size(88.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (playing) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp))
    }
}

/**
 * The Short's progress, and a scrub bar: drag (or tap) along it to move through the Short. It
 * thickens under your finger and shows the time you'd jump to.
 */
@Composable
private fun Scrubber(position: Long, duration: Long, accent: Color, enabled: Boolean) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: if (duration > 0) position.toFloat() / duration else 0f
    val thick by animateDpAsState(if (dragging != null) 8.dp else 3.dp, label = "thick")
    Column(Modifier.fillMaxWidth()) {
        if (dragging != null && duration > 0) {
            Text(
                "${formatTime((shown * duration).toLong())} / ${formatTime(duration)}",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                style = shadowed,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .padding(horizontal = 12.dp)
                .pointerInput(enabled, duration) {
                    if (!enabled || duration <= 0) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { at -> dragging = (at.x / size.width).coerceIn(0f, 1f) },
                        onHorizontalDrag = { change, _ -> dragging = (change.position.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragging?.let { CounstarPlayer.player.seekTo((it * duration).toLong()) }
                            dragging = null
                        },
                        onDragCancel = { dragging = null },
                    )
                }
                .pointerInput(enabled, duration) {
                    if (!enabled || duration <= 0) return@pointerInput
                    detectTapGestures { at -> CounstarPlayer.player.seekTo(((at.x / size.width).coerceIn(0f, 1f) * duration).toLong()) }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxWidth().height(thick).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.28f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(shown.coerceIn(0f, 1f)).background(accent))
            }
        }
    }
}

/** The double-tap heart: pops in where you tapped, tilted, then floats up and fades. */
@Composable
private fun FloatingHeart(h: Heart, onDone: () -> Unit) {
    val scale = remember { Animatable(0.2f) }
    val alpha = remember { Animatable(1f) }
    val rise = remember { Animatable(0f) }
    LaunchedEffect(h.id) {
        scale.animateTo(1.15f, spring(dampingRatio = 0.42f, stiffness = 420f))
        launch { rise.animateTo(-180f, tween(650)) }
        launch { scale.animateTo(1.35f, tween(650)) }
        alpha.animateTo(0f, tween(600, delayMillis = 80))
        onDone()
    }
    Icon(
        Icons.Rounded.Favorite,
        contentDescription = null,
        tint = Color(0xFFFF2D55),
        modifier = Modifier
            .offset { IntOffset((h.at.x - 48.dp.toPx()).roundToInt(), (h.at.y - 48.dp.toPx() + rise.value).roundToInt()) }
            .size(96.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                rotationZ = h.tilt
                this.alpha = alpha.value
            },
    )
}

/** One of the actions down the right: the icon in a glass circle, and its count or name below. */
@Composable
private fun Rail(icon: ImageVector, label: String, tint: Color = Color.White, scale: Float = 1f, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .size(50.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(28.dp))
        }
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, style = shadowed, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.width(64.dp))
    }
}
