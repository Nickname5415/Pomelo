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

import dev.sfg.orchard.mobile.model.CatalogItem
import dev.sfg.orchard.mobile.model.CatalogSection
import dev.sfg.orchard.mobile.model.HomeFeed
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class HomeFeedsTest {
    private class FakeSource(
        override val id: String,
        override val name: String,
        private val connected: Boolean = true,
        private val failure: Exception? = null,
    ) : HomeSource {
        val seeAll = mutableListOf<String>()
        override fun available() = connected
        override suspend fun feed(): HomeFeed {
            failure?.let { throw it }
            return HomeFeed(sections = listOf(CatalogSection(id, name, emptyList())))
        }
        override suspend fun sectionItems(browseId: String, params: String): List<CatalogItem> {
            seeAll += browseId
            return emptyList()
        }
    }

    @Test
    fun preferredSourceServesWithoutWarning() = runBlocking {
        val tidal = FakeSource("tidal", "TIDAL")
        val result = HomeFeeds(listOf(FakeSource("youtube", "YouTube Music"), tidal)) { "tidal" }.load()
        assertEquals("tidal", result.source.id)
        assertNull(result.warning)
    }

    @Test
    fun disconnectedPreferredReportsErrorWithoutSwitchingCatalogs() {
        val youtube = FakeSource("youtube", "YouTube Music")
        val feeds = HomeFeeds(listOf(youtube, FakeSource("tidal", "TIDAL", connected = false))) { "tidal" }
        val error = assertThrows(IllegalStateException::class.java) { runBlocking { feeds.load() } }
        assertEquals("TIDAL is not connected.", error.message)
        assertNull(feeds.activeSource)
        runBlocking { feeds.sectionItems("shelf", "") }
        assertEquals(emptyList<String>(), youtube.seeAll)
    }

    @Test
    fun failedPreferredReportsItsOwnErrorWithoutSwitchingCatalogs() {
        val sources = listOf(
            FakeSource("youtube", "YouTube Music"),
            FakeSource("tidal", "TIDAL", failure = IllegalStateException("down")),
        )
        val feeds = HomeFeeds(sources) { "tidal" }
        val error = assertThrows(IllegalStateException::class.java) { runBlocking { feeds.load() } }
        assertEquals("down", error.message)
        assertNull(feeds.activeSource)
    }

    @Test
    fun allSourcesFailingRethrowsLastError() {
        val sources = listOf(FakeSource("youtube", "YouTube Music", failure = IllegalStateException("offline")))
        val error = assertThrows(IllegalStateException::class.java) { runBlocking { HomeFeeds(sources) { "youtube" }.load() } }
        assertEquals("offline", error.message)
    }
}
