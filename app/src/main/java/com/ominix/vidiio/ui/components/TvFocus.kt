package com.ominix.vidiio.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.ominix.vidiio.ui.tv.LocalTvDialogOpener
import com.ominix.vidiio.ui.tv.LocalTvFocusReturn

/**
 * Clickable that also works with a D-pad / TV remote: it's focusable, shows a bright ring
 * while focused (so you can see what the remote is on), and treats DPAD_CENTER / Enter as a
 * click. Use in place of `Modifier.clickable` on browseable cards and tiles.
 *
 * [returnKey] identifies a control that opens the player or a details page: leaving it puts the
 * remote back on that control (see [com.ominix.vidiio.ui.tv.TvFocusReturn]) instead of on the
 * page's first control.
 *
 * The focus state is shown only by drawing - a ring inset within the item's own bounds and a
 * faint lift in brightness. It never changes size, position or layout. It used to also scale
 * the item up, which made every card in a row grow, overlap its neighbours and spill outside
 * the list's bounds as focus moved: that, plus the scroll that followed, was the "shaking".
 *
 * A hold of OK runs [onLongClick]. The Menu key is deliberately left alone: it belongs to the TV
 * side menu (see [com.ominix.vidiio.ui.tv.TvNavScaffold]), which a card must not be able to
 * swallow, or the rail could not be reached from a row.
 *
 * [focusScale] is accepted so existing call sites keep compiling, and is ignored.
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tvClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    focusScale: Float = 1f,
    enabled: Boolean = true,
    returnKey: String? = null,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    // Recorded when this control is used to open a dialog, so focus can come back to it afterwards.
    val opener = LocalTvDialogOpener.current
    // Registered while on screen, so leaving the player can find this card again by its key.
    val focusReturn = LocalTvFocusReturn.current
    val requester = remember { FocusRequester() }
    if (returnKey != null && focusReturn != null) {
        DisposableEffect(focusReturn, returnKey, requester) {
            focusReturn.register(returnKey, requester)
            onDispose { focusReturn.unregister(returnKey, requester) }
        }
    }
    // Both the click and the long press count as using this control: a poster's long press opens
    // the sheet whose Play lands on a details page that is already playing.
    val use: () -> Unit = {
        opener?.record(requester)
        returnKey?.let { focusReturn?.rememberOpener(it) }
    }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    // Holding OK on a remote repeats the key down; combinedClickable only knows pointer long
    // presses and never sees that. This remembers a hold that has already opened the menu, so
    // its release is swallowed instead of also opening the title underneath.
    var longPressFired by remember { mutableStateOf(false) }
    val openMenu = onLongClick
    val ring by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(120),
        label = "tvRing"
    )
    val dim by animateFloatAsState(
        targetValue = if (pressed) 0.18f else 0f,
        animationSpec = tween(80),
        label = "tvPress"
    )
    return this
        .clip(shape)
        .focusRequester(requester)
        .onPreviewKeyEvent { event ->
            if (!enabled || openMenu == null) return@onPreviewKeyEvent false
            val confirm = event.key == Key.DirectionCenter || event.key == Key.Enter
            if (!confirm) return@onPreviewKeyEvent false
            when {
                // The first repeat of a held OK is the long press. Consume it and the release
                // so the same hold cannot also fire the ordinary click.
                event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount > 0 -> {
                    if (!longPressFired) {
                        longPressFired = true
                        openMenu()
                    }
                    true
                }
                event.type == KeyEventType.KeyUp && longPressFired -> {
                    longPressFired = false
                    true
                }
                else -> {
                    // A fresh press begins a new hold.
                    if (event.type == KeyEventType.KeyDown) longPressFired = false
                    false
                }
            }
        }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = {
                use()
                onClick()
            },
            onLongClick = openMenu?.let { open ->
                {
                    use()
                    open()
                }
            },
        )
        .drawWithContent {
            drawContent()
            if (ring > 0f) {
                // Lift the focused item slightly, then draw the ring inside its bounds.
                val outline = shape.createOutline(size, layoutDirection, this)
                drawOutline(outline, Color.White.copy(alpha = 0.14f * ring))
                drawFocusRing(shape, ring)
            }
            if (dim > 0f) {
                drawOutline(shape.createOutline(size, layoutDirection, this), Color.Black.copy(alpha = dim))
            }
        }
}

private val RING_WIDTH = 4.dp
private val RING_INNER_WIDTH = 2.dp

/**
 * White ring with a thin dark line just inside it. White alone vanishes on a white button; the
 * dark line alone vanishes on a dark card; together one of them always reads.
 */
private fun DrawScope.drawFocusRing(shape: Shape, alpha: Float) {
    val outer = RING_WIDTH.toPx()
    inset(outer / 2f) {
        drawOutline(shape.createOutline(size, layoutDirection, this), Color.White.copy(alpha = alpha), style = Stroke(width = outer))
    }
    val innerWidth = RING_INNER_WIDTH.toPx()
    inset(outer + innerWidth / 2f) {
        drawOutline(shape.createOutline(size, layoutDirection, this), Color.Black.copy(alpha = 0.85f * alpha), style = Stroke(width = innerWidth))
    }
}

/**
 * Focus ring for controls that are focusable on their own (Material buttons): they get the same
 * ring as cards. Matters most on light buttons, where the stock focus tint is white on white and
 * the button just looks greyed out rather than highlighted.
 */
@Composable
fun Modifier.tvFocusRing(shape: Shape): Modifier {
    var focused by remember { mutableStateOf(false) }
    val ring by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(120),
        label = "tvFocusRing"
    )
    return this
        .onFocusChanged { focused = it.hasFocus }
        .drawWithContent {
            drawContent()
            if (ring > 0f) drawFocusRing(shape, ring)
        }
}
