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

package dev.sfg.orchard.mobile.home

import dev.sfg.orchard.mobile.catalog.CatalogRepository
import dev.sfg.orchard.mobile.model.CatalogItem
import dev.sfg.orchard.mobile.model.HomeFeed
import dev.sfg.orchard.mobile.model.YOUTUBE_SOURCE

/** A service that can fill the home screen. [id] matches [dev.sfg.orchard.mobile.model.Track.playbackSource]. */
interface HomeSource {
    val id: String
    val name: String

    /** False when the service cannot serve a feed right now, such as when signed out. */
    fun available(): Boolean

    suspend fun feed(): HomeFeed

    /** Every item behind a shelf's "See all" link. */
    suspend fun sectionItems(browseId: String, params: String): List<CatalogItem>
}

/** YouTube Music serves guests, so it is always available. */
class YouTubeHomeSource(private val catalog: CatalogRepository) : HomeSource {
    override val id = YOUTUBE_SOURCE
    override val name = "YouTube Music"

    override fun available() = true

    override suspend fun feed() = catalog.home()

    override suspend fun sectionItems(browseId: String, params: String) = catalog.sectionItems(browseId, params)
}
