package dev.sonora.feature.home

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
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sonora.core.SonoraCoreClient
import dev.sonora.core.model.HomeItem
import dev.sonora.core.model.Provider
import dev.sonora.core.model.Track
import dev.sonora.design.component.DemoDataBadge
import dev.sonora.design.component.EmptyState
import dev.sonora.design.component.MediaCard
import dev.sonora.design.component.SectionHeader
import dev.sonora.design.component.Shelf
import dev.sonora.design.component.TrackRow

@Composable
fun HomeRoute(
    core: SonoraCoreClient,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onOpenAlbum: (Provider, String) -> Unit,
    onOpenPlaylist: (Provider, String) -> Unit,
    onOpenSettings: () -> Unit,
    onSignIn: (Provider) -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(core))
    val state by viewModel.state.collectAsStateWithLifecycle()

    HomeScreen(
        state = state,
        onSelectProvider = viewModel::select,
        onRetry = viewModel::refresh,
        onPlayTracks = onPlayTracks,
        onOpenAlbum = onOpenAlbum,
        onOpenPlaylist = onOpenPlaylist,
        onOpenSettings = onOpenSettings,
        onSignIn = onSignIn,
    )
}

/** Screen 5: per-provider Home with shelves and an honest demo-data badge. */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onSelectProvider: (Provider) -> Unit,
    onRetry: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onOpenAlbum: (Provider, String) -> Unit,
    onOpenPlaylist: (Provider, String) -> Unit,
    onOpenSettings: () -> Unit,
    onSignIn: (Provider) -> Unit,
    modifier: Modifier = Modifier,
) {
    val feed = state.feed

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = feed?.greeting ?: "Sonora",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        text = "Listen across Deezer and YouTube Music",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.isDemo) DemoDataBadge()
            }
        }

        item(key = "providers") {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Provider.entries.forEach { provider ->
                    FilterChip(
                        selected = provider == state.selected,
                        onClick = { onSelectProvider(provider) },
                        label = { Text(provider.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        }

        if (state.selected !in state.signedIn) {
            item(key = "signin-prompt") {
                SignInPrompt(provider = state.selected, onSignIn = onSignIn)
            }
        }

        when {
            state.loading && feed == null -> item(key = "loading") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }

            state.error != null && feed == null -> item(key = "error") {
                EmptyState(
                    title = "Home is out of reach",
                    body = state.error,
                    icon = Icons.Rounded.CloudOff,
                    actionLabel = "Try again",
                    onAction = onRetry,
                )
            }
        }

        if (feed != null) {
            items(feed.sections, key = { section -> "${feed.provider.slug}-${section.id}" }) { section ->
                val tracks = section.items.filterIsInstance<HomeItem.TrackItem>().map { it.track }
                SectionHeader(title = section.title, subtitle = section.subtitle)

                if (tracks.isNotEmpty()) {
                    Column {
                        tracks.forEachIndexed { index, track ->
                            TrackRow(
                                track = track,
                                onClick = { onPlayTracks(tracks, index) },
                                onAddToQueue = { onPlayTracks(listOf(track), 0) },
                            )
                        }
                    }
                }

                val cards = section.items.filter { it !is HomeItem.TrackItem }
                if (cards.isNotEmpty()) {
                    Shelf(itemCount = cards.size) { index ->
                        when (val item = cards[index]) {
                            is HomeItem.AlbumItem -> MediaCard(
                                title = item.album.title,
                                subtitle = item.album.artist,
                                artworkUrl = item.album.artworkUrl,
                                onClick = { onOpenAlbum(item.album.provider, item.album.id) },
                            )
                            is HomeItem.PlaylistItem -> MediaCard(
                                title = item.playlist.title,
                                subtitle = "Playlist · ${item.playlist.trackCount} tracks",
                                artworkUrl = item.playlist.artworkUrl,
                                onClick = { onOpenPlaylist(item.playlist.provider, item.playlist.id) },
                            )
                            is HomeItem.ArtistItem -> MediaCard(
                                title = item.artist.name,
                                subtitle = "Artist",
                                artworkUrl = item.artist.artworkUrl,
                                onClick = { },
                            )
                            is HomeItem.TrackItem -> MediaCard(
                                title = item.track.title,
                                subtitle = item.track.artistLine,
                                artworkUrl = item.track.artworkUrl,
                                onClick = { onPlayTracks(listOf(item.track), 0) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun SignInPrompt(
    provider: Provider,
    onSignIn: (Provider) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Sign in to ${provider.label} to sync your own library.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.TextButton(onClick = { onSignIn(provider) }) {
            Text("Sign in")
        }
    }
}
