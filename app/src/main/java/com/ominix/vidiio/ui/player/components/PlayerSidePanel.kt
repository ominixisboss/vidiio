package com.ominix.vidiio.ui.player.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Right-hand panel for the player's pickers (subtitles, audio, sources, ...).
 *
 * This replaces ModalBottomSheet, which lives in its own dialog window: on a TV remote focus
 * never entered it, so after opening a menu the D-pad kept driving the player underneath.
 * The panel is part of the player's own tree, takes focus the moment it opens, and Back
 * (handled by PlayerScreen) closes it. Tapping the dimmed area still dismisses it on touch.
 *
 * It slides in from the right on a spring while its scrim fades, so opening a menu reads as the
 * menu arriving rather than the screen cutting. The width is 92% of the screen, but never more
 * than 440dp: on a phone 440dp would run off the edge, and in landscape 92% alone made every
 * menu a wall across most of the screen. Its content also clears the status and navigation bars,
 * which otherwise sat on top of the title and the last row.
 *
 * The slide is a layout offset rather than a graphics layer: a layer moves pixels without moving
 * the hit area, which left the panel eating taps on the right edge while it was still off screen.
 */
@Composable
fun PlayerSidePanel(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val focus = remember { FocusRequester() }
    val slide = remember { Animatable(0f) }
    // Measured width, so the panel can be placed off screen and travel with the animation.
    var panelWidth by remember { mutableIntStateOf(0) }
    val width = minOf(440.dp, LocalConfiguration.current.screenWidthDp.dp * 0.92f)

    LaunchedEffect(Unit) {
        slide.animateTo(1f, spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow))
    }

    // Lazy list items are composed a frame after the panel; wait a beat before grabbing focus.
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { focus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f * slide.value))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(width)
                .onSizeChanged { panelWidth = it.width }
                // Offset during layout, not on the layer: the panel's touch target then travels with
                // it, so taps near the right edge reach the player until the menu is actually there.
                .offset { IntOffset((panelWidth * (1f - slide.value)).roundToInt(), 0) }
                .pointerInput(Unit) { detectTapGestures { } }
                .focusRequester(focus)
                .focusGroup()
        ) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                content = content
            )
        }
    }
}
