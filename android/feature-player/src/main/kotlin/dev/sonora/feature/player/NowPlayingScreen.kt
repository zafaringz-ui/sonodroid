package dev.sonora.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sonora.core.model.PlaybackState
import dev.sonora.design.component.Artwork
import dev.sonora.design.component.EmptyState
import dev.sonora.design.component.SeekBar
import dev.sonora.design.component.TransportControls
import dev.sonora.design.theme.SonoraTheme
import androidx.compose.material.icons.rounded.MusicNote

/**
 * Screen 16 in the plan: full-screen Now Playing.
 *
 * Hosted inside a `ModalBottomSheet` by the app shell so the swipe-up gesture and
 * the mini player share a single motion language.
 */
@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    val playback = state.playback
    val track = playback.track
    val colors = SonoraTheme.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(colors.nowPlayingTop, colors.nowPlayingBottom),
                ),
            ),
    ) {
        if (track == null) {
            EmptyState(
                title = "Nothing playing",
                body = "Pick something from Home, Search or your library.",
                icon = Icons.Rounded.MusicNote,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Close now playing",
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PLAYING FROM",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = track.album ?: track.artistLine,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onOpenQueue) {
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = "Queue",
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Artwork(
                title = track.title,
                artworkUrl = track.artworkUrl,
                cornerRadius = 24.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp)),
            )

            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artistLine,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (track.provider == dev.sonora.core.model.Provider.DEEZER) {
                        Text(
                            text = "30 s preview",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                LikeButton()
            }

            Spacer(Modifier.height(8.dp))

            val playbackError = playback.error
            if (playbackError != null) {
                Text(
                    text = playbackError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!track.previewUrl.isNullOrBlank()) {
                    TextButton(onClick = onRetry) { Text("Try again") }
                }
            }

            SeekBar(
                positionMs = playback.positionMs,
                durationMs = playback.durationMs,
                bufferedMs = playback.bufferedMs,
                onSeek = onSeek,
            )

            Spacer(Modifier.height(8.dp))

            TransportControls(
                state = playback,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onRepeat = onToggleRepeat,
                onShuffle = onToggleShuffle,
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onOpenLyrics) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = "Lyrics",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Devices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = playback.outputName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = track.qualityLabel ?: "Auto",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun LikeButton() {
    var liked by remember { mutableStateOf(false) }
    IconButton(onClick = { liked = !liked }) {
        Icon(
            imageVector = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (liked) "Remove from favourites" else "Add to favourites",
            tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Convenience overload for callers that only track the playback snapshot. */
@Composable
fun NowPlayingScreen(
    playback: PlaybackState,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NowPlayingScreen(
        state = PlayerUiState(playback = playback),
        onClose = onClose,
        onPlayPause = onPlayPause,
        onNext = onNext,
        onPrevious = onPrevious,
        onSeek = onSeek,
        onToggleRepeat = onToggleRepeat,
        onToggleShuffle = onToggleShuffle,
        onOpenQueue = onOpenQueue,
        onOpenLyrics = onOpenLyrics,
        modifier = modifier,
    )
}
