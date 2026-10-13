package app.counstar.ui

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.counstar.data.Feeds
import app.counstar.data.Session
import app.counstar.data.SessionRepository
import app.counstar.data.Updater
import app.counstar.playback.CounstarPlayer
import app.counstar.ui.components.frost
import app.counstar.ui.home.FeedScreen
import app.counstar.ui.login.LoginScreen
import app.counstar.ui.notifications.NotificationsScreen
import app.counstar.ui.search.SearchScreen
import app.counstar.ui.settings.SettingsSheet
import app.counstar.ui.watch.MiniPlayer
import app.counstar.ui.watch.MiniPlayerHeight
import app.counstar.ui.watch.WatchScreen
import app.counstar.ui.welcome.WelcomeScreen
import app.counstar.ui.you.HistoryScreen
import app.counstar.ui.channel.ChannelScreen
import app.counstar.ui.subscriptions.AllSubscriptionsScreen
import app.counstar.ui.subscriptions.SubscriptionsScreen
import androidx.compose.runtime.key
import app.counstar.ui.you.PlaylistScreen
import app.counstar.ui.you.YouScreen
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun CounstarApp() {
    val session by SessionRepository.session.collectAsState()
    val restored by SessionRepository.restored.collectAsState()
    var signingIn by remember { mutableStateOf(false) }
    val current = session
    Box(Modifier.fillMaxSize()) {
        when {
            !restored -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            current == null && signingIn -> LoginScreen(onDone = { signingIn = false }, onCancel = { signingIn = false })
            current == null -> WelcomeScreen(onSignIn = { signingIn = true }, onPreview = {
                Feeds.usePreviewHome()
                SessionRepository.startPreview()
            })
            else -> Shell(current)
        }
        UpdatePrompt()
        Intro()
    }
}

/** The dock is close to solid, like YouTube's bar: frosted, but never murky. */
private const val DOCK_TINT = 0.8f

@Composable
private fun Shell(session: Session) {
    var fullscreen by remember { mutableStateOf(false) }
    val activity = LocalActivity.current
    LaunchedEffect(Nav.watchOpen) { if (!Nav.watchOpen) fullscreen = false }
    // The app stays upright, whichever way the phone is turned. Only full screen is sideways.
    LaunchedEffect(fullscreen) {
        val window = activity?.window ?: return@LaunchedEffect
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        if (fullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            bars.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    val toggleFullscreen = { fullscreen = !fullscreen }
    BackHandler(Nav.tab != Tab.Home && !Nav.overlay && !Nav.watchOpen) { Nav.goTo(Tab.Home) }

    val density = LocalDensity.current
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val systemNav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBar = BottomBarHeight + systemNav
    val showMini = CounstarPlayer.current != null && !Nav.watchOpen
    val dock = bottomBar + if (showMini) MiniPlayerHeight else 0.dp
    val bottom: Dp = dock + 16.dp
    val overlay = Nav.overlay

    // The top bar slides away as you scroll down and comes back as soon as you scroll up.
    val chips = if (Nav.tab == Tab.Home) Feeds.home.chips else emptyList()
    val barHeight = TopBarHeight + if (chips.isNotEmpty()) ChipsHeight else 0.dp
    val barPx = with(density) { barHeight.toPx() }
    var barOffset by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Nav.tab, overlay) { barOffset = 0f }
    val hideOnScroll = remember(barPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                barOffset = (barOffset + available.y).coerceIn(-barPx, 0f)
                return Offset.Zero
            }
        }
    }
    val padding = PaddingValues(top = statusBar + barHeight, bottom = bottom)
    val haze = rememberHazeState()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Everything the bars float over: what's behind them shows through, frosted.
        Box(Modifier.fillMaxSize().hazeSource(haze).nestedScroll(hideOnScroll)) {
            when (Nav.tab) {
                Tab.Home -> FeedScreen(
                    Feeds.homeChipFeed ?: Feeds.home,
                    padding,
                    "Watch a few videos and your recommendations will show up here.",
                    refreshOffset = statusBar + barHeight,
                )
                Tab.Subscriptions -> SubscriptionsScreen(padding, refreshOffset = statusBar + barHeight)
                Tab.You -> YouScreen(session, padding)
            }
            // Pages stack: the one opened last is on top (and gets back first).
            if (Nav.searchOpen) SearchScreen(bottomPadding = bottom)
            if (Nav.notificationsOpen) NotificationsScreen(bottomPadding = bottom)
            if (Nav.historyOpen) HistoryScreen(bottomPadding = bottom)
            if (Nav.allSubscriptionsOpen) AllSubscriptionsScreen(bottomPadding = bottom)
            Nav.channels.lastOrNull()?.let { id -> key(id) { ChannelScreen(id, bottomPadding = bottom) } }
            Nav.playlist?.let { PlaylistScreen(it, bottomPadding = bottom) }
        }

        // Top: one even pane of frosted glass from the very top of the screen down to the
        // bottom of the top bar (as much of the bar as is showing).
        if (!overlay) {
            val barVisible = with(density) { (barPx + barOffset).toDp() }
            val glass = frost()
            Box(Modifier.fillMaxWidth().height(statusBar + barVisible).clipToBounds().hazeEffect(haze, glass)) {
                // Full height always (just slid up): squeezed to what's showing, the chips would vanish.
                Column(
                    Modifier
                        .padding(top = statusBar)
                        .wrapContentHeight(Alignment.Top, unbounded = true)
                        .offset { IntOffset(0, barOffset.roundToInt()) },
                ) {
                    TopBar(Nav.tab)
                    if (chips.isNotEmpty()) HomeChips(chips)
                }
                // The bar slides up under the status bar: keep its content out of the clock.
                Box(Modifier.fillMaxWidth().height(statusBar).hazeEffect(haze, glass))
            }
        } else if (Nav.playlist == null && Nav.channels.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(statusBar).background(MaterialTheme.colorScheme.background))
        }

        // Bottom: one frosted dock, all the way down behind the gesture area. While a video
        // plays, the mini player rides in it, right above the tabs.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(dock)
                .animateContentSize()
                .hazeEffect(haze, frost(DOCK_TINT)),
        ) {
            Column(Modifier.align(Alignment.BottomCenter)) {
                if (showMini) MiniPlayer()
                BottomBar(session, systemNav)
            }
        }

        AnimatedVisibility(
            Nav.watchOpen && CounstarPlayer.current != null,
            enter = slideInVertically(tween(380)) { it / 3 } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(300)) { it / 3 } + fadeOut(tween(220)),
        ) {
            WatchScreen(fullscreen = fullscreen, onToggleFullscreen = toggleFullscreen)
        }

        if (Nav.settingsOpen) SettingsSheet(onDismiss = { Nav.settingsOpen = false })
    }
}

/** Checks GitHub for a newer Counstar once a day and offers it. */
@Composable
private fun UpdatePrompt() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsState()
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { Updater.check(context) }
    val release = when (val s = state) {
        is Updater.State.Available -> s.release
        is Updater.State.Downloading -> s.release
        else -> null
    }
    if (release == null || dismissed) return
    val downloading = state as? Updater.State.Downloading
    AlertDialog(
        onDismissRequest = { if (downloading == null) dismissed = true },
        title = { Text("Counstar ${release.version} is out") },
        text = {
            if (downloading != null) {
                LinearProgressIndicator(progress = { downloading.progress }, modifier = Modifier.fillMaxWidth())
            } else {
                Text(release.notes.ifBlank { "A new version is ready to install." }, maxLines = 12)
            }
        },
        confirmButton = {
            if (downloading == null) {
                TextButton(onClick = { scope.launch { Updater.download(context, release) } }) { Text("Update") }
            }
        },
        dismissButton = {
            if (downloading == null) TextButton(onClick = { dismissed = true }) { Text("Later") }
        },
    )
}
