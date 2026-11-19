package dev.sonora.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sonora.core.SonoraCoreClient
import dev.sonora.core.model.Track
import dev.sonora.design.component.Artwork
import dev.sonora.design.component.EmptyState
import dev.sonora.design.util.formatDuration

@Composable
fun QueueRoute(
    core: SonoraCoreClient,
    onBack: () -> Unit,
) {
    val viewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.factory(core))
    val state by viewModel.state.collectAsStateWithLifecycle()

    QueueScreen(
        state = state,
        onBack = onBack,
        onPlayIndex = viewModel::playQueueIndex,
        onMove = viewModel::moveInQueue,
        onRemove = viewModel::removeFromQueue,
    )
}

/**
 * Screen 18. Drag-to-reorder is Phase 5 work (it needs a reorderable lazy list);
 * explicit up/down buttons keep the queue fully usable, and are more accessible
 * with TalkBack than a drag gesture.
 */
@Composable
fun QueueScreen(
    state: PlayerUiState,
    onBack: () -> Unit,
    onPlayIndex: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.Close, contentDescription = "Close queue")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Queue", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = state.playback.track?.let { "Playing from ${it.album ?: it.artistLine}" }
                        ?: "Nothing playing",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${state.queue.size} tracks",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.queue.isEmpty()) {
            EmptyState(
                title = "The queue is empty",
                body = "Play an album or playlist and it will show up here.",
                icon = Icons.Rounded.QueueMusic,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(state.queue, key = { index, track -> "$index-${track.id}" }) { index, track ->
                    QueueRow(
                        track = track,
                        isCurrent = index == state.playback.queueIndex,
                        canMoveUp = index > 0,
                        canMoveDown = index < state.queue.lastIndex,
                        onPlay = { onPlayIndex(index) },
                        onMoveUp = { onMove(index, index - 1) },
                        onMoveDown = { onMove(index, index + 1) },
                        onRemove = { onRemove(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    track: Track,
    isCurrent: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPlay: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isCurrent) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            title = track.title,
            artworkUrl = track.artworkUrl,
            modifier = Modifier.size(40.dp),
            cornerRadius = 8.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = track.artistLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatDuration(track.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(modifier = Modifier.width(0.dp))
        IconButton(onClick = onPlay, enabled = !isCurrent, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (isCurrent) Icons.Rounded.QueueMusic else Icons.Rounded.PlayArrow,
                contentDescription = if (isCurrent) "Currently playing" else "Play ${track.title}",
                tint = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Rounded.ArrowUpward,
                contentDescription = "Move up",
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Rounded.ArrowDownward,
                contentDescription = "Move down",
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = "Remove from queue",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
