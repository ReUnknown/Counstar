package app.counstar.ui.notifications

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.NotificationsNone
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.counstar.data.Notification
import app.counstar.data.VideoItem
import app.counstar.data.YouTubeParser
import app.counstar.ui.ChipButton
import app.counstar.ui.Nav
import app.counstar.ui.components.Avatar
import app.counstar.ui.components.BlurredImage
import app.counstar.ui.components.pressable
import coil3.compose.AsyncImage
import com.metrolist.innertube.WebApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Notifications, done better than YouTube's list: what's new comes first as big cards on the
 * video's own frosted art; everything else is grouped by when it happened, in rounded stacks.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(bottomPadding: Dp) {
    BackHandler { Nav.notificationsOpen = false }
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<Notification>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var filter by remember { mutableIntStateOf(0) }
    suspend fun load() {
        withContext(Dispatchers.IO) { WebApi.notifications().mapCatching(YouTubeParser::notifications) }
            .onSuccess { items = it; failed = false }
            .onFailure { failed = items == null }
    }
    LaunchedEffect(Unit) { load() }

    val all = items.orEmpty()
    val filters = listOf("All", "New", "Videos", "Other")
    val shown = when (filter) {
        1 -> all.filter { !it.read }
        2 -> all.filter { it.videoId != null }
        3 -> all.filter { it.videoId == null }
        else -> all
    }
    val fresh = shown.filter { !it.read }
    val rest = shown.filter { it.read }
    val groups = rest.groupBy { whenGroup(it.time) }.toSortedMap(compareBy { it.ordinal })

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { Nav.notificationsOpen = false }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text("Notifications", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp, modifier = Modifier.weight(1f))
            val unread = all.count { !it.read }
            if (unread > 0) {
                Text(
                    "$unread new",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filters.size) { i -> ChipButton(filters[i], filter == i) { filter = i } }
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                items == null && !failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) }
                shown.isEmpty() -> Empty(if (failed) "Couldn't load your notifications." else "You're all caught up.")
                else -> LazyColumn(
                    contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (fresh.isNotEmpty()) {
                        item { Heading("New") }
                        itemsIndexed(fresh) { _, n -> HeroCard(n) }
                    }
                    groups.forEach { (group, list) ->
                        item { Heading(group.title) }
                        itemsIndexed(list) { i, n -> StackRow(n, first = i == 0, last = i == list.lastIndex) }
                    }
                }
            }
        }
    }
}

private enum class When(val title: String) { Today("Today"), Week("This week"), Earlier("Earlier") }

private fun whenGroup(time: String?): When = when {
    time == null -> When.Earlier
    listOf("second", "minute", "hour").any { it in time } -> When.Today
    "day" in time -> When.Week
    else -> When.Earlier
}

@Composable
private fun Heading(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 2.dp))
}

private fun open(n: Notification) {
    val id = n.videoId ?: return
    Nav.notificationsOpen = false
    Nav.watch(VideoItem(id = id, title = n.message, thumbnail = n.thumbnail ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"))
}

/** A new notification: the video big, its art blurred into frosted glass under the message. */
@Composable
private fun HeroCard(n: Notification) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .pressable(RoundedCornerShape(28.dp), pressedScale = 0.97f) { open(n) },
    ) {
        Box(Modifier.fillMaxWidth()) {
            n.thumbnail?.let { BlurredImage(it, Modifier.matchParentSize(), radius = 48.dp) }
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.38f)))
            Column {
                if (n.thumbnail != null) {
                    AsyncImage(
                        n.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)),
                    )
                }
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Avatar(n.avatar, 40.dp)
                        Box(Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp).size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(n.message, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
                        n.time?.let { Text(it, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

/** An older notification, in a rounded stack with the others from the same stretch of time. */
@Composable
private fun StackRow(n: Notification, first: Boolean, last: Boolean) {
    val big = 24.dp
    val small = 6.dp
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .offset(y = if (first) 0.dp else (-7).dp)
            .fillMaxWidth()
            .pressable(
                RoundedCornerShape(if (first) big else small, if (first) big else small, if (last) big else small, if (last) big else small),
                color = MaterialTheme.colorScheme.surfaceContainer,
                pressedScale = 0.98f,
            ) { open(n) }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (n.thumbnail != null) {
            Box {
                AsyncImage(n.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.width(112.dp).height(63.dp).clip(RoundedCornerShape(14.dp)))
                Avatar(n.avatar, 26.dp, modifier = Modifier.align(Alignment.BottomStart).offset(x = (-4).dp, y = 4.dp), ring = MaterialTheme.colorScheme.surfaceContainer)
            }
        } else {
            Avatar(n.avatar, 44.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(n.message, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
            n.time?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun Empty(text: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(
            Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(40.dp))
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.NotificationsNone, contentDescription = null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp))
    }
}
