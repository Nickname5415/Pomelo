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
import org.json.JSONObject
import xyz.stuffbyren.pomelo.auth.TidalAuthRepository
import xyz.stuffbyren.pomelo.auth.TidalWebSession
import xyz.stuffbyren.pomelo.model.CatalogItem
import xyz.stuffbyren.pomelo.model.CatalogSection
import xyz.stuffbyren.pomelo.model.HomeFeed
import xyz.stuffbyren.pomelo.model.Playlist

object TidalHomeFeedFetcher {
    private const val TAG = "TidalHomeFeed"
    private const val TIDAL_V1_BASE = "https://api.tidal.com/v1"
    private const val CLIENT_TOKEN = "6BDSRdpK9hqEBTgU"

    suspend fun fetchHome(
        http: OkHttpClient,
        webSession: TidalWebSession?,
        auth: TidalAuthRepository?,
    ): HomeFeed? = withContext(Dispatchers.IO) {
        val token = webSession?.getAccessToken() ?: auth?.getAccessToken() ?: return@withContext null
        val countryCode = webSession?.getCountryCode() ?: auth?.session()?.countryCode ?: "US"
        val url = Uri.parse("$TIDAL_V1_BASE/pages/home").buildUpon()
            .appendQueryParameter("locale", "en_US")
            .appendQueryParameter("countryCode", countryCode)
            .appendQueryParameter("deviceType", "PHONE")
            .build()
            .toString()

        val json = executeGet(http, url, token) ?: return@withContext null
        val sections = mutableListOf<CatalogSection>()
        val rows = json.optJSONArray("rows") ?: return@withContext null

        for (r in 0 until rows.length()) {
            val row = rows.optJSONObject(r) ?: continue
            val modules = row.optJSONArray("modules") ?: continue
            for (m in 0 until modules.length()) {
                val mod = modules.optJSONObject(m) ?: continue
                parseModule(mod)?.let { sections.add(it) }
            }
        }
        if (sections.isEmpty()) null else HomeFeed(sections = sections)
    }

    suspend fun fetchSectionItems(
        http: OkHttpClient,
        webSession: TidalWebSession?,
        auth: TidalAuthRepository?,
        apiPath: String,
    ): List<CatalogItem> = withContext(Dispatchers.IO) {
        val token = webSession?.getAccessToken() ?: auth?.getAccessToken() ?: return@withContext emptyList()
        val countryCode = webSession?.getCountryCode() ?: auth?.session()?.countryCode ?: "US"
        val cleanPath = apiPath.trim().removePrefix("/")
        val baseUrl = if (cleanPath.startsWith("http")) cleanPath else "$TIDAL_V1_BASE/$cleanPath"
        val url = Uri.parse(baseUrl).buildUpon()
            .apply {
                if (build().getQueryParameter("countryCode") == null) {
                    appendQueryParameter("countryCode", countryCode)
                }
                if (build().getQueryParameter("deviceType") == null) {
                    appendQueryParameter("deviceType", "PHONE")
                }
            }
            .build()
            .toString()

        val json = executeGet(http, url, token) ?: return@withContext emptyList()
        val items = mutableListOf<CatalogItem>()

        val rows = json.optJSONArray("rows")
        if (rows != null) {
            for (r in 0 until rows.length()) {
                val row = rows.optJSONObject(r) ?: continue
                val modules = row.optJSONArray("modules") ?: continue
                for (m in 0 until modules.length()) {
                    val mod = modules.optJSONObject(m) ?: continue
                    items.addAll(parseModuleItems(mod))
                }
            }
        } else {
            val paged = json.optJSONObject("pagedList")
            val pagedItems = paged?.optJSONArray("items") ?: json.optJSONArray("items")
            if (pagedItems != null) {
                for (i in 0 until pagedItems.length()) {
                    val itemObj = pagedItems.optJSONObject(i) ?: continue
                    parseItem(itemObj, "")?.let { items.add(it) }
                }
            }
        }
        items.distinctBy { it.stableId }
    }

    private fun parseModule(module: JSONObject): CatalogSection? {
        val type = module.optString("type")
        if (type in listOf("TASK_LIST", "LIVE_SESSION_LIST", "PROMOTIONAL")) return null

        val title = module.optString("title").ifBlank { return null }
        val items = parseModuleItems(module)
        if (items.isEmpty()) return null

        val showMore = module.optJSONObject("showMore")
        val browseId = showMore?.optString("apiPath").orEmpty()
        val id = module.optString("id").ifBlank { "${type}_${title.hashCode()}" }

        return CatalogSection(
            id = id,
            title = title,
            items = items,
            browseId = browseId,
        )
    }

    private fun parseModuleItems(module: JSONObject): List<CatalogItem> {
        val type = module.optString("type")
        val pagedList = module.optJSONObject("pagedList") ?: return emptyList()
        val itemsArr = pagedList.optJSONArray("items") ?: return emptyList()
        val items = mutableListOf<CatalogItem>()

        for (i in 0 until itemsArr.length()) {
            val itemObj = itemsArr.optJSONObject(i) ?: continue
            parseItem(itemObj, type)?.let { items.add(it) }
        }
        return items
    }

    private fun parseItem(itemObj: JSONObject, moduleType: String): CatalogItem? {
        val itemType = itemObj.optString("type").uppercase()
        val rawItem = itemObj.optJSONObject("item") ?: itemObj

        return when {
            itemType == "ALBUM" || moduleType == "ALBUM_LIST" -> {
                TidalV1Catalog.parseAlbum(rawItem)?.let { CatalogItem.Record(it) }
            }
            itemType == "TRACK" || moduleType == "TRACK_LIST" -> {
                TidalV1Catalog.parseTrack(rawItem)?.let { CatalogItem.Song(it) }
            }
            itemType == "ARTIST" || moduleType == "ARTIST_LIST" -> {
                TidalV1Catalog.parseArtist(rawItem)?.let { CatalogItem.Performer(it) }
            }
            itemType == "PLAYLIST" || moduleType == "PLAYLIST_LIST" -> {
                TidalV1Catalog.parsePlaylist(rawItem)?.let { CatalogItem.Collection(it) }
            }
            moduleType == "MIX_LIST" || rawItem.has("mixType") || rawItem.has("graphic") -> {
                parseMix(rawItem)
            }
            rawItem.has("cover") -> {
                TidalV1Catalog.parseAlbum(rawItem)?.let { CatalogItem.Record(it) }
            }
            rawItem.has("duration") && rawItem.has("artists") -> {
                TidalV1Catalog.parseTrack(rawItem)?.let { CatalogItem.Song(it) }
            }
            rawItem.has("uuid") -> {
                TidalV1Catalog.parsePlaylist(rawItem)?.let { CatalogItem.Collection(it) }
            }
            rawItem.has("picture") -> {
                TidalV1Catalog.parseArtist(rawItem)?.let { CatalogItem.Performer(it) }
            }
            else -> null
        }
    }

    private fun parseMix(obj: JSONObject): CatalogItem.Collection? {
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        val title = obj.optString("title", "Mix")
        val subTitle = obj.optString("subTitle", "TIDAL Mix")
        val images = obj.optJSONObject("images")
        val medium = images?.optJSONObject("MEDIUM")?.optString("url")
        val large = images?.optJSONObject("LARGE")?.optString("url")
        val small = images?.optJSONObject("SMALL")?.optString("url")
        val artworkUrl = medium?.ifBlank { null } ?: large?.ifBlank { null } ?: small.orEmpty()

        val playlist = Playlist(
            id = id,
            title = title,
            author = subTitle,
            artworkUrl = artworkUrl,
            description = subTitle,
        )
        return CatalogItem.Collection(playlist)
    }

    private fun executeGet(http: OkHttpClient, url: String, token: String): JSONObject? {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("x-tidal-token", CLIENT_TOKEN)
            .build()

        return try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Tidal home request failed: ${response.code} ${response.message} on $url")
                    return null
                }
                val body = response.body.string()
                JSONObject(body)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception fetching Tidal home: $url", e)
            null
        }
    }
}
