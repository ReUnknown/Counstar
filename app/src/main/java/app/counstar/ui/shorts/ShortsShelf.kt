package app.counstar.ui.shorts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.counstar.data.ShortsShelf
import app.counstar.ui.Nav
import app.counstar.ui.components.pressable
import coil3.compose.AsyncImage

/** A row of Shorts in a feed or search: tall cards, title and views over the picture. Tap to watch. */
@Composable
fun ShortsRow(shelf: ShortsShelf, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
            Text(shelf.title ?: "Shorts", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shelf.items, key = { it.id }) { s ->
                Box(
                    Modifier
                        .width(156.dp)
                        .aspectRatio(9f / 16f)
                        .pressable(RoundedCornerShape(18.dp), color = colors.surfaceContainerHigh, pressedScale = 0.96f) { Nav.watch(s) },
                ) {
                    AsyncImage(s.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))))
                    Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                        Text(s.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        s.views?.let { Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1) }
                    }
                }
            }
        }
    }
}
