/*
 * Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package xyz.stuffbyren.pomelo.lyrics

import xyz.stuffbyren.pomelo.model.LyricLine
import xyz.stuffbyren.pomelo.model.LyricsProvider
import xyz.stuffbyren.pomelo.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class ProvidedLyrics(val lines: List<LyricLine>, val provider: LyricsProvider? = null)

internal class LyricsProviderResolver(
    private val fetch: suspend (Track, LyricsProvider) -> List<LyricLine>,
) {
    private data class Key(
        val id: String, val title: String, val artist: String, val album: String,
        val durationMs: Long, val provider: LyricsProvider,
    )
    private val cache = object : LinkedHashMap<Key, List<LyricLine>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, List<LyricLine>>?): Boolean = size > 160
    }

    suspend fun resolve(track: Track, providers: List<LyricsProvider>): ProvidedLyrics {
        for (provider in providers.distinct()) {
            currentCoroutineContext().ensureActive()
            val key = Key(track.id, track.title, track.artist, track.album, track.durationMs, provider)
            val cached = synchronized(cache) { cache[key] }
            if (cached != null) return ProvidedLyrics(cached, provider)
            val lines = try {
                fetch(track, provider)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            currentCoroutineContext().ensureActive()
            if (lines.isNotEmpty()) {
                synchronized(cache) { cache[key] = lines }
                return ProvidedLyrics(lines, provider)
            }
        }
        return ProvidedLyrics(emptyList())
    }
}
