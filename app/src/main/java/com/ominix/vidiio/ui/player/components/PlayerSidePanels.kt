package com.ominix.vidiio.ui.player.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ominix.vidiio.ui.components.tvClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ominix.vidiio.data.model.Episode
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.StreamSource
import com.ominix.vidiio.data.repository.SourceHealth
import com.ominix.vidiio.ui.components.SourceHealthBadge
import com.ominix.vidiio.ui.tv.TvDialogFocusEffect
import kotlin.math.roundToInt

/**
 * Episode picker for the player: a slim panel with a row of season chips and one compact line per
 * episode (number, title, a marker on the one playing). No thumbnails or synopses - those are on the
 * Details page; here the job is to get to another episode quickly.
 */
@Composable
fun EpisodeSidebar(
    movie: Movie,
    currentEpisode: Episode?,
    onEpisodeSelect: (Episode) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val seasons = movie.seasons.filter { it.episodes.isNotEmpty() }.sortedBy { it.seasonNumber }
    var selectedSeason by remember(movie.id, currentEpisode?.seasonNumber) {
        mutableIntStateOf(
            seasons.firstOrNull { it.seasonNumber == currentEpisode?.seasonNumber }?.seasonNumber
                ?: seasons.firstOrNull()?.seasonNumber ?: 0
        )
    }
    val episodes = seasons.firstOrNull { it.seasonNumber == selectedSeason }?.episodes.orEmpty()
        .sortedBy { it.episodeNumber }
    // Put the remote on the episode that is playing as soon as the panel opens, so it is never left
    // on nothing. Once per season and driven by the row being laid out: a row that is scrolled out
    // and back in is composed again, and must not pull focus away from what the viewer is browsing.
    val playingFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    var playingPlaced by remember(selectedSeason) { mutableStateOf(false) }
    var playingFocused by remember(selectedSeason) { mutableStateOf(false) }
    TvDialogFocusEffect(playingPlaced, playingFocus) { playingFocused }

    // Arrives the same way the picker panels do, so every player menu reads as one kind of thing.
    val slide = androidx.compose.runtime.remember { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        slide.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow))
    }
    // Measured width, so the panel can be placed off screen and travel with the animation.
    var panelWidth by remember { mutableIntStateOf(0) }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp)
            .onSizeChanged { panelWidth = it.width }
            // Offset during layout, not on the layer: a layer would move the pixels while leaving
            // the touch target at the final position, so the arriving panel ate taps it had not
            // reached yet.
            .offset { IntOffset((panelWidth * (1f - slide.value)).roundToInt(), 0) },
        // Solid and dark with explicit light text: the theme surface can be light, and the video shows through a translucent one.
        color = Color(0xFF14161B),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Episodes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                }
            }

            if (seasons.size > 1) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(seasons, key = { it.seasonNumber }) { season ->
                        val chosen = season.seasonNumber == selectedSeason
                        Text(
                            text = season.name ?: "S${season.seasonNumber}",
                            fontSize = 12.sp,
                            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier
                                .tvClickable(onClick = { selectedSeason = season.seasonNumber }, shape = RoundedCornerShape(50))
                                .background(if (chosen) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Keyed by position too: an addon's episode list can repeat an id.
                itemsIndexed(episodes, key = { i, e -> "${e.id}#$i" }) { _, episode ->
                    EpisodeItem(
                        episode = episode,
                        isSelected = episode.id == currentEpisode?.id,
                        onClick = { onEpisodeSelect(episode) },
                        modifier = if (episode.id == currentEpisode?.id) {
                            Modifier
                                .focusRequester(playingFocus)
                                .onPlaced { playingPlaced = true }
                                .onFocusChanged { playingFocused = it.isFocused }
                        } else Modifier
                    )
                }
            }
        }
    }
}

/** One compact line: number, title, and a marker when it is the episode playing. */
@Composable
fun EpisodeItem(
    episode: Episode,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .tvClickable(onClick = onClick, shape = RoundedCornerShape(8.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Text(
            text = "${episode.episodeNumber}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
            modifier = Modifier.width(26.dp)
        )
        Text(
            text = episode.name,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Playing", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesPanel(
    sources: List<StreamSource>,
    selectedSource: StreamSource?,
    sourceHealth: Map<String, SourceHealth>,
    unavailableCount: Int,
    showUnavailable: Boolean,
    onShowUnavailableChange: (Boolean) -> Unit,
    onSourceSelect: (StreamSource) -> Unit,
    onDismiss: () -> Unit
) {
    PlayerSidePanel(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Switch Source", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (unavailableCount > 0) {
                TextButton(onClick = { onShowUnavailableChange(!showUnavailable) }) {
                    Text(if (showUnavailable) "Hide not working" else "Show not working ($unavailableCount)")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Take the height that is left: in landscape the panel is short, and the list has to
            // scroll inside it instead of running past the bottom edge.
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(sources) { source ->
                    ListItem(
                        headlineContent = { Text(source.serverName) },
                        supportingContent = { 
                            val info = mutableListOf<String>()
                            source.quality?.let { info.add(it) }
                            source.size?.let { info.add(it) }
                            source.seeders?.let { info.add("👥 $it") }
                            Column {
                                Text("${source.sourceName} • ${info.joinToString(" • ")}")
                                SourceHealthBadge(health = sourceHealth[source.url])
                            }
                        },
                        leadingContent = { RadioButton(selected = source == selectedSource, onClick = null) },
                        modifier = Modifier.tvClickable(onClick = { onSourceSelect(source); onDismiss() }, shape = RoundedCornerShape(8.dp))
                    )
                }
            }
        }
    }
}
