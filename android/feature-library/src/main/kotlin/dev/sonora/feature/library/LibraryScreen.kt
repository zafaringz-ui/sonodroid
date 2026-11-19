package dev.sonora.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.sonora.core.model.Album
import dev.sonora.core.model.Artist
import dev.sonora.core.model.DownloadItem
import dev.sonora.core.model.Playlist
import dev.sonora.core.model.Provider
import dev.sonora.core.model.Track
import dev.sonora.design.component.Artwork
import dev.sonora.design.component.DemoDataBadge
import dev.sonora.design.component.EmptyState
import dev.sonora.design.component.MediaCard
import dev.sonora.design.component.TrackRow
import dev.sonora.design.util.formatBytes

@Composable
fun LibraryRoute(
    core: SonoraCoreClient,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onOpenAlbum: (Provider, String) -> Unit,
    onOpenPlaylist: (Provider, String) -> Unit,
) {
    val viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(core))
    val state by viewModel.state.collectAsStateWithLifecycle()

    LibraryScreen(
        state = state,
        onSelectProvider = viewModel::selectProvider,
        onSelectTab = viewModel::selectTab,
        onToggleLayout = viewModel::toggleLayout,
        onPlayTracks = onPlayTracks,
        onOpenAlbum = onOpenAlbum,
        onOpenPlaylist = onOpenPlaylist,
    )
}

/** Screen 7: the library with tabs, layout toggle and downloads. */
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onSelectProvider: (Provider) -> Unit,
    onSelectTab: (LibraryTab) -> Unit,
    onToggleLayout: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onOpenAlbum: (Provider, String) -> Unit,
    onOpenPlaylist: (Provider, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot
    val tabs = LibraryTab.entries

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Your library", style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = state.provider.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.isDemo) DemoDataBadge(modifier = Modifier.padding(end = 4.dp))
            IconButton(onClick = onToggleLayout) {
                Icon(
                    imageVector = if (state.grid) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                    contentDescription = if (state.grid) "List layout" else "Grid layout",
                )
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Provider.entries.forEach { provider ->
                FilterChip(
                    selected = provider == state.provider,
                    onClick = { onSelectProvider(provider) },
                    label = { Text(provider.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }

        PrimaryScrollableTabRow(
            selectedTabIndex = tabs.indexOf(state.tab),
            edgePadding = 12.dp,
            divider = {},
        ) {
            tabs.forEach { tab ->
                Tab(
                    selected = tab == state.tab,
                    onClick = { onSelectTab(tab) },
                    text = { Text(tab.label, style = MaterialTheme.typography.labelLarge) },
                )
            }
        }

        when {
            state.error != null && snapshot == null -> EmptyState(
                title = "Library unavailable",
                body = state.error,
                icon = Icons.Rounded.LibraryMusic,
            )

            snapshot == null -> Box(modifier = Modifier.fillMaxSize()) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                )
            }

            else -> LibraryContent(
                state = state,
                onPlayTracks = onPlayTracks,
                onOpenAlbum = onOpenAlbum,
                onOpenPlaylist = onOpenPlaylist,
            )
        }
    }
}

@Composable
private fun LibraryContent(
    state: LibraryUiState,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onOpenAlbum: (Provider, String) -> Unit,
    onOpenPlaylist: (Provider, String) -> Unit,
) {
    val snapshot = state.snapshot ?: return

    when (state.tab) {
        LibraryTab.PLAYLISTS -> {
            if (snapshot.playlists.isEmpty()) {
                EmptyState(title = "No playlists yet", icon = Icons.Rounded.LibraryMusic)
            } else if (state.grid) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    contentPadding = PaddingValues(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(snapshot.playlists, key = { it.id }) { playlist ->
                        MediaCard(
                            title = playlist.title,
                            subtitle = "${playlist.trackCount} tracks",
                            artworkUrl = playlist.artworkUrl,
                            width = 150,
                            onClick = { onOpenPlaylist(playlist.provider, playlist.id) },
                        )
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                    items(snapshot.playlists, key = { it.id }) { playlist ->
                        CollectionRow(
                            title = playlist.title,
                            subtitle = "${playlist.trackCount} tracks",
                            artworkTitle = playlist.title,
                            artworkUrl = playlist.artworkUrl,
                            onClick = { onOpenPlaylist(playlist.provider, playlist.id) },
                        )
                    }
                }
            }
        }

        LibraryTab.ALBUMS -> {
            if (snapshot.albums.isEmpty()) {
                EmptyState(title = "No albums yet", icon = Icons.Rounded.LibraryMusic)
            } else if (state.grid) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    contentPadding = PaddingValues(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(snapshot.albums, key = { it.id }) { album ->
                        MediaCard(
                            title = album.title,
                            subtitle = album.artist,
                            artworkUrl = album.artworkUrl,
                            width = 150,
                            onClick = { onOpenAlbum(album.provider, album.id) },
                        )
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                    items(snapshot.albums, key = { it.id }) { album ->
                        CollectionRow(
                            title = album.title,
                            subtitle = listOfNotNull(album.artist, album.year?.toString())
                                .joinToString(" · "),
                            artworkTitle = album.title,
                            artworkUrl = album.artworkUrl,
                            onClick = { onOpenAlbum(album.provider, album.id) },
                        )
                    }
                }
            }
        }

        LibraryTab.ARTISTS -> {
            if (snapshot.artists.isEmpty()) {
                EmptyState(title = "No artists yet", icon = Icons.Rounded.LibraryMusic)
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                    items(snapshot.artists, key = { it.id }) { artist ->
                        CollectionRow(
                            title = artist.name,
                            subtitle = "Artist",
                            artworkTitle = artist.name,
                            artworkUrl = artist.artworkUrl,
                            circular = true,
                            onClick = { },
                        )
                    }
                }
            }
        }

        LibraryTab.SONGS -> {
            LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                item(key = "play-all") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${snapshot.tracks.size} songs",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onPlayTracks(snapshot.tracks, 0) }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Play all")
                        }
                    }
                }
                items(snapshot.tracks, key = { it.provider.slug + it.id }) { track ->
                    TrackRow(
                        track = track,
                        index = snapshot.tracks.indexOf(track),
                        onClick = { onPlayTracks(snapshot.tracks, snapshot.tracks.indexOf(track)) },
                    )
                }
            }
        }

        LibraryTab.DOWNLOADS -> {
            if (state.downloads.isEmpty()) {
                EmptyState(
                    title = "Nothing downloaded",
                    body = "Deezer downloads are stored encrypted and play offline.",
                    icon = Icons.Rounded.Download,
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                    items(state.downloads, key = { it.trackId }) { download ->
                        DownloadRow(download)
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionRow(
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
                .size(52.dp)
                .clip(if (circular) RoundedCornerShape(50) else RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
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

@Composable
private fun DownloadRow(download: DownloadItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            title = download.title,
            modifier = Modifier.size(48.dp),
            cornerRadius = 10.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(download.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                text = "${download.artist} · ${download.quality.label} · ${formatBytes(download.bytesOnDisk)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (download.state == dev.sonora.core.model.DownloadState.RUNNING) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { download.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        ) {
            Text(
                text = download.state.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
