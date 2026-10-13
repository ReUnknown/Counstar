package app.counstar.ui.you

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.counstar.data.Feeds
import app.counstar.data.Library
import app.counstar.data.Playlist
import app.counstar.data.Session
import app.counstar.ui.ChipButton
import app.counstar.ui.Nav
import app.counstar.ui.components.Avatar
import app.counstar.ui.components.SmallVideoCard
import app.counstar.ui.components.pressable
import coil3.compose.AsyncImage

private enum class Sort(val title: String) { Recents("Recents"), AToZ("A–Z"), ZToA("Z–A") }

/** You: your account, what you've watched, and your library. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YouScreen(session: Session, contentPadding: PaddingValues) {
    val history = Feeds.history
    LaunchedEffect(history) { history.load() }
    LaunchedEffect(Unit) { Library.load() }
    var sort by rememberSaveable { mutableStateOf(Sort.Recents) }
    var kind by rememberSaveable { mutableStateOf<Playlist.Kind?>(null) }
    val account = Library.account

    // Watch later and Liked videos are always there, first, like YouTube's library.
    val pinned = listOf(
        Playlist("WL", "Watch later", null, "Private", null, Playlist.Kind.Playlist),
        Playlist("LL", "Liked videos", null, "Private", null, Playlist.Kind.Playlist),
    )
    val all = pinned.filter { p -> Library.playlists.none { it.id == p.id } } + Library.playlists
    val kinds = Playlist.Kind.entries.filter { k -> all.any { it.kind == k } }
    val shown = all
        .filter { kind == null || it.kind == kind }
        .let { list ->
            when (sort) {
                Sort.Recents -> list
                Sort.AToZ -> list.sortedBy { it.title.lowercase() }
                Sort.ZToA -> list.sortedByDescending { it.title.lowercase() }
            }
        }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "profile") {
            Row(
                Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Avatar(account?.photo ?: session.photoUrl, 76.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        account?.name ?: session.name,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.8).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    (account?.handle ?: session.email)?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }

        item(key = "history") {
            Column(Modifier.padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("History") { Nav.historyOpen = true }
                when {
                    history.items.isNotEmpty() -> LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(history.items.take(20), key = { it.id }) { item -> SmallVideoCard(item, onClick = { Nav.watch(item) }) }
                    }
                    history.loading -> Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator(Modifier.size(48.dp))
                    }
                    else -> Text(
                        "Videos you watch show up here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }

        item(key = "library") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Library", onOpen = null)
                LazyRow(
                    Modifier.padding(top = 4.dp, bottom = 10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item { SortChip(sort) { sort = it } }
                    items(kinds, key = { it.name }) { k ->
                        ChipButton(k.title, kind == k) { kind = if (kind == k) null else k }
                    }
                }
            }
        }
        items(shown, key = { "p" + it.id }) { p -> PlaylistRow(p) { Nav.playlist = p } }
        if (Library.loading && Library.playlists.isEmpty()) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(44.dp)) }
            }
        }
    }
}

/** A section's title; with [onOpen], an arrow beside it opens the whole thing. */
@Composable
private fun SectionTitle(title: String, onOpen: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onOpen != null) Modifier.pressable(RoundedCornerShape(16.dp), pressedScale = 0.98f, onClick = onOpen) else Modifier)
            .padding(start = 16.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, modifier = Modifier.weight(1f))
        if (onOpen != null) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "See all $title")
            }
        }
    }
}

/** The first chip: how the library is ordered, picked from a dropdown. */
@Composable
private fun SortChip(sort: Sort, onPick: (Sort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Box {
        Row(
            Modifier
                .height(34.dp)
                .pressable(RoundedCornerShape(17.dp), color = colors.onSurface, pressedScale = 0.94f) { open = true }
                .padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(sort.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.surface)
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Sort", tint = colors.surface, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(20.dp)) {
            Sort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.title, fontWeight = if (s == sort) FontWeight.Bold else FontWeight.Normal) },
                    trailingIcon = { if (s == sort) Icon(Icons.Rounded.Check, contentDescription = null) },
                    onClick = {
                        open = false
                        onPick(s)
                    },
                )
            }
        }
    }
}

@Composable
private fun PlaylistRow(p: Playlist, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(RoundedCornerShape(16.dp), pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // A little stack: the playlist's cover on top of the edges of the ones beneath.
        Box(Modifier.width(132.dp)) {
            Box(
                Modifier
                    .padding(horizontal = 10.dp)
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .offset(y = (-5).dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.onSurface.copy(alpha = 0.18f)),
            )
            Box(
                Modifier
                    .padding(top = 3.dp)
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (p.thumbnail != null) {
                    AsyncImage(p.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(placeholderIcon(p), contentDescription = null, modifier = Modifier.size(34.dp), tint = colors.primary)
                }
                p.count?.let {
                    Text(
                        it,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.62f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(p.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            p.subtitle?.let { Text(it, fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

private fun placeholderIcon(p: Playlist): ImageVector = when (p.id) {
    "WL" -> Icons.Rounded.Schedule
    "LL" -> Icons.Rounded.ThumbUp
    else -> Icons.AutoMirrored.Rounded.PlaylistPlay
}
