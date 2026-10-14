package app.counstar.ui.watch

import android.content.Intent
import app.counstar.ui.components.blockTouches
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import app.counstar.ui.components.SaveToPlaylistSheet
import app.counstar.ui.components.rememberArtColorScheme
import app.counstar.ui.components.rememberHaptics
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.graphics.graphicsLayer
import app.counstar.ui.components.frost
import app.counstar.ui.theme.isCounstarDark
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.counstar.data.BellOption
import app.counstar.data.Feeds
import app.counstar.data.Feed
import app.counstar.data.VideoItem
import app.counstar.data.WatchInfo
import app.counstar.data.YouTubeParser
import app.counstar.playback.CounstarPlayer
import app.counstar.ui.ChipButton
import app.counstar.ui.Nav
import app.counstar.ui.components.Avatar
import app.counstar.ui.components.VideoCard
import app.counstar.ui.components.pressable
import com.metrolist.innertube.WebApi
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

/**
 * The watch page: the player on top, then the video's details, its channel, actions and what to
 * watch next. The video's own light glows behind it all; everything else is the app's colors.
 */
@Composable
fun WatchScreen(fullscreen: Boolean, onToggleFullscreen: () -> Unit) {
    val item = CounstarPlayer.current ?: return
    BackHandler { if (fullscreen) onToggleFullscreen() else Nav.watchOpen = false }

    var info by remember(item.id) { mutableStateOf<WatchInfo?>(null) }
    var raw by remember(item.id) { mutableStateOf<String?>(null) }
    var failed by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id) {
        withContext(Dispatchers.IO) { YouTube.rawNext(videoId = item.id).mapCatching { it to YouTubeParser.watch(it) } }
            .onSuccess { (json, parsed) ->
                raw = json
                info = parsed
            }
            .onFailure { failed = true }
    }

    if (fullscreen) {
        VideoPlayer(
            fullscreen = true,
            onToggleFullscreen = onToggleFullscreen,
            onMinimize = null,
            heatmap = info?.heatmap.orEmpty(),
            chapters = info?.chapters.orEmpty(),
            title = info?.title ?: item.title,
            // The bars around the picture too: nothing behind full screen can be tapped.
            modifier = Modifier.fillMaxSize().blockTouches(),
        )
        return
    }

    // Swipe down on the video to minimize it: the page follows the finger, and past a point
    // it lets go into the mini player.
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    val density = LocalDensity.current

    val list = rememberLazyListState()
    var commentsOpen by remember(item.id) { mutableStateOf(false) }
    var descriptionOpen by remember(item.id) { mutableStateOf(false) }
    val details = info
    val comments = remember(details?.commentsToken) {
        details?.commentsToken?.let { CommentsState(it, details.commentSorts, details.commentCount) }
    }
    val dark = isCounstarDark()
    val page = MaterialTheme.colorScheme.background
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = drag.value
                val f = (drag.value / size.height).coerceIn(0f, 1f)
                alpha = 1f - f * 0.6f
                scaleX = 1f - f * 0.15f
                scaleY = 1f - f * 0.15f
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
            .background(page)
            .blockTouches(),
    ) {
        // Acrylic glow from the video itself, spilling out under the player and fading into the
        // page. It belongs to the top of the page: scrolling down carries it away with the title.
        // Dimmer in dark mode, so only the video itself is bright.
        val glowHeight = 560.dp
        Box(
            Modifier
                .fillMaxWidth()
                .height(glowHeight)
                .graphicsLayer {
                    val gone = list.firstVisibleItemIndex > 0
                    translationY = if (gone) -size.height else -list.firstVisibleItemScrollOffset.toFloat()
                    alpha = if (gone) 0f else 1f
                },
        ) {
            AmbientLight(item.thumbnail, Modifier.matchParentSize(), strength = if (dark) 0.5f else 0.8f)
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to page.copy(alpha = if (dark) 0.25f else 0f),
                            0.3f to Color.Transparent,
                            1f to page,
                        ),
                    ),
            )
        }
        Column(Modifier.fillMaxSize()) {
            // Above the video: the page's own color, like below it (never the glow).
            Box(Modifier.fillMaxWidth().background(page).statusBarsPadding())
            VideoPlayer(
                fullscreen = false,
                onToggleFullscreen = onToggleFullscreen,
                onMinimize = { Nav.watchOpen = false },
                heatmap = info?.heatmap.orEmpty(),
                chapters = info?.chapters.orEmpty(),
                title = info?.title ?: item.title,
                onSwipe = { dy -> scope.launch { drag.snapTo(dy) } },
                onSwipeEnd = { dy ->
                    scope.launch {
                        if (dy > with(density) { 140.dp.toPx() }) {
                            Nav.watchOpen = false
                            drag.animateTo(drag.value + with(density) { 200.dp.toPx() }, tween(160))
                            drag.snapTo(0f)
                        } else {
                            drag.animateTo(0f, spring(dampingRatio = 0.7f))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
            Box(Modifier.fillMaxSize()) {
                WatchDetails(item, info, raw, failed, list, comments, onOpenComments = { commentsOpen = true }, onOpenDescription = { descriptionOpen = true })
                // The description opens the same way, in the same space.
                androidx.compose.animation.AnimatedVisibility(
                    visible = descriptionOpen && details != null,
                    enter = slideInVertically(tween(340)) { it } + fadeIn(tween(160)),
                    exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
                ) {
                    details?.let { DescriptionPanel(item, it, onClose = { descriptionOpen = false }) }
                }
                // Comments take over everything under the video, like YouTube's app.
                androidx.compose.animation.AnimatedVisibility(
                    visible = commentsOpen && comments != null,
                    enter = slideInVertically(tween(340)) { it } + fadeIn(tween(160)),
                    exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
                ) {
                    comments?.let { CommentsPanel(it, onClose = { commentsOpen = false }) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WatchDetails(
    item: VideoItem,
    details: WatchInfo?,
    raw: String?,
    failed: Boolean,
    list: LazyListState,
    comments: CommentsState?,
    onOpenComments: () -> Unit,
    onOpenDescription: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var saving by remember(item.id) { mutableStateOf(false) }
    var liked by remember(item.id, details) { mutableStateOf(details?.liked ?: false) }
    var disliked by remember(item.id, details) { mutableStateOf(details?.disliked ?: false) }
    fun failed(what: String, error: Throwable) {
        Toast.makeText(context, "Couldn't $what: ${error.message}", Toast.LENGTH_SHORT).show()
    }
    val baseLikes = details?.likeCount?.let { it - if (details.liked) 1 else 0 }

    // Up next: the watch page's own list, plus chips to pull more. YouTube's own chips when it
    // sends them (signed in it usually does); otherwise our own: the channel, your feed, related.
    val upNext = remember(raw) { raw?.let { Feed.upNext(it) } }
    LaunchedEffect(upNext) { upNext?.load() }
    val ytChips = upNext?.chips.orEmpty()
    val chipFeeds: List<Pair<String, () -> Feed?>> = remember(ytChips, details?.channelId) {
        if (ytChips.isNotEmpty()) {
            ytChips.mapIndexed { i, c -> c.title to { if (i == 0) null else c.token?.let { Feed.nextFrom(it) } } }
        } else {
            listOfNotNull(
                "All" to { null },
                details?.channelId?.let { id -> "From ${details.channelName ?: "channel"}" to { Feed.channelVideos(id) } },
                "For you" to { Feeds.home },
                "Related" to { Feeds.search(details?.title ?: item.title) },
            )
        }
    }
    val chips = chipFeeds.map { it.first }
    var chip by remember(item.id) { mutableStateOf(0) }
    val chipFeed = remember(chip, chipFeeds) { chipFeeds.getOrNull(chip)?.second?.invoke() }
    val feed = chipFeed ?: upNext
    LaunchedEffect(chipFeed) { chipFeed?.load() }

    LaunchedEffect(item.id) { list.scrollToItem(0) }
    val nearEnd by remember {
        derivedStateOf {
            val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= list.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, feed?.items?.size) { if (nearEnd && feed?.canLoadMore == true) feed.loadMore() }

    val colors = MaterialTheme.colorScheme
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Where the chips sit in the list below: after the title, channel, actions (and comments).
    val chipsIndex = if (comments != null) 4 else 3
    val chipsStuck by remember(chips.size) { derivedStateOf { chips.isNotEmpty() && list.firstVisibleItemIndex >= chipsIndex } }

    @Composable
    fun ChipsRow() {
        LazyRow(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(chips.size) { i ->
                ChipButton(chips[i], chip == i) {
                    chip = i
                    scope.launch { if (list.firstVisibleItemIndex > chipsIndex) list.scrollToItem(chipsIndex) }
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = maxHeight
        val haze = rememberHazeState()
        LazyColumn(
            state = list,
            // Lists run on under the gesture bar instead of stopping above it.
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().hazeSource(haze),
        ) {
            item(key = "title") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable { if (details != null) onOpenDescription() },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        details?.title ?: item.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, lineHeight = 25.sp, fontSize = 20.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = listOfNotNull(details?.views ?: item.views, details?.published ?: item.published).joinToString(" · ")
                    Text(
                        buildString {
                            append(meta)
                            if (details != null) append("  ...more")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            item(key = "channel") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // The avatar and name open the channel.
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { Nav.openChannel(details?.channelId ?: item.channelId) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                    Avatar(details?.channelAvatar ?: item.channelAvatar, 40.dp)
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            details?.channelName ?: item.channelName.orEmpty(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        details?.subscribers?.let {
                            Text(it.removeSuffix(" subscribers"), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
                        }
                    }
                    }
                    val channelId = details?.channelId
                    if (channelId != null) {
                        val channelColors = rememberArtColorScheme(details.channelAvatar)
                        SubscribeButton(channelId, details.subscribed, details.bellOptions, accent = channelColors.primary, onAccent = channelColors.onPrimary)
                    }
                }
            }
            item(key = "actions") {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        // Like and dislike, joined like YouTube's. Dislikes show no count.
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            val count = baseLikes?.let { it + if (liked) 1 else 0 }
                            Action(
                                icon = if (liked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                                label = count?.let(::compact) ?: details?.likes ?: "Like",
                                active = liked,
                                shape = RoundedCornerShape(20.dp, 6.dp, 6.dp, 20.dp),
                            ) {
                                haptics.click()
                                val want = !liked
                                liked = want
                                if (want) disliked = false
                                scope.launch {
                                    (if (want) WebApi.like(item.id) else WebApi.removeLike(item.id)).onFailure { liked = !want; failed("like that", it) }
                                }
                            }
                            Action(
                                icon = if (disliked) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown,
                                label = null,
                                active = disliked,
                                shape = RoundedCornerShape(6.dp, 20.dp, 20.dp, 6.dp),
                                description = "Dislike",
                            ) {
                                haptics.click()
                                val want = !disliked
                                disliked = want
                                if (want) liked = false
                                scope.launch {
                                    (if (want) WebApi.dislike(item.id) else WebApi.removeLike(item.id)).onFailure { disliked = !want; failed("dislike that", it) }
                                }
                            }
                        }
                    }
                    item {
                        Action(icon = Icons.Rounded.Share, label = "Share") {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${item.id}")
                            }
                            context.startActivity(Intent.createChooser(send, null))
                        }
                    }
                    item {
                        Action(
                            icon = Icons.Outlined.BookmarkBorder,
                            label = "Save",
                        ) { saving = true }
                    }
                }
            }
            if (comments != null) {
                item(key = "comments") { CommentsCard(comments, onOpenComments) }
            }
            if (chips.isNotEmpty()) {
                item(key = "chips") { ChipsRow() }
            }
            if ((details == null && !failed) || (feed?.loading == true && feed.items.isEmpty())) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(56.dp)) }
                }
            }
            items(feed?.items.orEmpty(), key = { "r" + it.id }) { next ->
                VideoCard(next, onClick = { Nav.watch(next) })
            }
            if (feed?.loading == true && feed.items.isNotEmpty()) {
                item(key = "more") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(44.dp)) }
                }
            }
        }


        if (saving) SaveToPlaylistSheet(item.id) { saving = false }

        // Once the chips scroll up to the top, they stay there on frosted glass.
        if (chipsStuck) {
            Box(Modifier.fillMaxWidth().height(50.dp).hazeEffect(haze, frost())) {
                ChipsRow()
            }
        }
    }
}

/**
 * Subscribe, like YouTube's: tap to subscribe (notifications start out personalized). Once
 * subscribed, it's a bell: tap it for all / personalized / none, or to unsubscribe.
 */
@Composable
fun SubscribeButton(
    channelId: String,
    initiallySubscribed: Boolean,
    options: List<BellOption>,
    /** The channel's own color (from its picture), instead of plain white. */
    accent: Color? = null,
    onAccent: Color? = null,
    modifier: Modifier = Modifier,
    big: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    var subscribed by remember(channelId, initiallySubscribed) { mutableStateOf(initiallySubscribed) }
    var bell by remember(channelId, options) {
        mutableStateOf(options.firstOrNull { it.selected }?.kind ?: BellOption.Kind.Personalized)
    }
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    Box {
        if (!subscribed) {
            Text(
                "Subscribe",
                color = onAccent ?: colors.surface,
                fontWeight = FontWeight.Bold,
                fontSize = if (big) 15.sp else 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = modifier
                    .pressable(RoundedCornerShape(if (big) 25.dp else 20.dp), color = accent ?: colors.onSurface) {
                        haptics.click()
                        subscribed = true
                        bell = BellOption.Kind.Personalized
                        scope.launch { WebApi.subscribe(channelId).onFailure { subscribed = false; toast("Couldn't subscribe: ${it.message}") } }
                    }
                    .padding(horizontal = 18.dp, vertical = if (big) 15.dp else 10.dp),
            )
        } else {
            Row(
                modifier
                    .pressable(RoundedCornerShape(if (big) 25.dp else 20.dp), color = (accent ?: colors.onSurface).copy(alpha = if (accent != null) 0.22f else 0.1f)) {
                        haptics.click()
                        menu = true
                    }
                    .padding(start = 12.dp, end = 10.dp, top = if (big) 13.dp else 8.dp, bottom = if (big) 13.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                Icon(bellIcon(bell), contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Subscribed", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.widthIn(min = 260.dp),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.title, fontWeight = if (option.kind == bell) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = { Icon(bellIcon(option.kind), contentDescription = null) },
                    onClick = {
                        menu = false
                        val before = bell
                        bell = option.kind
                        scope.launch { WebApi.setChannelNotifications(option.params).onFailure { bell = before; toast("Couldn't change notifications: ${it.message}") } }
                    },
                )
            }
            if (options.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Unsubscribe") },
                leadingIcon = { Icon(Icons.Rounded.PersonRemove, contentDescription = null) },
                onClick = {
                    menu = false
                    subscribed = false
                    scope.launch { WebApi.unsubscribe(channelId).onFailure { subscribed = true; toast("Couldn't unsubscribe: ${it.message}") } }
                },
            )
        }
    }
}

private fun bellIcon(kind: BellOption.Kind): ImageVector = when (kind) {
    BellOption.Kind.All -> Icons.Outlined.NotificationsActive
    BellOption.Kind.Personalized -> Icons.Outlined.NotificationsNone
    BellOption.Kind.None -> Icons.Outlined.NotificationsOff
}

/** 950, 1.2K, 48K, 3.4M: like YouTube writes counts. */
internal fun compact(n: Long): String = when {
    n < 1_000 -> n.toString()
    n < 10_000 -> DecimalFormat("0.#").format(n / 1_000.0) + "K"
    n < 1_000_000 -> (n / 1_000).toString() + "K"
    n < 10_000_000 -> DecimalFormat("0.#").format(n / 1_000_000.0) + "M"
    else -> (n / 1_000_000).toString() + "M"
}

@Composable
private fun Action(
    icon: ImageVector,
    label: String?,
    active: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    description: String? = null,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val content = if (active) colors.surface else colors.onSurface
    Row(
        Modifier
            .height(40.dp)
            .pressable(shape, color = if (active) colors.onSurface else colors.onSurface.copy(alpha = 0.1f), onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            Icon(icon, contentDescription = description ?: label, modifier = Modifier.size(18.dp))
            if (label != null) Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = content)
        }
    }
}
