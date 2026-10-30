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

import xyz.stuffbyren.pomelo.model.Artist
import xyz.stuffbyren.pomelo.model.BrowseDetail
import xyz.stuffbyren.pomelo.model.CatalogItem
import xyz.stuffbyren.pomelo.model.CatalogKind
import xyz.stuffbyren.pomelo.model.SearchResults
import xyz.stuffbyren.pomelo.model.Track
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRouterTest {
    private class FakeSource(val id: String) : CatalogRouter.Source {
        val calls = mutableListOf<String>()
        var failure: Exception? = null
        override suspend fun search(query: String): SearchResults {
            calls += "search:$query"
            failure?.let { throw it }
            return SearchResults(tracks = listOf(Track("$id.song", query, "Artist", playbackSource = id)))
        }
        override fun pages(id: String, seed: CatalogItem?) = flowOf(BrowseDetail(id, CatalogKind.ARTIST, "Artist")).also {
            calls += "pages:$id"
        }
        override suspend fun sectionItems(id: String, params: String): List<CatalogItem> {
            calls += "section:$id:$params"
            return emptyList()
        }
        override suspend fun artist(name: String): BrowseDetail? {
            calls += "artist:$name"
            failure?.let { throw it }
            return BrowseDetail("am.artists.123", CatalogKind.ARTIST, name)
        }
    }

    @Test
    fun searchDelegatesToSource() = runBlocking {
        val youtube = FakeSource("youtube")
        val router = CatalogRouter(youtube)
        assertEquals("youtube", router.search("Song").tracks.single().playbackSource)
        assertEquals(listOf("search:Song"), youtube.calls)
    }

    @Test
    fun pagesDelegatesToSource() = runBlocking {
        val youtube = FakeSource("youtube")
        val router = CatalogRouter(youtube)
        assertEquals("UCartist", router.pages("UCartist").toList().single().id)
        assertEquals(listOf("pages:UCartist"), youtube.calls)
    }
}
