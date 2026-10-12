package app.counstar.ui.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import app.counstar.data.Feed
import app.counstar.ui.Nav
import app.counstar.ui.components.VideoCard
import kotlinx.coroutines.launch

/**
 * A feed of full-width video cards with a top bar that scrolls away, like YouTube's. Loads more
 * as you near the end; pull down to refresh.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    feed: Feed,
    contentPadding: PaddingValues,
    emptyText: String,
    refreshOffset: Dp,
) {
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(feed) { feed.load() }
    val nearEnd by remember {
        derivedStateOf {
            val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= list.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, feed.items.size) {
        if (nearEnd && feed.canLoadMore) feed.loadMore()
    }

    val refreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        state = refreshState,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = refreshState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = refreshOffset),
            )
        },
        onRefresh = {
            scope.launch {
                refreshing = true
                feed.load(force = true)
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = list,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(feed.items, key = { it.id }) { item ->
                VideoCard(item, onClick = { Nav.watch(item) }, modifier = Modifier.animateItem())
            }
            when {
                feed.error != null && feed.items.isEmpty() -> item(key = "error") {
                    Message(feed.error!!, action = "Try again") { scope.launch { feed.load(force = true) } }
                }
                feed.loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator(Modifier.size(56.dp))
                    }
                }
                feed.loadedOnce && feed.items.isEmpty() -> item(key = "empty") { Message(emptyText) }
            }
        }
    }
}

@Composable
private fun Message(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) Button(onClick = onAction) { Text(action) }
    }
}

/** Counstar's mark: a tilted rounded square with a play triangle. */
@Composable
fun CounstarLogo(size: Dp = 30.dp) {
    Box(
        Modifier
            .size(size)
            .rotate(-8f)
            .clip(RoundedCornerShape(size * 0.37f))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(size * 0.7f),
        )
    }
}
