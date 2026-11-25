package com.ominix.vidiio.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ominix.vidiio.navigation.VidiioRoute
import com.ominix.vidiio.ui.components.tvClickable

/**
 * Phone-only: the bottom bar has room for five tabs, so My List, Downloads and Settings live
 * here instead of crowding it. (TVs and tablets show them in the side menu directly.)
 *
 * Grouped into what you watch and what the app owns, and the rows arrive one after another instead
 * of all at once, so the page opens rather than snaps.
 */
@Composable
fun LibraryScreen(
    onOpen: (VidiioRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "What you saved, downloaded and watch live.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f)
            )
        }

        SectionLabel("Watch", shown, 0)
        LibraryEntry(shown, 1, Icons.Rounded.LiveTv, "Live TV", "Live channels and sports") { onOpen(VidiioRoute.LiveTv) }
        LibraryEntry(shown, 2, Icons.Rounded.Favorite, "My List", "Titles you saved") { onOpen(VidiioRoute.Favorites) }
        LibraryEntry(shown, 3, Icons.Rounded.Download, "Downloads", "Available offline") { onOpen(VidiioRoute.Downloads) }

        SectionLabel("App", shown, 4)
        LibraryEntry(shown, 5, Icons.Rounded.Settings, "Settings", "Playback, subtitles, sources and more") { onOpen(VidiioRoute.Settings) }
    }
}

@Composable
private fun SectionLabel(text: String, shown: Boolean, index: Int) {
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(200, delayMillis = index * 40))
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = Color.White.copy(alpha = 0.45f),
            modifier = Modifier.padding(top = 10.dp, start = 4.dp)
        )
    }
}

/** One row, arriving slightly after the one above it. */
@Composable
private fun LibraryEntry(
    shown: Boolean,
    index: Int,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val delay = index * 40
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(220, delayMillis = delay)) +
            slideInVertically(tween(280, delayMillis = delay)) { it / 3 }
    ) {
        LibraryRow(icon, title, subtitle, onClick)
    }
}

@Composable
private fun LibraryRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvClickable(onClick = onClick, shape = shape)
            .background(Color.White.copy(alpha = 0.07f))
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
    }
}
