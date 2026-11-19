package dev.sonora.design.component

import androidx.compose.animation.core.RepeatMode as AnimationRepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sonora.core.model.Track
import dev.sonora.design.util.formatDuration

/**
 * A track in a list.
 *
 * Used by Home shelves, album/playlist detail, the library and the queue. The
 * overflow menu holds the actions the desktop app puts in its right-click menu;
 * callbacks left null are hidden, so the queue can reuse the row without "go to
 * album" noise.
 */
@Composable
fun TrackRow(
    track: Track,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    index: Int? = null,
    onClick: () -> Unit = {},
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onGoToAlbum: (() -> Unit)? = null,
    onGoToArtist: (() -> Unit)? = null,
    onSongInfo: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics {
                contentDescription =
                    "${track.title} by ${track.artistLine}, ${formatDuration(track.durationMs)}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isPlaying) {
            PlayingBars(Modifier.size(width = 18.dp, height = 16.dp))
        } else if (index != null) {
            Text(
                text = (index + 1).toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Artwork(
            title = track.title,
            artworkUrl = track.artworkUrl,
            modifier = Modifier.size(44.dp),
            cornerRadius = 8.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isPlaying) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (track.isExplicit) {
                    Spacer(Modifier.width(6.dp))
                    ExplicitBadge()
                }
            }
            Text(
                text = listOfNotNull(track.artistLine, track.qualityLabel).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = formatDuration(track.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onPlayNext != null || onAddToQueue != null || onSongInfo != null) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (onPlayNext != null) {
                        DropdownMenuItem(
                            text = { Text("Play next") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Add, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                onPlayNext()
                            },
                        )
                    }
                    if (onAddToQueue != null) {
                        DropdownMenuItem(
                            text = { Text("Add to queue") },
                            leadingIcon = {
                                Icon(Icons.Rounded.PlaylistAdd, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                onAddToQueue()
                            },
                        )
                    }
                    if (onGoToAlbum != null && track.album != null) {
                        DropdownMenuItem(
                            text = { Text("Go to album") },
                            leadingIcon = { Icon(Icons.Rounded.Album, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onGoToAlbum()
                            },
                        )
                    }
                    if (onGoToArtist != null) {
                        DropdownMenuItem(
                            text = { Text("Go to artist") },
                            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onGoToArtist()
                            },
                        )
                    }
                    if (onSongInfo != null) {
                        DropdownMenuItem(
                            text = { Text("Song info") },
                            leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onSongInfo()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExplicitBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = "E",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.surface,
        )
    }
}

/** Three animated bars shown on the currently playing row. */
@Composable
fun PlayingBars(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "playing-bars")
    val heights = listOf(200, 420, 300).mapIndexed { index, duration ->
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = duration, easing = FastOutSlowInEasing),
                repeatMode = AnimationRepeatMode.Reverse,
            ),
            label = "bar-$index",
        ).value
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        heights.forEach { scale ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        scaleY = scale
                        alpha = 0.9f
                    }
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
            )
        }
    }
}
