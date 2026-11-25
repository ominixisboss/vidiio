package com.ominix.vidiio.ui.player.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ominix.vidiio.ui.components.tvClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Below this width the transport needs two button rows: one row pushes the last controls off-screen. */
private const val COMPACT_WIDTH_DP = 600

@Composable
fun PlayerTransport(
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    bufferedPosition: Long,
    isLive: Boolean = false,
    isSubtitlesActive: Boolean,
    isAudioActive: Boolean,
    isSpeedActive: Boolean,
    isAspectActive: Boolean,
    isFullscreen: Boolean,
    playbackSpeed: Float,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onToggleSubtitles: () -> Unit,
    onToggleAudio: () -> Unit,
    onToggleSpeed: () -> Unit,
    onToggleAspect: () -> Unit,
    onToggleSources: (() -> Unit)? = null,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    // A phone has no room for the time label beside nine controls at the same time.
    val compact = LocalConfiguration.current.screenWidthDp < COMPACT_WIDTH_DP

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.85f)
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .navigationBarsPadding()
    ) {
        // Seek Bar - drag locally, commit on release so polling can't yank the thumb back.
        var isScrubbing by remember { mutableStateOf(false) }
        var scrubValue by remember { mutableFloatStateOf(0f) }
        var settleUntil by remember { mutableLongStateOf(0L) }
        var isSliderFocused by remember { mutableStateOf(false) }
        val focusManager = LocalFocusManager.current

        val playbackFraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
        val bufferedFraction = if (duration > 0) (bufferedPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
        val sliderValue = when {
            isScrubbing -> scrubValue
            System.currentTimeMillis() < settleUntil -> scrubValue
            else -> playbackFraction
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .onFocusChanged { isSliderFocused = it.isFocused }
                .then(
                    if (isSliderFocused) {
                        Modifier
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    } else Modifier
                )
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && isSliderFocused) {
                        val stepFraction = if (duration > 0) 10_000f / duration else 0.01f
                        when (event.key) {
                            Key.DirectionLeft -> {
                                val newFraction = (sliderValue - stepFraction).coerceIn(0f, 1f)
                                scrubValue = newFraction
                                isScrubbing = true
                                onSeek((newFraction * duration).toLong())
                                isScrubbing = false
                                settleUntil = System.currentTimeMillis() + 700
                                true
                            }
                            Key.DirectionRight -> {
                                val newFraction = (sliderValue + stepFraction).coerceIn(0f, 1f)
                                scrubValue = newFraction
                                isScrubbing = true
                                onSeek((newFraction * duration).toLong())
                                isScrubbing = false
                                settleUntil = System.currentTimeMillis() + 700
                                true
                            }
                            // Up/Down must leave the bar. Material's Slider reads them as "change the
                            // value", so Up used to nudge playback instead of reaching the top bar.
                            Key.DirectionUp -> { focusManager.moveFocus(FocusDirection.Up); true }
                            Key.DirectionDown -> { focusManager.moveFocus(FocusDirection.Down); true }
                            else -> false
                        }
                    } else false
                }
        ) {
            // Buffered range behind the active track.
            LinearProgressIndicator(
                progress = { bufferedFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(horizontal = 10.dp)
                    .height(4.dp),
                color = Color.White.copy(alpha = 0.35f),
                trackColor = Color.White.copy(alpha = 0.15f),
            )
            Slider(
                value = sliderValue,
                onValueChange = {
                    isScrubbing = true
                    scrubValue = it
                },
                onValueChangeFinished = {
                    // Only commit a seek the user actually made. The Slider also fires this on the
                    // key-up of any D-pad press once it has focus - including the Up that moved focus
                    // onto it - and with nothing dragged, scrubValue is 0: that jumped playback to 00:00.
                    if (isScrubbing) {
                        onSeek((scrubValue * duration).toLong())
                        isScrubbing = false
                        settleUntil = System.currentTimeMillis() + 700
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.Transparent
                )
            )
        }

        val timeLabel: @Composable () -> Unit = {
            // A live feed has no end time: "00:00 / 00:00" reads like a broken video.
            Text(
                text = if (isLive) "LIVE" else "${formatTime((sliderValue * duration).toLong())} / ${formatTime(duration)}",
                color = if (isLive) Color(0xFFE5242F) else Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isLive) FontWeight.Bold else FontWeight.Medium
            )
        }

        if (compact) {
            // Time on its own line, then transport, then the menus: everything stays on screen.
            timeLabel()
            PrimaryTransport(isPlaying, onPlayPause, onRewind, onForward)
            SecondaryTransport(
                isSubtitlesActive = isSubtitlesActive,
                isAudioActive = isAudioActive,
                isSpeedActive = isSpeedActive,
                isAspectActive = isAspectActive,
                isFullscreen = isFullscreen,
                playbackSpeed = playbackSpeed,
                onToggleSubtitles = onToggleSubtitles,
                onToggleAudio = onToggleAudio,
                onToggleSpeed = onToggleSpeed,
                onToggleSources = onToggleSources,
                onToggleAspect = onToggleAspect,
                onToggleFullscreen = onToggleFullscreen
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                timeLabel()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    PrimaryTransport(isPlaying, onPlayPause, onRewind, onForward)

                    Spacer(Modifier.width(8.dp))

                    SecondaryTransport(
                        isSubtitlesActive = isSubtitlesActive,
                        isAudioActive = isAudioActive,
                        isSpeedActive = isSpeedActive,
                        isAspectActive = isAspectActive,
                        isFullscreen = isFullscreen,
                        playbackSpeed = playbackSpeed,
                        onToggleSubtitles = onToggleSubtitles,
                        onToggleAudio = onToggleAudio,
                        onToggleSpeed = onToggleSpeed,
                        onToggleSources = onToggleSources,
                        onToggleAspect = onToggleAspect,
                        onToggleFullscreen = onToggleFullscreen
                    )
                }
            }
        }
    }
}

/** Rewind, play/pause, forward. Play/pause takes focus when the chrome appears. */
@Composable
private fun PrimaryTransport(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit
) {
    val playPauseFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { playPauseFocusRequester.requestFocus() }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        TransportIconButton(Icons.Rounded.Replay10, "Rewind", onClick = onRewind)

        Box(
            modifier = Modifier
                .focusRequester(playPauseFocusRequester)
                .tvClickable(
                    onClick = onPlayPause,
                    shape = CircleShape,
                    focusScale = 1.12f
                ),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(56.dp).focusProperties { canFocus = false },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        TransportIconButton(Icons.Rounded.Forward10, "Forward", onClick = onForward)
    }
}

/** The menu buttons: subtitles, audio, speed, sources, aspect, fullscreen. */
@Composable
private fun SecondaryTransport(
    isSubtitlesActive: Boolean,
    isAudioActive: Boolean,
    isSpeedActive: Boolean,
    isAspectActive: Boolean,
    isFullscreen: Boolean,
    playbackSpeed: Float,
    onToggleSubtitles: () -> Unit,
    onToggleAudio: () -> Unit,
    onToggleSpeed: () -> Unit,
    onToggleSources: (() -> Unit)?,
    onToggleAspect: () -> Unit,
    onToggleFullscreen: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        TransportToggleIcon(
            icon = Icons.Rounded.Subtitles,
            isActive = isSubtitlesActive,
            onClick = onToggleSubtitles
        )
        TransportToggleIcon(
            icon = Icons.Rounded.Audiotrack,
            isActive = isAudioActive,
            onClick = onToggleAudio
        )

        Box(
            modifier = Modifier.tvClickable(
                onClick = onToggleSpeed,
                shape = CircleShape,
                focusScale = 1.12f
            )
        ) {
            TextButton(onClick = onToggleSpeed, modifier = Modifier.focusProperties { canFocus = false }) {
                Text(
                    text = "${playbackSpeed}x",
                    color = if (isSpeedActive) MaterialTheme.colorScheme.primary else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        if (onToggleSources != null) {
            TransportToggleIcon(
                icon = Icons.Rounded.SwapHoriz,
                isActive = false,
                onClick = onToggleSources
            )
        }

        TransportToggleIcon(
            icon = Icons.Rounded.AspectRatio,
            isActive = isAspectActive,
            onClick = onToggleAspect
        )

        TransportIconButton(
            if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
            "Fullscreen",
            onClick = onToggleFullscreen
        )
    }
}

@Composable
private fun TransportIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.tvClickable(
            onClick = onClick,
            shape = CircleShape,
            focusScale = 1.15f
        )
    ) {
        IconButton(onClick = onClick, modifier = Modifier.focusProperties { canFocus = false }) {
            Icon(icon, contentDescription = contentDescription, tint = Color.White)
        }
    }
}

@Composable
private fun TransportToggleIcon(
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.tvClickable(
            onClick = onClick,
            shape = CircleShape,
            focusScale = 1.15f
        )
    ) {
        IconButton(onClick = onClick, modifier = Modifier.focusProperties { canFocus = false }) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary else Color.White
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
