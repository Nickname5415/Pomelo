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

package xyz.stuffbyren.pomelo.songlinks

import xyz.stuffbyren.pomelo.model.BrowseDetail
import xyz.stuffbyren.pomelo.model.CatalogKind
import xyz.stuffbyren.pomelo.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongLinksRepositoryTest {
    private val repo = SongLinksRepository()

    @Test
    fun buildsTidalUniversalLinks() {
        assertEquals("https://tidal.com/track/228822578/u",
            repo.trackUrl(Track(id = "228822578", title = "Song", artist = "Artist")))
        assertEquals("https://tidal.com/album/459304950/u",
            repo.collectionUrl(BrowseDetail(id = "459304950", kind = CatalogKind.ALBUM,
                title = "Album")))
        assertEquals("https://tidal.com/playlist/my-playlist-id/u",
            repo.collectionUrl(BrowseDetail(id = "my-playlist-id", kind = CatalogKind.PLAYLIST,
                title = "Playlist")))
        assertEquals("https://tidal.com/artist/12345/u",
            repo.collectionUrl(BrowseDetail(id = "12345", kind = CatalogKind.ARTIST,
                title = "Artist")))
    }

    @Test
    fun doesNotShareInternalOrLocalIdentities() {
        assertNull(repo.trackUrl(Track(id = "local_track", title = "Song", artist = "Artist",
            playbackSource = "local")))
        assertNull(repo.collectionUrl(BrowseDetail(id = "MPREb_internal", kind = CatalogKind.ALBUM,
            title = "Album")))
        assertNull(repo.collectionUrl(BrowseDetail(id = "offline_downloads", kind = CatalogKind.PLAYLIST,
            title = "Downloads")))
    }

    @Test
    fun parsesPublicLinksAndTidalLinks() {
        val tidalTrack = repo.parseLink("https://tidal.com/track/228822578/u")
        assertTrue(tidalTrack is SongLinkTarget.Video)
        assertEquals("228822578", (tidalTrack as SongLinkTarget.Video).videoId)

        val tidalAlbum = repo.parseLink("https://tidal.com/album/459304950/u")
        assertTrue(tidalAlbum is SongLinkTarget.Browse)
        assertEquals("459304950", (tidalAlbum as SongLinkTarget.Browse).browseId)
        assertEquals("album", tidalAlbum.kind)

        val song = repo.parseLink("https://song.link/y/dQw4w9WgXcQ")
        assertTrue(song is SongLinkTarget.Video)
        assertEquals("dQw4w9WgXcQ", (song as SongLinkTarget.Video).videoId)

        val album = repo.parseLink("album.link/y/OLAK5uy_sample")
        assertTrue(album is SongLinkTarget.Browse)
        assertEquals("OLAK5uy_sample", (album as SongLinkTarget.Browse).browseId)
        assertEquals("album", album.kind)

        val playlist = repo.parseLink("https://album.link/y/PL123456789")
        assertTrue(playlist is SongLinkTarget.Browse)
        assertEquals("PL123456789", (playlist as SongLinkTarget.Browse).browseId)

        assertEquals("dQw4w9WgXcQ",
            (repo.parseLink("https://youtu.be/dQw4w9WgXcQ") as SongLinkTarget.Video).videoId)
        assertEquals("PL123456789",
            (repo.parseLink("https://music.youtube.com/playlist?list=VLPL123456789") as SongLinkTarget.Browse).browseId)
        assertEquals("dQw4w9WgXcQ",
            (repo.parseLink("Song - Artist\nhttps://song.link/y/dQw4w9WgXcQ") as SongLinkTarget.Video).videoId)
    }

    @Test
    fun ignoresUnsupportedResolverAndForeignLinks() {
        assertNull(repo.parseLink("https://songlinks.sfg545.dev/s/legacy-id"))
        assertNull(repo.parseLink("https://example.com/y/dQw4w9WgXcQ"))
        assertNull(repo.parseLink("https://song.link/y/not-a-video-id"))
    }
}
