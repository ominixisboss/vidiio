package com.ominix.vidiio.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ominix.vidiio.data.guide.GuideEntry
import com.ominix.vidiio.ui.components.tvClickable
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class EpgChannelData(
    val channelName: String,
    val channelLogo: String? = null,
    val entries: List<GuideEntry> = emptyList()
)

@Composable
fun EpgGridComponent(
    channels: List<EpgChannelData>,
    onSelectEntry: (GuideEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val timeFormat = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }

    val now = remember { LocalTime.now() }
    val currentHour = now.hour
    val timeSlots = remember(currentHour) {
        (0..6).map { i ->
            LocalTime.of((currentHour + i) % 24, 0)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF12141A))
    ) {
        // --- Time slots header ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E222A))
                .padding(vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .padding(start = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "CHANNELS",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Row(
                modifier = Modifier
                    .horizontalScroll(scrollState)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                timeSlots.forEach { timeSlot ->
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .background(
                                if (timeSlot.hour == currentHour) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(vertical = 6.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = timeSlot.format(timeFormat),
                            color = if (timeSlot.hour == currentHour) MaterialTheme.colorScheme.primary else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(1.dp).fillMaxWidth().background(Color.White.copy(alpha = 0.1f)))

        // --- Channels & Program Cards List ---
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            items(channels, key = { it.channelName }) { channel ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Channel Logo & Title
                    Surface(
                        modifier = Modifier
                            .width(140.dp)
                            .padding(start = 12.dp, end = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.08f)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!channel.channelLogo.isNullOrBlank()) {
                                AsyncImage(
                                    model = channel.channelLogo,
                                    contentDescription = channel.channelName,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(
                                    Icons.Rounded.Tv,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = channel.channelName,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Right Program Cards
                    Row(
                        modifier = Modifier
                            .horizontalScroll(scrollState)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (channel.entries.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .width(180.dp)
                                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Text("Live Stream Feed", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                            }
                        } else {
                            channel.entries.forEach { entry ->
                                val isAiringNow = entry.time != null && entry.time.hour == currentHour
                                val shape = RoundedCornerShape(10.dp)
                                var isFocused by remember { mutableStateOf(false) }

                                Surface(
                                    modifier = Modifier
                                        .width(180.dp)
                                        .onFocusChanged { isFocused = it.isFocused }
                                        .tvClickable(onClick = { onSelectEntry(entry) }, shape = shape),
                                    shape = shape,
                                    color = if (isFocused) Color.White
                                    else if (isAiringNow) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    else Color.White.copy(alpha = 0.06f),
                                    border = BorderStroke(
                                        0.5.dp,
                                        if (isFocused) Color.White else if (isAiringNow) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = entry.showName,
                                                color = if (isFocused) Color.Black else Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isAiringNow) {
                                                Icon(
                                                    Icons.Rounded.PlayArrow,
                                                    contentDescription = "Live",
                                                    tint = if (isFocused) Color.Black else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        entry.episodeTitle?.takeIf { it.isNotBlank() }?.let { epTitle ->
                                            Text(
                                                text = epTitle,
                                                color = if (isFocused) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.6f),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        entry.episodeCode?.let { code ->
                                            Text(
                                                text = code,
                                                color = if (isFocused) Color.Black else MaterialTheme.colorScheme.primary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
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
