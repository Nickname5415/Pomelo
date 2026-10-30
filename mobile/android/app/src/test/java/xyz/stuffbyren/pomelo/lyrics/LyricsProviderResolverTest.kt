/*
 * Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package xyz.stuffbyren.pomelo.lyrics

import xyz.stuffbyren.pomelo.model.LyricLine
import xyz.stuffbyren.pomelo.model.LyricsProvider
import xyz.stuffbyren.pomelo.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LyricsProviderResolverTest {
    private val track = Track(id = "song", title = "Song", artist = "Artist")

    @Test
    fun usesFirstAvailableProviderInCustomOrder() = runBlocking {
        val calls = mutableListOf<LyricsProvider>()
        val resolver = LyricsProviderResolver { _, provider ->
            calls += provider
            listOf(LyricLine(provider.label))
        }
        val result = resolver.resolve(track, listOf(LyricsProvider.BETTER_LYRICS, LyricsProvider.BINI))
        assertEquals(LyricsProvider.BETTER_LYRICS, result.provider)
        assertEquals(listOf(LyricsProvider.BETTER_LYRICS), calls)
    }

    @Test
    fun skipsUnavailableAndFailedProviders() = runBlocking {
        val calls = mutableListOf<LyricsProvider>()
        val resolver = LyricsProviderResolver { _, provider ->
            calls += provider
            when (provider) {
                LyricsProvider.BETTER_LYRICS -> error("Unavailable")
                LyricsProvider.LRCLIB -> listOf(LyricLine("Found", 1000))
                else -> emptyList()
            }
        }
        val order = listOf(LyricsProvider.BETTER_LYRICS, LyricsProvider.BINI, LyricsProvider.LRCLIB)
        assertEquals(LyricsProvider.LRCLIB, resolver.resolve(track, order).provider)
        assertEquals(order, calls)
    }

    @Test
    fun disabledProvidersAreNeverFetched() = runBlocking {
        val resolver = LyricsProviderResolver { _, _ -> error("No provider should be called") }
        assertTrue(resolver.resolve(track, emptyList()).lines.isEmpty())
    }

    @Test
    fun providerSpecificCacheRespectsSwitchingAndReordering() = runBlocking {
        val calls = mutableListOf<LyricsProvider>()
        val resolver = LyricsProviderResolver { _, provider ->
            calls += provider
            listOf(LyricLine(provider.label))
        }
        assertEquals(LyricsProvider.BINI, resolver.resolve(track, listOf(LyricsProvider.BINI)).provider)
        assertEquals(LyricsProvider.LRCLIB, resolver.resolve(track, listOf(LyricsProvider.LRCLIB)).provider)
        val result = resolver.resolve(track, listOf(LyricsProvider.LRCLIB, LyricsProvider.BINI))
        assertEquals("LRCLIB", result.lines.single().text)
        assertEquals(listOf(LyricsProvider.BINI, LyricsProvider.LRCLIB), calls)
        assertEquals(LyricsProvider.BINI, resolver.resolve(track, listOf(LyricsProvider.BINI)).provider)
    }

    @Test
    fun higherPriorityProviderIsTriedBeforeCachedLowerPriorityLyrics() = runBlocking {
        var available = false
        val resolver = LyricsProviderResolver { _, provider ->
            if (provider == LyricsProvider.BINI || available) listOf(LyricLine(provider.label)) else emptyList()
        }
        resolver.resolve(track, listOf(LyricsProvider.BINI))
        available = true
        assertEquals(LyricsProvider.BETTER_LYRICS,
            resolver.resolve(track, listOf(LyricsProvider.BETTER_LYRICS, LyricsProvider.BINI)).provider)
    }

    @Test
    fun updatedMetadataAndFailedRequestsCanBeRetried() = runBlocking {
        var calls = 0
        val resolver = LyricsProviderResolver { item, _ ->
            calls++
            if (calls == 1) emptyList() else listOf(LyricLine(item.title))
        }
        val order = listOf(LyricsProvider.BETTER_LYRICS)
        assertTrue(resolver.resolve(track, order).lines.isEmpty())
        assertEquals("Song", resolver.resolve(track, order).lines.single().text)
        assertEquals("Correct title", resolver.resolve(track.copy(title = "Correct title"), order).lines.single().text)
        assertEquals(3, calls)
    }

    @Test
    fun cancellationDoesNotFallThroughOrCacheLyrics() = runBlocking {
        var calls = 0
        val resolver = LyricsProviderResolver { _, _ -> calls++; throw CancellationException("Song changed") }
        try {
            resolver.resolve(track, LyricsProvider.entries)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(1, calls)
        }
    }
}
