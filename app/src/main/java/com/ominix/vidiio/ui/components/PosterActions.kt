package com.ominix.vidiio.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ominix.vidiio.VidiioApplication
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.ui.viewmodel.PosterActionsViewModel
import com.ominix.vidiio.ui.viewmodel.VidiioViewModelFactories

/**
 * What a long press on a poster offers: play it, put it in My List, or open the details page.
 * Without this a grid could only ever do one thing, and playing anything meant a detour through
 * the details screen first.
 *
 * The rows are glass cards with the same focus ring as the posters behind them, so a remote can
 * reach every action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosterActionsSheet(
    movie: Movie,
    onDismiss: () -> Unit,
    onPlay: (Movie) -> Unit,
    onDetails: (Movie) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as VidiioApplication
    val viewModel: PosterActionsViewModel = viewModel(
        key = "poster-actions",
        factory = VidiioViewModelFactories.posterActions(app)
    )
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val inMyList = movie.id in favoriteIds
    val firstAction = remember { FocusRequester() }
    // The sheet leaves focus on its drag handle as it opens, which on a TV means the first
    // OK press does nothing. Hand focus to Play once the sheet has settled.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(350)
        runCatching { firstAction.requestFocus() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Text(
                text = movie.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(10.dp))
            PosterActionRow(
                icon = Icons.Rounded.PlayArrow,
                label = "Play",
                modifier = Modifier.focusRequester(firstAction)
            ) {
                onDismiss()
                onPlay(movie)
            }
            PosterActionRow(
                icon = if (inMyList) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = if (inMyList) "Remove from My List" else "Add to My List"
            ) {
                val added = viewModel.toggle(movie)
                android.widget.Toast.makeText(
                    context,
                    if (added) "Added to My List" else "Removed from My List",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                onDismiss()
            }
            PosterActionRow(Icons.Rounded.Info, "Details") {
                onDismiss()
                onDetails(movie)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PosterActionRow(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GlassCard(onClick = onClick, alpha = 0.06f, modifier = modifier.padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
