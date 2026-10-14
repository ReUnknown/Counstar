package app.counstar.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.counstar.BuildConfig
import app.counstar.data.Feeds
import app.counstar.data.SessionRepository
import app.counstar.data.Settings
import app.counstar.data.Updater
import app.counstar.playback.CounstarPlayer
import app.counstar.ui.Nav
import app.counstar.ui.components.pressable
import kotlinx.coroutines.launch

/** Settings, as a sheet over the app: playback, look, updates, and signing out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val update by Updater.state.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))

            Label("Playback")
            Tile(RoundedCornerShape(24.dp, 24.dp, 6.dp, 6.dp), onClick = { Settings.saveAmbientGlow(!Settings.ambientGlow) }) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = colors.primary)
                Column(Modifier.weight(1f)) {
                    Text("Video glow", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("The video's colors glow softly around the player", fontSize = 13.sp, lineHeight = 17.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
                Switch(checked = Settings.ambientGlow, onCheckedChange = { Settings.saveAmbientGlow(it) })
            }
            Tile(RoundedCornerShape(6.dp), onClick = { Settings.saveShowShorts(!Settings.showShorts) }) {
                Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = colors.primary)
                Column(Modifier.weight(1f)) {
                    Text("Shorts", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("The Shorts tab and Shorts in feeds and search. Off: Shorts open as normal videos", fontSize = 13.sp, lineHeight = 17.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
                Switch(checked = Settings.showShorts, onCheckedChange = { Settings.saveShowShorts(it) })
            }
            Tile(RoundedCornerShape(6.dp), onClick = { Settings.saveShowPosts(!Settings.showPosts) }) {
                Icon(Icons.Rounded.Forum, contentDescription = null, tint = colors.primary)
                Column(Modifier.weight(1f)) {
                    Text("Posts", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Creators' posts and polls, in your feeds and on channels", fontSize = 13.sp, lineHeight = 17.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
                Switch(checked = Settings.showPosts, onCheckedChange = { Settings.saveShowPosts(it) })
            }
            Tile(RoundedCornerShape(6.dp), onClick = { Settings.saveBackgroundPlay(!Settings.backgroundPlay) }) {
                Icon(Icons.Rounded.Headphones, contentDescription = null, tint = colors.primary)
                Column(Modifier.weight(1f)) {
                    Text("Background play", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Keeps playing when you leave Counstar or lock your phone", fontSize = 13.sp, lineHeight = 17.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
                Switch(checked = Settings.backgroundPlay, onCheckedChange = { Settings.saveBackgroundPlay(it) })
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .pressable(RoundedCornerShape(6.dp, 6.dp, 24.dp, 24.dp), color = colors.surfaceContainerHigh, pressedScale = 1f) {}
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Highest quality", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Segmented(Settings.qualities.map { "${it}p" }, Settings.qualities.indexOf(Settings.maxHeight)) {
                    Settings.saveMaxHeight(Settings.qualities[it])
                }
            }

            Label("Look")
            Column(
                Modifier
                    .fillMaxWidth()
                    .pressable(RoundedCornerShape(24.dp), color = colors.surfaceContainerHigh, pressedScale = 1f) {}
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Theme", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Segmented(Settings.Theme.entries.map { it.title }, Settings.theme.ordinal) { Settings.saveTheme(Settings.Theme.entries[it]) }
            }

            Label("Counstar")
            Tile(RoundedCornerShape(24.dp, 24.dp, 6.dp, 6.dp), onClick = {
                // Android only sends youtube.com links to Counstar once you add them here.
                context.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                        android.net.Uri.parse("package:${context.packageName}"),
                    ),
                )
            }) {
                Icon(Icons.Rounded.Link, contentDescription = null, tint = colors.primary)
                Column(Modifier.weight(1f)) {
                    Text("Open YouTube links", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Add youtube.com and youtu.be under \"Open by default\"", fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
            }
            Tile(RoundedCornerShape(6.dp), onClick = {
                scope.launch {
                    when (val s = update) {
                        is Updater.State.Available -> Updater.download(context, s.release)
                        is Updater.State.Ready -> Updater.install(context, s.apk)
                        else -> Updater.check(context, force = true)
                    }
                }
            }) {
                Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = colors.primary)
                Column(Modifier.weight(1f)) {
                    Text("Updates", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        when (val s = update) {
                            Updater.State.Checking -> "Checking…"
                            Updater.State.UpToDate -> "Counstar ${BuildConfig.VERSION_NAME} is the latest"
                            Updater.State.Failed -> "Couldn't check. Tap to try again"
                            is Updater.State.Available -> "${s.release.version} is out. Tap to update"
                            is Updater.State.Downloading -> "Downloading ${(s.progress * 100).toInt()}%"
                            is Updater.State.Ready -> "Tap to install ${s.release.version}"
                            Updater.State.Idle -> "Counstar ${BuildConfig.VERSION_NAME}. Tap to check"
                        },
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            Tile(RoundedCornerShape(6.dp, 6.dp, 24.dp, 24.dp), onClick = {
                scope.launch {
                    onDismiss()
                    CounstarPlayer.stop()
                    Feeds.clear()
                    Nav.reset()
                    SessionRepository.signOut(context)
                }
            }) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = colors.error)
                Text("Sign out", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.error, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun Tile(shape: Shape, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .pressable(shape, color = MaterialTheme.colorScheme.surfaceContainerHigh, pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

/** Connected buttons, one per choice: the picked one fills and rounds out fully. */
@Composable
private fun Segmented(options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val outer = 20.dp
            val inner = if (on) 20.dp else 6.dp
            val shape = RoundedCornerShape(
                topStart = if (i == 0) outer else inner,
                bottomStart = if (i == 0) outer else inner,
                topEnd = if (i == options.lastIndex) outer else inner,
                bottomEnd = if (i == options.lastIndex) outer else inner,
            )
            Row(
                Modifier
                    .weight(1f)
                    .height(42.dp)
                    .pressable(shape, color = if (on) colors.primary else colors.surfaceContainerHighest, pressedScale = 0.94f) { onPick(i) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) colors.onPrimary else colors.onSurface)
            }
        }
    }
}
