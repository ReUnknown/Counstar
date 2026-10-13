package app.counstar.ui.posts

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.counstar.data.Links
import app.counstar.data.Poll
import app.counstar.data.Post
import app.counstar.data.YouTubeParser
import app.counstar.ui.Nav
import app.counstar.ui.components.Avatar
import app.counstar.ui.components.VideoRow
import app.counstar.ui.components.pressable
import app.counstar.ui.components.rememberArtColorScheme
import app.counstar.ui.components.rememberHaptics
import app.counstar.ui.watch.CommentsPanel
import app.counstar.ui.watch.CommentsState
import app.counstar.ui.you.Page
import coil3.compose.AsyncImage
import com.metrolist.innertube.WebApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Your likes and poll votes on posts, kept while the app runs so every view of a post agrees. */
private object PostVotes {
    val liked = mutableStateMapOf<String, Boolean>()
    /** The choice you picked in each post's poll (-1: none). */
    val choice = mutableStateMapOf<String, Int>()
}

/**
 * A creator's post, like the YouTube app's but on a card of its own: who and when, the text,
 * then its pictures, poll or video, and Like, Comments and Share. Tap it to open the post with
 * its comments ([onOpen] null: already open).
 */
@Composable
fun PostCard(post: Post, modifier: Modifier = Modifier, onOpen: (() -> Unit)? = { Nav.openPost(post) }, onComments: () -> Unit = { Nav.openPost(post) }) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .pressable(RoundedCornerShape(28.dp), color = colors.surfaceContainer, enabled = onOpen != null, pressedScale = 0.99f) { onOpen?.invoke() }
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(post.avatar, 40.dp, modifier = Modifier.clip(CircleShape).clickable { Nav.openChannel(post.channelId) })
            Column(Modifier.weight(1f)) {
                Text(post.author, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                post.time?.let { Text(it, fontSize = 13.sp, color = colors.onSurfaceVariant, maxLines = 1) }
            }
        }
        if (post.text.isNotBlank()) PostText(post, full = onOpen == null)
        when {
            post.images.size == 1 -> AsyncImage(
                post.images.first(),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth().heightIn(max = 560.dp).clip(RoundedCornerShape(20.dp)),
            )
            post.images.size > 1 -> Pictures(post.images)
        }
        post.video?.let { v -> VideoRow(v, onClick = { Nav.watch(v) }) }
        post.poll?.let { PollView(post, it) }
        Actions(post, onComments)
    }
}

/** The text, with its links; long posts fold to six lines until opened ([full] never folds). */
@Composable
private fun PostText(post: Post, full: Boolean) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var expanded by remember(post.id) { mutableStateOf(full) }
    var folds by remember(post.id) { mutableStateOf(false) }
    val text = remember(post.id, colors.primary) {
        buildAnnotatedString {
            append(post.text)
            val style = TextLinkStyles(SpanStyle(color = colors.primary, fontWeight = FontWeight.SemiBold))
            for (link in post.links) {
                val end = (link.start + link.length).coerceAtMost(post.text.length)
                if (link.start >= end) continue
                addLink(
                    LinkAnnotation.Clickable("link", style) {
                        val url = link.url
                        when {
                            link.channelId != null && (url == null || "/@" in url || "/channel/" in url) -> Nav.openChannel(link.channelId)
                            url != null && Links.find(url) != null && ("youtu.be" in url || "/watch" in url) -> Links.pending = url
                            url != null -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                        }
                    },
                    link.start,
                    end,
                )
            }
        }
    }
    Column(Modifier.padding(horizontal = 16.dp).animateContentSize()) {
        Text(
            text,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 6,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) folds = it.hasVisualOverflow },
        )
        if (folds && !expanded) {
            Text(
                "Read more",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).clickable { expanded = true },
            )
        }
    }
}

/** Several pictures: swipe through them, squares like YouTube's, with where you are below. */
@Composable
private fun Pictures(images: List<String>) {
    val colors = MaterialTheme.colorScheme
    val pager = rememberPagerState { images.size }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 12.dp), pageSpacing = 8.dp) { page ->
            Box {
                AsyncImage(
                    images[page],
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)),
                )
                Text(
                    "${page + 1} / ${images.size}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(images.size) { i ->
                val on = i == pager.currentPage
                val width by animateFloatAsState(if (on) 18f else 6f, label = "dot")
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = width.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(if (on) colors.primary else colors.onSurface.copy(alpha = 0.25f)),
                )
            }
        }
    }
}

/**
 * The poll: tap an answer to vote (again to take it back). Once you've voted, each answer fills
 * to its share, yours in the channel's color with a check.
 */
@Composable
private fun PollView(post: Post, poll: Poll) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val chosen = PostVotes.choice[post.id] ?: poll.choices.indexOfFirst { it.selected }
    val voted = chosen >= 0
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        poll.choices.forEachIndexed { i, c ->
            val mine = i == chosen
            val percent = if (!voted) null else if (mine) c.percentIfSelected else c.percentIfNot
            val ratio = if (!voted) 0f else ((if (mine) c.ratioIfSelected else c.ratioIfNot) ?: percent?.removeSuffix("%")?.trim()?.toFloatOrNull()?.div(100f) ?: 0f)
            val fill by animateFloatAsState(ratio.coerceIn(0f, 1f), label = "share")
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .pressable(RoundedCornerShape(if (mine) 26.dp else 16.dp), color = colors.surfaceContainerHighest, pressedScale = 0.98f) {
                        val action = if (mine) c.deselectAction else c.selectAction
                        if (action == null) {
                            Toast.makeText(context, "Sign in to vote", Toast.LENGTH_SHORT).show()
                            return@pressable
                        }
                        haptics.tick()
                        val before = chosen
                        PostVotes.choice[post.id] = if (mine) -1 else i
                        scope.launch {
                            withContext(Dispatchers.IO) { WebApi.commentAction(action) }.onFailure {
                                PostVotes.choice[post.id] = before
                                Toast.makeText(context, it.message ?: "Couldn't vote", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
            ) {
                if (fill > 0f) {
                    Box(Modifier.matchParentSize()) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(if (mine) colors.primary.copy(alpha = 0.35f) else colors.onSurface.copy(alpha = 0.08f)))
                    }
                }
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    c.image?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))) }
                    Text(c.text, fontSize = 15.sp, fontWeight = if (mine) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
                    if (mine) Icon(Icons.Rounded.Check, contentDescription = "Your vote", tint = colors.primary, modifier = Modifier.size(20.dp))
                    percent?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        poll.total?.let { Text(it, fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, top = 2.dp)) }
    }
}

@Composable
private fun Actions(post: Post, onComments: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val liked = PostVotes.liked[post.id] ?: post.liked
    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Action(if (liked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp, likes(post.likes, post.liked, liked), on = liked) {
            val action = if (liked) post.unlikeAction else post.likeAction
            if (action == null) {
                Toast.makeText(context, "Sign in to like posts", Toast.LENGTH_SHORT).show()
                return@Action
            }
            haptics.tick()
            PostVotes.liked[post.id] = !liked
            scope.launch {
                withContext(Dispatchers.IO) { WebApi.commentAction(action) }.onFailure {
                    PostVotes.liked[post.id] = liked
                    Toast.makeText(context, it.message ?: "Couldn't like that", Toast.LENGTH_SHORT).show()
                }
            }
        }
        Action(Icons.Outlined.ChatBubbleOutline, post.comments ?: "Comment", on = false, onClick = onComments)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/post/${post.id}")
            }
            context.startActivity(Intent.createChooser(send, null))
        }) { Icon(Icons.Rounded.Share, contentDescription = "Share post", tint = colors.onSurfaceVariant) }
    }
}

@Composable
private fun Action(icon: ImageVector, label: String?, on: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .height(38.dp)
            .pressable(RoundedCornerShape(if (on) 19.dp else 12.dp), color = if (on) colors.primary else colors.onSurface.copy(alpha = 0.08f), pressedScale = 0.94f, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val tint = if (on) colors.onPrimary else colors.onSurface
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        label?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = tint) }
    }
}

/** The like count, plus or minus yours when you've changed it here (exact counts only). */
private fun likes(count: String?, was: Boolean, now: Boolean): String? {
    if (was == now) return count
    val n = count?.replace(",", "")?.toLongOrNull() ?: return count ?: if (now) "1" else null
    return (n + if (now) 1 else -1).coerceAtLeast(0).toString()
}

/**
 * A post on its own, all in its creator's colors (remembered for the page, like a channel's):
 * the whole post at the top, then its comments right below with the box to add yours.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PostScreen(post: Post, bottomPadding: Dp) {
    BackHandler { Nav.post = null }
    var detail by remember(post.id) { mutableStateOf<Post?>(null) }
    var token by remember(post.id) { mutableStateOf<String?>(null) }
    var loaded by remember(post.id) { mutableStateOf(post.detailParams == null) }
    LaunchedEffect(post.id) {
        val params = post.detailParams ?: return@LaunchedEffect
        withContext(Dispatchers.IO) { WebApi.browse("FEpost_detail", params).mapCatching(YouTubeParser::postDetail) }
            .onSuccess { (p, t) ->
                detail = p
                token = t
            }
        loaded = true
    }
    val shown = detail ?: post
    val comments = remember(token) { token?.let { CommentsState(it, emptyList(), shown.comments, browse = true) } }
    MaterialTheme(colorScheme = rememberArtColorScheme(post.avatar)) {
        val colors = MaterialTheme.colorScheme
        Page("Post", onBack = { Nav.post = null }) {
            if (comments != null) {
                CommentsPanel(
                    comments,
                    onClose = null,
                    modifier = Modifier.padding(bottom = bottomPadding),
                    header = { item(key = "post") { PostCard(shown, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp), onOpen = null, onComments = {}) } },
                    background = colors.background,
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(key = "post") { PostCard(shown, onOpen = null, onComments = {}) }
                    item(key = "comments") {
                        if (!loaded) {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(44.dp)) }
                        } else {
                            Text("Comments are off for this post.", fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
                        }
                    }
                }
            }
        }
    }
}
