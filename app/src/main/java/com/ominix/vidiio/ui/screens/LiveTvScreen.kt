package com.ominix.vidiio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ominix.vidiio.ui.viewmodel.LiveRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ominix.vidiio.data.live.Game
import com.ominix.vidiio.data.live.GameState
import com.ominix.vidiio.data.live.League
import com.ominix.vidiio.data.live.Team
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.StreamSource
import com.ominix.vidiio.ui.browse.FlixAccent
import com.ominix.vidiio.ui.browse.FlixBackground
import com.ominix.vidiio.ui.browse.FlixHeader
import com.ominix.vidiio.ui.components.tvClickable
import com.ominix.vidiio.ui.viewmodel.LiveTvViewModel

/** Live sports by league (from ESPN, with PenguPlay streams when it has them), then free live channels. */
@Composable
fun LiveTvScreen(
    viewModel: LiveTvViewModel,
    onPlay: (Movie, StreamSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val rows = state.rows
    var selectedCategory by remember { mutableStateOf("All") }
    val liveCategories = listOf("All", "Adult Swim", "Sports", "News", "Movies", "Entertainment", "Kids", "Music", "Documentary")

    val filteredRows = remember(rows, selectedCategory) {
        rows?.filter { row ->
            val title = row.title.lowercase()
            when (selectedCategory) {
                "Adult Swim" -> title.contains("adult swim") || title.contains("toonami") || title.contains("rick and morty")
                "Sports" -> title.contains("sport") || title.contains("game") || title.contains("live now") || row.items.any { it.title.lowercase().contains(" vs ") }
                "News" -> title.contains("news") || title.contains("weather")
                "Movies" -> title.contains("movie") || title.contains("film") || title.contains("cinema")
                "Entertainment" -> title.contains("entertainment") || title.contains("series") || title.contains("comedy")
                "Kids" -> title.contains("kid") || title.contains("animation") || title.contains("cartoon")
                "Music" -> title.contains("music")
                "Documentary" -> title.contains("docu") || title.contains("science")
                else -> true
            }
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    Box(modifier.fillMaxSize().background(FlixBackground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(top = 14.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "title") { FlixHeader("Live TV") }

            item(key = "categories") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(liveCategories) { cat ->
                        val selected = cat == selectedCategory
                        val shape = RoundedCornerShape(20.dp)
                        Box(
                            modifier = Modifier
                                .tvClickable(onClick = { selectedCategory = cat }, shape = shape)
                                .clip(shape)
                                .background(if (selected) FlixAccent else Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(cat, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            if (selectedCategory == "All" || selectedCategory == "Sports") {
                item(key = "sports") {
                    LiveSportsSection(
                        sports = state.sports,
                        selected = state.league,
                        onSelectLeague = viewModel::selectLeague,
                        onOpenGame = viewModel::openGame,
                    )
                }
            }

            if (filteredRows == null) item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = FlixAccent)
                }
            } else {
                if (filteredRows.isEmpty()) item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (state.failed) "Couldn't load live TV. Check the connection and try again." else "No channels found for this category.",
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        TextButton(onClick = viewModel::refresh) { Text("Refresh") }
                    }
                }
                items(filteredRows, key = { "live-${it.channels}-${it.title}" }) { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlixHeader(row.title)
                        if (row.items.isEmpty()) {
                            Text(row.note.orEmpty(), color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 14.dp))
                        } else LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(row.items, key = { "${row.title}-${it.id}" }) { item ->
                                if (row.channels) ChannelCard(item, onClick = { viewModel.open(item) })
                                else LiveEventCard(item, onClick = { viewModel.open(item) })
                            }
                        }
                    }
                }
            }
        }
    }

    val opening = state.opening
    val streams = state.streams
    if (opening != null) {
        LaunchedEffect(streams) {
            if (streams?.size == 1) {
                viewModel.dismiss()
                onPlay(opening, streams.first())
            }
        }
        // One stream plays at once; none keeps the sheet up so the item and its outcome stay visible.
        if (streams == null || streams.size != 1) {
            val shape = RoundedCornerShape(20.dp)
            androidx.compose.ui.window.Dialog(
                onDismissRequest = viewModel::dismiss,
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    shape = shape,
                    color = Color(0xFF18181C),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(0xFFE5242F))
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = opening.title,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = viewModel::dismiss) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.Gray)
                            }
                        }

                        if (streams == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = FlixAccent)
                                    Spacer(Modifier.height(10.dp))
                                    Text("Searching live feeds...", color = Color.Gray, fontSize = 13.sp)
                                }
                            }
                        } else if (streams.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = state.emptyNote ?: "No playable live stream for this one right now.",
                                    color = Color.White.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            Text(
                                text = "Available Live SportzX Feeds (${streams.size})",
                                color = FlixAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.heightIn(max = 320.dp)
                            ) {
                                items(streams) { source ->
                                    val itemShape = RoundedCornerShape(12.dp)
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .tvClickable(
                                                onClick = {
                                                    viewModel.dismiss()
                                                    onPlay(opening, source)
                                                },
                                                shape = itemShape
                                            ),
                                        shape = itemShape,
                                        color = Color.White.copy(alpha = 0.07f),
                                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Text("⚡", fontSize = 16.sp)
                                                Spacer(Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = source.serverName,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    source.quality?.let { q ->
                                                        Text(
                                                            text = "Quality: $q",
                                                            color = FlixAccent,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(FlixAccent.copy(alpha = 0.2f))
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                                Text("WATCH", color = FlixAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A channel: its logo, fitted rather than cropped, over the channel name. */
@Composable
private fun ChannelCard(channel: Movie, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .width(168.dp)
            .tvClickable(onClick = onClick, shape = shape)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
            if (channel.posterUrl.isNotBlank()) {
                AsyncImage(
                    model = channel.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(channel.title.take(2).uppercase(), color = FlixAccent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            channel.title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Leagues as tabs (with how many games are live), and the picked league's games, live first. */
@Composable
private fun LiveSportsSection(
    sports: List<Pair<League, List<Game>>>?,
    selected: League?,
    onSelectLeague: (League) -> Unit,
    onOpenGame: (Game) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlixHeader("Live Sports")
        if (sports == null) {
            Text("Loading games...", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 14.dp))
            return@Column
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sports, key = { "league-${it.first.name}" }) { (league, games) ->
                val live = games.count { it.state == GameState.LIVE }
                val chosen = league == selected
                val shape = RoundedCornerShape(20.dp)
                Row(
                    modifier = Modifier
                        .tvClickable(onClick = { onSelectLeague(league) }, shape = shape)
                        .clip(shape)
                        .background(if (chosen) FlixAccent else Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(league.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (live > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text("$live live", color = if (chosen) Color.White else Color(0xFFFF5252), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        val games = sports.firstOrNull { it.first == selected }?.second.orEmpty()
        if (selected != null && games.isEmpty()) {
            Text("No ${selected.label} games today.", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 14.dp))
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(games, key = { "game-${it.league.name}-${it.id}" }) { game -> GameCard(game, onClick = { onOpenGame(game) }) }
            }
        }
    }
}

/** One game: both teams with logos and scores, its clock or start time, and the network. */
@Composable
private fun GameCard(game: Game, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .width(240.dp)
            .tvClickable(onClick = onClick, shape = shape)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (label, color) = when (game.state) {
                GameState.LIVE -> "LIVE" to Color(0xFFE5242F)
                GameState.UPCOMING -> "UPCOMING" to Color.White.copy(alpha = 0.18f)
                GameState.FINAL -> "FINAL" to Color.White.copy(alpha = 0.10f)
            }
            Text(
                label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(color).padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(game.detail, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TeamLine(game.away, showScore = game.state != GameState.UPCOMING)
        TeamLine(game.home, showScore = game.state != GameState.UPCOMING)
        if (game.networks.isNotEmpty()) {
            Text(game.networks.joinToString(" / "), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TeamLine(team: Team, showScore: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (team.logo != null) AsyncImage(model = team.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(8.dp))
        Text(team.name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (showScore && team.score != null) {
            Text(team.score, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** A rectangular event card showing full details (title, backdrop/poster, synopsis). */
@Composable
private fun LiveEventCard(item: Movie, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .width(230.dp)
            .tvClickable(onClick = onClick, shape = shape)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val imageUrl = item.backdropUrl.takeIf { !it.isNullOrBlank() } ?: item.posterUrl.takeIf { !it.isBlank() }
        if (imageUrl != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(105.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "LIVE",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFE5242F))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Text(
            text = item.title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        if (!item.synopsis.isNullOrBlank()) {
            Text(
                text = item.synopsis,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
