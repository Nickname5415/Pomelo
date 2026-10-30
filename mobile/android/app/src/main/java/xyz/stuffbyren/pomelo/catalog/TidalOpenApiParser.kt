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

import org.json.JSONArray
import org.json.JSONObject
import xyz.stuffbyren.pomelo.model.Album
import xyz.stuffbyren.pomelo.model.Artist
import xyz.stuffbyren.pomelo.model.Playlist
import xyz.stuffbyren.pomelo.model.TIDAL_SOURCE
import xyz.stuffbyren.pomelo.model.Track

internal data class ParsedAlbumInfo(
    val title: String,
    val coverArtId: String,
    val releaseDate: String,
    val explicit: Boolean,
)

internal object TidalOpenApiParser {

    fun parseArtworksMap(included: JSONArray): Map<String, String> {
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

    fun parseArtistsMap(included: JSONArray): Map<String, String> {
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

    fun parseAlbumsMap(included: JSONArray): Map<String, ParsedAlbumInfo> {
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

    fun parseTrackWithContext(
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
            ?: attributes.optString("artistName", fallbackArtist).ifBlank { fallbackArtist.ifBlank { "TIDAL Artist" } }

        val albumRel = rels?.optJSONObject("albums")?.optJSONArray("data")
        val albumId = albumRel?.optJSONObject(0)?.optString("id", "").orEmpty()
        val albumInfo = albumsMap[albumId]
        val albumTitle = albumInfo?.title?.ifBlank { null } ?: fallbackAlbum

        val artworkUrl = albumInfo?.coverArtId?.let { artworksMap[it] }
            ?: resolveArtworkUrl(attributes).ifBlank { fallbackArtwork }

        val isHires = attributes.optJSONArray("audioModes")?.toString()?.contains("HIRES", ignoreCase = true) == true

        return Track(
            id = id,
            title = title,
            artist = artistName,
            album = albumTitle,
            albumId = albumId,
            artistId = artistId,
            artworkUrl = artworkUrl,
            durationMs = durationMs,
            explicit = explicit,
            playbackSource = TIDAL_SOURCE,
            hires = isHires,
            bitDepth = if (isHires) 24 else 16,
            sampleRate = if (isHires) 96000 else 44100,
            artists = listOf(Artist(id = artistId, name = artistName, artworkUrl = artworkUrl)),
        )
    }

    fun parseAlbumWithContext(
        obj: JSONObject,
        artistsMap: Map<String, String>,
        artworksMap: Map<String, String>,
        fallbackArtist: String = "",
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
            ?: attributes.optString("artistName", fallbackArtist).ifBlank { fallbackArtist.ifBlank { "TIDAL Artist" } }

        return Album(
            id = id,
            title = title,
            artist = artist,
            artworkUrl = artworkUrl,
            year = year,
            explicit = explicit,
        )
    }

    fun parsePlaylistWithContext(
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

    fun parseArtist(obj: JSONObject): Artist? {
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

    fun resolveArtworkUrl(attributes: JSONObject): String {
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

    fun parseDurationMs(isoDuration: String, secondsFallback: Long): Long {
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
