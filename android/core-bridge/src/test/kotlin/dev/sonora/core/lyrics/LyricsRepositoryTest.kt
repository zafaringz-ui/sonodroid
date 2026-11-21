package dev.sonora.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsRepositoryTest {
    @Test fun parsesWordTimedLrcWithoutLosingWhitespaceOrTiming() {
        val line = parseLrc("[00:01.00]<00:01.10>Hello <00:01.50>world").single()

        assertEquals("Hello world", line.text)
        assertEquals(2, line.words.size)
        assertEquals("Hello ", line.words[0].text)
        assertEquals(1_100L, line.words[0].startMs)
        assertEquals(1_500L, line.words[0].endMs)
        assertEquals("world", line.words[1].text)
        assertEquals(1_500L, line.words[1].startMs)
    }

    @Test fun estimatesWordCadenceAcrossLineTimedLyrics() {
        val words = estimateWordTimings(
            listOf(LyricLine(1_000L, "one two"), LyricLine(3_000L, "three")),
            durationMs = 5_000L,
        ).first().words

        assertEquals(listOf("one ", "two"), words.map { it.text })
        assertEquals(1_000L, words.first().startMs)
        assertEquals(3_000L, words.last().endMs)
    }

    @Test fun appliesLrcOffsetToLineAndWordTimes() {
        val line = parseLrc("[offset:100]\n[00:01.00]<00:01.10>word").single()

        assertEquals(900L, line.timeMs)
        assertEquals(1_000L, line.words.single().startMs)
    }
}
