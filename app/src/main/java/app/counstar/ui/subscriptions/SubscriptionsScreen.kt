package app.counstar.ui.subscriptions

import androidx.compose.ui.platform.LocalContext
import app.counstar.ui.home.refreshFailed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.counstar.data.ChannelItem
import app.counstar.data.Channels
import app.counstar.data.FeedEntry
import app.counstar.data.Feeds
import app.counstar.data.mix
import app.counstar.ui.posts.PostCard
import app.counstar.data.VideoItem
import app.counstar.ui.ChipButton
import app.counstar.ui.Nav
import app.counstar.ui.components.Avatar
import app.counstar.ui.components.VideoCard
import app.counstar.ui.components.pressable
import app.counstar.ui.components.rememberHaptics
import app.counstar.ui.theme.YouTubeRed
import app.counstar.ui.you.Page
import kotlinx.coroutines.launch

private enum class Filter(val title: String) { All("All"), Today("Today"), Videos("Videos"), Live("Live"), Posts("Posts"), Continue("Continue watching"), Unwatched("Unwatched") }

private fun Filter.keeps(v: VideoItem): Boolean = when (this) {
    Filter.All -> true
    Filter.Today -> v.isLive || v.published?.let { p -> listOf("second", "minute", "hour").any { it in p } } == true
    Filter.Videos -> !v.isLive
    Filter.Live -> v.isLive
    Filter.Posts -> false
    Filter.Continue -> v.watched != null && v.watched < 0.95f
    Filter.Unwatched -> v.watched == null
}

/**
 * Subscriptions, like the concept: your channels along the top (whoever posted most recently
 * first, live ones ringed in red), then filters, then their videos.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(contentPadding: PaddingValues, refreshOffset: Dp) {
    val feed = Feeds.subscriptions
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    LaunchedEffect(feed) { feed.load() }
    LaunchedEffect(Unit) { Channels.loadSubscriptions() }
    var filter by rememberSaveable { mutableStateOf(Filter.All) }
    var refreshing by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 4 } }
    LaunchedEffect(nearEnd, feed.items.size) { if (nearEnd && feed.canLoadMore) feed.loadMore() }

    // Channels in the order they last posted, then the rest of your subscriptions.
    val recent = feed.items.filter { it.channelId != null }.distinctBy { it.channelId }
        .map { ChannelItem(it.channelId!!, it.channelName.orEmpty(), it.channelAvatar) to it.isLive }
    val rest = Channels.subscriptions.orEmpty().filter { c -> recent.none { it.first.id == c.id } }.map { it to false }
    val channels = (recent + rest).take(30)
    // All: videos with the channels' posts among them; Posts: just those.
    val shown = when (filter) {
        Filter.All -> mix(feed.items, feed.posts)
        Filter.Posts -> feed.posts.takeIf { app.counstar.data.Settings.showPosts }.orEmpty().map { FeedEntry.PostEntry(it) }
        else -> feed.items.filter(filter::keeps).map { FeedEntry.Video(it) }
    }

    val refresh = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        state = refresh,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(state = refresh, isRefreshing = refreshing, modifier = Modifier.align(Alignment.TopCenter).padding(top = refreshOffset))
        },
        onRefresh = {
            scope.launch {
                haptics.tick()
                refreshing = true
                Channels.loadSubscriptions(force = true)
                if (!feed.refresh()) refreshFailed(context)
                list.scrollToItem(0)
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (channels.isNotEmpty()) {
                item(key = "channels") {
                    LazyRow(
                        Modifier.padding(top = 8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(channels, key = { it.first.id }) { (c, live) -> ChannelBubble(c, live) }
                        item(key = "all") {
                            Box(
                                Modifier
                                    .height(60.dp)
                                    .pressable(RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) { Nav.allSubscriptionsOpen = true }
                                    .padding(horizontal = 18.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text("All", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            }
            item(key = "filters") {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Filter.entries.filter { it != Filter.Posts || (feed.posts.isNotEmpty() && app.counstar.data.Settings.showPosts) }, key = { it.name }) { f -> ChipButton(f.title, filter == f) { filter = f } }
                }
            }
            items(shown, key = { it.key }) { e ->
                when (e) {
                    is FeedEntry.Video -> VideoCard(e.item, onClick = { Nav.watch(e.item) }, modifier = Modifier.animateItem())
                    is FeedEntry.PostEntry -> PostCard(e.post, modifier = Modifier.animateItem())
                    is FeedEntry.Channel -> {}
                }
            }
            when {
                feed.loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(56.dp)) }
                }
                feed.loadedOnce && shown.isEmpty() -> item(key = "empty") {
                    Text(
                        if (feed.items.isEmpty()) "Subscribe to channels to see their new videos here." else "Nothing here right now.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelBubble(c: ChannelItem, live: Boolean) {
    Column(
        Modifier.width(66.dp).pressable(RoundedCornerShape(18.dp), pressedScale = 0.9f, clipAndRipple = false) { Nav.openChannel(c.id) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Avatar(c.avatar, 56.dp, ring = if (live) YouTubeRed else null)
            if (live) {
                Text(
                    "LIVE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.background(YouTubeRed, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Text(c.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Every channel you're subscribed to. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AllSubscriptionsScreen(bottomPadding: Dp) {
    BackHandler { Nav.allSubscriptionsOpen = false }
    LaunchedEffect(Unit) { Channels.loadSubscriptions() }
    val all = Channels.subscriptions
    Page("All subscriptions", onBack = { Nav.allSubscriptionsOpen = false }) {
        when {
            all == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(56.dp)) }
            all.isEmpty() -> Text("No subscriptions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(32.dp))
            else -> LazyColumn(contentPadding = PaddingValues(bottom = bottomPadding)) {
                items(all, key = { it.id }) { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pressable(RoundedCornerShape(16.dp), pressedScale = 0.98f) { Nav.openChannel(c.id) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Avatar(c.avatar, 48.dp)
                        Column(Modifier.weight(1f)) {
                            Text(c.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            listOfNotNull(c.handle, c.subscribers).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
