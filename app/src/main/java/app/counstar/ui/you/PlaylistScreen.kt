package app.counstar.ui.you

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.counstar.data.Feeds
import app.counstar.data.Playlist
import app.counstar.ui.Nav
import app.counstar.ui.components.BlurredImage
import app.counstar.ui.components.pressable
import coil3.compose.AsyncImage

/** A playlist from your library: its art and title on its own blurred art, then the videos. */
@Composable
fun PlaylistScreen(playlist: Playlist, bottomPadding: Dp) {
    BackHandler { Nav.playlist = null }
    val feed = remember(playlist.id) { Feeds.playlist(playlist.id) }
    LaunchedEffect(feed) { feed.load() }
    val colors = MaterialTheme.colorScheme
    Page(playlist.title, onBack = { Nav.playlist = null }) {
        VideoList(feed, bottomPadding, empty = "Nothing in this playlist yet.", numbered = true, header = {
            item(key = "header") {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp).clip(RoundedCornerShape(28.dp))) {
                    val art = playlist.thumbnail ?: feed.items.firstOrNull()?.hdThumbnail
                    art?.let { BlurredImage(it, Modifier.matchParentSize(), radius = 56.dp) }
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.55f)))))
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (art != null) {
                            AsyncImage(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp)))
                        }
                        Text(playlist.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        listOfNotNull(playlist.subtitle, playlist.count).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                            Text(it, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                        }
                        // Play all and shuffle, joined like a button group.
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            val videos = feed.items
                            GroupButton("Play all", Icons.Rounded.PlayArrow, RoundedCornerShape(22.dp, 8.dp, 8.dp, 22.dp), Color.White, colors.scrim, Modifier.weight(1f)) {
                                videos.firstOrNull()?.let(Nav::watch)
                            }
                            GroupButton("Shuffle", Icons.Rounded.Shuffle, RoundedCornerShape(8.dp, 22.dp, 22.dp, 8.dp), Color.White.copy(alpha = 0.2f), Color.White, Modifier.weight(1f)) {
                                videos.randomOrNull()?.let(Nav::watch)
                            }
                        }
                    }
                }
            }
        })
    }
}

@Composable
private fun GroupButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    shape: RoundedCornerShape,
    container: Color,
    content: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier.height(44.dp).pressable(shape, color = container, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(label, color = content, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
    }
}
