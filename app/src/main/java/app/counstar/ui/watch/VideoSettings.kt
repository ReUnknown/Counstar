package app.counstar.ui.watch

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.counstar.data.Settings
import app.counstar.playback.CounstarPlayer
import app.counstar.ui.components.pressable

private enum class Page { Main, Quality, Speed }

private val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private fun speedLabel(s: Float) = if (s == 1f) "Normal" else (if (s % 1f == 0f) s.toInt().toString() else s.toString().trimEnd('0')) + "x"

/** The video's settings: quality, playback speed, loop and captions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoSettingsSheet(onDismiss: () -> Unit, onCaptions: () -> Unit) {
    var page by remember { mutableStateOf(Page.Main) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        AnimatedContent(page, label = "settings") { current ->
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                when (current) {
                    Page.Main -> {
                        Text("Video settings", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))
                        val height = CounstarPlayer.height
                        val quality = CounstarPlayer.pickedHeight?.let { "${it}p" } ?: ("Auto" + (height?.let { " (${it}p)" } ?: ""))
                        Option(Icons.Rounded.HighQuality, "Quality", quality, arrow = true) { page = Page.Quality }
                        Option(Icons.Rounded.Speed, "Playback speed", speedLabel(CounstarPlayer.speed), arrow = true) { page = Page.Speed }
                        Option(Icons.Outlined.ClosedCaption, "Captions", CounstarPlayer.caption?.name ?: if (CounstarPlayer.captionTracks.isEmpty()) "None" else "Off", arrow = true, onClick = onCaptions)
                        Option(Icons.Rounded.Repeat, "Loop video", null, trailing = {
                            Switch(checked = CounstarPlayer.loop, onCheckedChange = { CounstarPlayer.changeLoop(it) })
                        }) { CounstarPlayer.changeLoop(!CounstarPlayer.loop) }
                    }
                    Page.Quality -> {
                        SubHeader("Quality") { page = Page.Main }
                        Choice("Auto (up to ${Settings.maxHeight}p)", CounstarPlayer.pickedHeight == null) { CounstarPlayer.pickQuality(null); onDismiss() }
                        CounstarPlayer.heights.forEach { h ->
                            Choice("${h}p" + if (h >= 720) " HD" else "", CounstarPlayer.pickedHeight == h) { CounstarPlayer.pickQuality(h); onDismiss() }
                        }
                    }
                    Page.Speed -> {
                        SubHeader("Playback speed") { page = Page.Main }
                        speeds.forEach { s -> Choice(speedLabel(s), CounstarPlayer.speed == s) { CounstarPlayer.changeSpeed(s); onDismiss() } }
                    }
                }
            }
        }
    }
}

/** Captions: off, or any of the video's languages (auto-generated ones marked). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptionsSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text("Captions", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))
            val tracks = CounstarPlayer.captionTracks
            if (tracks.isEmpty()) {
                Text("This video has no captions.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
            } else {
                Choice("Off", CounstarPlayer.caption == null) { CounstarPlayer.showCaptions(null); onDismiss() }
                tracks.forEach { t ->
                    Choice(t.name, CounstarPlayer.caption == t) { CounstarPlayer.showCaptions(t); onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun Option(
    icon: ImageVector,
    title: String,
    value: String?,
    arrow: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(58.dp)
            .pressable(RoundedCornerShape(18.dp), pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null)
        Text(title, fontSize = 16.sp, modifier = Modifier.weight(1f))
        value?.let { Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        trailing?.invoke()
        if (arrow) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SubHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.padding(start = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(52.dp)
            .pressable(RoundedCornerShape(18.dp), pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        else Spacer(Modifier.width(22.dp))
        Text(label, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(start = 16.dp))
    }
}
