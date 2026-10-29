/*
 * Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package xyz.stuffbyren.pomelo.lyrics

import xyz.stuffbyren.pomelo.model.LyricsProvider
import xyz.stuffbyren.pomelo.model.Track
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LyricsRepositoryTest {
    private val track = Track(id = "abcdefghijk", title = "Song", artist = "Artist")

    @Test
    fun betterLyricsIsIndependentFromBiniAndOtherProviders() = runBlocking {
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            assertEquals("lyrics-api.boidu.dev", chain.request().url.host)
            val ttml = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="3s">Found lyrics</p></div></body></tt>"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(JSONObject().put("ttml", ttml).toString().toResponseBody()).build()
        }.build()
        val repository = LyricsRepository(client) { error("YouTube must not be called") }
        val lines = repository.lyrics(track, listOf(LyricsProvider.BETTER_LYRICS))
        assertEquals("Found lyrics", lines.single().text)
        assertEquals(1000L, lines.single().startMs)
        assertEquals(LyricsProvider.BETTER_LYRICS, repository.sources.value[track.id])
        assertEquals(lines, repository.lyrics(track, listOf(LyricsProvider.BETTER_LYRICS)))
        assertEquals(1, requests)
        assertTrue(repository.lyrics(track, emptyList()).isEmpty())
        assertNull(repository.sources.value[track.id])
    }

    @Test
    fun tidalReturnsEmptyWhenNoLyricsEndpointConfigured() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { error("No HTTP lyrics provider should be called") }.build()
        val repository = LyricsRepository(client) { "" }
        val lines = repository.lyrics(track.copy(artist = ""), listOf(LyricsProvider.TIDAL))
        assertTrue(lines.isEmpty())
    }

    @Test
    fun songOverrideDoesNotChangeOtherSongsAndCanBeReset() {
        val repository = LyricsRepository(OkHttpClient()) { "" }
        repository.selectProvider(track.id, LyricsProvider.LRCLIB)
        repository.selectProvider("another-song", LyricsProvider.BETTER_LYRICS)
        assertEquals(LyricsProvider.LRCLIB, repository.providerOverrides.value[track.id])
        repository.selectProvider(track.id, null)
        assertNull(repository.providerOverrides.value[track.id])
        assertEquals(LyricsProvider.BETTER_LYRICS, repository.providerOverrides.value["another-song"])
    }
}
