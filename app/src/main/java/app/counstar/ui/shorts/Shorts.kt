package app.counstar.ui.shorts

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
import app.counstar.ui.components.blockTouches
import app.counstar.ui.components.rememberArtColorScheme
import app.counstar.ui.components.rememberHaptics
import app.counstar.ui.watch.CommentsPanel
import app.counstar.ui.watch.CommentsState
import app.counstar.ui.watch.SubscribeButton
import app.counstar.ui.watch.VideoSurface
import app.counstar.ui.watch.compact
import app.counstar.ui.watch.rememberPlaybackState
import com.metrolist.innertube.WebApi
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A Short, opened from a link: the video fills the screen and loops; tap to pause. Like,
 * dislike, comments and share run down the right; the channel (with Subscribe) and the title sit
 * at the bottom, over a shade, like YouTube's. Comments come up over the lower part of the screen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShortsScreen(item: VideoItem) {
    var commentsOpen by remember(item.id) { mutableStateOf(false) }
    BackHandler { if (commentsOpen) commentsOpen = false else Nav.closeShort() }
    var info by remember(item.id) { mutableStateOf<WatchInfo?>(null) }
    LaunchedEffect(item.id) {
        withContext(Dispatchers.IO) { YouTube.rawNext(videoId = item.id).mapCatching(YouTubeParser::watch) }.onSuccess { info = it }
    }
    val details = info
    val comments = remember(details) { details?.commentsToken?.let { CommentsState(it, details.commentSorts, details.commentCount) } }
    val state = rememberPlaybackState(CounstarPlayer.player)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    MaterialTheme(colorScheme = rememberArtColorScheme(details?.channelAvatar ?: item.channelAvatar ?: item.thumbnail)) {
        val colors = MaterialTheme.colorScheme
        Box(Modifier.fillMaxSize().background(Color.Black).blockTouches()) {
            VideoSurface(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        with(CounstarPlayer.player) { if (isPlaying) pause() else play() }
                    },
                contentScale = ContentScale.Crop,
            )
            if (CounstarPlayer.loading || (state.buffering && !state.playing)) {
                LoadingIndicator(Modifier.align(Alignment.Center).size(64.dp), color = Color.White)
            } else if (!state.playing && CounstarPlayer.error == null) {
                Box(Modifier.align(Alignment.Center).size(72.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(44.dp))
                }
            }
            CounstarPlayer.error?.let { Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(32.dp)) }

            // Shades top and bottom, so the controls read over any video.
            Box(Modifier.fillMaxWidth().height(140.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))))
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))),
            )

            Row(Modifier.statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { Nav.closeShort() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White) }
                Text("Shorts", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }

            // Like, dislike, comments, share.
            var liked by remember(details) { mutableStateOf(details?.liked == true) }
            var disliked by remember(details) { mutableStateOf(details?.disliked == true) }
            val baseLikes = details?.likeCount?.let { it - if (details.liked) 1 else 0 }
            Column(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 8.dp, bottom = 96.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Rail(if (liked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp, baseLikes?.let { compact(it + if (liked) 1 else 0) } ?: details?.likes ?: "Like", on = liked) {
                    if (YouTube.cookie.isNullOrBlank()) {
                        Toast.makeText(context, "Sign in to like", Toast.LENGTH_SHORT).show()
                        return@Rail
                    }
                    haptics.tick()
                    val want = !liked
                    liked = want
                    if (want) disliked = false
                    scope.launch { withContext(Dispatchers.IO) { if (want) WebApi.like(item.id) else WebApi.removeLike(item.id) }.onFailure { liked = !want } }
                }
                Rail(if (disliked) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown, "Dislike", on = disliked) {
                    if (YouTube.cookie.isNullOrBlank()) {
                        Toast.makeText(context, "Sign in to dislike", Toast.LENGTH_SHORT).show()
                        return@Rail
                    }
                    haptics.tick()
                    val want = !disliked
                    disliked = want
                    if (want) liked = false
                    scope.launch { withContext(Dispatchers.IO) { if (want) WebApi.dislike(item.id) else WebApi.removeLike(item.id) }.onFailure { disliked = !want } }
                }
                Rail(Icons.Outlined.ChatBubbleOutline, details?.commentCount ?: "Comments", on = false) { if (comments != null) commentsOpen = true }
                Rail(Icons.Rounded.Share, "Share", on = false) {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "https://youtube.com/shorts/${item.id}")
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }
            }

            // Who and what, at the bottom; then how far through it is.
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    Modifier.padding(start = 16.dp, end = 80.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val channelId = details?.channelId ?: item.channelId
                    Avatar(details?.channelAvatar ?: item.channelAvatar, 36.dp, modifier = Modifier.clip(CircleShape).clickable { Nav.closeShort(); Nav.openChannel(channelId) })
                    Text(
                        details?.channelName ?: item.channelName.orEmpty(),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (details?.channelId != null) {
                        SubscribeButton(details.channelId, details.subscribed, details.bellOptions, accent = colors.primary, onAccent = colors.onPrimary)
                    }
                }
                Text(
                    details?.title?.ifBlank { null } ?: item.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp, end = 80.dp),
                )
                val progress = if (state.duration > 0) state.position.toFloat() / state.duration else 0f
                Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(colors.primary))
                }
            }

            AnimatedVisibility(
                commentsOpen && comments != null,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(180)),
            ) {
                comments?.let {
                    CommentsPanel(
                        it,
                        onClose = { commentsOpen = false },
                        modifier = Modifier
                            .fillMaxHeight(0.68f)
                            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                            .navigationBarsPadding(),
                    )
                }
            }
        }
    }
}

/** One of the actions down the right: icon in a glass circle, and its count or name below. */
@Composable
private fun Rail(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(if (on) colors.primary else Color.Black.copy(alpha = 0.3f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = if (on) colors.onPrimary else Color.White, modifier = Modifier.size(26.dp))
        }
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.width(64.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
