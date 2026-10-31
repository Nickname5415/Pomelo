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
import xyz.stuffbyren.pomelo.auth.TidalWebSession
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
 * Communicates with TIDAL Open API v2 and client v1 endpoints to provide tracks, albums,
 * artists, playlists, and search results to the Pomelo application.
 */
class TidalCatalogRepository(
    private val http: OkHttpClient,
    private val auth: TidalAuthRepository,
    private val webSession: TidalWebSession? = null,
) {
    private val v1Catalog = webSession?.let { TidalV1Catalog(http, it) }

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

    suspend fun search(query: String, filter: String = "all", guest: Boolean = false): SearchResults =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return@withContext SearchResults()

            if (v1Catalog != null && webSession?.hasToken() == true) {
                val v1Res = runCatching { v1Catalog.search(trimmed, filter) }.getOrNull()
                if (v1Res != null && (v1Res.tracks.isNotEmpty() || v1Res.albums.isNotEmpty() || v1Res.artists.isNotEmpty() || v1Res.playlists.isNotEmpty())) {
                    return@withContext v1Res
                }
            }

            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val params = mutableMapOf(
                "query" to encoded,
                "include" to "tracks,albums,artists,playlists,tracks.artists,tracks.albums,tracks.albums.coverArt,albums.artists,albums.coverArt,playlists.coverArt",
            )
            val json = executeGet("/searchResults/$encoded", params)
                ?: executeGet("/search", params)
                ?: return@withContext SearchResults()

            val included = json.optJSONArray("included") ?: JSONArray()
            val artworksMap = TidalOpenApiParser.parseArtworksMap(included)
            val artistsMap = TidalOpenApiParser.parseArtistsMap(included)
            val albumsMap = TidalOpenApiParser.parseAlbumsMap(included)

            val tracks = mutableListOf<Track>()
            val albums = mutableListOf<Album>()
            val artists = mutableListOf<Artist>()
            val playlists = mutableListOf<Playlist>()

            for (i in 0 until included.length()) {
                val item = included.optJSONObject(i) ?: continue
                when (item.optString("type")) {
                    "tracks" -> TidalOpenApiParser.parseTrackWithContext(item, artistsMap, albumsMap, artworksMap)?.let { tracks.add(it) }
                    "albums" -> TidalOpenApiParser.parseAlbumWithContext(item, artistsMap, artworksMap)?.let { albums.add(it) }
                    "artists" -> TidalOpenApiParser.parseArtist(item)?.let { artists.add(it) }
                    "playlists" -> TidalOpenApiParser.parsePlaylistWithContext(item, artworksMap)?.let { playlists.add(it) }
                }
            }
            SearchResults(tracks = tracks, albums = albums, artists = artists, playlists = playlists)
        }

    suspend fun home(): HomeFeed = withContext(Dispatchers.IO) {
        val realHome = TidalHomeFeedFetcher.fetchHome(http, webSession, auth)
        if (realHome != null && realHome.sections.isNotEmpty()) return@withContext realHome
        val fallback = listOf("featured-playlists" to "Featured Playlists", "trending-songs" to "Top Songs", "popular-albums" to "Popular Albums")
            .mapNotNull { (id, title) -> sectionItems(id).takeIf { it.isNotEmpty() }?.let { CatalogSection(id = id, title = title, items = it) } }
        HomeFeed(sections = fallback)
    }

    suspend fun browse(id: String, seed: CatalogItem? = null): BrowseDetail = withContext(Dispatchers.IO) {
        val cleanId = id.trim()
        if (cleanId == "liked-music") return@withContext likedSongs()

        val isArtist = cleanId.startsWith("artist") || seed is CatalogItem.Performer
        val isAlbum = cleanId.startsWith("album") || seed is CatalogItem.Record
        val isPlaylist = cleanId.startsWith("playlist") || cleanId.startsWith("mix") ||
            seed is CatalogItem.Collection || (!isArtist && !isAlbum && cleanId.contains("-"))

        val v1 = v1Catalog
        if (v1 != null && webSession?.hasToken() == true) {
            val v1Detail = runCatching {
                when {
                    isArtist -> v1.getArtist(cleanId)
                    isAlbum -> v1.getAlbum(cleanId)
                    isPlaylist -> v1.getPlaylist(cleanId)
                    else -> v1.getAlbum(cleanId) ?: v1.getArtist(cleanId)
                }
            }.getOrNull()
            if (v1Detail != null) {
                return@withContext if (seed is CatalogItem.Collection && v1Detail.artworkUrl.isBlank()) {
                    v1Detail.copy(
                        title = seed.playlist.title.ifBlank { v1Detail.title },
                        description = seed.playlist.description.ifBlank { v1Detail.description },
                        artworkUrl = seed.playlist.artworkUrl.ifBlank { v1Detail.artworkUrl },
                    )
                } else {
                    v1Detail
                }
            }
        }

        when {
            cleanId.startsWith("section-") -> {
                val sectionName = cleanId.removePrefix("section-")
                val items = sectionItems(sectionName)
                BrowseDetail(
                    id = cleanId,
                    kind = CatalogKind.PLAYLIST,
                    title = sectionName.replace("-", " ").replaceFirstChar { it.uppercase() },
                    tracks = items.filterIsInstance<CatalogItem.Song>().map { it.track },
                    source = TIDAL_SOURCE,
                )
            }
            isArtist -> browseArtist(cleanId.removePrefix("artist-").removePrefix("artist/").removePrefix("artist:"))
            isAlbum -> browseAlbum(cleanId.removePrefix("album-").removePrefix("album/").removePrefix("album:"))
            isPlaylist -> browsePlaylist(cleanId.removePrefix("playlist-").removePrefix("playlist/").removePrefix("playlist:"))
            else -> {
                val album = browseAlbum(cleanId)
                if (album.tracks.isNotEmpty()) album else browseArtist(cleanId)
            }
        }
    }

    fun browsePages(id: String, seed: CatalogItem? = null): Flow<BrowseDetail> = flow {
        emit(browse(id, seed))
    }.flowOn(Dispatchers.IO)

    private suspend fun browseAlbum(albumId: String): BrowseDetail {
        val json = executeGet("/albums/$albumId", mapOf("include" to "items,artists,coverArt"))
            ?: return BrowseDetail(id = albumId, kind = CatalogKind.ALBUM, title = "Album", source = TIDAL_SOURCE)

        val data = json.optJSONObject("data") ?: JSONObject()
        val attributes = data.optJSONObject("attributes") ?: JSONObject()
        val included = json.optJSONArray("included") ?: JSONArray()
        val artworksMap = TidalOpenApiParser.parseArtworksMap(included)
        val artistsMap = TidalOpenApiParser.parseArtistsMap(included)
        val albumsMap = TidalOpenApiParser.parseAlbumsMap(included)

        val title = attributes.optString("title", "Album")
        val rels = data.optJSONObject("relationships")
        val coverRel = rels?.optJSONObject("coverArt")?.optJSONArray("data")
        val coverArtId = coverRel?.optJSONObject(0)?.optString("id", "").orEmpty()
        val artworkUrl = artworksMap[coverArtId] ?: TidalOpenApiParser.resolveArtworkUrl(attributes)
        val releaseDate = attributes.optString("releaseDate", "")

        val tracks = mutableListOf<Track>()
        var primaryArtist = ""
        for (i in 0 until included.length()) {
            val inc = included.optJSONObject(i) ?: continue
            if (inc.optString("type") == "artists" && primaryArtist.isEmpty()) {
                primaryArtist = inc.optJSONObject("attributes")?.optString("name", "").orEmpty()
            }
        }

        for (i in 0 until included.length()) {
            val inc = included.optJSONObject(i) ?: continue
            if (inc.optString("type") == "tracks") {
                TidalOpenApiParser.parseTrackWithContext(
                    inc, artistsMap, albumsMap, artworksMap,
                    fallbackArtist = primaryArtist, fallbackAlbum = title, fallbackArtwork = artworkUrl,
                )?.let { tracks.add(it) }
            }
        }

        return BrowseDetail(
            id = albumId,
            kind = CatalogKind.ALBUM,
            title = title,
            artist = primaryArtist,
            year = releaseDate.take(4),
            artworkUrl = artworkUrl,
            tracks = tracks,
            source = TIDAL_SOURCE,
        )
    }

    private suspend fun browseArtist(artistId: String): BrowseDetail {
        if (webSession?.hasToken() == true) {
            val v1 = runCatching { v1Catalog?.getArtist(artistId) }.getOrNull()
            if (v1 != null) return v1
        }
        val json = executeGet("/artists/$artistId", mapOf("include" to "albums"))
            ?: return BrowseDetail(id = artistId, kind = CatalogKind.ARTIST, title = "Artist", source = TIDAL_SOURCE)

        val data = json.optJSONObject("data") ?: JSONObject()
        val attributes = data.optJSONObject("attributes") ?: JSONObject()
        val name = attributes.optString("name", "Artist")
        val included = json.optJSONArray("included") ?: JSONArray()
        val artworksMap = TidalOpenApiParser.parseArtworksMap(included)
        val artistsMap = TidalOpenApiParser.parseArtistsMap(included)

        val albums = mutableListOf<CatalogItem>()
        for (i in 0 until included.length()) {
            val inc = included.optJSONObject(i) ?: continue
            if (inc.optString("type") == "albums") {
                TidalOpenApiParser.parseAlbumWithContext(inc, artistsMap + (artistId to name), artworksMap, fallbackArtist = name)
                    ?.let { albums.add(CatalogItem.Record(it)) }
            }
        }

        return BrowseDetail(
            id = artistId,
            kind = CatalogKind.ARTIST,
            title = name,
            artworkUrl = "",
            tracks = emptyList(),
            sections = if (albums.isNotEmpty()) listOf(CatalogSection(id = "albums", title = "Albums", items = albums)) else emptyList(),
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
        val artworksMap = TidalOpenApiParser.parseArtworksMap(included)
        val artistsMap = TidalOpenApiParser.parseArtistsMap(included)
        val albumsMap = TidalOpenApiParser.parseAlbumsMap(included)

        val rels = data.optJSONObject("relationships")
        val coverRel = rels?.optJSONObject("coverArt")?.optJSONArray("data")
        val coverArtId = coverRel?.optJSONObject(0)?.optString("id", "").orEmpty()
        val artworkUrl = artworksMap[coverArtId] ?: TidalOpenApiParser.resolveArtworkUrl(attributes)

        val tracks = mutableListOf<Track>()
        for (i in 0 until included.length()) {
            val inc = included.optJSONObject(i) ?: continue
            if (inc.optString("type") == "tracks") {
                TidalOpenApiParser.parseTrackWithContext(inc, artistsMap, albumsMap, artworksMap, fallbackArtwork = artworkUrl)?.let { tracks.add(it) }
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
        val artworksMap = TidalOpenApiParser.parseArtworksMap(included)
        val artistsMap = TidalOpenApiParser.parseArtistsMap(included)
        val albumsMap = TidalOpenApiParser.parseAlbumsMap(included)
        val tracks = mutableListOf<Track>()
        for (i in 0 until included.length()) {
            val inc = included.optJSONObject(i) ?: continue
            if (inc.optString("type") == "tracks") {
                TidalOpenApiParser.parseTrackWithContext(inc, artistsMap, albumsMap, artworksMap)?.let { tracks.add(it) }
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
                TidalOpenApiParser.parseArtist(inc)?.let { artists.add(it) }
            }
        }
        artists
    }

    suspend fun library(): List<CatalogSection> = withContext(Dispatchers.IO) {
        if (v1Catalog != null && webSession?.hasToken() == true) {
            val v1Lib = runCatching { v1Catalog.library() }.getOrNull()
            if (!v1Lib.isNullOrEmpty()) return@withContext v1Lib
        }

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
        if (v1Catalog != null && webSession?.hasToken() == true) {
            val v1Playlists = runCatching { v1Catalog.getPlaylists() }.getOrNull()
            if (!v1Playlists.isNullOrEmpty()) return@withContext v1Playlists.map { CatalogItem.Collection(it) }
        }

        val session = auth.session() ?: return@withContext emptyList()
        val json = executeGet("/userCollectionPlaylists/me", mapOf("include" to "items,items.coverArt"))
            ?: (if (session.userId.isNotBlank()) executeGet("/userCollectionPlaylists/${session.userId}", mapOf("include" to "items,items.coverArt")) else null)
            ?: return@withContext emptyList()

        val included = json.optJSONArray("included") ?: JSONArray()
        val artworksMap = TidalOpenApiParser.parseArtworksMap(included)
        val items = mutableListOf<CatalogItem>()
        for (i in 0 until included.length()) {
            val inc = included.optJSONObject(i) ?: continue
            if (inc.optString("type") == "playlists") {
                TidalOpenApiParser.parsePlaylistWithContext(inc, artworksMap)?.let { items.add(CatalogItem.Collection(it)) }
            }
        }
        items
    }

    suspend fun savedAlbums(): List<CatalogItem> = withContext(Dispatchers.IO) {
        if (v1Catalog != null && webSession?.hasToken() == true) {
            val v1Albums = runCatching { v1Catalog.getSavedAlbums() }.getOrNull()
            if (!v1Albums.isNullOrEmpty()) return@withContext v1Albums.map { CatalogItem.Record(it) }
        }

        val session = auth.session() ?: return@withContext emptyList()
        val json = executeGet("/userCollectionAlbums/me", mapOf("include" to "items,items.artists,items.coverArt"))
            ?: (if (session.userId.isNotBlank()) executeGet("/userCollectionAlbums/${session.userId}", mapOf("include" to "items,items.artists,items.coverArt")) else null)
            ?: return@withContext emptyList()

        val included = json.optJSONArray("included") ?: JSONArray()
        val artworksMap = TidalOpenApiParser.parseArtworksMap(included)
        val artistsMap = TidalOpenApiParser.parseArtistsMap(included)
        val items = mutableListOf<CatalogItem>()
        for (i in 0 until included.length()) {
            val inc = included.optJSONObject(i) ?: continue
            if (inc.optString("type") == "albums") {
                TidalOpenApiParser.parseAlbumWithContext(inc, artistsMap, artworksMap)?.let { items.add(CatalogItem.Record(it)) }
            }
        }
        items
    }

    suspend fun likedSongs(): BrowseDetail = withContext(Dispatchers.IO) {
        if (v1Catalog != null && webSession?.hasToken() == true) {
            val v1Tracks = runCatching { v1Catalog.getLikedTracks() }.getOrNull()
            if (!v1Tracks.isNullOrEmpty()) {
                return@withContext BrowseDetail(
                    id = "liked-music",
                    kind = CatalogKind.PLAYLIST,
                    title = "Favorite Tracks",
                    tracks = v1Tracks,
                    source = TIDAL_SOURCE,
                )
            }
        }

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
                val artworksMap = TidalOpenApiParser.parseArtworksMap(included)
                val artistsMap = TidalOpenApiParser.parseArtistsMap(included)
                val albumsMap = TidalOpenApiParser.parseAlbumsMap(included)
                val tracks = mutableListOf<Track>()
                for (i in 0 until included.length()) {
                    val inc = included.optJSONObject(i) ?: continue
                    if (inc.optString("type") == "tracks") {
                        TidalOpenApiParser.parseTrackWithContext(inc, artistsMap, albumsMap, artworksMap)?.let { tracks.add(it) }
                    }
                }
                return@withContext BrowseDetail(id = "liked-music", kind = CatalogKind.PLAYLIST, title = "Favorite Tracks", tracks = tracks, source = TIDAL_SOURCE)
            }
        }
        BrowseDetail(id = "liked-music", kind = CatalogKind.PLAYLIST, title = "Favorite Tracks", source = TIDAL_SOURCE)
    }

    suspend fun sectionItems(browseId: String, params: String = ""): List<CatalogItem> {
        if (browseId.startsWith("pages/")) {
            val items = TidalHomeFeedFetcher.fetchSectionItems(http, webSession, auth, browseId)
            if (items.isNotEmpty()) return items
        }
        val (query, filter) = when (browseId) {
            "featured-playlists" -> "Hits" to "playlists"
            "trending-songs" -> "Top Hits" to "songs"
            "popular-albums" -> "Hits" to "albums"
            "essential-playlists" -> "Essentials" to "playlists"
            else -> browseId to "all"
        }
        val searchRes = search(query, filter = filter)
        return (searchRes.tracks.map { CatalogItem.Song(it) } +
            searchRes.albums.map { CatalogItem.Record(it) } +
            searchRes.playlists.map { CatalogItem.Collection(it) }).distinctBy { it.stableId }
    }
}
