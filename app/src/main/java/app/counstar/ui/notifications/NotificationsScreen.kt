package app.counstar.ui.notifications

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.counstar.data.Notification
import app.counstar.data.VideoItem
import app.counstar.data.YouTubeParser
import app.counstar.ui.Nav
import app.counstar.ui.components.Avatar
import app.counstar.ui.components.pressable
import coil3.compose.AsyncImage
import com.metrolist.innertube.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotificationsScreen(bottomPadding: Dp) {
    BackHandler { Nav.notificationsOpen = false }
    var items by remember { mutableStateOf<List<Notification>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { Notifications.raw().mapCatching(YouTubeParser::notifications) }
            .onSuccess { items = it }
            .onFailure { failed = true }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { Nav.notificationsOpen = false }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text("Notifications", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp)
        }
        val list = items
        when {
            list == null && !failed -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(56.dp)) }
            list.isNullOrEmpty() -> Text(
                if (failed) "Couldn't load notifications." else "No notifications yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
            else -> LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding + 16.dp)) {
                items(list) { n -> Row(n) }
            }
        }
    }
}

@Composable
private fun Row(n: Notification) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(RoundedCornerShape(0.dp), pressedScale = 0.98f) {
                val id = n.videoId ?: return@pressable
                Nav.notificationsOpen = false
                Nav.watch(VideoItem(id = id, title = n.message, thumbnail = n.thumbnail ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"))
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(8.dp).padding(top = 18.dp)) {
            if (!n.read) Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
        Avatar(n.avatar, 44.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(n.message, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            n.time?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        n.thumbnail?.let {
            AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.width(112.dp).height(63.dp).clip(RoundedCornerShape(12.dp)))
        }
    }
}
