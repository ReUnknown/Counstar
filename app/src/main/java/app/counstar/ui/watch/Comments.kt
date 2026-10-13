package app.counstar.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.counstar.data.Comment
import app.counstar.data.YouTubeParser
import app.counstar.ui.components.Avatar
import app.counstar.ui.components.pressable
import com.metrolist.innertube.WebApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A video's comments, a page at a time. */
@Stable
class CommentsState(private val firstToken: String) {
    var count by mutableStateOf<String?>(null)
    var comments by mutableStateOf<List<Comment>>(emptyList())
    var loading by mutableStateOf(false)
    var failed by mutableStateOf(false)
    private var next: String? = firstToken
    private var started = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val hasMore get() = next != null

    fun loadMore() {
        val token = next ?: return
        if (loading) return
        started = true
        loading = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { WebApi.nextContinuation(token).mapCatching(YouTubeParser::comments) }
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

    fun start() {
        if (!started) loadMore()
    }
}

/** The comments teaser under the actions, like YouTube's: count and the top comment. */
@Composable
fun CommentsCard(state: CommentsState, onOpen: () -> Unit) {
    LaunchedEffect(state) { state.start() }
    val first = state.comments.firstOrNull()
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
            state.count?.let { Text(it.removeSuffix(" Comments").removeSuffix(" comments"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        when {
            first != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Avatar(first.avatar, 24.dp)
                Text(first.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            state.failed -> Text("Comments are turned off or couldn't load.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Text("Loading…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Every comment, in a sheet that loads more as you near the end. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CommentsSheet(state: CommentsState, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val list = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 5 } }
    LaunchedEffect(nearEnd, state.comments.size) { if (nearEnd && state.hasMore) state.loadMore() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Comments", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            state.count?.let { Text(it.removeSuffix(" Comments").removeSuffix(" comments"), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxHeight(0.92f),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        ) {
            items(state.comments, key = { it.id }) { CommentRow(it) }
            if (state.loading) {
                item { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(44.dp)) } }
            }
        }
    }
}

@Composable
private fun CommentRow(c: Comment) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Avatar(c.avatar, 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val name = Modifier.let { if (c.byCreator) it.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), RoundedCornerShape(10.dp)).padding(horizontal = 6.dp) else it }
                Text(c.author, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = name)
                c.time?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(c.text, fontSize = 14.sp, lineHeight = 20.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.ThumbUp, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    c.likes?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                c.replies?.let { Text("$it ${if (it == "1") "reply" else "replies"}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}
