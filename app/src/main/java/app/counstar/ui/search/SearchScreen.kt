package app.counstar.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.counstar.data.ChannelItem
import app.counstar.data.Feed
import app.counstar.data.FeedEntry
import app.counstar.data.Feeds
import app.counstar.data.Settings
import app.counstar.data.Suggestion
import app.counstar.data.VideoItem
import app.counstar.data.YouTubeParser
import app.counstar.data.mix
import app.counstar.ui.Nav
import app.counstar.ui.components.Avatar
import app.counstar.ui.components.blockTouches
import app.counstar.ui.components.VideoCard
import app.counstar.ui.components.VideoRow
import com.metrolist.innertube.WebApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Search. While you type: your past searches (box empty), then YouTube's suggestions, with the
 * channel you mean (MrBeast, picture and handle) and the video, when one is a clear match, right
 * there to tap. Search, and the results: the channel you're after big on top, its other channels
 * as rows further down, where YouTube puts them.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(bottomPadding: androidx.compose.ui.unit.Dp) {
    BackHandler { Nav.searchOpen = false }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var results by remember { mutableStateOf<Feed?>(null) }
    /** Typing (suggestions showing) rather than looking at results. */
    var typing by remember { mutableStateOf(true) }
    var suggestions by remember { mutableStateOf<List<Suggestion>>(emptyList()) }
    var quickVideo by remember { mutableStateOf<VideoItem?>(null) }
    var quickChannel by remember { mutableStateOf<ChannelItem?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val query = field.text
    LaunchedEffect(Unit) { if (results == null) focus.requestFocus() }
    results?.let { feed -> LaunchedEffect(feed) { feed.load() } }

    // Suggestions as you type (a moment after you stop, so not one request per letter).
    LaunchedEffect(query, typing) {
        val q = query.trim()
        if (!typing || q.isEmpty()) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(150)
        withContext(Dispatchers.IO) { WebApi.suggestions(q).mapCatching(YouTubeParser::suggestions) }
            .onSuccess { suggestions = it.distinctBy { s -> s.text.lowercase() }.take(10) }
    }
    // A quick look at the top results, for the channel and a video that clearly match.
    LaunchedEffect(query, typing) {
        val q = query.trim()
        quickVideo = null
        quickChannel = null
        if (!typing || q.length < 3) return@LaunchedEffect
        delay(500)
        val page = withContext(Dispatchers.IO) {
            com.metrolist.innertube.YouTube.rawSearch(query = q).mapCatching { YouTubeParser.feed(it) }
        }.getOrNull() ?: return@LaunchedEffect
        quickChannel = page.channels.firstOrNull()?.takeIf { it.after == 0 }
        quickVideo = page.items.firstOrNull()?.takeIf { matches(q, it.title) }
    }

    fun submit(text: String = query) {
        val q = text.trim()
        if (q.isEmpty()) return
        keyboard?.hide()
        field = TextFieldValue(q, TextRange(q.length))
        Settings.rememberSearch(q)
        typing = false
        results = Feeds.search(q)
    }

    /** Puts [text] in the box to keep typing from (the arrow on a suggestion). */
    fun fill(text: String) {
        field = TextFieldValue("$text ", TextRange(text.length + 1))
        typing = true
        focus.requestFocus()
    }

    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background).blockTouches().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { Nav.searchOpen = false }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Row(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.surfaceContainerHigh)
                    .padding(start = 18.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search YouTube", color = colors.onSurfaceVariant, fontSize = 16.sp)
                    BasicTextField(
                        value = field,
                        onValueChange = {
                            if (it.text != field.text) typing = true
                            field = it
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(colors.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        field = TextFieldValue("")
                        typing = true
                        focus.requestFocus()
                    }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search") }
                }
            }
        }
        val feed = results
        if (typing || feed == null) {
            LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding + 16.dp), modifier = Modifier.fillMaxSize()) {
                val q = query.trim()
                if (q.isEmpty()) {
                    items(Settings.searchHistory, key = { "h$it" }) { past ->
                        Line(Icons.Rounded.History, past, typed = "", onClick = { submit(past) }, onFill = { fill(past) }, onRemove = { Settings.forgetSearch(past) })
                    }
                } else {
                    // The channel you mean: from the suggestions, or the top result.
                    val channel = suggestions.firstNotNullOfOrNull { it.channel } ?: quickChannel
                    channel?.let { c -> item(key = "channel") { ChannelLine(c) { Settings.rememberSearch(q); Nav.openChannel(c.id) } } }
                    quickVideo?.let { v ->
                        item(key = "video") {
                            VideoRow(v, onClick = {
                                Settings.rememberSearch(q)
                                Nav.watch(v)
                            })
                        }
                    }
                    val past = Settings.searchHistory.filter { it.startsWith(q, ignoreCase = true) && !it.equals(q, ignoreCase = true) }.take(3)
                    items(past, key = { "h$it" }) { p -> Line(Icons.Rounded.History, p, typed = q, onClick = { submit(p) }, onFill = { fill(p) }, onRemove = { Settings.forgetSearch(p) }) }
                    items(suggestions.filter { s -> past.none { it.equals(s.text, ignoreCase = true) } }, key = { "s" + it.text }) { s ->
                        Line(Icons.Rounded.Search, s.text, typed = q, onClick = { submit(s.text) }, onFill = { fill(s.text) })
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding + 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // The channel you're most likely after, big in its own colors, when YouTube puts
                // one first. Its other channels (MrBeast Gaming...) follow as rows, where YouTube
                // puts them among the videos.
                val top = feed.channels.firstOrNull()?.takeIf { it.after == 0 }
                if (top != null) item(key = "top") { app.counstar.ui.channel.ChannelResult(top) }
                items(mix(feed.items, feed.posts, feed.channels.filter { it.id != top?.id }), key = { it.key }) { e ->
                    when (e) {
                        is FeedEntry.Video -> VideoCard(e.item, onClick = { Nav.watch(e.item) })
                        is FeedEntry.Channel -> app.counstar.ui.channel.ChannelRow(e.channel)
                        is FeedEntry.PostEntry -> app.counstar.ui.posts.PostCard(e.post)
                    }
                }
                if (feed.loading) {
                    item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(56.dp)) } }
                } else if (feed.canLoadMore) {
                    item { LaunchedEffect(feed.items.size) { feed.loadMore() } }
                }
                feed.error?.let { item { Text(it, modifier = Modifier.padding(24.dp), color = colors.onSurfaceVariant) } }
            }
        }
    }
}

/** A video is a clear match when its title has every word typed. */
private fun matches(query: String, title: String): Boolean {
    val words = query.lowercase().split(Regex("\\s+")).filter { it.length > 1 }
    if (words.size < 2) return false
    val t = title.lowercase()
    return words.all { it in t }
}

/** A suggestion or past search: the part you typed plain, the rest bold, and an arrow to fill it in. */
@Composable
private fun Line(icon: ImageVector, text: String, typed: String, onClick: () -> Unit, onFill: () -> Unit, onRemove: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 4.dp).height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Text(
            buildAnnotatedString {
                if (typed.isNotEmpty() && text.startsWith(typed, ignoreCase = true)) {
                    append(text.substring(0, typed.length))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(typed.length)) }
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text) }
                }
            },
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 18.dp),
        )
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, contentDescription = "Remove from history", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
        }
        IconButton(onClick = onFill) { Icon(Icons.Rounded.NorthWest, contentDescription = "Edit this search", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
    }
}

/** The channel you're typing, to go straight to: picture, name and handle. */
@Composable
private fun ChannelLine(channel: ChannelItem, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Avatar(channel.avatar, 44.dp, modifier = Modifier.clip(CircleShape))
        Column(Modifier.weight(1f)) {
            Text(channel.title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(channel.handle, "Channel").joinToString(" · "), fontSize = 13.sp, color = colors.onSurfaceVariant, maxLines = 1)
        }
    }
}
