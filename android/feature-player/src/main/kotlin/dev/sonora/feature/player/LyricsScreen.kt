package dev.sonora.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sonora.core.SonoraCoreClient
import dev.sonora.design.component.EmptyState
import dev.sonora.design.theme.SonoraTheme

@Composable
fun LyricsRoute(
    core: SonoraCoreClient,
    onBack: () -> Unit,
) {
    val viewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.factory(core))
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.playback.track?.id) {
        viewModel.ensureLyrics()
    }

    LyricsScreen(
        state = state,
        onBack = onBack,
        onSeekToLine = { positionMs -> viewModel.seekTo(positionMs) },
    )
}

/** Screen 17: synced lyrics, tap a line to seek there. */
@Composable
fun LyricsScreen(
    state: PlayerUiState,
    onBack: () -> Unit,
    onSeekToLine: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SonoraTheme.colors
    val lyrics = state.lyrics
    val activeIndex = if (lyrics == null) -1 else lyrics.activeLineIndex(state.playback.positionMs)
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            listState.animateScrollToItem(activeIndex.coerceAtLeast(0))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.nowPlayingBottom),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.Close, contentDescription = "Close lyrics")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.playback.track?.title ?: "Lyrics",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = state.playback.track?.artistLine.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (lyrics != null) {
                Text(
                    text = if (lyrics.synced) "SYNCED" else "UNSYNCED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when {
            state.lyricsLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            lyrics == null || lyrics.lines.none { it.text.isNotBlank() } -> EmptyState(
                title = "No lyrics yet",
                body = "Sonora asks the provider first, then LRCLIB, Musixmatch, NetEase and KuGou.",
                icon = Icons.Rounded.Lyrics,
            )

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 24.dp,
                    end = 24.dp,
                    top = 24.dp,
                    bottom = 96.dp,
                ),
            ) {
                itemsIndexed(lyrics.lines) { index, line ->
                    val isActive = index == activeIndex
                    Text(
                        text = line.text.ifBlank { "♪" },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        },
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = line.text.isNotBlank()) {
                                onSeekToLine(line.timeMs)
                            }
                            .padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}
