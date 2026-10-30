/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Pomelo.
 *
 * Pomelo is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Pomelo is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Pomelo. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.stuffbyren.pomelo.playback

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class TidalStreamResolverTest {

    @Test
    fun parseBtsManifestReturnsDirectCdnUrl() {
        val btsJson = JSONObject().apply {
            put("codecs", "flac")
            put("urls", org.json.JSONArray().apply {
                put("https://sp-pr-cf.audio.tidal.com/data/sample.flac?token=12345")
            })
        }
        val encoded = Base64.getEncoder().encodeToString(btsJson.toString().toByteArray())
        val manifestUri = "data:application/vnd.tidal.bts;base64,$encoded"

        val resolved = TidalStreamResolver.extractUrl(manifestUri)
        assertNotNull(resolved)
        assertEquals("https://sp-pr-cf.audio.tidal.com/data/sample.flac?token=12345", resolved)
    }

    @Test
    fun parseRawBase64BtsReturnsDirectCdnUrl() {
        val btsJson = JSONObject().apply {
            put("codecs", "flac")
            put("urls", org.json.JSONArray().apply {
                put("https://sp-pr-cf.audio.tidal.com/data/sample2.flac?token=abc")
            })
        }
        val encoded = Base64.getEncoder().encodeToString(btsJson.toString().toByteArray())

        val resolved = TidalStreamResolver.extractUrl(encoded)
        assertNotNull(resolved)
        assertEquals("https://sp-pr-cf.audio.tidal.com/data/sample2.flac?token=abc", resolved)
    }

    @Test
    fun parseHlsMasterManifestExtractsChildPlaylistUrl() {
        val hlsManifest = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-STREAM-INF:BANDWIDTH=384209,AVERAGE-BANDWIDTH=340246,CODECS="fLaC"
            https://im-cf.manifest.tidal.com/1/manifests/child.m3u8?token=xyz
        """.trimIndent()
        val encoded = Base64.getEncoder().encodeToString(hlsManifest.toByteArray())
        val dataUri = "data:application/vnd.apple.mpegurl;base64,$encoded"

        val resolvedFromData = TidalStreamResolver.extractUrl(dataUri)
        assertNotNull(resolvedFromData)
        assertEquals("https://im-cf.manifest.tidal.com/1/manifests/child.m3u8?token=xyz", resolvedFromData)

        val resolvedFromRaw = TidalStreamResolver.extractUrl(encoded)
        assertNotNull(resolvedFromRaw)
        assertEquals("https://im-cf.manifest.tidal.com/1/manifests/child.m3u8?token=xyz", resolvedFromRaw)
    }

    @Test
    fun parseDirectHttpsUriReturnsUnchanged() {
        val directUrl = "https://sp-pr-cf.audio.tidal.com/stream/track.flac"
        val resolved = TidalStreamResolver.extractUrl(directUrl)
        assertEquals(directUrl, resolved)

        val directHlsUrl = "https://im-cf.manifest.tidal.com/1/manifests/master.m3u8?token=123"
        val resolvedHls = TidalStreamResolver.extractUrl(directHlsUrl)
        assertEquals(directHlsUrl, resolvedHls)
    }

    @Test
    fun parseInvalidManifestReturnsNull() {
        assertNull(TidalStreamResolver.extractUrl(""))
        assertNull(TidalStreamResolver.extractUrl("invalid_non_base64_json"))
    }
}
