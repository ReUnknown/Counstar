package app.counstar.ui.watch

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.counstar.data.CaptionTrack
import app.counstar.data.Cue
import app.counstar.data.VideoItem
import app.counstar.data.WatchInfo
import app.counstar.data.YouTubeParser
import app.counstar.playback.CounstarPlayer
import app.counstar.ui.components.pressable
import coil3.compose.AsyncImage
import com.metrolist.innertube.WebApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The full description, as a sheet: exact likes, views and release date, hashtags, the text,
 * the chapters (tap one to jump there), and the transcript.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DescriptionSheet(item: VideoItem, info: WatchInfo, onDismiss: () -> Unit) {
    var transcript by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        AnimatedContent(transcript, label = "description") { showTranscript ->
            if (showTranscript) {
                Transcript(onBack = { transcript = false })
                return@AnimatedContent
            }
            LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
                item {
                    Text(
                        info.title.ifBlank { item.title },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 25.sp,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
                item {
                    // The three figures, joined like a button group.
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        val (day, year) = info.date?.let { d -> d.substringBeforeLast(' ') to d.substringAfterLast(' ') } ?: (null to null)
                        Stat(info.exactLikes ?: info.likes ?: "–", "Likes", RoundedCornerShape(20.dp, 6.dp, 6.dp, 20.dp), Modifier.weight(1f))
                        Stat(info.exactViews ?: info.views ?: "–", "Views", RoundedCornerShape(6.dp), Modifier.weight(1.3f))
                        Stat(year ?: info.published ?: "–", day ?: "Released", RoundedCornerShape(6.dp, 20.dp, 20.dp, 6.dp), Modifier.weight(1f))
                    }
                }
                if (info.hashtags.isNotEmpty()) {
                    item {
                        FlowRow(Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            info.hashtags.forEach { Text(it, color = colors.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
                info.description?.let {
                    item { Text(it, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 20.dp)) }
                }
                if (info.chapters.isNotEmpty()) {
                    item { Heading("Chapters") }
                    itemsIndexed(info.chapters) { _, chapter ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .pressable(RoundedCornerShape(16.dp), pressedScale = 0.98f) {
                                    CounstarPlayer.player.seekTo(chapter.startMs)
                                    CounstarPlayer.player.play()
                                    onDismiss()
                                }
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(Modifier.width(112.dp).height(63.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerHighest)) {
                                chapter.thumbnail?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().fillMaxHeight()) }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(chapter.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(formatTime(chapter.startMs), fontSize = 13.sp, color = colors.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                item { Heading("Transcript") }
                item {
                    Row(
                        Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 32.dp)
                            .fillMaxWidth()
                            .height(60.dp)
                            .pressable(RoundedCornerShape(20.dp), color = colors.surfaceContainerHigh, pressedScale = 0.98f) { transcript = true }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(Icons.Rounded.Subtitles, contentDescription = null, tint = colors.primary)
                        Text(
                            if (CounstarPlayer.captionTracks.isEmpty()) "No transcript for this video" else "Show transcript",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 8.dp))
}

@Composable
private fun Stat(value: String, label: String, shape: RoundedCornerShape, modifier: Modifier) {
    Column(
        modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, textAlign = TextAlign.Center)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** The video's words, line by line with their times: tap a line to jump there. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Transcript(onBack: () -> Unit) {
    val tracks = CounstarPlayer.captionTracks
    val track: CaptionTrack? = CounstarPlayer.caption
        ?: tracks.firstOrNull { it.language.startsWith("en") && !it.auto } ?: tracks.firstOrNull { !it.auto } ?: tracks.firstOrNull()
    var lines by remember(track) { mutableStateOf<List<Cue>?>(null) }
    LaunchedEffect(track) {
        lines = if (track == null) emptyList() else withContext(Dispatchers.IO) {
            WebApi.fetch(track.url.replace("&fmt=srv3", "") + "&fmt=json3").mapCatching(YouTubeParser::cues).getOrDefault(emptyList())
        }
    }
    val state = rememberPlaybackState(CounstarPlayer.player)
    val list = rememberLazyListState()
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
        Row(Modifier.padding(start = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Column {
                Text("Transcript", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                track?.let { Text(it.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        val all = lines
        when {
            all == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(48.dp)) }
            all.isEmpty() -> Text("No transcript for this video.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
            else -> LazyColumn(state = list) {
                itemsIndexed(all) { _, cue ->
                    val now = state.position in cue.startMs until cue.endMs
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .pressable(
                                RoundedCornerShape(14.dp),
                                color = if (now) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                                pressedScale = 0.98f,
                            ) { CounstarPlayer.player.seekTo(cue.startMs) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            formatTime(cue.startMs),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(48.dp).padding(top = 2.dp),
                        )
                        Text(cue.text, fontSize = 15.sp, lineHeight = 20.sp)
                    }
                }
            }
        }
    }
}
