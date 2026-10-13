package app.counstar.ui

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import app.counstar.ui.components.BackdropBlurBand
import app.counstar.ui.components.FROSTED_TINT
import app.counstar.ui.components.backdropSource
import app.counstar.ui.components.rememberBackdrop
import app.counstar.ui.home.FeedScreen
import app.counstar.ui.login.LoginScreen
import app.counstar.ui.notifications.NotificationsScreen
import app.counstar.ui.search.SearchScreen
import app.counstar.ui.watch.MiniPlayer
import app.counstar.ui.watch.MiniPlayerHeight
import android.view.OrientationEventListener
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.animateContentSize
import app.counstar.ui.watch.WatchScreen
import app.counstar.ui.welcome.WelcomeScreen
import app.counstar.ui.you.YouScreen
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun CounstarApp() {
    val session by SessionRepository.session.collectAsState()
    val restored by SessionRepository.restored.collectAsState()
    var signingIn by remember { mutableStateOf(false) }
    val current = session
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
}

/** The dock is close to solid, like YouTube's bar: frosted, but never murky. */
private const val DOCK_TINT = 0.84f

@Composable
private fun Shell(session: Session) {
    var fullscreen by remember { mutableStateOf(false) }
    val activity = LocalActivity.current
    LaunchedEffect(Nav.watchOpen) { if (!Nav.watchOpen) fullscreen = false }
    // Like YouTube: the app stays upright; the video goes full screen sideways. Turning the
    // phone on its side while watching goes full screen, and turning it back comes out of it.
    var byRotation by remember { mutableStateOf(false) }
    var waitForUpright by remember { mutableStateOf(false) }
    val context = LocalContext.current
    DisposableEffect(Nav.watchOpen) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == ORIENTATION_UNKNOWN) return
                val sideways = degrees in 65..115 || degrees in 245..295
                val upright = degrees < 25 || degrees > 335
                if (upright) waitForUpright = false
                if (!Nav.watchOpen || waitForUpright) return
                if (sideways && !fullscreen) {
                    byRotation = true
                    fullscreen = true
                } else if (upright && fullscreen && byRotation) {
                    byRotation = false
                    fullscreen = false
                }
            }
        }
        if (Nav.watchOpen && listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }
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
    val toggleFullscreen = {
        // Leaving full screen with the button while the phone is still sideways: don't jump
        // straight back in until it's been upright.
        if (fullscreen) waitForUpright = true
        byRotation = false
        fullscreen = !fullscreen
    }
    BackHandler(Nav.tab != Tab.Home && !Nav.searchOpen && !Nav.notificationsOpen && !Nav.watchOpen) { Nav.goTo(Tab.Home) }

    val density = LocalDensity.current
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val systemNav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBar = BottomBarHeight + systemNav
    val showMini = CounstarPlayer.current != null && !Nav.watchOpen
    val dock = bottomBar + if (showMini) MiniPlayerHeight else 0.dp
    val bottom: Dp = dock + 16.dp
    val overlay = Nav.searchOpen || Nav.notificationsOpen

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

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val screenHeight = maxHeight
        val backdrop = rememberBackdrop()
        Box(Modifier.fillMaxSize().backdropSource(backdrop).nestedScroll(hideOnScroll)) {
            when (Nav.tab) {
                Tab.Home -> FeedScreen(
                    Feeds.homeChipFeed ?: Feeds.home,
                    padding,
                    "Watch a few videos and your recommendations will show up here.",
                    refreshOffset = statusBar + barHeight,
                )
                Tab.Subscriptions -> FeedScreen(
                    Feeds.subscriptions,
                    padding,
                    "Subscribe to channels to see their new videos here.",
                    refreshOffset = statusBar + barHeight,
                )
                Tab.You -> YouScreen(session, padding)
            }
            if (Nav.searchOpen) SearchScreen(bottomPadding = bottom)
            if (Nav.notificationsOpen) NotificationsScreen(bottomPadding = bottom)
        }

        // Top: one even pane of frosted glass from the very top of the screen down to the
        // bottom of the top bar (as much of the bar as is showing). No solid strip, no fade.
        if (!overlay) {
            val barVisible = with(density) { (barPx + barOffset).toDp() }
            Box(Modifier.fillMaxWidth().height(statusBar + barVisible).clipToBounds()) {
                BackdropBlurBand(backdrop, screenHeight, atBottom = false, modifier = Modifier.matchParentSize(), radius = 72.dp)
                Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.background.copy(alpha = FROSTED_TINT)))
                Column(Modifier.padding(top = statusBar).offset { IntOffset(0, barOffset.roundToInt()) }) {
                    TopBar(Nav.tab)
                    if (chips.isNotEmpty()) HomeChips(chips)
                }
                // The bar slides up under the status bar: keep its content out of the clock.
                Box(Modifier.fillMaxWidth().height(statusBar).clipToBounds()) {
                    BackdropBlurBand(backdrop, screenHeight, atBottom = false, modifier = Modifier.matchParentSize(), radius = 72.dp)
                    Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.background.copy(alpha = FROSTED_TINT)))
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().height(statusBar).background(MaterialTheme.colorScheme.background))
        }

        // Bottom: one frosted dock, all the way down behind the gesture area. While a video
        // plays, the mini player rides in it, right above the tabs.
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(dock).animateContentSize()) {
            BackdropBlurBand(backdrop, screenHeight, atBottom = true, modifier = Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.background.copy(alpha = DOCK_TINT)))
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
