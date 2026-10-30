/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Orchard.
 *
 * Orchard is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Orchard is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Orchard. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.stuffbyren.pomelo.catalog

import xyz.stuffbyren.pomelo.model.BrowseDetail
import xyz.stuffbyren.pomelo.model.CatalogItem
import xyz.stuffbyren.pomelo.model.SearchResults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last

internal fun isAppleMusicId(id: String): Boolean = false

/** Home, search and detail pages. */
internal class CatalogRouter(
    private val youtube: Source,
    private val preferred: () -> String = { "youtube" },
) {
    internal interface Source {
        suspend fun search(query: String): SearchResults
        fun pages(id: String, seed: CatalogItem? = null): Flow<BrowseDetail>
        suspend fun sectionItems(id: String, params: String): List<CatalogItem>
        suspend fun artist(name: String): BrowseDetail? = null
    }

    constructor(youtube: CatalogRepository, preferred: () -> String = { "youtube" }) : this(
        object : Source {
            override suspend fun search(query: String) = youtube.search(query)
            override fun pages(id: String, seed: CatalogItem?) = youtube.browsePages(id, seed)
            override suspend fun sectionItems(id: String, params: String) = youtube.sectionItems(id, params)
        },
        preferred,
    )

    suspend fun search(query: String): SearchResults = youtube.search(query)

    fun pages(id: String, seed: CatalogItem? = null): Flow<BrowseDetail> = flow {
        emitAll(youtube.pages(id, seed))
    }

    suspend fun browse(id: String): BrowseDetail = pages(id).last()

    suspend fun sectionItems(browseId: String, params: String): List<CatalogItem> {
        return youtube.sectionItems(browseId, params)
    }
}
