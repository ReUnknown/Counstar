package app.counstar.ui.you

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.counstar.data.Feed
import app.counstar.data.Feeds
import app.counstar.ui.Nav
import app.counstar.ui.components.VideoRow
import kotlinx.coroutines.launch

/** Everything you've watched, newest first, under the dates YouTube files it by. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(bottomPadding: Dp) {
    BackHandler { Nav.historyOpen = false }
    val feed = Feeds.history
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(feed) { feed.load() }
    Page("History", onBack = { Nav.historyOpen = false }) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; feed.refresh(); refreshing = false } },
            modifier = Modifier.fillMaxSize(),
        ) {
            VideoList(feed, bottomPadding, empty = "Videos you watch show up here.", dated = true)
        }
    }
}

/** A full-screen page over the tabs: back arrow and a big title, then [content]. */
@Composable
fun Page(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp, maxLines = 1, modifier = Modifier.padding(start = 4.dp))
        }
        content()
    }
}

/** A feed as rows, loading more near the end. [dated]: headings where the date changes. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoList(
    feed: Feed,
    bottomPadding: Dp,
    empty: String,
    dated: Boolean = false,
    numbered: Boolean = false,
    header: (LazyListScope.() -> Unit)? = null,
) {
    val list = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 5 } }
    LaunchedEffect(nearEnd, feed.items.size) { if (nearEnd && feed.canLoadMore) feed.loadMore() }
    LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding)) {
        header?.invoke(this)
        var section: String? = null
        feed.items.forEachIndexed { i, video ->
            if (dated && video.section != null && video.section != section) {
                section = video.section
                item(key = "s$i") {
                    Text(
                        video.section,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(start = 16.dp, top = if (i == 0) 4.dp else 20.dp, bottom = 6.dp),
                    )
                }
            }
            item(key = "v$i" + video.id) { VideoRow(video, onClick = { Nav.watch(video) }, index = if (numbered) i + 1 else null) }
        }
        when {
            feed.loading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(48.dp)) }
            }
            feed.items.isEmpty() -> item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(feed.error ?: empty, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
