package app.counstar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.metrolist.innertube.WebApi
import kotlinx.coroutines.launch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.counstar.data.VideoItem
import app.counstar.ui.theme.YouTubeRed
import coil3.compose.AsyncImage

/** A video's thumbnail with its duration (or LIVE) and how much you've watched. */
@Composable
fun Thumbnail(item: VideoItem, modifier: Modifier = Modifier, corner: Dp = 28.dp, small: Boolean = false) {
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        // Sharp when YouTube has a 720p thumbnail, the feed's own (smaller) one when it doesn't.
        var model by remember(item.id) { mutableStateOf(item.hdThumbnail) }
        AsyncImage(
            model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { if (model != item.thumbnail) model = item.thumbnail },
            modifier = Modifier.fillMaxSize(),
        )
        val badgePad = if (small) 6.dp else 10.dp
        when {
            item.isLive -> Text(
                "LIVE",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(badgePad)
                    .clip(RoundedCornerShape(8.dp))
                    .background(YouTubeRed)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
            item.duration != null -> Text(
                item.duration,
                color = Color.White,
                fontSize = if (small) 11.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(badgePad)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
        item.watched?.let { watched ->
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.3f)),
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(watched.coerceIn(0.02f, 1f)).background(YouTubeRed))
            }
        }
    }
}

/**
 * The card YouTube uses in every feed: the thumbnail edge to edge (no rounding, like the app),
 * then avatar, title and byline.
 */
@Composable
fun VideoCard(item: VideoItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .pressable(RoundedCornerShape(0.dp), pressedScale = 0.98f, clipAndRipple = false, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Thumbnail(item, Modifier.fillMaxWidth(), corner = 0.dp)
        Row(Modifier.padding(start = 12.dp, end = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(item.channelAvatar, 40.dp, ring = if (item.isLive) YouTubeRed else null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.title,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // YouTube's app: "Channel   ▷ 77M   4y ago", spaced out instead of dotted.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val meta = MaterialTheme.colorScheme.onSurfaceVariant
                    item.channelName?.let { Text(it, fontSize = 13.sp, color = meta, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)) }
                    item.shortViews?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = meta, modifier = Modifier.size(15.dp))
                            Text(it, fontSize = 13.sp, color = meta, maxLines = 1)
                        }
                    }
                    item.published?.let { Text(it, fontSize = 13.sp, color = meta, maxLines = 1) }
                }
            }
            CardMenu(item)
        }
    }
}

/** ⋮ on a card: save it for later, or share it. */
@Composable
private fun CardMenu(item: VideoItem) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More options", modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(20.dp)) {
            DropdownMenuItem(
                text = { Text("Save to Watch later") },
                leadingIcon = { Icon(Icons.Outlined.WatchLater, null) },
                onClick = {
                    open = false
                    scope.launch { WebApi.watchLater(item.id, add = true) }
                },
            )
            DropdownMenuItem(
                text = { Text("Share") },
                leadingIcon = { Icon(Icons.Rounded.Share, null) },
                onClick = {
                    open = false
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${item.id}")
                    }
                    context.startActivity(Intent.createChooser(send, null))
                },
            )
        }
    }
}

/** A small card for sideways rows (history). */
@Composable
fun SmallVideoCard(item: VideoItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .width(168.dp)
            .pressable(RoundedCornerShape(18.dp), pressedScale = 0.95f, clipAndRipple = false, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Thumbnail(item, Modifier.fillMaxWidth(), corner = 18.dp, small = true)
        Text(item.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
        item.channelName?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun Avatar(url: String?, size: Dp, modifier: Modifier = Modifier, ring: Color? = null) {
    val ringed = if (ring != null) {
        modifier
            .size(size + 6.dp)
            .clip(CircleShape)
            .background(ring)
            .padding(2.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.background)
            .padding(1.dp)
    } else {
        modifier.size(size)
    }
    Box(ringed.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        if (url != null) AsyncImage(url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}
