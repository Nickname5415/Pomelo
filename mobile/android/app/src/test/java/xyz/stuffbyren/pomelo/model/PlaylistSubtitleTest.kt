/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Orchard.
 *
 * Orchard is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Orchard is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Orchard. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.stuffbyren.pomelo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistSubtitleTest {
    private val track = Track(id = "a", title = "A", artist = "B")

    @Test fun `grid count wins over a partial track page`() {
        val playlist = Playlist("VLx", "Mix", "mari", tracks = listOf(track), trackCount = 21)
        assertEquals(21, playlist.songCount)
        assertEquals("21 songs · mari", playlist.subtitle)
    }

    @Test fun `falls back to loaded tracks, then to the creator`() {
        assertEquals("1 song · mari", Playlist("VLx", "Mix", "mari", tracks = listOf(track)).subtitle)
        assertEquals("mari", Playlist("VLx", "Mix", "mari").subtitle)
        assertEquals("0 songs · mari", Playlist("VLx", "Mix", "mari", trackCount = 0).subtitle)
        assertEquals("3 songs", Playlist("VLx", "Mix", "", trackCount = 3).subtitle)
    }

    @Test fun `track count survives the library cache`() {
        val saved = Playlist("VLx", "Mix", "mari", trackCount = 1035)
        assertEquals(1035, CatalogJson.playlist(CatalogJson.playlist(saved)).trackCount)
        assertNull(CatalogJson.playlist(CatalogJson.playlist(saved.copy(trackCount = null))).trackCount)
    }
}
