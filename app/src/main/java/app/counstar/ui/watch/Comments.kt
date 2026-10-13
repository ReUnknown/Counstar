package app.counstar.ui.watch

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.counstar.data.Chip
import app.counstar.data.Comment
import app.counstar.data.YouTubeParser
import app.counstar.ui.ChipButton
import app.counstar.ui.components.Avatar
import app.counstar.ui.components.pressable
import app.counstar.ui.theme.YouTubeRed
import com.metrolist.innertube.WebApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A list of comments (or one comment's replies), a page at a time. */
@Stable
class CommentList(firstToken: String?, private val replies: Boolean) {
    var comments by mutableStateOf<List<Comment>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var count by mutableStateOf<String?>(null)
        private set
    private var next: String? = firstToken
    private var started = false
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val hasMore get() = next != null

    fun start() {
        if (!started) loadMore()
    }

    /** Starts over from [token] (another sort order). */
    fun restart(token: String) {
        job?.cancel()
        comments = emptyList()
        failed = false
        loading = false
        next = token
        loadMore()
    }

    fun loadMore() {
        val token = next ?: return
        if (loading) return
        started = true
        loading = true
        job = scope.launch {
            try {
                withContext(Dispatchers.IO) { WebApi.nextContinuation(token).mapCatching { YouTubeParser.comments(it, replies) } }
                    .onSuccess { page ->
                        page.count?.let { count = it }
                        val seen = comments.mapTo(HashSet()) { it.id }
                        comments = comments + page.comments.filter { it.id !in seen }
                        next = page.continuation?.takeIf { it != token && page.comments.isNotEmpty() }
                    }
                    .onFailure {
                        failed = comments.isEmpty()
                        next = null
                    }
            } finally {
                loading = false
            }
        }
    }
}

/** A video's comments: the list, its count, and the orders it can be sorted in. */
@Stable
class CommentsState(firstToken: String, val sorts: List<Chip>, private val knownCount: String?) {
    val list = CommentList(firstToken, replies = false)
    var sort by mutableIntStateOf(0)
        private set
    val count: String? get() = knownCount ?: list.count?.removeSuffix(" Comments")?.removeSuffix(" comments")

    fun start() = list.start()

    fun selectSort(index: Int) {
        if (index == sort) return
        val token = sorts.getOrNull(index)?.token ?: return
        sort = index
        list.restart(token)
    }
}

/** Your likes and dislikes on comments, kept while the app runs so every view agrees. */
private object Votes {
    val liked = mutableStateMapOf<String, Boolean>()
    val disliked = mutableStateMapOf<String, Boolean>()
}

/** The comments teaser under the actions, like YouTube's: count and the top comment. */
@Composable
fun CommentsCard(state: CommentsState, onOpen: () -> Unit) {
    LaunchedEffect(state) { state.start() }
    val first = state.list.comments.firstOrNull()
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .pressable(RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), pressedScale = 0.98f, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Comments", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            state.count?.let { Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        when {
            first != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Avatar(first.avatar, 24.dp)
                Text(first.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            state.list.failed -> Text("Comments are turned off or couldn't load.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Text("Loading…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Comments, like YouTube's app: they take the whole space under the video. A header with an
 * X, Top / Newest, then the comments. Tap one to open its thread in the same space: a back
 * arrow, the comment itself, and its replies below.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CommentsPanel(state: CommentsState, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var thread by remember(state) { mutableStateOf<Comment?>(null) }
    BackHandler { if (thread != null) thread = null else onClose() }
    LaunchedEffect(state) { state.start() }
    val colors = MaterialTheme.colorScheme
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(colors.surfaceContainerLow),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(colors.onSurfaceVariant.copy(alpha = 0.4f)))
        }
        AnimatedContent(
            targetState = thread,
            transitionSpec = {
                val forward = targetState != null
                (slideInHorizontally(tween(300)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(260)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(160)))
            },
            label = "comments",
        ) { open ->
            if (open == null) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Comments", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp)
                        state.count?.let {
                            Text(it, fontSize = 16.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close comments") }
                    }
                    if (state.sorts.isNotEmpty()) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.sorts.forEachIndexed { i, chip -> ChipButton(chip.title, state.sort == i) { state.selectSort(i) } }
                        }
                    }
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
                    val list = rememberLazyListState()
                    LaunchedEffect(state.sort) { list.scrollToItem(0) }
                    LoadMoreNearEnd(list, state.list)
                    LazyColumn(
                        state = list,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp + bottomInset),
                    ) {
                        itemsIndexed(state.list.comments, key = { _, c -> c.id }) { _, c ->
                            CommentRow(c, avatar = 36.dp, onOpen = { thread = c }, showReplies = true)
                        }
                        footer(state.list, "No comments yet.")
                    }
                }
            } else {
                Thread(open, onBack = { thread = null }, onClose = onClose, bottomInset = bottomInset)
            }
        }
    }
}

/** One comment's thread: the comment highlighted at the top, its replies hanging off a line. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Thread(parent: Comment, onBack: () -> Unit, onClose: () -> Unit, bottomInset: Dp) {
    val colors = MaterialTheme.colorScheme
    val replies = remember(parent.id) { CommentList(parent.repliesToken, replies = true) }
    LaunchedEffect(replies) { replies.start() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to comments") }
            Text("Replies", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp, modifier = Modifier.padding(start = 8.dp))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close comments") }
        }
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
        val list = rememberLazyListState()
        LoadMoreNearEnd(list, replies)
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp + bottomInset)) {
            item(key = "parent") {
                Box(Modifier.fillMaxWidth().background(colors.surfaceContainerHigh)) {
                    CommentRow(parent, avatar = 40.dp, onOpen = null, showReplies = false, fullText = true)
                }
            }
            itemsIndexed(replies.comments, key = { _, c -> c.id }) { i, c ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    // The thread line, running down from the comment's avatar past each reply.
                    Spacer(Modifier.width(35.dp))
                    ThreadLine(last = i == replies.comments.lastIndex && !replies.hasMore)
                    CommentRow(c, avatar = 28.dp, onOpen = null, showReplies = false, modifier = Modifier.weight(1f), start = 8.dp)
                }
            }
            footer(replies, if (parent.repliesToken == null) "No replies yet." else "No replies.")
        }
    }
}

@Composable
private fun ThreadLine(last: Boolean) {
    val color = MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.width(2.dp).then(if (last) Modifier.height(30.dp) else Modifier.fillMaxHeight()).background(color))
}

@Composable
private fun LoadMoreNearEnd(list: LazyListState, comments: CommentList) {
    val nearEnd by remember { derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 5 } }
    LaunchedEffect(nearEnd, comments.comments.size) { if (nearEnd && comments.hasMore) comments.loadMore() }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.footer(comments: CommentList, empty: String) {
    when {
        comments.loading -> item(key = "loading") {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(44.dp)) }
        }
        comments.comments.isEmpty() -> item(key = "empty") {
            Text(
                if (comments.failed) "Comments couldn't load." else empty,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
        }
    }
}

/** A comment: who and when, what they said, then like, dislike and reply. */
@Composable
private fun CommentRow(
    c: Comment,
    avatar: Dp,
    onOpen: (() -> Unit)?,
    showReplies: Boolean,
    modifier: Modifier = Modifier,
    start: Dp = 16.dp,
    fullText: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val liked = Votes.liked[c.id] ?: c.liked
    val disliked = Votes.disliked[c.id] ?: c.disliked
    fun vote(like: Boolean) {
        val wasLiked = liked
        val wasDisliked = disliked
        val action = when {
            like && wasLiked -> c.unlikeAction
            like -> c.likeAction
            wasDisliked -> c.undislikeAction
            else -> c.dislikeAction
        }
        if (action == null) {
            Toast.makeText(context, "Sign in to rate comments", Toast.LENGTH_SHORT).show()
            return
        }
        if (like) {
            Votes.liked[c.id] = !wasLiked
            if (!wasLiked) Votes.disliked[c.id] = false
        } else {
            Votes.disliked[c.id] = !wasDisliked
            if (!wasDisliked) Votes.liked[c.id] = false
        }
        scope.launch {
            withContext(Dispatchers.IO) { WebApi.commentAction(action) }.onFailure {
                Votes.liked[c.id] = wasLiked
                Votes.disliked[c.id] = wasDisliked
                Toast.makeText(context, "Couldn't rate that comment: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val likes = adjust(c.likes, (if (liked) 1 else 0) - (if (c.liked) 1 else 0))
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onOpen != null) Modifier.pressable(RoundedCornerShape(0.dp), pressedScale = 0.99f, clipAndRipple = true, onClick = onOpen) else Modifier)
            .padding(start = start, end = 8.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(c.avatar, avatar)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val name = Modifier.let {
                    if (c.byCreator) it.clip(RoundedCornerShape(10.dp)).background(colors.onSurface.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 1.dp) else it
                }
                Text(c.author, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant, modifier = name, maxLines = 1)
                if (c.verified) Icon(Icons.Rounded.CheckCircle, contentDescription = "Verified", modifier = Modifier.size(12.dp), tint = colors.onSurfaceVariant)
                c.time?.let { Text("· $it", fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1) }
            }
            Text(
                c.text,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = if (fullText) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Vote(if (liked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp, likes, "Like") { vote(like = true) }
                Vote(if (disliked) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown, null, "Dislike") { vote(like = false) }
                if (onOpen != null) Vote(Icons.AutoMirrored.Outlined.Comment, null, "Reply", onClick = onOpen)
                if (c.hearted) {
                    Icon(Icons.Rounded.Favorite, contentDescription = "Hearted by the creator", tint = YouTubeRed, modifier = Modifier.padding(start = 8.dp).size(16.dp))
                }
            }
            if (showReplies && c.replies != null && onOpen != null) {
                Text(
                    "${c.replies} ${if (c.replies == "1") "reply" else "replies"}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .pressable(RoundedCornerShape(16.dp), pressedScale = 0.95f, onClick = onOpen)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun Vote(icon: ImageVector, label: String?, description: String, onClick: () -> Unit) {
    Row(
        Modifier
            .height(36.dp)
            .pressable(CircleShape, pressedScale = 0.85f, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp))
        label?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** A count YouTube wrote out, moved by [delta] when it's a plain number (not "1.2K"). */
private fun adjust(count: String?, delta: Int): String? {
    if (delta == 0) return count
    val n = (count ?: "0").toIntOrNull() ?: return count
    return (n + delta).takeIf { it > 0 }?.toString()
}
