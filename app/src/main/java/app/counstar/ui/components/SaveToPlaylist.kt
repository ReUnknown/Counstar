package app.counstar.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.counstar.data.Library
import app.counstar.data.PlaylistOption
import app.counstar.data.YouTubeParser
import com.metrolist.innertube.WebApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "Save to": Watch later and your playlists, ticked where the video already is. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SaveToPlaylistSheet(videoId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var options by remember { mutableStateOf<List<PlaylistOption>?>(null) }
    val ticked = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(videoId) {
        val fromYouTube = withContext(Dispatchers.IO) { WebApi.playlistOptions(videoId).mapCatching(YouTubeParser::playlistOptions) }.getOrDefault(emptyList())
        // If YouTube's list doesn't come, offer Watch later and the library's own playlists.
        options = fromYouTube.ifEmpty {
            listOf(PlaylistOption("WL", "Watch later", contains = false, private = true)) +
                Library.playlists.filter { it.kind == app.counstar.data.Playlist.Kind.Playlist && it.id != "WL" && it.id != "LL" }
                    .map { PlaylistOption(it.id, it.title, contains = false, private = "Private" in it.subtitle.orEmpty()) }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text("Save to…", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))
            val list = options
            if (list == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(44.dp)) }
            }
            list?.forEach { option ->
                val on = ticked[option.id] ?: option.contains
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(56.dp)
                        .pressable(RoundedCornerShape(18.dp), pressedScale = 0.98f) {
                            val want = !on
                            ticked[option.id] = want
                            scope.launch {
                                withContext(Dispatchers.IO) { WebApi.editPlaylist(option.id, videoId, want) }
                                    .onSuccess {
                                        Toast.makeText(context, if (want) "Saved to ${option.title}" else "Removed from ${option.title}", Toast.LENGTH_SHORT).show()
                                    }
                                    .onFailure {
                                        ticked[option.id] = !want
                                        Toast.makeText(context, "Couldn't save: ${it.message}", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Checkbox(checked = on, onCheckedChange = null)
                    Text(option.title, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Icon(
                        when {
                            option.id == "WL" -> Icons.Rounded.Schedule
                            option.private -> Icons.Rounded.Lock
                            else -> Icons.AutoMirrored.Rounded.PlaylistPlay
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
