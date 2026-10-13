package app.counstar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.counstar.data.Chip
import app.counstar.data.Feeds
import app.counstar.data.Session
import app.counstar.ui.components.Avatar
import app.counstar.ui.components.pressable
import app.counstar.ui.home.CounstarLogo

val TopBarHeight = 56.dp
val ChipsHeight = 48.dp
val BottomBarHeight = 52.dp

/** The top bar: Counstar on Home, the tab's name elsewhere, with notifications and search. */
@Composable
fun TopBar(tab: Tab) {
    Row(
        Modifier.fillMaxWidth().height(TopBarHeight).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tab == Tab.Home) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CounstarLogo()
                Text("Counstar", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.7).sp)
            }
        } else {
            Text(
                if (tab == Tab.Subscriptions) "Subscriptions" else "You",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.7).sp,
                modifier = Modifier.weight(1f),
            )
        }
        IconButton(onClick = { Nav.notificationsOpen = true }) { Icon(Icons.Outlined.Notifications, contentDescription = "Notifications") }
        if (tab == Tab.You) {
            IconButton(onClick = { Nav.settingsOpen = true }) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
        }
        IconButton(onClick = { Nav.searchOpen = true }) { Icon(Icons.Rounded.Search, contentDescription = "Search") }
    }
}

/** Home's filter chips, as YouTube offers them: the selected one is a pill. */
@Composable
fun HomeChips(chips: List<Chip>) {
    val selected = Feeds.homeChip
    LazyRow(
        Modifier.fillMaxWidth().height(ChipsHeight),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(chips, key = { it.title }) { chip ->
            val on = if (selected == null) chip == chips.first() else chip.title == selected.title
            ChipButton(chip.title, on) { Feeds.selectHomeChip(chip) }
        }
    }
}

@Composable
fun ChipButton(title: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .height(34.dp)
            .pressable(
                RoundedCornerShape(if (selected) 17.dp else 10.dp),
                color = if (selected) colors.onSurface else colors.onSurface.copy(alpha = 0.1f),
                pressedScale = 0.94f,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.surface else colors.onSurface,
        )
    }
}

/** YouTube-sized bottom bar: Home, Shorts (unless they're off), Subscriptions, and You (your photo). */
@Composable
fun BottomBar(session: Session, bottomInset: Dp) {
    Row(
        Modifier.fillMaxWidth().height(BottomBarHeight + bottomInset).padding(bottom = bottomInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val searching = Nav.overlay
        BarItem("Home", Nav.tab == Tab.Home && !searching, Modifier.weight(1f), onClick = { Nav.goTo(Tab.Home) }) { on ->
            Icon(if (on) Icons.Rounded.Home else Icons.Outlined.Home, contentDescription = null, modifier = Modifier.size(25.dp))
        }
        if (app.counstar.data.Settings.showShorts) {
            BarItem("Shorts", Nav.tab == Tab.Shorts && !searching, Modifier.weight(1f), onClick = { Nav.goTo(Tab.Shorts) }) { on ->
                Icon(if (on) Icons.Rounded.PlayCircle else Icons.Outlined.PlayCircle, contentDescription = null, modifier = Modifier.size(25.dp))
            }
        }
        BarItem("Subscriptions", Nav.tab == Tab.Subscriptions && !searching, Modifier.weight(1f), onClick = { Nav.goTo(Tab.Subscriptions) }) { on ->
            Icon(if (on) Icons.Rounded.Subscriptions else Icons.Outlined.Subscriptions, contentDescription = null, modifier = Modifier.size(25.dp))
        }
        BarItem("You", Nav.tab == Tab.You && !searching, Modifier.weight(1f), onClick = { Nav.goTo(Tab.You) }) { on ->
            Avatar(session.photoUrl, 25.dp, ring = if (on) MaterialTheme.colorScheme.onSurface else null)
        }
    }
}

@Composable
private fun BarItem(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit, icon: @Composable (Boolean) -> Unit) {
    Column(
        modifier
            .height(BottomBarHeight)
            .pressable(RoundedCornerShape(16.dp), pressedScale = 0.88f, clipAndRipple = false, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon(selected)
        // Bright like YouTube's, and tucked right under the icon: every label readable, the
        // current one bold.
        Text(
            label,
            style = TextStyle(
                fontSize = 11.sp,
                lineHeight = 12.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}
