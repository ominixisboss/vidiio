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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
 * Takes Menu from the TV keyboard on a dialog's text field and opens the side rail.
 *
 * The on-screen keyboard takes every key before Compose sees it, so Menu has to be taken ahead of
 * it, the same way the search field does. A dialog also has a window of its own, above the page and
 * its rail, so the menu can only be reached once the dialog has gone: [onCloseDialog] hides it
 * without handing the page focus back, and the rail takes the remote instead.
 */
@Composable
fun Modifier.tvDialogMenuToRail(onCloseDialog: () -> Unit): Modifier {
    if (!LocalIsTv.current) return this
    val openRail = LocalTvRailOpener.current ?: return this
    val keyboard = LocalSoftwareKeyboardController.current
    // A recomposition while the dialog is up must not leave a stale dismissal behind.
    val closeDialog = rememberUpdatedState(onCloseDialog)
    return this.onPreInterceptKeyBeforeSoftKeyboard { event ->
        if (event.key != Key.Menu) return@onPreInterceptKeyBeforeSoftKeyboard false
        if (event.type == KeyEventType.KeyDown) {
            keyboard?.hide()
            closeDialog.value()
            openRail()
        }
        true
    }
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
 * Remembers the control that opened the page now on top, so the page under it takes the remote back
 * there rather than to its own first control.
 *
 * A route visit takes the page it was opened from out of composition - the focus requesters of that
 * page go with it, so the page cannot simply be asked to focus that control again. What does
 * survive is a key: each control that can open a page registers one while it is on screen. Whatever
 * closes arms the key that opened it, and the page appearing next asks for that key.
 */
class TvFocusReturn {
    /** The control used last - the one that opened whatever is on screen now. */
    private var opener: String? = null
    /** The control the page appearing next should take focus back to, while a return is pending. */
    private var armedKey: String? = null
    private val controls = HashMap<String, FocusRequester>()

    /** The control with [key] was used; this is where focus goes if it opens a page. */
    internal fun rememberOpener(key: String) {
        opener = key
    }

    internal fun openerKey(): String? = opener

    internal fun register(key: String, requester: FocusRequester) {
        controls[key] = requester
    }

    internal fun unregister(key: String, requester: FocusRequester) {
        if (controls[key] === requester) controls.remove(key)
    }

    /**
     * Arms the return for the page that appears next: the control that opened whatever is closing,
     * or the one the closing page says to use instead (Details closes over the card that opened it
     * long before the page itself already recorded other openers, such as Play).
     */
    internal fun arm(key: String? = opener) {
        armedKey = key
    }

    /**
     * Puts focus back on the armed control. Waits for the page to be laid out (a page whose rows
     * load late composes that control a moment after the route changes), and gives up when it never
     * appears - the row was removed, or the player was opened some other way. True when it landed.
     */
    suspend fun restoreOpenerFocus(view: View): Boolean {
        val key = armedKey ?: return false
        var waited = 0
        while (waited < 2000) {
            val target = controls[key]
            if (target != null && view.hasWindowFocus()) {
                if (view.isInTouchMode) runCatching { view.requestFocusFromTouch() }
                // A detached requester means the control is not on screen (yet); try again.
                val landed = runCatching { target.requestFocus() }.getOrDefault(false)
                if (landed && view.hasWindowFocus()) {
                    armedKey = null
                    return true
                }
            }
            waited += 100
            delay(100)
        }
        armedKey = null
        return false
    }
}

val LocalTvFocusReturn = compositionLocalOf<TvFocusReturn?> { null }

/**
 * Registers a control that is not [Modifier.tvClickable] (a Material button) as the one that opened
 * the page under [key] - the player, or a details page - and returns what to call from its click
 * handler. A null [key] registers nothing, for controls that only sometimes open a page.
 */
@Composable
fun rememberTvReturnKey(key: String?, requester: FocusRequester): () -> Unit {
    val focusReturn = LocalTvFocusReturn.current
    if (key != null) {
        DisposableEffect(focusReturn, key, requester) {
            focusReturn?.register(key, requester)
            onDispose { focusReturn?.unregister(key, requester) }
        }
    }
    return remember(focusReturn, key) { { key?.let { focusReturn?.rememberOpener(it) } } }
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
