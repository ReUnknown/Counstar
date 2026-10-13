package app.counstar.ui.watch

import android.widget.Toast
import app.counstar.ui.components.blockTouches
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import app.counstar.data.ImageRun
import app.counstar.data.Library
import app.counstar.data.LinkRun
import app.counstar.playback.CounstarPlayer
import app.counstar.ui.theme.isCounstarDark
import coil3.compose.AsyncImage
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import app.counstar.ui.components.rememberHaptics
import app.counstar.ui.components.pressable
import app.counstar.ui.theme.YouTubeRed
import com.metrolist.innertube.WebApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A list of comments (or one comment's replies), a page at a time. A video's come through
 * "next"; a post's through "browse" ([browse]).
 */
@Stable
class CommentList(firstToken: String?, private val replies: Boolean, private val browse: Boolean = false) {
    var comments by mutableStateOf<List<Comment>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var count by mutableStateOf<String?>(null)
        private set
    /** What to send to post a new comment here (signed in, comments on). */
    var createParams by mutableStateOf<String?>(null)
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

    /** Your new comment, at the top. */
    fun prepend(comment: Comment) {
        comments = listOf(comment) + comments
    }

    /** Your new reply, at the end of the thread. */
    fun append(comment: Comment) {
        comments = comments + comment
    }

    fun loadMore() {
        val token = next ?: return
        if (loading) return
        started = true
        loading = true
        job = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    (if (browse) WebApi.browseContinuation(token) else WebApi.nextContinuation(token)).mapCatching { YouTubeParser.comments(it, replies) }
                }
                    .onSuccess { page ->
                        page.count?.let { count = it }
                        page.createParams?.let { createParams = it }
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

/** A video's (or post's: [browse]) comments: the list, its count, and the orders it can be sorted in. */
@Stable
class CommentsState(firstToken: String, val sorts: List<Chip>, private val knownCount: String?, val browse: Boolean = false) {
    val list = CommentList(firstToken, replies = false, browse = browse)
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
 * X, Top / Newest, then the comments, and a box to add yours at the bottom. Tap one to open its
 * thread in the same space: a back arrow, the comment itself, and its replies below.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CommentsPanel(
    state: CommentsState,
    /** Null when the comments are the page itself (a post's): no X, back belongs to the page. */
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** Above the comments, scrolling with them (a post, over its comments). */
    header: (androidx.compose.foundation.lazy.LazyListScope.() -> Unit)? = null,
    background: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    var thread by remember(state) { mutableStateOf<Comment?>(null) }
    BackHandler(enabled = thread != null || onClose != null) { if (thread != null) thread = null else onClose?.invoke() }
    LaunchedEffect(state) { state.start() }
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(background).blockTouches()) {
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
                    val title: @Composable () -> Unit = { Column {
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Comments", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp)
                            state.count?.let {
                                Text(it, fontSize = 16.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close comments") }
                        }
                        if (state.sorts.isNotEmpty()) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.sorts.forEachIndexed { i, chip -> ChipButton(chip.title, state.sort == i) { state.selectSort(i) } }
                            }
                        }
                        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
                    } }
                    // With a header, the title scrolls with it (under the post); otherwise it stays put.
                    if (header == null) title()
                    val list = rememberLazyListState()
                    LaunchedEffect(state.sort) { if (header == null) list.scrollToItem(0) }
                    LoadMoreNearEnd(list, state.list)
                    LazyColumn(state = list, modifier = Modifier.weight(1f), contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)) {
                        if (header != null) {
                            header()
                            item(key = "comments-title") { title() }
                        }
                        itemsIndexed(state.list.comments, key = { _, c -> c.id }) { _, c ->
                            CommentRow(c, avatar = 36.dp, onOpen = { thread = c }, onReply = { thread = c }, showReplies = true)
                        }
                        footer(state.list, "No comments yet.")
                    }
                    Composer(
                        params = state.list.createParams,
                        hint = "Add a comment…",
                        prefill = "",
                        post = { text -> state.list.createParams?.let { WebApi.createComment(it, text) } ?: Result.failure(IllegalStateException("Sign in to comment")) },
                        onPosted = { text -> state.list.prepend(mine(text)) },
                    )
                }
            } else {
                Thread(open, browse = state.browse, onBack = { thread = null }, onClose = onClose ?: { thread = null })
            }
        }
    }
}

/** Your picture: YouTube's, or your Google account's until that's loaded. */
private fun myPhoto(): String? = Library.account?.photo ?: app.counstar.data.SessionRepository.session.value?.photoUrl

/** A comment of yours, shown right away once it's posted. */
private fun mine(text: String) = Comment(
    id = "mine-" + System.nanoTime(),
    author = Library.account?.handle ?: Library.account?.name ?: "You",
    avatar = myPhoto(),
    text = text,
    time = "Just now",
    likes = null,
    replies = null,
    byCreator = false,
)

/**
 * How deep each reply sits, like YouTube's threads: a reply that starts with @someone hangs
 * under that person's latest reply; one more from the same person stays at their depth.
 */
private fun depths(replies: List<Comment>): List<Int> {
    val latest = HashMap<String, Int>()
    var previous: Comment? = null
    var previousDepth = 1
    return replies.map { r ->
        val mention = Regex("^@([^\\s]+)").find(r.text.trim())?.groupValues?.get(1)?.trimEnd(',', '.', ':', '!', '?')
        val depth = when {
            // Only two levels, like YouTube: replies to replies (at any depth) share one thread.
            mention != null && latest.containsKey("@$mention") -> 2
            mention == null && previous?.author == r.author -> previousDepth
            else -> 1
        }
        latest[r.author] = depth
        previous = r
        previousDepth = depth
        depth
    }
}

/** One comment's thread: the comment highlighted at the top, its replies nested below it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Thread(parent: Comment, browse: Boolean, onBack: () -> Unit, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val replies = remember(parent.id) { CommentList(parent.repliesToken, replies = true, browse = browse) }
    var replyTo by remember(parent.id) { mutableStateOf<Comment?>(null) }
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
        val levels = remember(replies.comments) { depths(replies.comments) }
        LazyColumn(state = list, modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            item(key = "parent") {
                Box(Modifier.fillMaxWidth().background(colors.surfaceContainerHigh)) {
                    CommentRow(parent, avatar = 40.dp, onOpen = null, onReply = { replyTo = null }, showReplies = false, fullText = true)
                }
            }
            itemsIndexed(replies.comments, key = { _, c -> c.id }) { i, c ->
                val depth = levels.getOrElse(i) { 1 }
                val nextNested = levels.getOrElse(i + 1) { 1 } == 2
                val lineColor = colors.outlineVariant
                CommentRow(
                    c,
                    avatar = 28.dp,
                    onOpen = null,
                    onReply = { replyTo = c },
                    showReplies = false,
                    start = if (depth == 2) 100.dp else 64.dp,
                    modifier = Modifier.drawBehind {
                        // One connected line: down from a reply's avatar, past its nested
                        // replies, curving into each.
                        val x = 78.dp.toPx()
                        val stroke = 2.dp.toPx()
                        if (depth == 1 && nextNested) {
                            drawLine(lineColor, androidx.compose.ui.geometry.Offset(x, 44.dp.toPx()), androidx.compose.ui.geometry.Offset(x, size.height), stroke)
                        }
                        if (depth == 2) {
                            val elbowY = 26.dp.toPx()
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(x, 0f)
                                lineTo(x, elbowY - 10.dp.toPx())
                                quadraticTo(x, elbowY, x + 10.dp.toPx(), elbowY)
                                lineTo(98.dp.toPx(), elbowY)
                            }
                            drawPath(path, lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                            if (nextNested) drawLine(lineColor, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), stroke)
                        }
                    },
                )
            }
            footer(replies, if (parent.repliesToken == null) "No replies yet." else "No replies.")
        }
        val target = replyTo
        Composer(
            params = parent.replyParams,
            hint = if (target != null) "Reply to ${target.author}…" else "Reply…",
            prefill = target?.let { "${it.author} " }.orEmpty(),
            post = { text -> parent.replyParams?.let { WebApi.createReply(it, text) } ?: Result.failure(IllegalStateException("Sign in to reply")) },
            onPosted = { text -> replies.append(mine(text)) },
        )
    }
}

/**
 * The box at the bottom for writing a comment or reply. Without [params] (signed out, or
 * comments are off) it says so instead.
 */
@Composable
private fun Composer(
    params: String?,
    hint: String,
    prefill: String,
    post: suspend (String) -> Result<String>,
    onPosted: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember(prefill) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(prefill, androidx.compose.ui.text.TextRange(prefill.length))) }
    var sending by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(prefill) { if (prefill.isNotEmpty()) runCatching { focus.requestFocus() } }
    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerLow)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Avatar(myPhoto(), 32.dp)
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(colors.surfaceContainerHighest)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (params == null && !app.counstar.BuildConfig.DEBUG) {
                Text("Sign in to comment", color = colors.onSurfaceVariant, fontSize = 15.sp)
            } else {
                if (text.text.isEmpty()) Text(hint, color = colors.onSurfaceVariant, fontSize = 15.sp)
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = colors.onSurface, fontSize = 15.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        }
        if (text.text.isNotBlank() && (params != null || app.counstar.BuildConfig.DEBUG)) {
            Box(
                Modifier
                    .size(44.dp)
                    .pressable(CircleShape, color = colors.primary, enabled = !sending) {
                        val body = text.text.trim()
                        sending = true
                        scope.launch {
                            withContext(Dispatchers.IO) { post(body) }
                                .onSuccess {
                                    haptics.confirm()
                                    onPosted(body)
                                    text = androidx.compose.ui.text.input.TextFieldValue("")
                                }
                                .onFailure { Toast.makeText(context, "Couldn't post: ${it.message}", Toast.LENGTH_SHORT).show() }
                            sending = false
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Post", tint = colors.onPrimary, modifier = Modifier.size(20.dp))
            }
        }
    }
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

/** YouTube's link blue, for @mentions, links and timestamps. */
@Composable
private fun linkBlue(): Color = if (isCounstarDark()) Color(0xFF3EA6FF) else Color(0xFF065FD4)

/**
 * A comment's text: @mentions, links and timestamps in blue (a timestamp jumps the video
 * there), custom emoji inline, and GIFs or stickers as images under the text.
 */
@Composable
private fun CommentBody(c: Comment, maxLines: Int) {
    val blue = linkBlue()
    val context = LocalContext.current
    val big = c.runs.filterIsInstance<ImageRun>().filter { it.height > 32 || it.width > 48 }
    val inline = HashMap<String, InlineTextContent>()
    val text = buildAnnotatedString {
        var at = 0
        val raw = c.text
        // A leading @handle is a mention even when YouTube sends no link for it.
        val leading = Regex("^@[^\\s]+").find(raw)?.range?.let { it.first to it.last + 1 }
        for (run in c.runs.sortedBy { it.start }) {
            val start = run.start.coerceIn(at, raw.length)
            val end = (run.start + run.length).coerceIn(start, raw.length)
            if (start > at) appendPlain(raw.substring(at, start), at, leading, blue)
            when (run) {
                is ImageRun -> if (run !in big) {
                    val id = "img${run.start}"
                    inline[id] = InlineTextContent(Placeholder(18.sp, 18.sp, PlaceholderVerticalAlign.TextCenter)) {
                        AsyncImage(run.url, run.label, modifier = Modifier.fillMaxSize())
                    }
                    appendInlineContent(id, run.label ?: raw.substring(start, end))
                }
                is LinkRun -> {
                    val seek = run.seekSeconds
                    val url = run.url
                    val channel = run.channelId
                    val link = when {
                        channel != null -> LinkAnnotation.Clickable("channel", TextLinkStyles(SpanStyle(color = blue))) {
                            app.counstar.ui.channel.ChannelPeek.id = channel
                        }
                        seek != null -> LinkAnnotation.Clickable("seek", TextLinkStyles(SpanStyle(color = blue))) {
                            CounstarPlayer.player.seekTo(seek * 1000L)
                            CounstarPlayer.player.play()
                        }
                        url != null -> LinkAnnotation.Clickable("url", TextLinkStyles(SpanStyle(color = blue))) {
                            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(cleanUrl(url)))) }
                        }
                        else -> null
                    }
                    if (link != null) withLink(link) { append(raw.substring(start, end)) }
                    else withStyle(SpanStyle(color = blue)) { append(raw.substring(start, end)) }
                }
            }
            at = end
        }
        if (at < raw.length) appendPlain(raw.substring(at), at, leading, blue)
    }
    if (text.isNotBlank()) {
        Text(
            text,
            inlineContent = inline,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
    big.forEach { image ->
        AsyncImage(
            image.url,
            image.label,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .padding(top = 6.dp)
                .widthIn(max = 220.dp)
                .heightIn(max = 220.dp)
                .aspectRatio((image.width.toFloat() / image.height.coerceAtLeast(1)).coerceIn(0.5f, 2f))
                .clip(RoundedCornerShape(12.dp)),
        )
    }
}

/** Plain text, except the part inside [leading] (a leading @handle), which is blue. */
private fun androidx.compose.ui.text.AnnotatedString.Builder.appendPlain(part: String, offset: Int, leading: Pair<Int, Int>?, blue: Color) {
    if (leading == null || offset >= leading.second || offset + part.length <= leading.first) {
        append(part)
        return
    }
    val from = (leading.first - offset).coerceAtLeast(0)
    val to = (leading.second - offset).coerceAtMost(part.length)
    append(part.substring(0, from))
    withStyle(SpanStyle(color = blue)) { append(part.substring(from, to)) }
    append(part.substring(to))
}

/** YouTube wraps outside links in a redirect: open the real address. */
private fun cleanUrl(url: String): String {
    val q = android.net.Uri.parse(url).getQueryParameter("q")
    return if ("/redirect" in url && q != null) q else url
}

/** A comment: who and when, what they said, then like, dislike and reply. */
@Composable
private fun CommentRow(
    c: Comment,
    avatar: Dp,
    onOpen: (() -> Unit)?,
    onReply: () -> Unit,
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
    val haptics = rememberHaptics()
    fun vote(like: Boolean) {
        haptics.click()
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
        // Their picture and name open their channel card, like YouTube's.
        val peek = Modifier.clickable(enabled = c.authorChannelId != null) { app.counstar.ui.channel.ChannelPeek.id = c.authorChannelId }
        Avatar(c.avatar, avatar, modifier = Modifier.clip(CircleShape).then(peek))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val name = Modifier.let {
                    if (c.byCreator) it.clip(RoundedCornerShape(10.dp)).background(colors.onSurface.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 1.dp) else it
                }
                Text(c.author, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant, modifier = name.then(peek), maxLines = 1)
                if (c.verified) Icon(Icons.Rounded.CheckCircle, contentDescription = "Verified", modifier = Modifier.size(12.dp), tint = colors.onSurfaceVariant)
                c.time?.let { Text("· $it", fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1) }
            }
            CommentBody(c, if (fullText) Int.MAX_VALUE else 6)
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Vote(if (liked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp, likes, "Like") { vote(like = true) }
                Vote(if (disliked) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown, null, "Dislike") { vote(like = false) }
                Vote(Icons.AutoMirrored.Outlined.Comment, null, "Reply", onClick = onReply)
                if (c.hearted) {
                    Icon(Icons.Rounded.Favorite, contentDescription = "Hearted by the creator", tint = YouTubeRed, modifier = Modifier.padding(start = 8.dp).size(16.dp))
                }
            }
            if (showReplies && c.replies != null && onOpen != null) {
                Text(
                    "${c.replies} ${if (c.replies == "1") "reply" else "replies"}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = linkBlue(),
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
