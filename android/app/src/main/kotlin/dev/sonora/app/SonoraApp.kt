package dev.sonora.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.sonora.core.PublicCatalogClient
import dev.sonora.core.model.PlanTier
import dev.sonora.core.model.Provider
import dev.sonora.design.component.EmptyState
import dev.sonora.design.component.MiniPlayer
import dev.sonora.feature.auth.SignInRoute
import dev.sonora.feature.auth.WelcomeScreen
import dev.sonora.feature.home.HomeRoute
import dev.sonora.feature.library.AlbumRoute
import dev.sonora.feature.library.LibraryRoute
import dev.sonora.feature.library.PlaylistRoute
import dev.sonora.feature.player.LyricsRoute
import dev.sonora.feature.player.NowPlayingScreen
import dev.sonora.feature.player.PlayerViewModel
import dev.sonora.feature.player.QueueRoute
import dev.sonora.feature.search.SearchRoute
import dev.sonora.feature.settings.QualityRoute
import dev.sonora.feature.settings.SettingsRoute
import kotlinx.coroutines.launch

/**
 * App shell (plan §7): bottom bar, mini player and the swipe-up Now Playing sheet,
 * with a type-safe NavHost in between.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonoraApp(graph: AppGraph) {
    val core = graph.core
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val playerViewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModel.factory(core),
    )
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        graph.deepLinks.collect { link ->
            snackbarHostState.showSnackbar(
                "${link.provider.label} ${link.description} recognised (${link.id}). " +
                    "Resolving it needs the provider client.",
            )
        }
    }

    val startDestination: Any = if (
        graph.onboarding.isOnboarded || core is PublicCatalogClient
    ) {
        HomeDestination
    } else {
        WelcomeDestination
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                playerState.playback.track?.let { track ->
                    MiniPlayer(
                        track = track,
                        isPlaying = playerState.playback.isPlaying,
                        progress = playerState.playback.progress,
                        onOpen = { showNowPlaying = true },
                        onPlayPause = playerViewModel::togglePlayPause,
                        onNext = playerViewModel::next,
                    )
                }
                if (isTopLevel(destination)) {
                    SonoraBottomBar(
                        destination = destination,
                        onSelect = { route -> navController.navigateTopLevel(route) },
                    )
                }
                // Debug builds only: names the active core so demo data is never
                // mistaken for the native one.
                CoreModeBadgeHost(
                    selection = graph.coreSelection,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
        ) {
            composable<WelcomeDestination> {
                WelcomeScreen(
                    onContinue = { provider ->
                        navController.navigate(SignInDestination(provider.slug))
                    },
                    onPreview = {
                        scope.launch {
                            core.completeSignIn(
                                Provider.DEEZER,
                                "Demo listener",
                                PlanTier.HIFI,
                            )
                            core.completeSignIn(
                                Provider.YOUTUBE_MUSIC,
                                "Demo listener",
                                PlanTier.PREMIUM,
                            )
                            graph.onboarding.isOnboarded = true
                            navController.navigate(HomeDestination) {
                                popUpTo(WelcomeDestination) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                )
            }

            composable<SignInDestination> { entry ->
                val args = entry.toRoute<SignInDestination>()
                if (core is PublicCatalogClient && args.providerSlug != Provider.DEEZER.slug) {
                    EmptyState(
                        title = "Sign-in unavailable",
                        body = "YouTube Music sign-in is not available in this public Deezer build.",
                        actionLabel = "Back",
                        onAction = { navController.popBackStack() },
                    )
                } else {
                    SignInRoute(
                        core = core,
                        credentials = graph.credentials,
                        providerSlug = args.providerSlug,
                        onBack = { navController.popBackStack() },
                        onDone = {
                            graph.onboarding.isOnboarded = true
                            navController.navigate(HomeDestination) {
                                popUpTo(WelcomeDestination) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }
            }

            composable<HomeDestination> {
                HomeRoute(
                    core = core,
                    onPlayTracks = { tracks, index ->
                        playerViewModel.playTracks(tracks, index)
                        showNowPlaying = true
                    },
                    onOpenAlbum = { provider, albumId ->
                        navController.navigate(AlbumDestination(provider.slug, albumId))
                    },
                    onOpenPlaylist = { provider, playlistId ->
                        navController.navigate(PlaylistDestination(provider.slug, playlistId))
                    },
                    onOpenSettings = { navController.navigateTopLevel(SettingsDestination) },
                    onSignIn = { provider ->
                        navController.navigate(SignInDestination(provider.slug))
                    },
                )
            }

            composable<SearchDestination> {
                SearchRoute(
                    core = core,
                    onPlayTracks = { tracks, index ->
                        playerViewModel.playTracks(tracks, index)
                        showNowPlaying = true
                    },
                    onOpenAlbum = { provider, albumId ->
                        navController.navigate(AlbumDestination(provider.slug, albumId))
                    },
                    onOpenPlaylist = { provider, playlistId ->
                        navController.navigate(PlaylistDestination(provider.slug, playlistId))
                    },
                )
            }

            composable<LibraryDestination> {
                LibraryRoute(
                    core = core,
                    onPlayTracks = { tracks, index ->
                        playerViewModel.playTracks(tracks, index)
                        showNowPlaying = true
                    },
                    onOpenAlbum = { provider, albumId ->
                        navController.navigate(AlbumDestination(provider.slug, albumId))
                    },
                    onOpenPlaylist = { provider, playlistId ->
                        navController.navigate(PlaylistDestination(provider.slug, playlistId))
                    },
                )
            }

            composable<AlbumDestination> { entry ->
                val args = entry.toRoute<AlbumDestination>()
                AlbumRoute(
                    core = core,
                    providerSlug = args.providerSlug,
                    albumId = args.albumId,
                    onBack = { navController.popBackStack() },
                    onPlayTracks = { tracks, index ->
                        playerViewModel.playTracks(tracks, index)
                        showNowPlaying = true
                    },
                )
            }

            composable<PlaylistDestination> { entry ->
                val args = entry.toRoute<PlaylistDestination>()
                PlaylistRoute(
                    core = core,
                    providerSlug = args.providerSlug,
                    playlistId = args.playlistId,
                    onBack = { navController.popBackStack() },
                    onPlayTracks = { tracks, index ->
                        playerViewModel.playTracks(tracks, index)
                        showNowPlaying = true
                    },
                )
            }

            composable<SettingsDestination> {
                SettingsRoute(
                    core = core,
                    credentials = graph.credentials,
                    onOpenQuality = { provider ->
                        navController.navigate(QualityDestination(provider.slug))
                    },
                    onSignIn = { provider ->
                        navController.navigate(SignInDestination(provider.slug))
                    },
                    onOpenDownloads = {
                        navController.navigateTopLevel(LibraryDestination)
                    },
                )
            }

            composable<QualityDestination> { entry ->
                val args = entry.toRoute<QualityDestination>()
                QualityRoute(
                    core = core,
                    providerSlug = args.providerSlug,
                    onBack = { navController.popBackStack() },
                )
            }

            composable<QueueDestination> {
                Surface(modifier = Modifier.fillMaxSize()) {
                    QueueRoute(core = core, onBack = { navController.popBackStack() })
                }
            }

            composable<LyricsDestination> {
                LyricsRoute(core = core, onBack = { navController.popBackStack() })
            }
        }
    }

    if (showNowPlaying && playerState.playback.track != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showNowPlaying = false },
            sheetState = sheetState,
            dragHandle = null,
        ) {
            NowPlayingScreen(
                state = playerState,
                onClose = { showNowPlaying = false },
                onPlayPause = playerViewModel::togglePlayPause,
                onNext = playerViewModel::next,
                onPrevious = playerViewModel::previous,
                onSeek = playerViewModel::seekTo,
                onToggleRepeat = playerViewModel::toggleRepeat,
                onToggleShuffle = playerViewModel::toggleShuffle,
                onOpenQueue = {
                    showNowPlaying = false
                    navController.navigate(QueueDestination)
                },
                onOpenLyrics = {
                    showNowPlaying = false
                    navController.navigate(LyricsDestination)
                },
                onRetry = playerViewModel::retryCurrent,
                modifier = Modifier.fillMaxHeight(),
            )
        }
    }
}

private data class TopLevelItem(
    val route: Any,
    val label: String,
    val icon: ImageVector,
)

private val TopLevelItems = listOf(
    TopLevelItem(HomeDestination, "Home", Icons.Rounded.Home),
    TopLevelItem(SearchDestination, "Search", Icons.Rounded.Search),
    TopLevelItem(LibraryDestination, "Library", Icons.Rounded.LibraryMusic),
    TopLevelItem(SettingsDestination, "Settings", Icons.Rounded.Settings),
)

@Composable
private fun SonoraBottomBar(
    destination: NavDestination?,
    onSelect: (Any) -> Unit,
) {
    NavigationBar {
        TopLevelItems.forEach { item ->
            val selected = when (item.route) {
                HomeDestination -> destination?.hasRoute<HomeDestination>() == true
                SearchDestination -> destination?.hasRoute<SearchDestination>() == true
                LibraryDestination -> destination?.hasRoute<LibraryDestination>() == true
                else -> destination?.hasRoute<SettingsDestination>() == true
            }
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(item.route) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
            )
        }
    }
}

private fun isTopLevel(destination: NavDestination?): Boolean {
    if (destination == null) return false
    return destination.hasRoute<HomeDestination>() ||
        destination.hasRoute<SearchDestination>() ||
        destination.hasRoute<LibraryDestination>() ||
        destination.hasRoute<SettingsDestination>()
}

private fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
