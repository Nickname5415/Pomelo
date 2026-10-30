package xyz.stuffbyren.pomelo.ui.screens

import xyz.stuffbyren.pomelo.model.Album
import xyz.stuffbyren.pomelo.model.Artist
import xyz.stuffbyren.pomelo.model.CatalogItem
import xyz.stuffbyren.pomelo.model.SearchResults
import xyz.stuffbyren.pomelo.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchTopResultTest {
    private val track = Track(id = "t1", title = "Song", artist = "Someone")
    private val album = Album(id = "a1", title = "Album", artist = "Someone")
    private val artist = Artist(id = "r1", name = "Someone")

    @Test
    fun exactArtistNameBeatsFirstSong() {
        val results = SearchResults(tracks = listOf(track), artists = listOf(artist))
        assertEquals(CatalogItem.Performer(artist), topResult(results, " someone "))
    }

    @Test
    fun firstSongWinsWithoutExactArtist() {
        val results = SearchResults(tracks = listOf(track), albums = listOf(album), artists = listOf(artist))
        assertEquals(CatalogItem.Song(track), topResult(results, "song"))
    }

    @Test
    fun fallsBackToAlbumThenNull() {
        assertEquals(CatalogItem.Record(album), topResult(SearchResults(albums = listOf(album)), "x"))
        assertNull(topResult(SearchResults(), "x"))
    }
}
