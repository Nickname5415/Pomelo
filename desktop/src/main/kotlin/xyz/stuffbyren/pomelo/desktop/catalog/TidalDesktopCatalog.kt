package xyz.stuffbyren.pomelo.desktop.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import xyz.stuffbyren.pomelo.desktop.auth.TidalDesktopAuth
import xyz.stuffbyren.pomelo.desktop.model.Album
import xyz.stuffbyren.pomelo.desktop.model.Artist
import xyz.stuffbyren.pomelo.desktop.model.Playlist
import xyz.stuffbyren.pomelo.desktop.model.Track
import java.net.URLEncoder

class TidalDesktopCatalog(
    private val http: OkHttpClient,
    private val auth: TidalDesktopAuth,
) {
    private val apiBase = "https://openapi.tidal.com/v2"

    private fun artworkUrl(coverId: String?): String {
        if (coverId.isNullOrBlank()) return ""
        val formatted = coverId.replace("-", "/")
        return "https://resources.tidal.com/images/$formatted/640x640.jpg"
    }

    private suspend fun requestJson(path: String, queryParams: Map<String, String> = emptyMap()): JSONObject? = withContext(Dispatchers.IO) {
        val token = auth.accessToken ?: return@withContext null
        val query = if (queryParams.isNotEmpty()) {
            "?" + queryParams.map { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }.joinToString("&")
        } else ""

        val req = Request.Builder()
            .url("$apiBase$path$query")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/vnd.tidal.v1+json")
            .build()

        runCatching {
            http.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    JSONObject(res.body.string())
                } else null
            }
        }.getOrNull()
    }

    suspend fun search(query: String): Triple<List<Track>, List<Album>, List<Artist>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext Triple(emptyList(), emptyList(), emptyList())
        val json = requestJson("/searchResults/$query", mapOf("countryCode" to auth.countryCode))
            ?: return@withContext searchFallback(query)

        val tracks = mutableListOf<Track>()
        val albums = mutableListOf<Album>()
        val artists = mutableListOf<Artist>()

        val included = json.optJSONArray("included") ?: JSONArray()
        for (i in 0 until included.length()) {
            val item = included.optJSONObject(i) ?: continue
            val type = item.optString("type")
            val id = item.optString("id")
            val attr = item.optJSONObject("attributes") ?: JSONObject()

            when (type) {
                "tracks" -> {
                    tracks += Track(
                        id = id,
                        title = attr.optString("title"),
                        artist = attr.optString("artistName", "Unknown Artist"),
                        durationMs = attr.optLong("duration", 0L) * 1000L,
                        explicit = attr.optBoolean("explicit", false),
                    )
                }
                "albums" -> {
                    albums += Album(
                        id = id,
                        title = attr.optString("title"),
                        artist = attr.optString("artistName", "Unknown Artist"),
                        artworkUrl = artworkUrl(attr.optString("imageCover")),
                        releaseYear = attr.optString("releaseDate").take(4),
                    )
                }
                "artists" -> {
                    artists += Artist(
                        id = id,
                        name = attr.optString("name"),
                        pictureUrl = artworkUrl(attr.optString("picture")),
                    )
                }
            }
        }
        Triple(tracks, albums, artists)
    }

    private suspend fun searchFallback(query: String): Triple<List<Track>, List<Album>, List<Artist>> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.tidal.com/v1/search?query=${URLEncoder.encode(query, "UTF-8")}&limit=25&countryCode=${auth.countryCode}")
            .header("x-tidal-token", "ZL6uNG7Udwi7M8C0")
            .build()

        runCatching {
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext Triple(emptyList(), emptyList(), emptyList())
                val root = JSONObject(res.body.string())
                val tracks = parseV1Tracks(root.optJSONObject("tracks")?.optJSONArray("items"))
                val albums = parseV1Albums(root.optJSONObject("albums")?.optJSONArray("items"))
                val artists = parseV1Artists(root.optJSONObject("artists")?.optJSONArray("items"))
                Triple(tracks, albums, artists)
            }
        }.getOrDefault(Triple(emptyList(), emptyList(), emptyList()))
    }

    suspend fun getHomeTopTracks(): List<Track> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.tidal.com/v1/pages/explore?countryCode=${auth.countryCode}&deviceType=PHONE")
            .header("x-tidal-token", "ZL6uNG7Udwi7M8C0")
            .build()

        runCatching {
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext searchFallback("Top Hits").first
                val root = JSONObject(res.body.string())
                val categories = root.optJSONArray("categories") ?: root.optJSONArray("rows") ?: JSONArray()
                for (i in 0 until categories.length()) {
                    val row = categories.optJSONObject(i) ?: continue
                    val items = row.optJSONObject("modules")?.optJSONArray("items")
                        ?: row.optJSONArray("items")
                    val parsed = parseV1Tracks(items)
                    if (parsed.isNotEmpty()) return@withContext parsed
                }
                searchFallback("Top Hits").first
            }
        }.getOrElse { searchFallback("Pop Hits").first }
    }

    suspend fun getAlbumTracks(albumId: String): List<Track> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.tidal.com/v1/albums/$albumId/tracks?countryCode=${auth.countryCode}&limit=100")
            .header("x-tidal-token", "ZL6uNG7Udwi7M8C0")
            .build()

        runCatching {
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext emptyList()
                val json = JSONObject(res.body.string())
                parseV1Tracks(json.optJSONArray("items"))
            }
        }.getOrDefault(emptyList())
    }

    private fun parseV1Tracks(items: JSONArray?): List<Track> {
        if (items == null) return emptyList()
        val list = mutableListOf<Track>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
            val title = item.optString("title", "Untitled")
            val artistObj = item.optJSONObject("artist")
            val artistName = artistObj?.optString("name") ?: item.optString("artistName", "Unknown Artist")
            val artistId = artistObj?.optString("id", "") ?: ""
            val albumObj = item.optJSONObject("album")
            val albumTitle = albumObj?.optString("title", "") ?: ""
            val albumId = albumObj?.optString("id", "") ?: ""
            val cover = albumObj?.optString("cover") ?: item.optString("cover")
            val durationSec = item.optLong("duration", 0L)
            val trackNum = item.optInt("trackNumber", i + 1)
            val explicit = item.optBoolean("explicit", false)

            list += Track(
                id = id,
                title = title,
                artist = artistName,
                album = albumTitle,
                albumId = albumId,
                artistId = artistId,
                artworkUrl = artworkUrl(cover),
                durationMs = durationSec * 1000L,
                trackNumber = trackNum,
                explicit = explicit,
            )
        }
        return list
    }

    private fun parseV1Albums(items: JSONArray?): List<Album> {
        if (items == null) return emptyList()
        val list = mutableListOf<Album>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
            val title = item.optString("title", "Untitled Album")
            val artistObj = item.optJSONObject("artist")
            val artistName = artistObj?.optString("name") ?: item.optString("artistName", "Unknown Artist")
            val cover = item.optString("cover")
            val releaseDate = item.optString("releaseDate", "")

            list += Album(
                id = id,
                title = title,
                artist = artistName,
                artworkUrl = artworkUrl(cover),
                releaseYear = releaseDate.take(4),
                trackCount = item.optInt("numberOfTracks", 0),
            )
        }
        return list
    }

    private fun parseV1Artists(items: JSONArray?): List<Artist> {
        if (items == null) return emptyList()
        val list = mutableListOf<Artist>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
            val name = item.optString("name", "Unknown Artist")
            val pic = item.optString("picture")
            list += Artist(id, name, artworkUrl(pic))
        }
        return list
    }
}
