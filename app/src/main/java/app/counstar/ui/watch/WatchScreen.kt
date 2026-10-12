package app.counstar.ui.watch

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.Image
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import app.counstar.ui.ChipButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.counstar.data.VideoItem
import app.counstar.data.WatchInfo
import app.counstar.data.YouTubeParser
import app.counstar.playback.CounstarPlayer
import app.counstar.ui.Nav
import app.counstar.ui.components.Avatar
import app.counstar.ui.components.VideoCard
import app.counstar.ui.components.pressable
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The watch page: the player on top, then the video's details, its channel, actions and what to
 * watch next. The page takes on the video's colors, a little.
 */
@Composable
fun WatchScreen(fullscreen: Boolean, onToggleFullscreen: () -> Unit) {
    val item = CounstarPlayer.current ?: return
    BackHandler { if (fullscreen) onToggleFullscreen() else Nav.watchOpen = false }

    if (fullscreen) {
        VideoPlayer(fullscreen = true, onToggleFullscreen = onToggleFullscreen, onMinimize = null, modifier = Modifier.fillMaxSize())
        return
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AmbientGlow(item)
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().statusBarsPadding())
            VideoPlayer(
                fullscreen = false,
                onToggleFullscreen = onToggleFullscreen,
                onMinimize = { Nav.watchOpen = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
            )
            WatchDetails(item)
        }
    }
}

/**
 * Acrylic glow from the video itself, like YouTube's ambient mode: the picture, blurred far past
 * recognition, spilling out under the player and fading into the page. It follows the video as
 * it plays (the thumbnail stands in until the first frame).
 */
@Composable
private fun AmbientGlow(item: VideoItem) {
    val frame = Ambient.frame
    val modifier = Modifier
        .fillMaxWidth()
        .height(560.dp)
        .blur(90.dp, BlurredEdgeTreatment.Unbounded)
        .alpha(0.8f)
    Crossfade(frame, animationSpec = tween(900), label = "ambient") { bitmap ->
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
        } else {
            AsyncImage(item.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(560.dp)
            .background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to MaterialTheme.colorScheme.background)),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WatchDetails(item: VideoItem) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var info by remember(item.id) { mutableStateOf<WatchInfo?>(null) }
    var failed by remember(item.id) { mutableStateOf(false) }
    var expanded by remember(item.id) { mutableStateOf(false) }
    var liked by remember(item.id) { mutableStateOf(false) }
    var subscribed by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id) {
        withContext(Dispatchers.IO) { YouTube.rawNext(videoId = item.id).mapCatching(YouTubeParser::watch) }
            .onSuccess {
                info = it
                liked = it.liked
                subscribed = it.subscribed
            }
            .onFailure { failed = true }
    }
    val list = rememberLazyListState()
    LaunchedEffect(item.id) { list.scrollToItem(0) }
    val colors = MaterialTheme.colorScheme
    val details = info

    // Lists run on under the gesture bar instead of stopping above it.
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var filter by remember(item.id) { mutableStateOf(0) }
    LazyColumn(
        state = list,
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "title") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .animateContentSize()
                    .clickable { expanded = !expanded },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    details?.title ?: item.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, lineHeight = 25.sp, fontSize = 20.sp),
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(details?.views ?: item.views, details?.published ?: item.published).joinToString(" · ")
                Text(
                    buildString {
                        append(meta)
                        if (details?.description != null && !expanded) append("  ...more")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                if (expanded) {
                    details?.description?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
        item(key = "channel") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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
                val channelId = details?.channelId
                if (channelId != null) {
                    Text(
                        if (subscribed) "Subscribed" else "Subscribe",
                        color = if (subscribed) colors.onSurface else colors.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .pressable(RoundedCornerShape(20.dp), color = if (subscribed) colors.surfaceContainerHighest else colors.primary) {
                                val want = !subscribed
                                subscribed = want
                                scope.launch {
                                    YouTube.subscribeChannel(channelId, want).onFailure { subscribed = !want }
                                }
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
        }
        item(key = "actions") {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Action(
                        icon = { Icon(if (liked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp, null, Modifier.size(18.dp)) },
                        label = details?.likes ?: "Like",
                        active = liked,
                    ) {
                        val want = !liked
                        liked = want
                        scope.launch { YouTube.likeVideo(item.id, want).onFailure { liked = !want } }
                    }
                }
                item {
                    Action(icon = { Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)) }, label = "Share") {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${item.id}")
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    }
                }
            }
        }
        if (details == null && !failed) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(56.dp)) }
            }
        }
        if (details != null && details.related.isNotEmpty()) {
            // Like YouTube: chips to narrow Up next, which stay at the top as you scroll through it.
            val channel = details.channelName
            val filters = listOfNotNull("All", channel?.let { "From $it" }, "Unwatched")
            stickyHeader(key = "chips") {
                LazyRow(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
                        .padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filters.size) { i -> ChipButton(filters[i], filter == i) { filter = i } }
                }
            }
            val shown = when (filters.getOrNull(filter)) {
                "Unwatched" -> details.related.filter { it.watched == null }
                null, "All" -> details.related
                else -> details.related.filter { it.channelName == channel }
            }
            items(shown, key = { "r" + it.id }) { next ->
                VideoCard(next, onClick = { Nav.watch(next) })
            }
        }
    }
}

@Composable
private fun Action(icon: @Composable () -> Unit, label: String, active: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .height(40.dp)
            .pressable(RoundedCornerShape(20.dp), color = if (active) colors.primary else colors.primary.copy(alpha = 0.16f), onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides if (active) colors.onPrimary else colors.onSurface,
        ) {
            icon()
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (active) colors.onPrimary else colors.onSurface)
        }
    }
}

