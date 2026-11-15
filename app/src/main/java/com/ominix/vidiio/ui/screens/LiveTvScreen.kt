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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.ominix.vidiio.ui.browse.FlixPoster
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
            item(key = "sports") {
                LiveSportsSection(
                    sports = state.sports,
                    selected = state.league,
                    onSelectLeague = viewModel::selectLeague,
                    onOpenGame = viewModel::openGame,
                )
            }
            if (rows == null) item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = FlixAccent)
                }
            } else {
                if (rows.isEmpty()) item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (state.failed) "Couldn't load live TV. Check the connection and try again." else "Nothing is live right now.",
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        TextButton(onClick = viewModel::refresh) { Text("Refresh") }
                    }
                }
                items(rows, key = { "live-${it.channels}-${it.title}" }) { row ->
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
                                else FlixPoster(item, onClick = { viewModel.open(item) }, width = 132.dp)
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
        // One stream: play it straight away. Several: let the viewer pick.
        LaunchedEffect(streams) {
            if (streams?.size == 1) {
                viewModel.dismiss()
                onPlay(opening, streams.first())
            }
        }
        if (streams == null || streams.size > 1) {
            AlertDialog(
                onDismissRequest = viewModel::dismiss,
                title = { Text(opening.title) },
                text = {
                    if (streams == null) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = FlixAccent)
                        }
                    } else {
                        LazyColumn {
                            items(streams) { source ->
                                TextButton(
                                    onClick = {
                                        viewModel.dismiss()
                                        onPlay(opening, source)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(listOfNotNull(source.serverName, source.quality).joinToString(" · "), modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = viewModel::dismiss) { Text("Cancel") } }
            )
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
