package app.counstar.ui.channel

import android.content.Intent
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

private val tabs = listOf("Home", "Videos", "Live", "Playlists")

/**
 * A channel's page, in the channel's own colors: its banner big at the top, blurring and
 * fading into the page; the avatar ringed in the channel's color; name, handle, subscribers;
 * Subscribe; then Home, Videos, Live and Playlists.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChannelScreen(channelId: String, bottomPadding: Dp) {
    BackHandler { Nav.closeChannel() }
    val page = remember(channelId) { Channels.page(channelId) }
    LaunchedEffect(page) { page.load() }
    val info = page.info
    var tab by rememberSaveable(channelId) { mutableIntStateOf(0) }
    val feed: Feed? = when (tab) {
        0 -> page.home
        1 -> page.videos
        2 -> page.live
        else -> null
    }
    LaunchedEffect(tab) { if (feed != null) feed.load() else page.loadPlaylists() }

    MaterialTheme(colorScheme = rememberArtColorScheme(info?.avatar)) {
        val colors = MaterialTheme.colorScheme
        val list = rememberLazyListState()
        val nearEnd by remember { derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 4 } }
        LaunchedEffect(nearEnd, feed?.items?.size) { if (nearEnd && feed?.canLoadMore == true) feed.loadMore() }
        Box(Modifier.fillMaxSize().background(colors.background)) {
            LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding)) {
                item(key = "header") { Header(channelId, info, page.failed) }
                item(key = "tabs") { Tabs(tab) { tab = it } }
                when (tab) {
                    3 -> playlists(page.playlists)
                    else -> feed?.let { videos(it, featured = tab == 0) }
                }
            }
            // Back and share, on glass over the banner; once the header scrolls away, a solid
            // bar with the channel's name.
            val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 420 } }
            val bar by androidx.compose.animation.core.animateFloatAsState(if (scrolled) 1f else 0f, label = "bar")
            Box(Modifier.fillMaxWidth().graphicsLayer { alpha = bar }.background(colors.background).statusBarsPadding().height(60.dp))
            Row(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassButton({ Nav.closeChannel() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White) }
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
                }) { Icon(Icons.Rounded.Share, contentDescription = "Share channel", tint = Color.White) }
            }
        }
    }
}

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
    ) { content() }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Header(channelId: String, info: ChannelInfo?, failed: Boolean) {
    val colors = MaterialTheme.colorScheme
    var more by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        // The banner (or, without one, the avatar blown up), blurring toward its bottom and
        // fading into the page.
        val art = info?.banner
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            when {
                art != null -> {
                    // Its colors fill the whole header; the banner itself sits on top, close to
                    // its own wide shape (cropped tall it loses its words), blurring into the rest.
                    BlurredImage(art, Modifier.fillMaxSize(), radius = 56.dp)
                    ProgressiveBlurImage(
                        art,
                        Modifier.fillMaxWidth().aspectRatio(3.6f).padding(top = 0.dp).align(Alignment.TopCenter).offset(y = 40.dp),
                        start = 0.55f,
                        end = 1f,
                        maxRadius = 32.dp,
                    )
                }
                info?.avatar != null -> BlurredImage(info.avatar, Modifier.fillMaxSize(), radius = 72.dp)
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color.Transparent, 0.45f to colors.background.copy(alpha = 0.45f), 0.92f to colors.background),
                ),
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 170.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(
                info?.avatar,
                92.dp,
                // Ringed in the channel's color, a gap of page between.
                modifier = Modifier.border(2.dp, colors.primary, CircleShape).padding(4.dp),
            )
            if (info == null) {
                if (failed) Text("Couldn't load this channel.", color = colors.onSurfaceVariant)
                else Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(48.dp)) }
                return@Column
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(info.title, fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.9).sp, color = colors.onBackground)
                Text(
                    listOfNotNull(info.handle, info.subscribers, info.videoCount).joinToString(" · "),
                    fontSize = 14.sp,
                    color = colors.onSurfaceVariant,
                )
            }
            info.description?.let {
                Text(
                    it,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = if (more) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.onSurface,
                    modifier = Modifier.animateContentSize().clickable { more = !more },
                )
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
private fun Tabs(selected: Int, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 12.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 6.dp)) {
            itemsIndexed(tabs) { i, title ->
                val on = i == selected
                Column(
                    Modifier.height(48.dp).clickable { onSelect(i) }.padding(horizontal = 14.dp),
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
private fun LazyListScope.videos(feed: Feed, featured: Boolean) {
    val items = feed.items
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
            Box(Modifier.fillMaxWidth().height(120.dp)) {
                val art = info?.banner
                if (art != null) ProgressiveBlurImage(art, Modifier.fillMaxSize(), start = 0.6f, end = 1f, maxRadius = 24.dp)
                else channel.avatar?.let { BlurredImage(it, Modifier.fillMaxSize(), radius = 64.dp) }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 1f to colors.surfaceContainer)))
            }
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 72.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Avatar(channel.avatar, 72.dp, modifier = Modifier.border(2.dp, colors.primary, CircleShape).padding(3.dp))
                    Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
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
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
