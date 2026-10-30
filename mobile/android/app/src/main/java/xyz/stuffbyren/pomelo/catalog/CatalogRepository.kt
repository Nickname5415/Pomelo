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

import kotlinx.coroutines.flow.Flow
import xyz.stuffbyren.pomelo.model.Artist
import xyz.stuffbyren.pomelo.model.BrowseDetail
import xyz.stuffbyren.pomelo.model.CatalogItem
import xyz.stuffbyren.pomelo.model.CatalogKind
import xyz.stuffbyren.pomelo.model.CatalogSection
import xyz.stuffbyren.pomelo.model.HomeFeed
import xyz.stuffbyren.pomelo.model.SearchResults
import xyz.stuffbyren.pomelo.model.Track

/**
 * Catalog repository backed exclusively by TIDAL Open API.
 */
class CatalogRepository(
    private val tidalCatalog: TidalCatalogRepository,
) {
    suspend fun home(): HomeFeed = tidalCatalog.home()

    suspend fun search(query: String, filter: String = "all", guest: Boolean = false): SearchResults =
        tidalCatalog.search(query, filter, guest)

    suspend fun browse(id: String, seed: CatalogItem? = null): BrowseDetail = tidalCatalog.browse(id, seed)

    fun browsePages(id: String, seed: CatalogItem? = null): Flow<BrowseDetail> = tidalCatalog.browsePages(id, seed)

    suspend fun upNext(trackId: String): List<Track> = tidalCatalog.upNext(trackId)

    suspend fun trackArtists(trackId: String): List<Artist> = tidalCatalog.trackArtists(trackId)

    suspend fun setArtistSubscription(channelId: String, subscribed: Boolean) {
        // TIDAL artist following can be linked via UserCollectionArtists
    }

    suspend fun likedSongs(): BrowseDetail = tidalCatalog.likedSongs()

    suspend fun sectionItems(browseId: String, params: String = ""): List<CatalogItem> =
        tidalCatalog.sectionItems(browseId, params)

    suspend fun library(): List<CatalogSection> = tidalCatalog.library()

    suspend fun playlists(): List<CatalogItem> = tidalCatalog.playlists()

    suspend fun albums(): List<CatalogItem> = tidalCatalog.savedAlbums()

    companion object {
        const val LIKED_MUSIC = "liked-music"
        const val MAX_TRACK_PAGES = 50
        const val MAX_MIX_PAGES = 3

        fun pageBudget(id: String): Int =
            if (id.removePrefix("VL").startsWith("RD")) MAX_MIX_PAGES else MAX_TRACK_PAGES

        internal fun kindOf(browseId: String): CatalogKind = when {
            browseId.startsWith("artist") -> CatalogKind.ARTIST
            browseId.startsWith("album") -> CatalogKind.ALBUM
            else -> CatalogKind.PLAYLIST
        }
    }
}
