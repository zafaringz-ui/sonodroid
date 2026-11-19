package dev.sonora.app

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes (Navigation Compose 2.8 + kotlinx.serialization).
 *
 * Named `*Destination` so they never collide with the `*Route` composables that the
 * feature modules expose.
 */
@Serializable
object WelcomeDestination

@Serializable
object HomeDestination

@Serializable
object SearchDestination

@Serializable
object LibraryDestination

@Serializable
object SettingsDestination

@Serializable
object QueueDestination

@Serializable
object LyricsDestination

@Serializable
data class SignInDestination(val providerSlug: String)

@Serializable
data class AlbumDestination(val providerSlug: String, val albumId: String)

@Serializable
data class PlaylistDestination(val providerSlug: String, val playlistId: String)

@Serializable
data class QualityDestination(val providerSlug: String)
