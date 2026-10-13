package app.counstar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Plays once per launch of the app (not when the screen rotates or the app comes back). */
private object IntroState {
    var played = false
}

/**
 * Counstar's opening, like YouTube's: the logo springs in with a twist, its play button pops,
 * the name slides out from behind it, then it all lifts away into the app.
 */
@Composable
fun Intro() {
    var visible by remember { mutableStateOf(!IntroState.played) }
    if (!visible) return
    val logoScale = remember { Animatable(0.3f) }
    val logoTurn = remember { Animatable(-70f) }
    val play = remember { Animatable(0f) }
    val name = remember { Animatable(0f) }
    val leave = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        IntroState.played = true
        launch { logoScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
        launch { logoTurn.animateTo(-8f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)) }
        delay(220)
        launch { play.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium)) }
        delay(160)
        name.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        delay(260)
        leave.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        visible = false
    }
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - leave.value }
            .background(colors.background)
            // Swallow touches while it plays.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.graphicsLayer {
                val s = 1f + leave.value * 0.18f
                scaleX = s
                scaleY = s
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((14 * name.value).dp),
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                        rotationZ = logoTurn.value
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(44.dp).graphicsLayer {
                        scaleX = play.value
                        scaleY = play.value
                    },
                )
            }
            // The name unrolls from the logo's side.
            Text(
                "Counstar",
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1.2).sp,
                color = colors.onBackground,
                modifier = Modifier
                    // Takes up only as much room as has unrolled, so the logo starts centered.
                    .layout { measurable, constraints ->
                        val p = measurable.measure(constraints)
                        val w = (p.width * name.value).roundToInt()
                        layout(w, p.height) { p.placeRelative(0, 0) }
                    }
                    .graphicsLayer {
                    alpha = name.value
                    translationX = (1f - name.value) * -40.dp.toPx()
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    scaleX = 0.85f + 0.15f * name.value
                    scaleY = 0.85f + 0.15f * name.value
                },
            )
        }
    }
}
