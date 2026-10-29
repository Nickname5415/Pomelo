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

package xyz.stuffbyren.pomelo.home

import xyz.stuffbyren.pomelo.catalog.CatalogRepository
import xyz.stuffbyren.pomelo.model.CatalogItem
import xyz.stuffbyren.pomelo.model.HomeFeed
import xyz.stuffbyren.pomelo.model.TIDAL_SOURCE

/** A service that can fill the home screen. [id] matches [xyz.stuffbyren.pomelo.model.Track.playbackSource]. */
interface HomeSource {
    val id: String
    val name: String

    /** False when the service cannot serve a feed right now, such as when signed out. */
    fun available(): Boolean

    suspend fun feed(): HomeFeed

    /** Every item behind a shelf's "See all" link. */
    suspend fun sectionItems(browseId: String, params: String): List<CatalogItem>
}

/** TIDAL home source. */
class TidalHomeSource(
    private val catalog: CatalogRepository,
    private val isAvailable: () -> Boolean = { true },
) : HomeSource {
    override val id = TIDAL_SOURCE
    override val name = "TIDAL"

    override fun available() = isAvailable()

    override suspend fun feed() = catalog.home()

    override suspend fun sectionItems(browseId: String, params: String) = catalog.sectionItems(browseId, params)
}
