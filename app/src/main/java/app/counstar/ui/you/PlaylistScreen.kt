package app.counstar.ui.you

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.counstar.data.Feeds
import app.counstar.data.Playlist
import app.counstar.ui.Nav
import app.counstar.ui.components.BlurredImage
import app.counstar.ui.components.pressable
import app.counstar.ui.components.rememberArtColorScheme
import coil3.compose.AsyncImage

/**
 * A playlist from your library, in the colors of its art (like Wave's album pages): the art
 * blurred big behind the header and fading into the page, the cover, the title, Play all and
 * Shuffle, then the videos.
 */
@Composable
fun PlaylistScreen(playlist: Playlist, bottomPadding: Dp) {
    BackHandler { Nav.playlist = null }
    val feed = remember(playlist.id) { Feeds.playlist(playlist.id) }
    LaunchedEffect(feed) { feed.load() }
    val art = playlist.thumbnail ?: feed.items.firstOrNull()?.hdThumbnail
    MaterialTheme(colorScheme = rememberArtColorScheme(art)) {
        val colors = MaterialTheme.colorScheme
        Box(Modifier.fillMaxSize().background(colors.background)) {
            VideoList(feed, bottomPadding, empty = "Nothing in this playlist yet.", numbered = true, header = {
                item(key = "header") {
                    Box(Modifier.fillMaxWidth()) {
                        art?.let { BlurredImage(it, Modifier.matchParentSize(), radius = 64.dp, fadeBottom = 0.5f) }
                        Box(
                            Modifier.matchParentSize().background(
                                Brush.verticalGradient(0f to colors.background.copy(alpha = 0.25f), 0.55f to colors.background.copy(alpha = 0.35f), 1f to colors.background),
                            ),
                        )
                        Column(
                            Modifier.fillMaxWidth().statusBarsPadding().padding(top = 56.dp, bottom = 20.dp, start = 24.dp, end = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(0.86f)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(colors.surfaceContainerHighest),
                            ) {
                                art?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                            }
                            Text(
                                playlist.title,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.8).sp,
                                lineHeight = 32.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = colors.onBackground,
                            )
                            listOfNotNull(playlist.subtitle, playlist.count).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                                Text(it, fontSize = 14.sp, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                            }
                            // Play all and Shuffle, joined like a button group.
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                val videos = feed.items
                                GroupButton("Play all", Icons.Rounded.PlayArrow, RoundedCornerShape(24.dp, 8.dp, 8.dp, 24.dp), colors.primary, colors.onPrimary, Modifier.weight(1f)) {
                                    videos.firstOrNull()?.let(Nav::watch)
                                }
                                GroupButton("Shuffle", Icons.Rounded.Shuffle, RoundedCornerShape(8.dp, 24.dp, 24.dp, 8.dp), colors.secondaryContainer, colors.onSecondaryContainer, Modifier.weight(1f)) {
                                    videos.randomOrNull()?.let(Nav::watch)
                                }
                            }
                        }
                    }
                }
            })
            IconButton(
                onClick = { Nav.playlist = null },
                modifier = Modifier.statusBarsPadding().padding(8.dp).clip(CircleShape).background(colors.background.copy(alpha = 0.5f)),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colors.onBackground)
            }
        }
    }
}

@Composable
private fun GroupButton(label: String, icon: ImageVector, shape: RoundedCornerShape, container: Color, content: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(52.dp).pressable(shape, color = container, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        Text(label, color = content, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp))
    }
}
