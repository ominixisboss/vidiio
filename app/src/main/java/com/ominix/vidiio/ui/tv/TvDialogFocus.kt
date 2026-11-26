package com.ominix.vidiio.ui.tv

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalView

/**
 * Asks for focus on [requester] as soon as [placed] says its control has been laid out, and keeps
 * asking on each frame while the window has focus, until [focused] reports it landed.
 *
 * A dialog gets a window of its own. A request made before that window can take focus is dropped,
 * and the instant it does take focus it selects the first control in it - which is how the VPN
 * reminder ended up on its checkbox rather than the button. A fixed delay was a guess that missed
 * whenever a frame ran long. Waiting for placement and window focus is not a guess, and stopping
 * as soon as the control holds focus means a viewer who has already moved on is not pulled back.
 */
@Composable
fun TvDialogFocusEffect(placed: Boolean, requester: FocusRequester, focused: () -> Boolean) {
    val view = LocalView.current
    LaunchedEffect(placed) {
        if (!placed) return@LaunchedEffect
        var frames = 0
        var asked = 0
        while (frames < 300 && asked < 60 && !focused()) {
            if (view.hasWindowFocus()) {
                // A dialog opened by a tap starts in touch mode, where a request is dropped.
                if (view.isInTouchMode) runCatching { view.requestFocusFromTouch() }
                runCatching { requester.requestFocus() }
                asked++
            }
            frames++
            withFrameNanos { }
        }
    }
}

/**
 * Puts the remote on the control a dialog opens on. Apply to the control itself; see
 * [TvDialogFocusEffect] for when the focus is asked for.
 */
@Composable
fun Modifier.tvDialogFocus(): Modifier {
    val requester = remember { FocusRequester() }
    var placed by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    TvDialogFocusEffect(placed, requester) { focused }
    return this
        .focusRequester(requester)
        .onPlaced { placed = true }
        .onFocusChanged { focused = it.isFocused }
}

/**
 * Hands focus back to what a dialog or panel was opened over, including after a tap.
 *
 * A tap leaves the window in touch mode, where a focus request is dropped until the next key
 * press - so the page was left with no highlighted control. Leave touch mode first, then ask
 * again on each frame until the target really holds focus in a focused window.
 */
suspend fun restorePageFocus(view: View, requester: FocusRequester, hasFocus: () -> Boolean) {
    var attempts = 0
    while (attempts < 60) {
        if (view.isInTouchMode) runCatching { view.requestFocusFromTouch() }
        runCatching { requester.requestFocus() }
        attempts++
        withFrameNanos { }
        if (hasFocus() && view.hasWindowFocus()) break
    }
}
