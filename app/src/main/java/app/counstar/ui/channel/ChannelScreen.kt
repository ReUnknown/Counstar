package app.counstar.ui.channel

import android.content.Intent
import app.counstar.ui.components.blockTouches
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.counstar.data.ChannelInfo
import app.counstar.data.Channels
import app.counstar.data.Feed
import app.counstar.data.FeedEntry
import app.counstar.data.SortedFeed
import app.counstar.data.mix
import app.counstar.ui.ChipButton
import app.counstar.ui.posts.PostCard
import app.counstar.ui.Nav
import app.counstar.ui.components.Avatar
import app.counstar.ui.components.BlurredImage
import app.counstar.ui.components.ProgressiveBlurImage
import app.counstar.ui.components.VideoCard
import app.counstar.ui.components.VideoRow
import app.counstar.ui.components.pressable
import app.counstar.ui.components.rememberArtColorScheme
import app.counstar.ui.watch.SubscribeButton
import app.counstar.ui.you.PlaylistRow

private val tabs = listOf("Home", "Videos", "Live", "Posts", "Playlists")

/**
 * A channel's page, in the channel's own colors: its banner big across the top (the whole
 * TV-size picture, fading into the page; its middle, where the art is, sits well below the
 * camera); the avatar ringed in the channel's color;
 * name, handle, subscribers; Subscribe; then Home, Videos, Live, Posts and Playlists. Videos and
 * Live sort by Latest, Popular or Oldest.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChannelScreen(channelId: String, bottomPadding: Dp) {
    BackHandler { Nav.closeChannel() }
    val page = remember(channelId) { Channels.page(channelId) }
    LaunchedEffect(page) { page.load() }
    val info = page.info
    var picked by rememberSaveable(channelId) { mutableStateOf<String?>(null) }
    var aboutOpen by remember { mutableStateOf(false) }
    // Only the tabs this channel has (YouTube lists just those), minus any that came back empty.
    val shownTabs = tabs.filter { t ->
        val listed = when (t) {
            // Only once the page says how to load it.
            "Posts" -> app.counstar.data.Settings.showPosts && info != null && ("Posts" in info.tabParams || "Community" in info.tabParams)
            else -> info == null || info.tabs.isEmpty() || t in info.tabs
        }
        val empty = when (t) {
            "Home" -> page.home.loadedOnce && page.home.items.isEmpty()
            "Live" -> page.live.feed.loadedOnce && page.live.feed.items.isEmpty() && page.live.selected == page.live.sorts.firstOrNull()?.title
            "Posts" -> page.posts?.let { it.loadedOnce && it.posts.isEmpty() && it.items.isEmpty() } == true
            "Playlists" -> page.playlists?.isEmpty() == true
            else -> false
        }
        listed && !empty
    }.ifEmpty { listOf("Videos") }
    val tab = picked?.takeIf { it in shownTabs } ?: shownTabs.first()
    val sorted: SortedFeed? = when (tab) {
        "Videos" -> page.videos
        "Live" -> page.live
        else -> null
    }
    val feed: Feed? = when (tab) {
        "Home" -> page.home
        "Posts" -> page.posts
        else -> sorted?.feed
    }
    LaunchedEffect(tab, feed) { if (feed != null) feed.load() else if (tab == "Playlists") page.loadPlaylists() }

    MaterialTheme(colorScheme = rememberArtColorScheme(info?.avatar)) {
        val colors = MaterialTheme.colorScheme
        val list = rememberLazyListState()
        val nearEnd by remember { derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 4 } }
        LaunchedEffect(nearEnd, feed?.items?.size) { if (nearEnd && feed?.canLoadMore == true) feed.loadMore() }
        Box(Modifier.fillMaxSize().background(colors.background).blockTouches()) {
            LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding)) {
                item(key = "header") { Header(channelId, info, page.failed, onAbout = { aboutOpen = true }) }
                item(key = "tabs") { Tabs(shownTabs, tab) { picked = it } }
                if (sorted != null && sorted.sorts.size > 1) {
                    item(key = "sorts") {
                        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            sorted.sorts.forEach { chip -> ChipButton(chip.title, sorted.selected == chip.title) { sorted.pick(chip) } }
                        }
                    }
                }
                when (tab) {
                    "Playlists" -> playlists(page.playlists)
                    "Posts" -> feed?.let { posts(it) }
                    else -> feed?.let { videos(it, featured = tab == "Home", avatar = info?.avatar, name = info?.title) }
                }
            }
            if (aboutOpen) AboutSheet(page, onDismiss = { aboutOpen = false })
            // Back and share, on glass over the banner; once the header scrolls away, a solid
            // bar with the channel's name.
            val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 520 } }
            val bar by androidx.compose.animation.core.animateFloatAsState(if (scrolled) 1f else 0f, label = "bar")
            Box(Modifier.fillMaxWidth().graphicsLayer { alpha = bar }.background(colors.background).statusBarsPadding().height(60.dp))
            Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassButton({ Nav.closeChannel() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                Text(
                    info?.title.orEmpty(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.onBackground,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp).graphicsLayer { alpha = bar },
                )
                val context = LocalContext.current
                GlassButton({
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/" + (info?.handle ?: "channel/$channelId"))
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }) { Icon(Icons.Rounded.Share, contentDescription = "Share channel") }
            }
        }
    }
}

/** Back and share, on glass over the banner. */
@Composable
private fun GlassButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.32f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Color.White) { content() }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Header(channelId: String, info: ChannelInfo?, failed: Boolean, onAbout: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        // The whole TV-size banner (16:9), edge to edge under the status bar, fading into the
        // page: far more of it than YouTube's thin strip, and its middle clear of the camera.
        val banner = info?.banner
        if (banner != null) ChannelBanner(banner, aspect = 16f / 9f) else Spacer(Modifier.statusBarsPadding().height(64.dp))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Ringed in the channel's color, a gap of page between.
                Avatar(info?.avatar, 80.dp, modifier = Modifier.border(2.dp, colors.primary, CircleShape).padding(4.dp))
                if (info != null) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(info.title, fontSize = 26.sp, lineHeight = 29.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp, color = colors.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        info.handle?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground, maxLines = 1) }
                        Text(listOfNotNull(info.subscribers, info.videoCount).joinToString(" · "), fontSize = 13.sp, color = colors.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            if (info == null) {
                if (failed) Text("Couldn't load this channel.", color = colors.onSurfaceVariant)
                else Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(48.dp)) }
                return@Column
            }
            // The description's first lines; tap for everything about the channel.
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onAbout), verticalAlignment = Alignment.Bottom) {
                Text(
                    info.description?.replace('\n', ' ') ?: "More about this channel",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(" more", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
            }
            SubscribeButton(
                channelId,
                info.subscribed,
                info.bellOptions,
                accent = colors.primary,
                onAccent = colors.onPrimary,
                big = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Tabs(tabs: List<String>, selected: String, onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 12.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 6.dp)) {
            itemsIndexed(tabs) { _, title ->
                val on = title == selected
                Column(
                    Modifier.height(48.dp).clickable { onSelect(title) }.padding(horizontal = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        title,
                        fontSize = 15.sp,
                        fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (on) colors.onBackground else colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .height(3.dp)
                            .size(width = 40.dp, height = 3.dp)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(if (on) colors.primary else Color.Transparent),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.primary.copy(alpha = 0.14f)))
    }
}

/** Home leads with its newest video big, the rest as rows; Videos and Live are full cards. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.videos(feed: Feed, featured: Boolean, avatar: String?, name: String?) {
    // A channel's own tabs leave out its picture and name on each video: fill them in.
    val items = feed.items.map { it.copy(channelAvatar = it.channelAvatar ?: avatar, channelName = it.channelName ?: name) }
    if (featured) {
        items.firstOrNull()?.let { first ->
            item(key = "featured") {
                Column(Modifier.padding(top = 16.dp)) { VideoCard(first, onClick = { Nav.watch(first) }) }
            }
        }
        if (items.size > 1) {
            item(key = "more-title") {
                Text("Videos", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp))
            }
            items(items.drop(1), key = { "r" + it.id }) { v -> VideoRow(v, onClick = { Nav.watch(v) }) }
        }
    } else {
        items(items, key = { "c" + it.id }) { v ->
            Column(Modifier.padding(top = 16.dp)) { VideoCard(v, onClick = { Nav.watch(v) }) }
        }
    }
    when {
        feed.loading -> item(key = "loading") {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(48.dp)) }
        }
        items.isEmpty() && feed.loadedOnce -> item(key = "empty") {
            Text("Nothing here yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(32.dp))
        }
    }
}

/** A channel's posts, newest first. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.posts(feed: Feed) {
    item(key = "posts-top") { Spacer(Modifier.height(16.dp)) }
    items(mix(feed.items, feed.posts), key = { it.key }) { e ->
        when (e) {
            is FeedEntry.PostEntry -> PostCard(e.post, modifier = Modifier.padding(bottom = 12.dp))
            is FeedEntry.Video -> Column(Modifier.padding(bottom = 16.dp)) { VideoCard(e.item, onClick = { Nav.watch(e.item) }) }
            is FeedEntry.Channel -> {}
        }
    }
    when {
        feed.loading -> item(key = "loading") {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(48.dp)) }
        }
        feed.posts.isEmpty() && feed.items.isEmpty() && feed.loadedOnce -> item(key = "empty") {
            Text("No posts yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.playlists(playlists: List<app.counstar.data.Playlist>?) {
    when {
        playlists == null -> item(key = "loading") {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(48.dp)) }
        }
        playlists.isEmpty() -> item(key = "empty") {
            Text("No playlists.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(32.dp))
        }
        else -> {
            item(key = "pad") { Spacer(Modifier.height(10.dp)) }
            items(playlists, key = { "p" + it.id }) { p -> PlaylistRow(p) { Nav.playlist = p } }
        }
    }
}

/**
 * A channel in search results, like the page itself in small: its banner fading into its
 * colors, the avatar over it, name, handle and subscribers, and Subscribe.
 */
@Composable
fun ChannelResult(channel: app.counstar.data.ChannelItem) {
    val page = remember(channel.id) { Channels.page(channel.id) }
    LaunchedEffect(page) { page.load() }
    val info = page.info
    MaterialTheme(colorScheme = rememberArtColorScheme(channel.avatar)) {
        val colors = MaterialTheme.colorScheme
        Box(
            Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
                .pressable(RoundedCornerShape(28.dp), color = colors.surfaceContainer, pressedScale = 0.98f) { Nav.openChannel(channel.id) },
        ) {
            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                info?.banner?.let { ChannelBanner(it, aspect = 3.8f) } ?: Spacer(Modifier.height(4.dp))
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Avatar(channel.avatar, 64.dp, modifier = Modifier.border(2.dp, colors.primary, CircleShape).padding(3.dp))
                    Column(Modifier.weight(1f)) {
                        Text(channel.title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurface)
                        Text(
                            listOfNotNull(channel.handle ?: info?.handle, channel.subscribers ?: info?.subscribers).joinToString(" · "),
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                SubscribeButton(
                    channel.id,
                    info?.subscribed ?: false,
                    info?.bellOptions.orEmpty(),
                    accent = colors.primary,
                    onAccent = colors.onPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
                )
            }
        }
    }
}
