package dev.sonora.core.demo

import dev.sonora.core.model.AuthState
import dev.sonora.core.model.PlanTier
import dev.sonora.core.model.Provider
import dev.sonora.core.model.RepeatMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoCoreClientTest {

    @Test
    fun `playing a list starts at the requested track`() = runTest {
        val client = DemoCoreClient(backgroundScope)
        val tracks = DemoLibrary.tracks(Provider.DEEZER)

        client.playTracks(tracks, startIndex = 2)

        assertEquals("dz-3", client.playback.value.track?.id)
        assertTrue(client.playback.value.isPlaying)
        assertEquals(tracks.size, client.playback.value.queueSize)
        assertEquals(2, client.playback.value.queueIndex)
        client.close()
    }

    @Test
    fun `next and previous walk the queue`() = runTest {
        val client = DemoCoreClient(backgroundScope)
        client.playTracks(DemoLibrary.tracks(Provider.DEEZER))

        client.next()
        assertEquals("dz-2", client.playback.value.track?.id)
        client.previous()
        assertEquals("dz-1", client.playback.value.track?.id)
        client.close()
    }

    @Test
    fun `repeat one replays the same track`() = runTest {
        val client = DemoCoreClient(backgroundScope)
        client.playTracks(DemoLibrary.tracks(Provider.DEEZER))
        client.setRepeat(RepeatMode.ONE)

        client.next()
        assertEquals("dz-1", client.playback.value.track?.id)
        assertEquals(0L, client.playback.value.positionMs)
        client.close()
    }

    @Test
    fun `end of queue with repeat off pauses on the last track`() = runTest {
        val client = DemoCoreClient(backgroundScope)
        val tracks = DemoLibrary.tracks(Provider.DEEZER)
        client.playTracks(tracks, startIndex = tracks.lastIndex)

        client.next()

        val state = client.playback.value
        assertEquals(tracks.last().id, state.track?.id)
        assertFalse(state.isPlaying)
        client.close()
    }

    @Test
    fun `moving a queue item keeps the current track`() = runTest {
        val client = DemoCoreClient(backgroundScope)
        client.playTracks(DemoLibrary.tracks(Provider.DEEZER), startIndex = 1)

        client.moveInQueue(from = 1, to = 3)

        assertEquals("dz-2", client.playback.value.track?.id)
        assertEquals(3, client.playback.value.queueIndex)
        assertEquals(3, client.queue.value.indexOfFirst { it.id == "dz-2" })
        client.close()
    }

    @Test
    fun `removing the playing track advances to its neighbour`() = runTest {
        val client = DemoCoreClient(backgroundScope)
        client.playTracks(DemoLibrary.tracks(Provider.DEEZER))

        client.removeFromQueue(0)

        assertEquals("dz-2", client.playback.value.track?.id)
        assertEquals(5, client.queue.value.size)
        client.close()
    }

    @Test
    fun `sign in and sign out update the account state`() = runTest {
        val client = DemoCoreClient(backgroundScope)
        assertEquals(AuthState.SIGNED_OUT, client.auth.value.first().state)

        client.completeSignIn(Provider.DEEZER, "Listener", PlanTier.HIFI).getOrThrow()
        val signedIn = client.auth.value.first { it.provider == Provider.DEEZER }
        assertEquals(AuthState.SIGNED_IN, signedIn.state)
        assertEquals(PlanTier.HIFI, signedIn.account?.plan)

        client.signOut(Provider.DEEZER)
        assertEquals(
            AuthState.SIGNED_OUT,
            client.auth.value.first { it.provider == Provider.DEEZER }.state,
        )
        client.close()
    }

    @Test
    fun `hifi account resolves lossless while free does not`() = runTest {
        val client = DemoCoreClient(backgroundScope)

        val free = client.resolvedQuality(Provider.DEEZER, dev.sonora.core.model.StreamPurpose.STREAMING)
        assertNotNull(free)
        assertEquals(128, free?.bitrateKbps)

        client.completeSignIn(Provider.DEEZER, "Listener", PlanTier.HIFI).getOrThrow()
        val hifi = client.resolvedQuality(Provider.DEEZER, dev.sonora.core.model.StreamPurpose.STREAMING)
        assertEquals("flac", hifi?.codec)
        client.close()
    }

    @Test
    fun `search filters by query and provider`() = runTest {
        val client = DemoCoreClient(backgroundScope)
        val results = client.search(Provider.DEEZER, "ferry", dev.sonora.core.model.SearchFilter.ALL)

        assertEquals(1, results.tracks.size)
        assertEquals("Night Ferry", results.tracks.first().title)
        client.close()
    }
}
