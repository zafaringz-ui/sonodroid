package dev.sonora.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sonora.core.SonoraCoreClient
import dev.sonora.core.model.Album
import dev.sonora.core.model.Artist
import dev.sonora.core.model.Playlist
import dev.sonora.core.model.Provider
import dev.sonora.core.model.SearchFilter
import dev.sonora.core.model.Track
import dev.sonora.design.component.Artwork
import dev.sonora.design.component.EmptyState
import dev.sonora.design.component.SectionHeader
import dev.sonora.design.component.TrackRow

@Composable
fun SearchRoute(
    core: SonoraCoreClient,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onOpenAlbum: (Provider, String) -> Unit,
    onOpenPlaylist: (Provider, String) -> Unit,
) {
    val viewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory(core))
    val state by viewModel.state.collectAsStateWithLifecycle()

    SearchScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onSubmit = viewModel::submit,
        onClear = viewModel::clearQuery,
        onFilterChange = viewModel::setFilter,
        onProviderChange = viewModel::setProvider,
        onPlayTracks = onPlayTracks,
        onOpenAlbum = onOpenAlbum,
        onOpenPlaylist = onOpenPlaylist,
    )
}

/** Screen 6: search with provider switch and result filters. */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onFilterChange: (SearchFilter) -> Unit,
    onProviderChange: (Provider) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onOpenAlbum: (Provider, String) -> Unit,
    onOpenPlaylist: (Provider, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item(key = "field") {
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                Text("Search", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Songs, albums, artists, playlists") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = onClear) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    shape = RoundedCornerShape(16.dp),
                )
            }
        }

        item(key = "providers") {
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Provider.entries.forEach { provider ->
                    FilterChip(
                        selected = provider == state.provider,
                        onClick = { onProviderChange(provider) },
                        label = { Text(provider.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        }

        item(key = "filters") {
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = filter == state.filter,
                        onClick = { onFilterChange(filter) },
                        label = { Text(filter.label) },
                    )
                }
            }
        }

        if (state.loading) {
            item(key = "loading") {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }

        if (state.query.isBlank() && state.recent.isNotEmpty()) {
            item(key = "recent-header") { SectionHeader(title = "Recent searches") }
            items(state.recent, key = { "recent-$it" }) { term ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onQueryChange(term) }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(term, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        val results = state.results
        if (state.error != null) {
            item(key = "error") {
                EmptyState(title = "Search failed", body = state.error)
            }
        }

        if (results != null) {
            if (results.isEmpty) {
                item(key = "empty") {
                    EmptyState(
                        title = "No results for “${results.query}”",
                        body = "Try a different spelling, or switch provider.",
                        icon = Icons.Rounded.Search,
                    )
                }
            }
            if (results.tracks.isNotEmpty()) {
                item(key = "tracks-header") { SectionHeader(title = "Songs") }
                items(results.tracks, key = { "track-${it.provider.slug}-${it.id}" }) { track ->
                    TrackRow(
                        track = track,
                        onClick = {
                            onPlayTracks(results.tracks, results.tracks.indexOf(track))
                        },
                    )
                }
            }
            if (results.albums.isNotEmpty()) {
                item(key = "albums-header") { SectionHeader(title = "Albums") }
                items(results.albums, key = { "album-${it.provider.slug}-${it.id}" }) { album ->
                    MediaRow(
                        title = album.title,
                        subtitle = listOfNotNull(album.artist, album.year?.toString()).joinToString(" · "),
                        artworkTitle = album.title,
                        artworkUrl = album.artworkUrl,
                    ) { onOpenAlbum(album.provider, album.id) }
                }
            }
            if (results.artists.isNotEmpty()) {
                item(key = "artists-header") { SectionHeader(title = "Artists") }
                items(results.artists, key = { "artist-${it.provider.slug}-${it.id}" }) { artist ->
                    MediaRow(
                        title = artist.name,
                        subtitle = "Artist",
                        artworkTitle = artist.name,
                        artworkUrl = artist.artworkUrl,
                        circular = true,
                    ) { }
                }
            }
            if (results.playlists.isNotEmpty()) {
                item(key = "playlists-header") { SectionHeader(title = "Playlists") }
                items(results.playlists, key = { "playlist-${it.provider.slug}-${it.id}" }) { playlist ->
                    MediaRow(
                        title = playlist.title,
                        subtitle = "Playlist · ${playlist.trackCount} tracks",
                        artworkTitle = playlist.title,
                        artworkUrl = playlist.artworkUrl,
                    ) { onOpenPlaylist(playlist.provider, playlist.id) }
                }
            }
        }
    }
}

@Composable
private fun MediaRow(
    title: String,
    subtitle: String,
    artworkTitle: String,
    artworkUrl: String?,
    circular: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            title = artworkTitle,
            artworkUrl = artworkUrl,
            modifier = Modifier
                .size(48.dp)
                .clip(if (circular) RoundedCornerShape(50) else RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
