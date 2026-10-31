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
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import xyz.stuffbyren.pomelo.auth.TidalWebSession
import xyz.stuffbyren.pomelo.model.Album
import xyz.stuffbyren.pomelo.model.Artist
import xyz.stuffbyren.pomelo.model.BrowseDetail
import xyz.stuffbyren.pomelo.model.CatalogItem
import xyz.stuffbyren.pomelo.model.CatalogKind
import xyz.stuffbyren.pomelo.model.CatalogSection
import xyz.stuffbyren.pomelo.model.Playlist
import xyz.stuffbyren.pomelo.model.SearchResults
import xyz.stuffbyren.pomelo.model.TIDAL_SOURCE
import xyz.stuffbyren.pomelo.model.Track
import java.net.URLEncoder

private const val TAG = "TidalV1Catalog"
private const val TIDAL_V1_BASE = "https://api.tidal.com/v1"
private const val CLIENT_ID = "6BDSRdpK9hqEBTgU"

/**
 * Handles TIDAL Client v1 API operations for user favorites, playlists,
 * albums, artists, and search.
 */
class TidalV1Catalog(
    private val http: OkHttpClient,
    private val webSession: TidalWebSession,
) {
    private suspend fun executeGet(endpoint: String, queryParams: Map<String, String> = emptyMap()): JSONObject? =
        withContext(Dispatchers.IO) {
            val token = webSession.getAccessToken() ?: return@withContext null
            val countryCode = webSession.getCountryCode() ?: "US"
            val uriBuilder = Uri.parse("$TIDAL_V1_BASE$endpoint").buildUpon()
            queryParams.forEach { (k, v) -> uriBuilder.appendQueryParameter(k, v) }
            if (!queryParams.containsKey("countryCode")) {
                uriBuilder.appendQueryParameter("countryCode", countryCode)
            }

            val request = Request.Builder()
                .url(uriBuilder.build().toString())
                .header("Authorization", "Bearer $token")
                .header("x-tidal-token", CLIENT_ID)
                .build()

            runCatching {
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "TIDAL v1 API error: ${response.code} ${response.message} on $endpoint")
                        return@use null
                    }
                    val body = response.body.string()
                    if (body.isBlank()) null else JSONObject(body)
                }
            }.onFailure { Log.w(TAG, "TIDAL v1 API exception on $endpoint", it) }.getOrNull()
        }

    suspend fun getLikedTracks(): List<Track> = withContext(Dispatchers.IO) {
        val uid = webSession.getUserId() ?: return@withContext emptyList()
        val allTracks = mutableListOf<Track>()
        var offset = 0
        val pageSize = 100
        val maxTracks = 500

        while (offset < maxTracks) {
            val json = executeGet("/users/$uid/favorites/tracks", mapOf("limit" to pageSize.toString(), "offset" to offset.toString()))
                ?: break
            val items = json.optJSONArray("items") ?: break
            if (items.length() == 0) break

            for (i in 0 until items.length()) {
                val entry = items.optJSONObject(i) ?: continue
                val trackObj = entry.optJSONObject("item") ?: entry
                parseTrack(trackObj)?.let { allTracks.add(it) }
            }

            val total = json.optInt("totalNumberOfItems", allTracks.size)
            offset += items.length()
            if (offset >= total) break
        }
        Log.i(TAG, "Loaded ${allTracks.size} liked tracks for user $uid")
        allTracks
    }

    suspend fun getSavedAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val uid = webSession.getUserId() ?: return@withContext emptyList()
        val json = executeGet("/users/$uid/favorites/albums", mapOf("limit" to "100")) ?: return@withContext emptyList()
        val items = json.optJSONArray("items") ?: return@withContext emptyList()
        val albums = mutableListOf<Album>()
        for (i in 0 until items.length()) {
            val entry = items.optJSONObject(i) ?: continue
            val albumObj = entry.optJSONObject("item") ?: entry
            parseAlbum(albumObj)?.let { albums.add(it) }
        }
        Log.i(TAG, "Loaded ${albums.size} saved albums for user $uid")
        albums
    }

    suspend fun getSavedArtists(): List<Artist> = withContext(Dispatchers.IO) {
        val uid = webSession.getUserId() ?: return@withContext emptyList()
        val json = executeGet("/users/$uid/favorites/artists", mapOf("limit" to "100")) ?: return@withContext emptyList()
        val items = json.optJSONArray("items") ?: return@withContext emptyList()
        val artists = mutableListOf<Artist>()
        for (i in 0 until items.length()) {
            val entry = items.optJSONObject(i) ?: continue
            val artistObj = entry.optJSONObject("item") ?: entry
            parseArtist(artistObj)?.let { artists.add(it) }
        }
        Log.i(TAG, "Loaded ${artists.size} saved artists for user $uid")
        artists
    }

    suspend fun getPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        val uid = webSession.getUserId() ?: return@withContext emptyList()
        val playlists = mutableListOf<Playlist>()
        val seen = mutableSetOf<String>()

        val userJson = executeGet("/users/$uid/playlists", mapOf("limit" to "100"))
        userJson?.optJSONArray("items")?.let { items ->
            for (i in 0 until items.length()) {
                val obj = items.optJSONObject(i) ?: continue
                val p = obj.optJSONObject("item") ?: obj
                parsePlaylist(p)?.let { if (seen.add(it.id)) playlists.add(it) }
            }
        }

        val favJson = executeGet("/users/$uid/favorites/playlists", mapOf("limit" to "100"))
        favJson?.optJSONArray("items")?.let { items ->
            for (i in 0 until items.length()) {
                val obj = items.optJSONObject(i) ?: continue
                val p = obj.optJSONObject("item") ?: obj
                parsePlaylist(p)?.let { if (seen.add(it.id)) playlists.add(it) }
            }
        }
        Log.i(TAG, "Loaded ${playlists.size} playlists for user $uid")
        playlists
    }

    suspend fun library(): List<CatalogSection> = withContext(Dispatchers.IO) {
        val sections = mutableListOf<CatalogSection>()
        val playlists = getPlaylists()
        if (playlists.isNotEmpty()) {
            sections.add(CatalogSection(id = "library-playlists", title = "Playlists", items = playlists.map { CatalogItem.Collection(it) }))
        }
        val albums = getSavedAlbums()
        if (albums.isNotEmpty()) {
            sections.add(CatalogSection(id = "library-albums", title = "Albums", items = albums.map { CatalogItem.Record(it) }))
        }
        val artists = getSavedArtists()
        if (artists.isNotEmpty()) {
            sections.add(CatalogSection(id = "library-artists", title = "Artists", items = artists.map { CatalogItem.Performer(it) }))
        }
        sections
    }

    suspend fun getAlbum(albumId: String): BrowseDetail? = withContext(Dispatchers.IO) {
        val cleanId = albumId.removePrefix("album:").removePrefix("album-").removePrefix("album/")
        val albumJson = executeGet("/albums/$cleanId") ?: return@withContext null
        val album = parseAlbum(albumJson) ?: return@withContext null

        val itemsJson = executeGet("/albums/$cleanId/items", mapOf("limit" to "100"))
        val tracks = mutableListOf<Track>()
        itemsJson?.optJSONArray("items")?.let { items ->
            for (i in 0 until items.length()) {
                val entry = items.optJSONObject(i) ?: continue
                val trackObj = entry.optJSONObject("item") ?: entry
                parseTrack(trackObj, fallbackAlbum = album.title, fallbackArtwork = album.artworkUrl)?.let { tracks.add(it) }
            }
        }

        BrowseDetail(
            id = cleanId,
            kind = CatalogKind.ALBUM,
            title = album.title,
            artist = album.artist,
            year = album.year,
            artworkUrl = album.artworkUrl,
            tracks = tracks,
            source = TIDAL_SOURCE,
        )
    }

    suspend fun getPlaylist(playlistId: String): BrowseDetail? = withContext(Dispatchers.IO) {
        val cleanId = playlistId.removePrefix("playlist:").removePrefix("playlist-").removePrefix("playlist/")
            .removePrefix("mix:").removePrefix("mix-").removePrefix("mix/")
        val playlistJson = executeGet("/playlists/$cleanId")
        if (playlistJson != null) {
            val playlist = parsePlaylist(playlistJson)
            if (playlist != null) {
                val itemsJson = executeGet("/playlists/$cleanId/items", mapOf("limit" to "100"))
                val tracks = mutableListOf<Track>()
                itemsJson?.optJSONArray("items")?.let { items ->
                    for (i in 0 until items.length()) {
                        val entry = items.optJSONObject(i) ?: continue
                        val trackObj = entry.optJSONObject("item") ?: entry
                        parseTrack(trackObj)?.let { tracks.add(it) }
                    }
                }
                return@withContext BrowseDetail(
                    id = cleanId,
                    kind = CatalogKind.PLAYLIST,
                    title = playlist.title,
                    description = playlist.description.orEmpty(),
                    artworkUrl = playlist.artworkUrl,
                    tracks = tracks,
                    source = TIDAL_SOURCE,
                )
            }
        }

        val mixItemsJson = executeGet("/mixes/$cleanId/items") ?: return@withContext null
        val tracks = mutableListOf<Track>()
        mixItemsJson.optJSONArray("items")?.let { items ->
            for (i in 0 until items.length()) {
                val entry = items.optJSONObject(i) ?: continue
                val trackObj = entry.optJSONObject("item") ?: entry
                parseTrack(trackObj)?.let { tracks.add(it) }
            }
        }
        BrowseDetail(
            id = cleanId,
            kind = CatalogKind.PLAYLIST,
            title = "Mix",
            description = "TIDAL Mix",
            artworkUrl = "",
            tracks = tracks,
            source = TIDAL_SOURCE,
        )
    }

    suspend fun getArtist(artistId: String): BrowseDetail? = withContext(Dispatchers.IO) {
        val cleanId = artistId.removePrefix("artist:").removePrefix("artist-").removePrefix("artist/")
        val artistJson = executeGet("/artists/$cleanId") ?: return@withContext null
        val artist = parseArtist(artistJson) ?: return@withContext null

        val toptracksJson = executeGet("/artists/$cleanId/toptracks", mapOf("limit" to "25"))
        val tracks = mutableListOf<Track>()
        toptracksJson?.optJSONArray("items")?.let { items ->
            for (i in 0 until items.length()) {
                val entry = items.optJSONObject(i) ?: continue
                val trackObj = entry.optJSONObject("item") ?: entry
                parseTrack(trackObj)?.let { tracks.add(it) }
            }
        }

        val albumsJson = executeGet("/artists/$cleanId/albums", mapOf("limit" to "50"))
        val albums = mutableListOf<CatalogItem>()
        albumsJson?.optJSONArray("items")?.let { items ->
            for (i in 0 until items.length()) {
                val entry = items.optJSONObject(i) ?: continue
                val albumObj = entry.optJSONObject("item") ?: entry
                parseAlbum(albumObj)?.let { albums.add(CatalogItem.Record(it)) }
            }
        }

        val sections = mutableListOf<CatalogSection>()
        if (albums.isNotEmpty()) {
            sections.add(CatalogSection(id = "albums", title = "Albums", items = albums))
        }

        val similarJson = executeGet("/artists/$cleanId/similar", mapOf("limit" to "20"))
        val similar = mutableListOf<CatalogItem>()
        similarJson?.optJSONArray("items")?.let { items ->
            for (i in 0 until items.length()) {
                val entry = items.optJSONObject(i) ?: continue
                val artObj = entry.optJSONObject("item") ?: entry
                parseArtist(artObj)?.let { similar.add(CatalogItem.Performer(it)) }
            }
        }
        if (similar.isNotEmpty()) {
            sections.add(CatalogSection(id = "related", title = "Fans also like", items = similar))
        }

        val bioJson = executeGet("/artists/$cleanId/bio")
        val rawBio = bioJson?.optString("text", "").orEmpty()
        val bioText = rawBio
            .replace("<br/>", "\n\n")
            .replace("<br>", "\n\n")
            .replace(Regex("""\[/?wimpLink[^\]]*\]"""), "")
            .trim()

        BrowseDetail(
            id = artistId,
            kind = CatalogKind.ARTIST,
            title = artist.name,
            artworkUrl = artist.artworkUrl,
            description = bioText,
            tracks = tracks,
            sections = sections,
            related = similar,
            source = TIDAL_SOURCE,
        )
    }

    suspend fun search(query: String, filter: String = "all"): SearchResults = withContext(Dispatchers.IO) {
        val json = executeGet("/search", mapOf("query" to query.trim(), "limit" to "25")) ?: return@withContext SearchResults()

        val tracks = mutableListOf<Track>()
        json.optJSONObject("tracks")?.optJSONArray("items")?.let { items ->
            for (i in 0 until items.length()) parseTrack(items.optJSONObject(i))?.let { tracks.add(it) }
        }

        val albums = mutableListOf<Album>()
        json.optJSONObject("albums")?.optJSONArray("items")?.let { items ->
            for (i in 0 until items.length()) parseAlbum(items.optJSONObject(i))?.let { albums.add(it) }
        }

        val artists = mutableListOf<Artist>()
        json.optJSONObject("artists")?.optJSONArray("items")?.let { items ->
            for (i in 0 until items.length()) parseArtist(items.optJSONObject(i))?.let { artists.add(it) }
        }

        val playlists = mutableListOf<Playlist>()
        json.optJSONObject("playlists")?.optJSONArray("items")?.let { items ->
            for (i in 0 until items.length()) parsePlaylist(items.optJSONObject(i))?.let { playlists.add(it) }
        }

        SearchResults(tracks = tracks, albums = albums, artists = artists, playlists = playlists)
    }

    companion object {
        fun artworkUrl(imageId: String?): String {
            if (imageId.isNullOrBlank()) return ""
            val formatted = imageId.replace("-", "/")
            return "https://resources.tidal.com/images/$formatted/750x750.jpg"
        }

        fun parseTrack(obj: JSONObject?, fallbackAlbum: String = "", fallbackArtwork: String = ""): Track? {
            if (obj == null) return null
            val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
            val title = obj.optString("title", "Track")
            val durationSeconds = obj.optLong("duration", 0)

            val artistObj = obj.optJSONObject("artist")
            val artistsArr = obj.optJSONArray("artists")
            val artistName = artistObj?.optString("name", "")?.takeIf { it.isNotBlank() }
                ?: artistsArr?.optJSONObject(0)?.optString("name", "")?.takeIf { it.isNotBlank() }
                ?: "Unknown Artist"
            val artistId = artistObj?.optString("id", "")
                ?: artistsArr?.optJSONObject(0)?.optString("id", "").orEmpty()

            val albumObj = obj.optJSONObject("album")
            val albumTitle = albumObj?.optString("title", fallbackAlbum).orEmpty()
            val albumId = albumObj?.optString("id", "").orEmpty()
            val coverId = albumObj?.optString("cover", "").orEmpty()
            val artworkUrl = artworkUrl(coverId).ifBlank { fallbackArtwork }

            val parsedArtists = mutableListOf<Artist>()
            if (artistsArr != null) {
                for (i in 0 until artistsArr.length()) {
                    val a = artistsArr.optJSONObject(i) ?: continue
                    val aName = a.optString("name", "")
                    val aId = a.optString("id", "")
                    if (aName.isNotBlank()) parsedArtists.add(Artist(id = aId, name = aName, artworkUrl = artworkUrl))
                }
            }
            if (parsedArtists.isEmpty()) {
                parsedArtists.add(Artist(id = artistId, name = artistName, artworkUrl = artworkUrl))
            }

            val audioQuality = obj.optString("audioQuality", "")
            val isHires = audioQuality.contains("HI_RES", ignoreCase = true) ||
                obj.optJSONArray("mediaMetadata")?.toString()?.contains("HIRES") == true

            return Track(
                id = id,
                title = title,
                artist = artistName,
                album = albumTitle,
                albumId = albumId,
                artistId = artistId,
                artworkUrl = artworkUrl,
                durationMs = durationSeconds * 1000L,
                explicit = obj.optBoolean("explicit", false),
                playbackSource = TIDAL_SOURCE,
                hires = isHires,
                bitDepth = if (isHires) 24 else 16,
                sampleRate = if (isHires) 96000 else 44100,
                artists = parsedArtists,
            )
        }

        fun parseAlbum(obj: JSONObject?): Album? {
            if (obj == null) return null
            val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
            val title = obj.optString("title", "Album")
            val artistObj = obj.optJSONObject("artist")
            val artistsArr = obj.optJSONArray("artists")
            val artistName = artistObj?.optString("name", "")?.takeIf { it.isNotBlank() }
                ?: artistsArr?.optJSONObject(0)?.optString("name", "")?.takeIf { it.isNotBlank() }
                ?: "Unknown Artist"
            val coverId = obj.optString("cover", "")
            val releaseDate = obj.optString("releaseDate", "")

            return Album(
                id = id,
                title = title,
                artist = artistName,
                artworkUrl = artworkUrl(coverId),
                year = releaseDate.take(4),
                explicit = obj.optBoolean("explicit", false),
            )
        }

        fun parseArtist(obj: JSONObject?): Artist? {
            if (obj == null) return null
            val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
            val name = obj.optString("name", "Artist")
            val picture = obj.optString("picture", "")
            return Artist(id = id, name = name, artworkUrl = artworkUrl(picture))
        }

        fun parsePlaylist(obj: JSONObject?): Playlist? {
            if (obj == null) return null
            val id = obj.optString("uuid").ifBlank { obj.optString("id") }.takeIf { it.isNotBlank() } ?: return null
            val title = obj.optString("title", "Playlist")
            val description = obj.optString("description", "")
            val imageId = obj.optString("squareImage").ifBlank { obj.optString("image") }
            val trackCount = obj.optInt("numberOfTracks", 0).takeIf { it > 0 }

            return Playlist(
                id = id,
                title = title,
                author = "TIDAL",
                artworkUrl = artworkUrl(imageId),
                description = description,
                trackCount = trackCount,
            )
        }
    }
}
