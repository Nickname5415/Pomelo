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
                    val body = response.body.string()
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
            val trimmed = query.trim()
            if (trimmed.isBlank()) return@withContext SearchResults()

            val json = executeGet(
                endpoint = "/searchResults",
                queryParams = mapOf(
                    "filter[query]" to trimmed,
                    "include" to "tracks,albums,artists,playlists,tracks.artists,tracks.albums,albums.coverArt,playlists.coverArt",
                ),
            ) ?: return@withContext SearchResults()

            val included = json.optJSONArray("included") ?: JSONArray()
            val artworksMap = parseArtworksMap(included)
            val artistsMap = parseArtistsMap(included)
            val albumsMap = parseAlbumsMap(included)

            val tracks = mutableListOf<Track>()
            val albums = mutableListOf<Album>()
            val artists = mutableListOf<Artist>()
            val playlists = mutableListOf<Playlist>()

            for (i in 0 until included.length()) {
                val item = included.optJSONObject(i) ?: continue
                when (item.optString("type")) {
                    "tracks" -> parseTrackWithContext(item, artistsMap, albumsMap, artworksMap)?.let { tracks.add(it) }
                    "albums" -> parseAlbumWithContext(item, artistsMap, artworksMap)?.let { albums.add(it) }
                    "artists" -> parseArtist(item)?.let { artists.add(it) }
                    "playlists" -> parsePlaylistWithContext(item, artworksMap)?.let { playlists.add(it) }
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
     * Home feed featuring recommendations, popular playlists, trending songs, and top albums.
     */
    suspend fun home(): HomeFeed = withContext(Dispatchers.IO) {
        val sections = mutableListOf<CatalogSection>()

        // 1. Featured Playlists
        val featuredPlaylistsRes = search("Hits", filter = "playlists")
        val featuredItems = featuredPlaylistsRes.playlists.map { CatalogItem.Collection(it) }
        if (featuredItems.isNotEmpty()) {
            sections.add(
                CatalogSection(
                    id = "featured-playlists",
                    title = "Featured Playlists",
                    items = featuredItems,
                ),
            )
        }

        // 2. Trending Songs
        val trendingSongsRes = search("Top Hits", filter = "songs")
        val songItems = trendingSongsRes.tracks.map { CatalogItem.Song(it) }
        if (songItems.isNotEmpty()) {
            sections.add(
                CatalogSection(
                    id = "trending-songs",
                    title = "Trending Songs",
                    items = songItems,
                ),
            )
        }

        // 3. Popular Albums
        val albumsRes = search("Hits", filter = "albums")
        val albumItems = albumsRes.albums.map { CatalogItem.Record(it) }
        if (albumItems.isNotEmpty()) {
            sections.add(
                CatalogSection(
                    id = "popular-albums",
                    title = "Popular Albums",
                    items = albumItems,
                ),
            )
        }

        // 4. TIDAL Essentials
        val discoveryRes = search("Essentials", filter = "playlists")
        val discoveryItems = discoveryRes.playlists.map { CatalogItem.Collection(it) }
        if (discoveryItems.isNotEmpty()) {
            sections.add(
                CatalogSection(
                    id = "essential-playlists",
                    title = "TIDAL Essentials",
                    items = discoveryItems,
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
            cleanId == "featured-playlists" || cleanId == "trending-songs" ||
                cleanId == "popular-albums" || cleanId == "essential-playlists" -> {
                val items = sectionItems(cleanId)
                BrowseDetail(
                    id = cleanId,
                    kind = CatalogKind.PLAYLIST,
                    title = when (cleanId) {
                        "featured-playlists" -> "Featured Playlists"
                        "trending-songs" -> "Trending Songs"
                        "popular-albums" -> "Popular Albums"
                        else -> "TIDAL Essentials"
                    },
                    tracks = items.filterIsInstance<CatalogItem.Song>().map { it.track },
                    source = TIDAL_SOURCE,
                )
            }
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
        val json = executeGet("/albums/$albumId", mapOf("include" to "items,artists,coverArt"))
            ?: return BrowseDetail(id = albumId, kind = CatalogKind.ALBUM, title = "Album", source = TIDAL_SOURCE)

        val data = json.optJSONObject("data") ?: JSONObject()
        val attributes = data.optJSONObject("attributes") ?: JSONObject()
        val included = json.optJSONArray("included") ?: JSONArray()
        val artworksMap = parseArtworksMap(included)
        val artistsMap = parseArtistsMap(included)
        val albumsMap = parseAlbumsMap(included)

        val title = attributes.optString("title", "Album")
        val rels = data.optJSONObject("relationships")
        val coverRel = rels?.optJSONObject("coverArt")?.optJSONArray("data")
        val coverArtId = coverRel?.optJSONObject(0)?.optString("id", "").orEmpty()
        val artworkUrl = artworksMap[coverArtId] ?: resolveArtworkUrl(attributes)
        val releaseDate = attributes.optString("releaseDate", "")
        val year = releaseDate.take(4)

        val tracks = mutableListOf<Track>()
        var primaryArtist = ""

        for (i in 0 until included.length()) {
            val inc = included.optJSONObject(i) ?: continue
            val type = inc.optString("type")
            if (type == "tracks") {
                parseTrackWithContext(inc, artistsMap, albumsMap, artworksMap, fallbackAlbum = title, fallbackArtwork = artworkUrl)?.let { tracks.add(it) }
            } else if (type == "artists" && primaryArtist.isEmpty()) {
                primaryArtist = inc.optJSONObject("attributes")?.optString("name", "").orEmpty()
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
        val json = executeGet("/artists/$artistId", mapOf("include" to "albums"))
            ?: return BrowseDetail(id = artistId, kind = CatalogKind.ARTIST, title = "Artist", source = TIDAL_SOURCE)

        val data = json.optJSONObject("data") ?: JSONObject()
        val attributes = data.optJSONObject("attributes") ?: JSONObject()
        val name = attributes.optString("name", "Artist")
        val included = json.optJSONArray("included") ?: JSONArray()
        val artworksMap = parseArtworksMap(included)
        val artistsMap = parseArtistsMap(included)

        val albums = mutableListOf<CatalogItem>()
        for (i in 0 until included.length()) {
            val inc = included.optJSONObject(i) ?: continue
            if (inc.optString("type") == "albums") {
                parseAlbumWithContext(inc, artistsMap + (artistId to name), artworksMap)?.let { albums.add(CatalogItem.Record(it)) }
            }
        }

        return BrowseDetail(
            id = artistId,
            kind = CatalogKind.ARTIST,
            title = name,
            artworkUrl = "",
            tracks = emptyList(),
            related = albums,
            source = TIDAL_SOURCE,
        )
    }

    private suspend fun browsePlaylist(playlistId: String): BrowseDetail {
        val json = executeGet("/playlists/$playlistId", mapOf("include" to "items,coverArt"))
            ?: return BrowseDetail(id = playlistId, kind = CatalogKind.PLAYLIST, title = "Playlist", source = TIDAL_SOURCE)

        val data = json.optJSONObject("data") ?: JSONObject()
        val attributes = data.optJSONObject("attributes") ?: JSONObject()
        val title = attributes.optString("name", "Playlist")
        val description = attributes.optString("description", "")
        val included = json.optJSONArray("included") ?: JSONArray()
        val artworksMap = parseArtworksMap(included)
        val artistsMap = parseArtistsMap(included)
        val albumsMap = parseAlbumsMap(included)

        val rels = data.optJSONObject("relationships")
        val coverRel = rels?.optJSONObject("coverArt")?.optJSONArray("data")
        val coverArtId = coverRel?.optJSONObject(0)?.optString("id", "").orEmpty()
        val artworkUrl = artworksMap[coverArtId] ?: resolveArtworkUrl(attributes)

        val tracks = mutableListOf<Track>()
        for (i in 0 until included.length()) {
            val inc = included.optJSONObject(i) ?: continue
            if (inc.optString("type") == "tracks") {
                parseTrackWithContext(inc, artistsMap, albumsMap, artworksMap, fallbackArtwork = artworkUrl)?.let { tracks.add(it) }
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
        val artworksMap = parseArtworksMap(included)
        val artistsMap = parseArtistsMap(included)
        val albumsMap = parseAlbumsMap(included)
        val tracks = mutableListOf<Track>()
        for (i in 0 until included.length()) {
            val inc = included.optJSONObject(i) ?: continue
            if (inc.optString("type") == "tracks") {
                parseTrackWithContext(inc, artistsMap, albumsMap, artworksMap)?.let { tracks.add(it) }
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
        val sections = mutableListOf<CatalogSection>()
        val userPlaylists = playlists()
        if (userPlaylists.isNotEmpty()) {
            sections.add(CatalogSection(id = "library-playlists", title = "Playlists", items = userPlaylists))
        }
        val userAlbums = savedAlbums()
        if (userAlbums.isNotEmpty()) {
            sections.add(CatalogSection(id = "library-albums", title = "Albums", items = userAlbums))
        }
        sections
    }

    suspend fun playlists(): List<CatalogItem> = withContext(Dispatchers.IO) {
        val session = auth.session() ?: return@withContext emptyList()
        val json = executeGet("/userCollectionPlaylists/me", mapOf("include" to "items,items.coverArt"))
            ?: (if (session.userId.isNotBlank()) executeGet("/userCollectionPlaylists/${session.userId}", mapOf("include" to "items,items.coverArt")) else null)
            ?: return@withContext emptyList()

        val included = json.optJSONArray("included") ?: JSONArray()
        val artworksMap = parseArtworksMap(included)
        val items = mutableListOf<CatalogItem>()
        for (i in 0 until included.length()) {
            val inc = included.optJSONObject(i) ?: continue
            if (inc.optString("type") == "playlists") {
                parsePlaylistWithContext(inc, artworksMap)?.let { items.add(CatalogItem.Collection(it)) }
            }
        }
        items
    }

    suspend fun savedAlbums(): List<CatalogItem> = withContext(Dispatchers.IO) {
        val session = auth.session() ?: return@withContext emptyList()
        val json = executeGet("/userCollectionAlbums/me", mapOf("include" to "items,items.artists,items.coverArt"))
            ?: (if (session.userId.isNotBlank()) executeGet("/userCollectionAlbums/${session.userId}", mapOf("include" to "items,items.artists,items.coverArt")) else null)
            ?: return@withContext emptyList()

        val included = json.optJSONArray("included") ?: JSONArray()
        val artworksMap = parseArtworksMap(included)
        val artistsMap = parseArtistsMap(included)
        val items = mutableListOf<CatalogItem>()
        for (i in 0 until included.length()) {
            val inc = included.optJSONObject(i) ?: continue
            if (inc.optString("type") == "albums") {
                parseAlbumWithContext(inc, artistsMap, artworksMap)?.let { items.add(CatalogItem.Record(it)) }
            }
        }
        items
    }

    suspend fun likedSongs(): BrowseDetail = withContext(Dispatchers.IO) {
        val session = auth.session()
        if (session != null) {
            val json = executeGet(
                "/userCollectionTracks/me",
                mapOf("include" to "items,items.artists,items.albums,items.albums.coverArt"),
            ) ?: (if (session.userId.isNotBlank()) {
                executeGet(
                    "/userCollectionTracks/${session.userId}",
                    mapOf("include" to "items,items.artists,items.albums,items.albums.coverArt"),
                )
            } else null)

            if (json != null) {
                val included = json.optJSONArray("included") ?: JSONArray()
                val artworksMap = parseArtworksMap(included)
                val artistsMap = parseArtistsMap(included)
                val albumsMap = parseAlbumsMap(included)
                val tracks = mutableListOf<Track>()
                for (i in 0 until included.length()) {
                    val inc = included.optJSONObject(i) ?: continue
                    if (inc.optString("type") == "tracks") {
                        parseTrackWithContext(inc, artistsMap, albumsMap, artworksMap)?.let { tracks.add(it) }
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
        val query = when (browseId) {
            "featured-playlists" -> "Hits"
            "trending-songs" -> "Top Hits"
            "popular-albums" -> "Hits"
            "essential-playlists" -> "Essentials"
            else -> browseId
        }
        val filter = when (browseId) {
            "featured-playlists", "essential-playlists" -> "playlists"
            "trending-songs" -> "songs"
            "popular-albums" -> "albums"
            else -> "all"
        }
        val searchRes = search(query, filter = filter)
        return (searchRes.tracks.map { CatalogItem.Song(it) } +
            searchRes.albums.map { CatalogItem.Record(it) } +
            searchRes.playlists.map { CatalogItem.Collection(it) }).distinctBy { it.stableId }
    }

    // --- Parser helpers ---

    data class ParsedAlbumInfo(
        val title: String,
        val coverArtId: String,
        val releaseDate: String,
        val explicit: Boolean,
    )

    private fun parseArtworksMap(included: JSONArray): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (i in 0 until included.length()) {
            val item = included.optJSONObject(i) ?: continue
            if (item.optString("type") == "artworks") {
                val id = item.optString("id")
                val files = item.optJSONObject("attributes")?.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    var bestUrl = ""
                    var maxWidth = 0
                    for (f in 0 until files.length()) {
                        val fileObj = files.optJSONObject(f) ?: continue
                        val width = fileObj.optJSONObject("meta")?.optInt("width", 0) ?: 0
                        val href = fileObj.optString("href", "")
                        if (href.isNotBlank() && (bestUrl.isEmpty() || width > maxWidth)) {
                            bestUrl = href
                            maxWidth = width
                        }
                    }
                    if (bestUrl.isNotBlank()) {
                        map[id] = bestUrl
                    }
                }
            }
        }
        return map
    }

    private fun parseArtistsMap(included: JSONArray): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (i in 0 until included.length()) {
            val item = included.optJSONObject(i) ?: continue
            if (item.optString("type") == "artists") {
                val id = item.optString("id")
                val name = item.optJSONObject("attributes")?.optString("name", "").orEmpty()
                if (id.isNotBlank() && name.isNotBlank()) {
                    map[id] = name
                }
            }
        }
        return map
    }

    private fun parseAlbumsMap(included: JSONArray): Map<String, ParsedAlbumInfo> {
        val map = mutableMapOf<String, ParsedAlbumInfo>()
        for (i in 0 until included.length()) {
            val item = included.optJSONObject(i) ?: continue
            if (item.optString("type") == "albums") {
                val id = item.optString("id")
                val attrs = item.optJSONObject("attributes") ?: JSONObject()
                val title = attrs.optString("title", "Album")
                val rels = item.optJSONObject("relationships")
                val coverRel = rels?.optJSONObject("coverArt")?.optJSONArray("data")
                val coverArtId = coverRel?.optJSONObject(0)?.optString("id", "").orEmpty()
                val releaseDate = attrs.optString("releaseDate", "")
                val explicit = attrs.optBoolean("explicit", false)
                map[id] = ParsedAlbumInfo(
                    title = title,
                    coverArtId = coverArtId,
                    releaseDate = releaseDate,
                    explicit = explicit,
                )
            }
        }
        return map
    }

    private fun parseTrackWithContext(
        obj: JSONObject,
        artistsMap: Map<String, String>,
        albumsMap: Map<String, ParsedAlbumInfo>,
        artworksMap: Map<String, String>,
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

        val rels = obj.optJSONObject("relationships")
        val artistRel = rels?.optJSONObject("artists")?.optJSONArray("data")
        val artistId = artistRel?.optJSONObject(0)?.optString("id", "").orEmpty()
        val artistName = artistsMap[artistId]?.ifBlank { null }
            ?: attributes.optString("artistName", fallbackArtist).ifBlank { "TIDAL Artist" }

        val albumRel = rels?.optJSONObject("albums")?.optJSONArray("data")
        val albumId = albumRel?.optJSONObject(0)?.optString("id", "").orEmpty()
        val albumInfo = albumsMap[albumId]
        val albumTitle = albumInfo?.title?.ifBlank { null } ?: fallbackAlbum

        val artworkUrl = albumInfo?.coverArtId?.let { artworksMap[it] }
            ?: resolveArtworkUrl(attributes).ifBlank { fallbackArtwork }

        return Track(
            id = id,
            title = title,
            artist = artistName,
            album = albumTitle,
            artworkUrl = artworkUrl,
            durationMs = durationMs,
            explicit = explicit,
            playbackSource = TIDAL_SOURCE,
            artists = listOf(Artist(id = artistId, name = artistName, artworkUrl = artworkUrl)),
        )
    }

    private fun parseAlbumWithContext(
        obj: JSONObject,
        artistsMap: Map<String, String>,
        artworksMap: Map<String, String>,
    ): Album? {
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        val attributes = obj.optJSONObject("attributes") ?: JSONObject()
        val title = attributes.optString("title", "Album")
        val rels = obj.optJSONObject("relationships")
        val coverRel = rels?.optJSONObject("coverArt")?.optJSONArray("data")
        val coverArtId = coverRel?.optJSONObject(0)?.optString("id", "").orEmpty()
        val artworkUrl = artworksMap[coverArtId] ?: resolveArtworkUrl(attributes)
        val releaseDate = attributes.optString("releaseDate", "")
        val year = releaseDate.take(4)
        val explicit = attributes.optBoolean("explicit", false)

        val artistRel = rels?.optJSONObject("artists")?.optJSONArray("data")
        val artistId = artistRel?.optJSONObject(0)?.optString("id", "").orEmpty()
        val artist = artistsMap[artistId]?.ifBlank { null }
            ?: attributes.optString("artistName", "TIDAL Artist")

        return Album(
            id = id,
            title = title,
            artist = artist,
            artworkUrl = artworkUrl,
            year = year,
            explicit = explicit,
        )
    }

    private fun parsePlaylistWithContext(
        obj: JSONObject,
        artworksMap: Map<String, String>,
    ): Playlist? {
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        val attributes = obj.optJSONObject("attributes") ?: JSONObject()
        val title = attributes.optString("name", "Playlist")
        val description = attributes.optString("description", "")
        val rels = obj.optJSONObject("relationships")
        val coverRel = rels?.optJSONObject("coverArt")?.optJSONArray("data")
        val coverArtId = coverRel?.optJSONObject(0)?.optString("id", "").orEmpty()
        val artworkUrl = artworksMap[coverArtId] ?: resolveArtworkUrl(attributes)
        val trackCount = attributes.optInt("numberOfTracks", 0).takeIf { it > 0 }
            ?: attributes.optInt("numberOfItems", 0).takeIf { it > 0 }

        return Playlist(
            id = id,
            title = title,
            author = "TIDAL",
            artworkUrl = artworkUrl,
            description = description,
            trackCount = trackCount,
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
