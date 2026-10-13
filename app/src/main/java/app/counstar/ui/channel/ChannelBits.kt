package app.counstar.ui.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.counstar.data.Channels
import app.counstar.ui.Nav
import app.counstar.ui.components.Avatar
import app.counstar.ui.components.BlurredImage
import app.counstar.ui.components.pressable
import app.counstar.ui.components.rememberArtColorScheme
import app.counstar.ui.watch.SubscribeButton
import coil3.compose.AsyncImage

/**
 * The whole banner image, not the thin strip YouTube crops for desktop: the same picture
 * asked for uncropped (16:9), so there's real banner above and below the usual strip.
 */
fun fullBanner(url: String): String {
    if ("googleusercontent.com" !in url && "ggpht.com" !in url) return url
    return url.substringBefore('=') + "=w1440-fcrop64=1,00000000ffffffff-k-c0xffffffff-no-nd-rj"
}

/**
 * A channel's banner across the top of a page, edge to edge: the usual strip plus a little
 * more of the real banner above and below it, fading into the page at the bottom. Nothing is
 * stretched or made up.
 */
@Composable
fun ChannelBanner(banner: String, modifier: Modifier = Modifier, aspect: Float = 3.4f) {
    AsyncImage(
        fullBanner(banner),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(0.55f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
            },
    )
}

/** Which channel's preview card is up (tapped from a comment or an @mention). */
object ChannelPeek {
    var id by mutableStateOf<String?>(null)
}

/**
 * A channel's card, like YouTube's when you tap someone in the comments: banner, picture, name,
 * handle and subscribers, with Subscribe and a button to the full channel.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChannelPeekSheet(channelId: String, onDismiss: () -> Unit) {
    val page = remember(channelId) { Channels.page(channelId) }
    LaunchedEffect(page) { page.load() }
    val info = page.info
    MaterialTheme(colorScheme = rememberArtColorScheme(info?.avatar)) {
        val colors = MaterialTheme.colorScheme
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.background,
            dragHandle = null,
        ) {
            Column(Modifier.fillMaxWidth()) {
                info?.banner?.let { ChannelBanner(it) }
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = if (info?.banner != null) 0.dp else 24.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Avatar(info?.avatar, 76.dp, modifier = Modifier.border(2.dp, colors.primary, CircleShape).padding(3.dp))
                    if (info == null) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            if (page.failed) Text("Couldn't load this channel.", color = colors.onSurfaceVariant) else LoadingIndicator(Modifier.size(44.dp))
                        }
                        return@Column
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(info.title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onBackground)
                        Text(listOfNotNull(info.handle, info.subscribers).joinToString(" · "), fontSize = 14.sp, color = colors.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(48.dp)
                                .pressable(RoundedCornerShape(24.dp, 8.dp, 8.dp, 24.dp), color = colors.secondaryContainer) {
                                    onDismiss()
                                    Nav.openChannel(channelId)
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text("View channel", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.onSecondaryContainer) }
                        SubscribeButton(
                            channelId,
                            info.subscribed,
                            info.bellOptions,
                            accent = colors.primary,
                            onAccent = colors.onPrimary,
                            modifier = Modifier.weight(1f),
                            big = true,
                        )
                    }
                }
            }
        }
    }
}

/** Everything about a channel: its whole description, links, and figures. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutSheet(page: app.counstar.data.ChannelPage, onDismiss: () -> Unit) {
    LaunchedEffect(page) { page.loadAbout() }
    val info = page.info
    val about = page.about
    val colors = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surfaceContainerLow) {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            item { Text(info?.title ?: "About", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp, modifier = Modifier.padding(bottom = 12.dp)) }
            item {
                Text(
                    about?.description ?: info?.description ?: "",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = colors.onSurface,
                )
            }
            if (about == null && info?.aboutToken != null) {
                item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(40.dp)) } }
            }
            if (about != null && about.links.isNotEmpty()) {
                item { Text("Links", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp)) }
                items(about.links.size) { i ->
                    val (title, url) = about.links[i]
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .pressable(RoundedCornerShape(16.dp), pressedScale = 0.98f) {
                                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                    ) {
                        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(url.removePrefix("https://").removePrefix("http://").removePrefix("www."), fontSize = 13.sp, color = if (app.counstar.ui.theme.isCounstarDark()) Color(0xFF3EA6FF) else Color(0xFF065FD4), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            val facts = listOfNotNull(
                (about?.url ?: info?.handle?.let { "youtube.com/$it" })?.let { "Channel" to it.removePrefix("http://").removePrefix("https://").removePrefix("www.") },
                (about?.subscribers ?: info?.subscribers)?.let { "Subscribers" to it },
                (about?.videos ?: info?.videoCount)?.let { "Videos" to it },
                about?.views?.let { "Views" to it },
                about?.joined?.let { "Joined" to it.removePrefix("Joined ") },
                about?.country?.let { "Country" to it },
            )
            if (facts.isNotEmpty()) {
                item { Text("More info", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp)) }
                items(facts.size) { i ->
                    val (label, value) = facts[i]
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                        Text(label, fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.width(110.dp))
                        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
