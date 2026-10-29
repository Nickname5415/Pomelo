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

package xyz.stuffbyren.pomelo.catalog

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import xyz.stuffbyren.pomelo.auth.TidalAuthRepository
import xyz.stuffbyren.pomelo.model.Album
import xyz.stuffbyren.pomelo.model.Artist
import xyz.stuffbyren.pomelo.model.BrowseDetail
import xyz.stuffbyren.pomelo.model.CatalogItem
import xyz.stuffbyren.pomelo.model.CatalogKind
import xyz.stuffbyren.pomelo.model.CatalogSection
import xyz.stuffbyren.pomelo.model.HomeFeed
import xyz.stuffbyren.pomelo.model.Playlist
import xyz.stuffbyren.pomelo.model.SearchResults
import xyz.stuffbyren.pomelo.model.TIDAL_SOURCE
import xyz.stuffbyren.pomelo.model.Track
import java.net.URLEncoder

private const val TAG = "TidalCatalog"
private const val TIDAL_API_BASE = "https://openapi.tidal.com/v2"

/**
 * Communicates with TIDAL Open API v2 to provide tracks, albums, artists,
 * playlists, and search results to the Pomelo application.
 */
class TidalCatalogRepository(
    private val http: OkHttpClient,
    private val auth: TidalAuthRepository,
) {

    private suspend fun executeGet(endpoint: String, queryParams: Map<String, String> = emptyMap()): JSONObject? =
        withContext(Dispatchers.IO) {
            val token = auth.getAccessToken()
            val uriBuilder = Uri.parse("$TIDAL_API_BASE$endpoint").buildUpon()
            queryParams.forEach { (k, v) -> uriBuilder.appendQueryParameter(k, v) }
            val session = auth.session()
            val countryCode = session?.countryCode ?: "US"
            if (!queryParams.containsKey("countryCode")) {
                uriBuilder.appendQueryParameter("countryCode", countryCode)
            }

            val requestBuilder = Request.Builder()
                .url(uriBuilder.build().toString())
                .header("Accept", "application/vnd.api+json")

            if (!token.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            try {
                http.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "TIDAL API request failed: ${response.code} ${response.message} on $endpoint")
                        return@withContext null
                    }
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) null else JSONObject(body)
                }
            } catch (e: Exception) {
                Log.w(TAG, "TIDAL API network exception on $endpoint", e)
                null
            }
        }

    /**
     * Search across TIDAL tracks, albums, artists, and playlists.
     */
    suspend fun search(query: String, filter: String = "all", guest: Boolean = false): SearchResults =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext SearchResults()

            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val json = executeGet(
                endpoint = "/searchResults/$encodedQuery",
                queryParams = mapOf("include" to "tracks,albums,artists,playlists"),
            ) ?: return@withContext SearchResults()

            val included = json.optJSONArray("included") ?: JSONArray()
            val tracks = mutableListOf<Track>()
            val albums = mutableListOf<Album>()
            val artists = mutableListOf<Artist>()
            val playlists = mutableListOf<Playlist>()

            for (i in 0 until included.length()) {
                val item = included.optJSONObject(i) ?: continue
                when (item.optString("type")) {
                    "tracks" -> parseTrack(item)?.let { tracks.add(it) }
                    "albums" -> parseAlbum(item)?.let { albums.add(it) }
                    "artists" -> parseArtist(item)?.let { artists.add(it) }
                    "playlists" -> parsePlaylist(item)?.let { playlists.add(it) }
                }
            }

            SearchResults(
                tracks = if (filter == "all" || filter == "songs") tracks else emptyList(),
                albums = if (filter == "all" || filter == "albums") albums else emptyList(),
                artists = if (filter == "all" || filter == "artists") artists else emptyList(),
                playlists = if (filter == "all" || filter == "playlists") playlists else emptyList(),
            )
        }

    /**
     * Home feed featuring recommendations, new releases, and popular playlists.
     */
    suspend fun home(): HomeFeed = withContext(Dispatchers.IO) {
        val sections = mutableListOf<CatalogSection>()
        val featuredPlaylists = mutableListOf<CatalogItem>()

        val searchRes = search("Pop Hits", filter = "playlists")
        searchRes.playlists.forEach {
            featuredPlaylists.add(CatalogItem.Collection(it))
        }

        if (featuredPlaylists.isNotEmpty()) {
            sections.add(
                CatalogSection(
                    id = "featured-playlists",
                    title = "Featured on TIDAL",
                    items = featuredPlaylists,
                ),
            )
        }

        val topTracksRes = search("Top Hits", filter = "songs")
        val topTrackItems = topTracksRes.tracks.map { CatalogItem.Song(it) }
        if (topTrackItems.isNotEmpty()) {
            sections.add(
                CatalogSection(
                    id = "popular-tracks",
                    title = "Trending Songs",
                    items = topTrackItems,
                ),
            )
        }

        HomeFeed(sections = sections)
    }

    /**
     * Browse detail for albums, artists, or playlists.
     */
    suspend fun browse(id: String): BrowseDetail = withContext(Dispatchers.IO) {
        val cleanId = id.substringBefore(":")
        when {
            cleanId.startsWith("album-") || cleanId.all { it.isDigit() } -> {
                val albumId = cleanId.removePrefix("album-")
                browseAlbum(albumId)
            }
            cleanId.startsWith("artist-") -> {
                val artistId = cleanId.removePrefix("artist-")
                browseArtist(artistId)
            }
            cleanId.startsWith("playlist-") || cleanId.contains("-") -> {
                val playlistId = cleanId.removePrefix("playlist-")
                browsePlaylist(playlistId)
            }
            else -> browseAlbum(cleanId)
        }
    }

    fun browsePages(id: String): Flow<BrowseDetail> = flow {
        emit(browse(id))
    }.flowOn(Dispatchers.IO)

    private suspend fun browseAlbum(albumId: String): BrowseDetail {
        val json = executeGet("/albums/$albumId", mapOf("include" to "items,artists"))
            ?: return BrowseDetail(id = albumId, kind = CatalogKind.ALBUM, title = "Album", source = TIDAL_SOURCE)

        val data = json.optJSONObject("data") ?: JSONObject()
        val attributes = data.optJSONObject("attributes") ?: JSONObject()
        val included = json.optJSONArray("included") ?: JSONArray()

        val title = attributes.optString("title", "Album")
        val artworkUrl = resolveArtworkUrl(attributes)
        val releaseDate = attributes.optString("releaseDate", "")
        val year = releaseDate.take(4)

        val tracks = mutableListOf<Track>()
        var primaryArtist = ""

        for (i in 0 until included.length()) {
            val inc = included.optJSONObject(i) ?: continue
            if (inc.optString("type") == "tracks") {
                parseTrack(inc, fallbackAlbum = title, fallbackArtwork = artworkUrl)?.let { tracks.add(it) }
            } else if (inc.optString("type") == "artists") {
                if (primaryArtist.isEmpty()) {
                    primaryArtist = inc.optJSONObject("attributes")?.optString("name", "").orEmpty()
                }
            }
        }

        return BrowseDetail(
            id = albumId,
            kind = CatalogKind.ALBUM,
            title = title,
            artist = primaryArtist,
            year = year,
            artworkUrl = artworkUrl,
            tracks = tracks,
            source = TIDAL_SOURCE,
        )
    }

    private suspend fun browseArtist(artistId: String): BrowseDetail {
        val json = executeGet("/artists/$artistId", mapOf("include" to "albums,tracks"))
            ?: return BrowseDetail(id = artistId, kind = CatalogKind.ARTIST, title = "Artist", source = TIDAL_SOURCE)

        val data = json.optJSONObject("data") ?: JSONObject()
        val attributes = data.optJSONObject("attributes") ?: JSONObject()
        val name = attributes.optString("name", "Artist")
        val artworkUrl = resolveArtworkUrl(attributes)

        val included = json.optJSONArray("included") ?: JSONArray()
        val tracks = mutableListOf<Track>()
        val albums = mutableListOf<CatalogItem>()

        for (i in 0 until included.length()) {
            val inc = included.optJSONObject(i) ?: continue
            when (inc.optString("type")) {
                "tracks" -> parseTrack(inc, fallbackArtist = name)?.let { tracks.add(it) }
                "albums" -> parseAlbum(inc)?.let { albums.add(CatalogItem.Record(it)) }
            }
        }

        return BrowseDetail(
            id = artistId,
            kind = CatalogKind.ARTIST,
            title = name,
            artworkUrl = artworkUrl,
            tracks = tracks,
            related = albums,
            source = TIDAL_SOURCE,
        )
    }

    private suspend fun browsePlaylist(playlistId: String): BrowseDetail {
        val json = executeGet("/playlists/$playlistId", mapOf("include" to "items"))
            ?: return BrowseDetail(id = playlistId, kind = CatalogKind.PLAYLIST, title = "Playlist", source = TIDAL_SOURCE)

        val data = json.optJSONObject("data") ?: JSONObject()
        val attributes = data.optJSONObject("attributes") ?: JSONObject()
        val title = attributes.optString("name", "Playlist")
        val description = attributes.optString("description", "")
        val artworkUrl = resolveArtworkUrl(attributes)

        val included = json.optJSONArray("included") ?: JSONArray()
        val tracks = mutableListOf<Track>()

        for (i in 0 until included.length()) {
            val inc = included.optJSONObject(i) ?: continue
            if (inc.optString("type") == "tracks") {
                parseTrack(inc, fallbackArtwork = artworkUrl)?.let { tracks.add(it) }
            }
        }

        return BrowseDetail(
            id = playlistId,
            kind = CatalogKind.PLAYLIST,
            title = title,
            description = description,
            artworkUrl = artworkUrl,
            tracks = tracks,
            source = TIDAL_SOURCE,
        )
    }

    suspend fun upNext(trackId: String): List<Track> = withContext(Dispatchers.IO) {
        if (trackId.isBlank()) return@withContext emptyList()
        val json = executeGet("/tracks/$trackId/relationships/suggestedTracks") ?: return@withContext emptyList()
        val included = json.optJSONArray("included") ?: JSONArray()
        val tracks = mutableListOf<Track>()
        for (i in 0 until included.length()) {
            val inc = included.optJSONObject(i) ?: continue
            if (inc.optString("type") == "tracks") {
                parseTrack(inc)?.let { tracks.add(it) }
            }
        }
        tracks
    }

    suspend fun trackArtists(trackId: String): List<Artist> = withContext(Dispatchers.IO) {
        if (trackId.isBlank()) return@withContext emptyList()
        val json = executeGet("/tracks/$trackId", mapOf("include" to "artists")) ?: return@withContext emptyList()
        val included = json.optJSONArray("included") ?: JSONArray()
        val artists = mutableListOf<Artist>()
        for (i in 0 until included.length()) {
            val inc = included.optJSONObject(i) ?: continue
            if (inc.optString("type") == "artists") {
                parseArtist(inc)?.let { artists.add(it) }
            }
        }
        artists
    }

    suspend fun library(): List<CatalogSection> = withContext(Dispatchers.IO) {
        val playlists = playlists()
        if (playlists.isEmpty()) emptyList()
        else listOf(CatalogSection(id = "library-playlists", title = "Playlists", items = playlists))
    }

    suspend fun playlists(): List<CatalogItem> = withContext(Dispatchers.IO) {
        val session = auth.session() ?: return@withContext emptyList()
        if (session.userId.isBlank()) return@withContext emptyList()

        val json = executeGet("/userCollections/${session.userId}/relationships/playlists") ?: return@withContext emptyList()
        val included = json.optJSONArray("included") ?: JSONArray()
        val items = mutableListOf<CatalogItem>()
        for (i in 0 until included.length()) {
            val inc = included.optJSONObject(i) ?: continue
            if (inc.optString("type") == "playlists") {
                parsePlaylist(inc)?.let { items.add(CatalogItem.Collection(it)) }
            }
        }
        items
    }

    suspend fun likedSongs(): BrowseDetail = withContext(Dispatchers.IO) {
        val session = auth.session()
        if (session != null && session.userId.isNotBlank()) {
            val json = executeGet("/userCollections/${session.userId}/relationships/tracks")
            if (json != null) {
                val included = json.optJSONArray("included") ?: JSONArray()
                val tracks = mutableListOf<Track>()
                for (i in 0 until included.length()) {
                    val inc = included.optJSONObject(i) ?: continue
                    if (inc.optString("type") == "tracks") {
                        parseTrack(inc)?.let { tracks.add(it) }
                    }
                }
                return@withContext BrowseDetail(
                    id = "liked-music",
                    kind = CatalogKind.PLAYLIST,
                    title = "Favorite Tracks",
                    tracks = tracks,
                    source = TIDAL_SOURCE,
                )
            }
        }
        BrowseDetail(
            id = "liked-music",
            kind = CatalogKind.PLAYLIST,
            title = "Favorite Tracks",
            source = TIDAL_SOURCE,
        )
    }

    suspend fun sectionItems(browseId: String, params: String = ""): List<CatalogItem> {
        val searchRes = search(browseId)
        return (searchRes.tracks.map { CatalogItem.Song(it) } +
            searchRes.albums.map { CatalogItem.Record(it) } +
            searchRes.playlists.map { CatalogItem.Collection(it) }).distinctBy { it.stableId }
    }

    // --- Parser helpers ---

    private fun parseTrack(
        obj: JSONObject,
        fallbackArtist: String = "",
        fallbackAlbum: String = "",
        fallbackArtwork: String = "",
    ): Track? {
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        val attributes = obj.optJSONObject("attributes") ?: JSONObject()
        val title = attributes.optString("title", "Track")
        val durationIso = attributes.optString("duration", "")
        val durationMs = parseDurationMs(durationIso, attributes.optLong("durationSeconds", 0))
        val explicit = attributes.optBoolean("explicit", false)
        val artworkUrl = resolveArtworkUrl(attributes).ifBlank { fallbackArtwork }
        val artist = attributes.optString("artistName", fallbackArtist).ifBlank { "TIDAL Artist" }

        return Track(
            id = id,
            title = title,
            artist = artist,
            album = fallbackAlbum,
            artworkUrl = artworkUrl,
            durationMs = durationMs,
            explicit = explicit,
            playbackSource = TIDAL_SOURCE,
            artists = listOf(Artist(id = "", name = artist, artworkUrl = artworkUrl)),
        )
    }

    private fun parseAlbum(obj: JSONObject): Album? {
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        val attributes = obj.optJSONObject("attributes") ?: JSONObject()
        val title = attributes.optString("title", "Album")
        val artworkUrl = resolveArtworkUrl(attributes)
        val releaseDate = attributes.optString("releaseDate", "")
        val year = releaseDate.take(4)
        val explicit = attributes.optBoolean("explicit", false)

        return Album(
            id = id,
            title = title,
            artist = attributes.optString("artistName", "TIDAL Artist"),
            artworkUrl = artworkUrl,
            year = year,
            explicit = explicit,
        )
    }

    private fun parseArtist(obj: JSONObject): Artist? {
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        val attributes = obj.optJSONObject("attributes") ?: JSONObject()
        val name = attributes.optString("name", "Artist")
        val artworkUrl = resolveArtworkUrl(attributes)

        return Artist(
            id = id,
            name = name,
            artworkUrl = artworkUrl,
        )
    }

    private fun parsePlaylist(obj: JSONObject): Playlist? {
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        val attributes = obj.optJSONObject("attributes") ?: JSONObject()
        val title = attributes.optString("name", "Playlist")
        val description = attributes.optString("description", "")
        val artworkUrl = resolveArtworkUrl(attributes)
        val trackCount = attributes.optInt("numberOfTracks", 0).takeIf { it > 0 }

        return Playlist(
            id = id,
            title = title,
            author = "TIDAL",
            artworkUrl = artworkUrl,
            description = description,
            trackCount = trackCount,
        )
    }

    private fun resolveArtworkUrl(attributes: JSONObject): String {
        val imageLinks = attributes.optJSONArray("imageLinks")
        if (imageLinks != null && imageLinks.length() > 0) {
            val linkObj = imageLinks.optJSONObject(0)
            val href = linkObj?.optString("href", "") ?: ""
            if (href.isNotBlank()) {
                return href.replace("{width}", "750").replace("{height}", "750")
            }
        }
        val imageId = attributes.optString("imageId", "").ifBlank {
            attributes.optString("cover", "")
        }
        if (imageId.isNotBlank()) {
            val formatted = imageId.replace("-", "/")
            return "https://resources.tidal.com/images/$formatted/750x750.jpg"
        }
        return ""
    }

    private fun parseDurationMs(isoDuration: String, secondsFallback: Long): Long {
        if (secondsFallback > 0) return secondsFallback * 1000L
        if (isoDuration.startsWith("PT")) {
            var totalSeconds = 0L
            val minutes = Regex("(\\d+)M").find(isoDuration)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val seconds = Regex("(\\d+)S").find(isoDuration)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            totalSeconds = (minutes * 60) + seconds
            if (totalSeconds > 0) return totalSeconds * 1000L
        }
        return 0L
    }
}
