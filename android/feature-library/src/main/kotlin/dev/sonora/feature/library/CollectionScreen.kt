package dev.sonora.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.sonora.core.Media3PlayerClient
import dev.sonora.core.SonoraCoreClient
import dev.sonora.core.model.Provider
import dev.sonora.core.model.Track
import dev.sonora.design.component.Artwork
import dev.sonora.design.component.EmptyState
import dev.sonora.design.component.TrackRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CollectionUiState(
    val kind: Kind = Kind.ALBUM,
    val loading: Boolean = true,
    val title: String? = null,
    val subtitle: String? = null,
    val artworkTitle: String = "",
    val artworkUrl: String? = null,
    val tracks: List<Track> = emptyList(),
    val error: String? = null,
) {
    enum class Kind { ALBUM, PLAYLIST }
}

class CollectionViewModel(
    private val core: SonoraCoreClient,
) : ViewModel() {

    private val _state = MutableStateFlow(CollectionUiState())
    val state = _state.asStateFlow()

    fun loadAlbum(provider: Provider, albumId: String) {
        viewModelScope.launch {
            _state.update { it.copy(kind = CollectionUiState.Kind.ALBUM, loading = true, error = null) }
            runCatching {
                val album = core.album(provider, albumId)
                val tracks = if (album.tracks.isNotEmpty() || core is Media3PlayerClient) {
                    album.tracks
                } else {
                    core.library(provider).tracks.filter { it.albumId == albumId }
                        .ifEmpty { core.library(provider).tracks.take(album.trackCount) }
                }
                Triple(album, tracks, null)
            }.onSuccess { (album, tracks, _) ->
                _state.update {
                    it.copy(
                        loading = false,
                        title = album.title,
                        subtitle = listOfNotNull(album.artist, album.year?.toString())
                            .joinToString(" · "),
                        artworkTitle = album.title,
                        artworkUrl = album.artworkUrl,
                        tracks = tracks,
                    )
                }
            }.onFailure { failure ->
                _state.update {
                    it.copy(loading = false, error = failure.message ?: "Could not load album")
                }
            }
        }
    }

    fun loadPlaylist(provider: Provider, playlistId: String) {
        viewModelScope.launch {
            _state.update { it.copy(kind = CollectionUiState.Kind.PLAYLIST, loading = true, error = null) }
            runCatching { core.playlist(provider, playlistId) }
                .onSuccess { detail ->
                    _state.update {
                        it.copy(
                            loading = false,
                            title = detail.playlist.title,
                            subtitle = detail.playlist.description
                                ?: "${detail.playlist.trackCount} tracks",
                            artworkTitle = detail.playlist.title,
                            artworkUrl = detail.playlist.artworkUrl,
                            tracks = detail.tracks,
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(loading = false, error = failure.message ?: "Could not load playlist")
                    }
                }
        }
    }

    companion object {
        fun factory(core: SonoraCoreClient): ViewModelProvider.Factory = viewModelFactory {
            initializer { CollectionViewModel(core) }
        }
    }
}

@Composable
fun AlbumRoute(
    core: SonoraCoreClient,
    providerSlug: String,
    albumId: String,
    onBack: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
) {
    val viewModel: CollectionViewModel = viewModel(factory = CollectionViewModel.factory(core))
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(providerSlug, albumId) {
        viewModel.loadAlbum(Provider.fromSlug(providerSlug), albumId)
    }

    CollectionScreen(state = state, onBack = onBack, onPlayTracks = onPlayTracks)
}

@Composable
fun PlaylistRoute(
    core: SonoraCoreClient,
    providerSlug: String,
    playlistId: String,
    onBack: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
) {
    val viewModel: CollectionViewModel = viewModel(factory = CollectionViewModel.factory(core))
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(providerSlug, playlistId) {
        viewModel.loadPlaylist(Provider.fromSlug(providerSlug), playlistId)
    }

    CollectionScreen(state = state, onBack = onBack, onPlayTracks = onPlayTracks)
}

/** Screens 8 and 9: album and playlist detail share one layout. */
@Composable
fun CollectionScreen(
    state: CollectionUiState,
    onBack: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.loading) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (state.error != null) {
        Column(modifier = modifier.fillMaxSize()) {
            Row(modifier = Modifier.padding(8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                }
            }
            EmptyState(title = "Could not open this", body = state.error)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item(key = "header") {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Artwork(
                        title = state.artworkTitle,
                        artworkUrl = state.artworkUrl,
                        modifier = Modifier.size(160.dp),
                        cornerRadius = 18.dp,
                    )
                    Spacer(Modifier.size(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.kind == CollectionUiState.Kind.ALBUM) "ALBUM" else "PLAYLIST",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = state.title.orEmpty(),
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = state.subtitle.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onPlayTracks(state.tracks, 0) }) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Play")
                    }
                    OutlinedButton(
                        onClick = {
                            val shuffled = state.tracks.shuffled()
                            onPlayTracks(shuffled, 0)
                        },
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Shuffle")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        items(state.tracks, key = { "${it.provider.slug}-${it.id}" }) { track ->
            TrackRow(
                track = track,
                index = state.tracks.indexOf(track),
                onClick = {
                    onPlayTracks(state.tracks, state.tracks.indexOf(track))
                },
            )
        }
    }
}
