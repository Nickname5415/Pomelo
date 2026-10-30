package xyz.stuffbyren.pomelo.desktop.model

data class Artist(
    val id: String,
    val name: String,
    val pictureUrl: String = "",
)

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String = "",
    val artworkUrl: String = "",
    val releaseYear: String = "",
    val trackCount: Int = 0,
    val durationMs: Long = 0L,
)

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val albumId: String = "",
    val artistId: String = "",
    val artworkUrl: String = "",
    val durationMs: Long = 0L,
    val trackNumber: Int = 1,
    val explicit: Boolean = false,
    val quality: String = "LOSSLESS",
)

data class Playlist(
    val id: String,
    val title: String,
    val author: String = "TIDAL",
    val artworkUrl: String = "",
    val trackCount: Int = 0,
)

sealed class CatalogItem {
    data class Song(val track: Track) : CatalogItem()
    data class Record(val album: Album) : CatalogItem()
    data class Performer(val artist: Artist) : CatalogItem()
    data class Collection(val playlist: Playlist) : CatalogItem()
}

data class PlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1.0f,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = 0,
)
