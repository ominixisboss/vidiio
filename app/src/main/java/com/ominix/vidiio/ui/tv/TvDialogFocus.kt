package com.ominix.vidiio.ui.tv

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
fun TvDialogFocusEffect(
    placed: Boolean,
    requester: FocusRequester,
    leaveTouchMode: Boolean = true,
    focused: () -> Boolean,
) {
    val view = LocalView.current
    LaunchedEffect(placed) {
        if (!placed) return@LaunchedEffect
        var frames = 0
        var asked = 0
        while (frames < 300 && asked < 60 && !focused()) {
            if (view.hasWindowFocus()) {
                // A dialog opened by a tap on a TV starts in touch mode, where a request is
                // dropped. On a phone the window is meant to stay in touch mode.
                if (leaveTouchMode && view.isInTouchMode) runCatching { view.requestFocusFromTouch() }
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
fun Modifier.tvDialogFocus(opensKeyboard: Boolean = false): Modifier {
    val isTv = LocalIsTv.current
    // Phones keep the behaviour they had: a text field does not take focus, and so does not pop
    // the keyboard, just because the dialog appeared.
    if (opensKeyboard && !isTv) return this
    val requester = remember { FocusRequester() }
    var placed by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    TvDialogFocusEffect(placed, requester, leaveTouchMode = isTv) { focused }
    return this
        .focusRequester(requester)
        .onPlaced { placed = true }
        .onFocusChanged { focused = it.isFocused }
}

/**
 * Remembers the control a dialog was opened from, so focus can go back there instead of to the
 * page's first focusable.
 *
 * Compose clears the page's focus while a dialog window holds it, and the control that opened the
 * dialog is normally still on screen behind it. Every clickable control reports itself when it is
 * used (see `tvClickable`), so the last one recorded is the control that opened the dialog - the
 * same for a remote press and for a tap.
 *
 * The controls used before it are kept as well: a dialog opened from another dialog (Create
 * Profile, from the profile list) leaves behind a row that is gone with its parent, so its
 * dismissal lands on the row that opened the parent dialog instead of on the page.
 */
class TvDialogOpener {
    private val recent = ArrayDeque<FocusRequester>()

    internal fun record(requester: FocusRequester) {
        recent.remove(requester)
        recent.addFirst(requester)
        while (recent.size > 4) recent.removeLast()
    }

    internal fun take(): List<FocusRequester> {
        val taken = recent.toList()
        recent.clear()
        return taken
    }
}

val LocalTvDialogOpener = compositionLocalOf<TvDialogOpener?> { null }

/**
 * Hands focus back to whatever opened the dialog that is closing, and to the page when that
 * control is gone (it was the row that was just removed, or its section was closed) or when the
 * dialog was never opened from one.
 */
@Composable
fun rememberDialogReturnFocus(): () -> Unit {
    val isTv = LocalIsTv.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val pageFocus = LocalTvPageFocus.current
    val opener = LocalTvDialogOpener.current
    // On a phone the dialog closes the way it always did: touch mode is left alone.
    if (!isTv) return remember { { } }
    return remember(view, scope, pageFocus, opener) {
        {
            scope.launch {
                var settled = false
                for (target in opener?.take().orEmpty()) {
                    if (settled) break
                    var frames = 0
                    var confirmed = 0
                    // Keep asking for a few frames after the control takes focus: a window that is
                    // regaining focus (the store behind a prompt, for one) can clear it again as
                    // the dialog above it goes away.
                    while (frames < 60 && confirmed < 8) {
                        if (view.isInTouchMode) runCatching { view.requestFocusFromTouch() }
                        // A detached requester means the control is gone; try the one before it. A
                        // request into a window that is not focused yet just returns false, which
                        // is why this keeps asking until the dialog has really gone.
                        val result = runCatching { target.requestFocus() }
                        if (result.isFailure) break
                        if (result.getOrDefault(false)) {
                            settled = true
                            confirmed++
                        } else {
                            settled = false
                            confirmed = 0
                        }
                        frames++
                        withFrameNanos { }
                    }
                }
                if (!settled) pageFocus?.invoke()
            }
        }
    }
}

/**
 * Remembers the card or row that opened the player, so leaving the player puts the remote back on
 * it rather than on the page's first control.
 *
 * The player is a route of its own, and visiting it takes the page it was opened from out of
 * composition - the focus requesters of that page go with it, so the page cannot simply be asked to
 * focus that control again. What does survive is a key: each control that can open the player
 * registers one while it is on screen, and the last one the viewer used is what [arm] keeps when
 * the player closes. The page then asks for that key instead of its own first control.
 */
class TvFocusReturn {
    private var opener: Any? = null
    private var armedKey: Any? = null
    private var armed = false
    private val controls = HashMap<Any, FocusRequester>()

    /** The control with [key] was used; this is where focus goes if the player opens from here. */
    internal fun rememberOpener(key: Any) {
        opener = key
    }

    /** The control used last - the one that opened whatever is on screen now. */
    internal fun openerKey(): Any? = opener

    internal fun register(key: Any, requester: FocusRequester) {
        controls[key] = requester
    }

    internal fun unregister(key: Any, requester: FocusRequester) {
        if (controls[key] === requester) controls.remove(key)
    }

    /** The player is closing over the page it was opened from. */
    internal fun arm() {
        armAt(opener)
    }

    /**
     * The page opened from [key] is closing: focus goes back to that control when the page under
     * it appears. Details uses this for the card it was opened from - by the time it closes, the
     * page itself has recorded other openers (playing a source), so it has to say which one to use.
     */
    internal fun armAt(key: Any?) {
        if (key != null) {
            armedKey = key
            armed = true
        }
    }

    /**
     * Puts focus back on the control that opened the player. Waits for the page to be laid out (a
     * page whose rows load late composes that control a moment after the route changes), and gives
     * up when it never appears - the row was removed, or the player was opened some other way.
     * True when focus landed on it.
     */
    suspend fun restoreOpenerFocus(view: View): Boolean {
        if (!armed) return false
        var rounds = 0
        while (rounds < 12) {
            val key = armedKey
            val target = if (armed && key != null) controls[key] else null
            if (target != null && view.hasWindowFocus()) {
                var attempts = 0
                while (attempts < 6) {
                    if (view.isInTouchMode) runCatching { view.requestFocusFromTouch() }
                    // A detached requester means the control is not on screen (yet); try again.
                    val result = runCatching { target.requestFocus() }
                    if (result.isFailure) break
                    attempts++
                    withFrameNanos { }
                    if (result.getOrDefault(false) && view.hasWindowFocus()) {
                        armed = false
                        armedKey = null
                        opener = null
                        return true
                    }
                }
            }
            rounds++
            delay(150)
        }
        armed = false
        armedKey = null
        opener = null
        return false
    }
}

val LocalTvFocusReturn = compositionLocalOf<TvFocusReturn?> { null }

/**
 * Registers a control that is not [Modifier.tvClickable] (a Material button) as the one that opened
 * the player under [key], and returns what to call from its click handler.
 */
@Composable
fun rememberTvReturnKey(key: Any, requester: FocusRequester): () -> Unit {
    val focusReturn = LocalTvFocusReturn.current
    DisposableEffect(focusReturn, key, requester) {
        focusReturn?.register(key, requester)
        onDispose { focusReturn?.unregister(key, requester) }
    }
    return remember(focusReturn, key) { { focusReturn?.rememberOpener(key) } }
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
