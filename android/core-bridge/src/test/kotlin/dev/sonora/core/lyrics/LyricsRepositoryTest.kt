package dev.sonora.core.lyrics

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import dev.sonora.core.model.LyricLine

class LyricsRepositoryTest {
    private lateinit var server: MockWebServer

    @Before fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @After fun stopServer() {
        server.shutdown()
    }

    @Test fun fetchesAndRanksLrcLibCandidatesSyncedFirst() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[
                  {"id":1,"trackName":"Song","artistName":"Artist","albumName":"Album","duration":180,"plainLyrics":"first\nsecond"},
                  {"id":2,"trackName":"Song","artistName":"Artist","albumName":"Album","duration":180,"syncedLyrics":"[00:00.00]first\n[02:59.00]second"}
                ]""",
            ),
        )
        val provider = LrcLibProvider(endpoint = server.url("/api/search").toString())
        val repository = LyricsRepository(listOf(provider))
        val lyrics = repository.lyrics(query())
        val request = server.takeRequest()

        assertTrue(request.path!!.contains("track_name=Song"))
        assertTrue(request.path!!.contains("artist_name=Artist"))
        assertEquals("SonoraAndroid/0.1 (https://github.com/zafaringz-ui/sonodroid)", request.getHeader("User-Agent"))
        assertNotNull(lyrics)
        assertEquals("LrcLib", lyrics!!.source)
        assertTrue(lyrics.synced)
        assertEquals(listOf("first", "second"), lyrics.lines.map(LyricLine::text))
        assertEquals(listOf(0L, 179_000L), lyrics.lines.map(LyricLine::timeMs))
    }

    @Test fun fallsBackToPlainLyricsWhenNoSyncedTextExists() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[{"trackName":"Song","artistName":"Artist","duration":180,"plainLyrics":"first\n\nsecond"}]""",
            ),
        )
        val lyrics = LyricsRepository(listOf(LrcLibProvider(endpoint = server.url("/").toString())))
            .lyrics(query())
        assertNotNull(lyrics)
        assertFalse(lyrics!!.synced)
        assertEquals(listOf("first", "second"), lyrics.lines.map(LyricLine::text))
    }

    @Test fun returnsNullForEmptyResponseAndDoesNotInventLyrics() = runTest {
        server.enqueue(MockResponse().setBody("[]"))
        assertNull(LyricsRepository(listOf(LrcLibProvider(endpoint = server.url("/").toString()))).lyrics(query()))
    }

    @Test fun parserHandlesMultipleStampsOffsetsInlineAndTrailingEndTags() {
        val lines = parseLrc(
            """[offset:-100]
               |[00:01.00][00:02.50] <00:01.00>Hello <00:01.40>world[00:03.00]
               |[ar:ignored]
               |[00:05.25]Last line
            """.trimMargin(),
        )
        assertEquals(listOf(1_100L, 2_600L, 5_350L), lines.map(LyricLine::timeMs))
        assertEquals("Hello world", lines[0].text)
        assertEquals("Last line", lines.last().text)
    }

    @Test fun rankerRejectsPoorMetadataAndTruncatedSyncedCandidate() {
        val query = query(durationMs = 200_000L)
        val wrongArtist = candidate(artist = "Someone Else", durationMs = 200_000L)
        val truncated = candidate(
            durationMs = 200_000L,
            lines = listOf(LyricLine(0, "tiny excerpt"), LyricLine(20_000, "end")),
        )
        val acceptable = candidate(durationMs = 199_000L)

        assertEquals(listOf(acceptable), LyricsRanker.rank(query, listOf(wrongArtist, truncated, acceptable)))
    }

    private fun query(durationMs: Long = 180_000L) = LyricsQuery(
        trackId = "123",
        title = "Song",
        artist = "Artist",
        album = "Album",
        durationMs = durationMs,
    )

    private fun candidate(
        artist: String = "Artist",
        durationMs: Long?,
        lines: List<LyricLine> = listOf(LyricLine(0L, "Hello"), LyricLine(175_000L, "world")),
    ) = LyricsCandidate(
        source = "LrcLib",
        trust = 0,
        title = "Song",
        artist = artist,
        album = "Album",
        durationMs = durationMs,
        synced = true,
        lines = lines,
    )
}
