package app.counstar.ui.you

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.counstar.BuildConfig
import app.counstar.data.Feeds
import app.counstar.data.Updater
import app.counstar.data.Session
import app.counstar.data.SessionRepository
import app.counstar.playback.CounstarPlayer
import app.counstar.ui.Nav
import app.counstar.ui.components.Avatar
import app.counstar.ui.components.SmallVideoCard
import app.counstar.ui.components.pressable
import kotlinx.coroutines.launch

/** You: your account, what you've watched, and signing out. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YouScreen(session: Session, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history = Feeds.history
    LaunchedEffect(history) { history.load() }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Avatar(session.photoUrl, 76.dp)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(session.name, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp)
                session.email?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("History", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 16.dp))
            when {
                history.items.isNotEmpty() -> LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(history.items.take(30), key = { it.id }) { item -> SmallVideoCard(item, onClick = { Nav.watch(item) }) }
                }
                history.loading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(48.dp))
                }
                else -> Text(
                    "Videos you watch show up here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val update by Updater.state.collectAsState()
            Row(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(64.dp)
                    .pressable(RoundedCornerShape(24.dp, 24.dp, 6.dp, 6.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        scope.launch {
                            when (val s = update) {
                                is Updater.State.Available -> Updater.download(context, s.release)
                                is Updater.State.Ready -> Updater.install(context, s.apk)
                                else -> Updater.check(context, force = true)
                            }
                        }
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(60.dp)
                    .pressable(RoundedCornerShape(6.dp, 6.dp, 24.dp, 24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        scope.launch {
                            CounstarPlayer.stop()
                            Feeds.clear()
                            Nav.reset()
                            SessionRepository.signOut(context)
                        }
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text("Sign out", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
