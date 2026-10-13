package app.counstar.ui.search

import androidx.activity.compose.BackHandler
import app.counstar.ui.components.blockTouches
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.counstar.data.Feed
import app.counstar.data.Feeds
import app.counstar.ui.Nav
import app.counstar.ui.components.VideoCard

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(bottomPadding: androidx.compose.ui.unit.Dp) {
    BackHandler { Nav.searchOpen = false }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<Feed?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { if (results == null) focus.requestFocus() }
    results?.let { feed -> LaunchedEffect(feed) { feed.load() } }

    fun submit() {
        val q = query.trim()
        if (q.isEmpty()) return
        keyboard?.hide()
        results = Feeds.search(q)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).blockTouches().statusBarsPadding()) {
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
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = 18.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search YouTube", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = ""; focus.requestFocus() }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                }
            }
        }
        val feed = results
        LazyColumn(
            contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding + 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (feed != null) {
                // Channels that match come first, as cards in their own colors.
                items(feed.channels.take(3), key = { "c" + it.id }) { channel -> app.counstar.ui.channel.ChannelResult(channel) }
                items(feed.items, key = { it.id }) { item -> VideoCard(item, onClick = { Nav.watch(item) }) }
                if (feed.loading) {
                    item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(56.dp)) } }
                } else if (feed.canLoadMore) {
                    item { LaunchedEffect(feed.items.size) { feed.loadMore() } }
                }
                feed.error?.let { item { Text(it, modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            }
        }
    }
}
