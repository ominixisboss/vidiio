package com.ominix.vidiio.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ominix.vidiio.data.update.AppUpdater
import com.ominix.vidiio.ui.tv.LocalTvPageFocus
import com.ominix.vidiio.ui.tv.tvDialogFocus
import kotlinx.coroutines.launch

/**
 * Checks GitHub for a newer release once per launch and, when [AppUpdater.offered] holds one,
 * offers to download and install it.
 */
@Composable
fun UpdatePrompt(updater: AppUpdater, checkOnStart: Boolean) {
    LaunchedEffect(checkOnStart) {
        if (checkOnStart && updater.offered.value == null) updater.check()?.let { updater.offered.value = it }
    }
    val update by updater.offered.collectAsState()
    val current = update ?: return
    val scope = rememberCoroutineScope()
    var progress by remember(current) { mutableStateOf<Float?>(null) }
    var error by remember(current) { mutableStateOf<String?>(null) }
    val focusPage = LocalTvPageFocus.current
    // A tap on a dialog button leaves the page with nothing highlighted until the next key press;
    // hand focus back to it, the way the VPN reminder does.
    fun dismiss() {
        updater.offered.value = null
        focusPage?.invoke()
    }

    AlertDialog(
        onDismissRequest = { if (progress == null) dismiss() },
        title = { Text("Update to ${current.version}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    progress != null -> {
                        Text("Downloading…")
                        LinearProgressIndicator(progress = { progress!! }, modifier = Modifier.fillMaxWidth())
                    }
                    else -> {
                        Text("A new version of Vidiio is available.")
                        if (current.notes.isNotBlank()) {
                            Text(
                                current.notes,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = progress == null,
                modifier = Modifier.tvDialogFocus(),
                onClick = {
                    // Android asks once for permission to install apps from Vidiio; take the viewer there first.
                    if (!updater.canInstall()) {
                        updater.openInstallPermission()
                        return@TextButton
                    }
                    error = null
                    progress = 0f
                    updater.ignoreVersion(current.version)
                    scope.launch {
                        try {
                            val apk = updater.download(current) { p -> progress = p }
                            updater.install(apk)
                            updater.offered.value = null
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            progress = null
                            error = "Couldn't download the update: ${e.message ?: "unknown error"}"
                        }
                    }
                }
            ) { Text(if (updater.canInstall()) "Update" else "Allow installs") }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    enabled = progress == null,
                    onClick = {
                        updater.ignoreVersion(current.version)
                        focusPage?.invoke()
                    }
                ) {
                    Text("Don't show again")
                }
                TextButton(
                    enabled = progress == null,
                    onClick = { dismiss() }
                ) {
                    Text("Later")
                }
            }
        }
    )
}
