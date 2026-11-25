package com.ominix.vidiio.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The one way Vidiio says "there is nothing here" or "this did not load".
 *
 * Before this, every screen invented its own: a red "Error: <exception>" string, a blank grid
 * when a filter matched nothing, or a network-hosted animation. One component means one voice,
 * one retry affordance and one place to fix the layout at large font sizes.
 */
@Composable
fun StateMessage(
    title: String,
    message: String? = null,
    icon: ImageVector = Icons.Rounded.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // A phone in landscape is ~360dp tall, and at 1.5x fonts the app bar leaves the states as
        // little as 140dp. Below that the full-size state cannot fit, so it shrinks instead of
        // pushing its last line under the navigation bar.
        val compact = maxHeight < 190.dp
        // A filtered list on a landscape phone leaves about 90dp once the filter chips are drawn:
        // not enough for the icon, so the icon goes and the two lines stay visible.
        // An action adds 48dp, so a retry has to lose the icon sooner than a plain message does.
        val tiny = maxHeight < 120.dp || (actionLabel != null && maxHeight < 200.dp)
        val iconBox = if (compact) 40.dp else 64.dp
        val glyph = if (compact) 22.dp else 30.dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = if (compact) 24.dp else 32.dp,
                    vertical = if (tiny) 2.dp else if (compact) 8.dp else 16.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        if (!tiny) {
            Box(
                modifier = Modifier
                    .size(iconBox)
                    .background(Color.White.copy(alpha = 0.07f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(glyph)
                )
            }
            Spacer(Modifier.height(if (compact) 6.dp else 14.dp))
        }
        Text(
            text = title,
            style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(if (tiny) 2.dp else if (compact) 4.dp else 8.dp))
            Text(
                text = message,
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(if (tiny) 8.dp else if (compact) 10.dp else 18.dp))
            val shape = RoundedCornerShape(24.dp)
            Button(
                onClick = onAction,
                shape = shape,
                modifier = Modifier
                    // Still a full-size target; only the padding around it shrinks.
                    .heightIn(min = if (tiny) 44.dp else 48.dp)
                    .tvFocusRing(shape)
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(actionLabel, fontWeight = FontWeight.SemiBold)
            }
        }
        }
    }
}

/**
 * A failed load. The technical message stays in the log: what is shown is either "you're
 * offline" (when the device says so) or a plain apology, never "Error: java.io.IOException".
 *
 * [onRetry] is deliberately optional: only pass it when the load behind this state can actually
 * be re-run from here (a network fetch). A screen reading the local database passes nothing, so
 * it never offers a button that cannot change the outcome. Pass [title]/[message] for those local
 * failures, where the default network copy would be wrong.
 */
@Composable
fun ErrorState(
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    title: String? = null,
    message: String? = null,
) {
    // Offline copy only applies when the caller has not explained the failure itself.
    val offline = title == null && !rememberIsOnline()
    StateMessage(
        title = when {
            offline -> "You're offline"
            title != null -> title
            else -> "Something went wrong"
        },
        message = when {
            offline -> "Vidiio needs a connection for this. Check your network and try again."
            message != null -> message
            else -> "Vidiio couldn't load this. It may be a temporary problem."
        },
        icon = if (offline) Icons.Rounded.CloudOff else Icons.Rounded.ErrorOutline,
        actionLabel = if (onRetry != null) "Try again" else null,
        onAction = onRetry,
        modifier = modifier
    )
}

/** Nothing to show: an empty list, or a filter that matched nothing. */
@Composable
fun EmptyState(
    title: String,
    message: String? = null,
    icon: ImageVector = Icons.Rounded.Inbox,
    modifier: Modifier = Modifier,
) {
    StateMessage(title = title, message = message, icon = icon, modifier = modifier)
}

/**
 * Whether the device currently has a usable network. Recomposes when connectivity changes, so a
 * screen that failed while offline can swap to the retry copy as soon as Wi-Fi is back.
 */
@Composable
fun rememberIsOnline(): Boolean {
    val context = LocalContext.current
    var online by remember { mutableStateOf(context.hasInternet()) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@DisposableEffect onDispose { }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                online = true
            }

            override fun onLost(network: Network) {
                online = context.hasInternet()
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }
    return online
}

private fun Context.hasInternet(): Boolean {
    val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
