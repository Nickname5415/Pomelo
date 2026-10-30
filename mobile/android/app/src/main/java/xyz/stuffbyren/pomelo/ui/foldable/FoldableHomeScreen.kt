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

package xyz.stuffbyren.pomelo.ui.foldable

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import xyz.stuffbyren.pomelo.ui.scroll.OrchardLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.stuffbyren.pomelo.model.BuiltInHomeSection
import xyz.stuffbyren.pomelo.model.CatalogItem
import xyz.stuffbyren.pomelo.model.LoadState
import xyz.stuffbyren.pomelo.ui.components.HomeSectionShimmer
import xyz.stuffbyren.pomelo.ui.screens.HomeData
import xyz.stuffbyren.pomelo.ui.screens.HomeSectionSheet

internal fun catalogItemSubtitle(item: CatalogItem): String =
    when (item) {
        is CatalogItem.Song -> item.track.artist
        is CatalogItem.Collection -> item.playlist.author
        is CatalogItem.Record -> item.album.artist
        is CatalogItem.Performer -> item.artist.subtitle
        is CatalogItem.Category -> item.title
    }

// Foldable-optimized Home screen interface with full-bleed cinematic hero presentation.
@Composable
fun FoldableHomeScreen(data: HomeData) {
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = remember { listOf("All", "Playlists", "Artists", "Albums", "Songs") }
    HomeSectionSheet(data)

    val actions = data.actions
    val offline = data.offline
    val featuredHeroItems = data.heroItems
    val openCatalogItem: (CatalogItem) -> Unit = data::open

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = FoldableChromeHeight),
    ) {
        // Full Bleed Hero Banner
        if (featuredHeroItems.isNotEmpty()) {
            item {
                FoldableFullBleedHero(
                    items = featuredHeroItems,
                    auth = data.auth,
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onSelectCategory = {
                        selectedCategory = if (selectedCategory == it) "All" else it
                    },
                    onSearch = actions.search,
                    onProfile = actions.openProfile,
                    onPlay = data::play,
                    onClick = openCatalogItem,
                )
            }
        } else {
            // Fallback Masthead if library/state is entirely empty
            item {
                FoldableMasthead(
                    auth = data.auth,
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onSelectCategory = {
                        selectedCategory = if (selectedCategory == it) "All" else it
                    },
                    onSearch = actions.search,
                    onProfile = actions.openProfile,
                )
            }
        }

        // Offline Mode Banner
        if (offline) {
            item {
                FoldableOfflineBanner(data.downloadedTracks.size)
                Spacer(Modifier.height(16.dp))
            }
        }

        // Loading shimmer state
        if (data.state is LoadState.Loading) {
            item { HomeSectionShimmer(title = "Loading...") }
        }

        // Content Sections filtered dynamically by selected category
        data.layout.forEach { config ->
            if (!config.enabled) return@forEach

            when (config.section) {
                BuiltInHomeSection.YOUR_PLAYLISTS -> {
                    if (offline) return@forEach
                    if (selectedCategory !in listOf("All", "Playlists")) return@forEach
                    val playlistItems = data.playlists
                    if (playlistItems.isNotEmpty()) {
                        foldableRail("Your Playlists", playlistItems, data.seeAll("Your Playlists", playlistItems), openCatalogItem)
                    }
                }

                BuiltInHomeSection.SUBSCRIBED_ARTISTS -> {
                    if (offline) return@forEach
                    if (selectedCategory !in listOf("All", "Artists")) return@forEach
                    val artistItems = data.artists
                    if (artistItems.isNotEmpty()) {
                        foldableRail("Keep listening", artistItems, data.seeAll("Keep listening", artistItems), openCatalogItem, cardWidth = 150.dp)
                    }
                }

                BuiltInHomeSection.TOP_SONGS -> {
                    if (offline) return@forEach
                    if (selectedCategory !in listOf("All", "Songs")) return@forEach
                    val songItems = data.topSongs.map { CatalogItem.Song(it) }
                    if (songItems.isNotEmpty()) {
                        foldableRail("Top Songs", songItems, data.seeAll("Top Songs", songItems), openCatalogItem)
                    }
                }

                BuiltInHomeSection.RECOMMENDATIONS -> {
                    if (offline) return@forEach
                    if (selectedCategory != "All") return@forEach
                    data.sections.forEach { section ->
                        if (section.items.isNotEmpty()) {
                            foldableRail(
                                section.title, section.items,
                                data.seeAll(section.title, section.items, section.browseId, section.params),
                                openCatalogItem, sectionKey = section.id,
                            )
                        }
                    }
                }

                BuiltInHomeSection.DOWNLOADED_PLAYLISTS -> {
                    if (selectedCategory !in listOf("All", "Playlists")) return@forEach
                    val downloadedPlaylists = data.offlinePlaylists
                    if (downloadedPlaylists.isNotEmpty()) {
                        foldableRail("Downloaded Playlists", downloadedPlaylists, data.seeAll("Downloaded Playlists", downloadedPlaylists), openCatalogItem)
                    }
                }

                BuiltInHomeSection.DOWNLOADED_ARTISTS -> {
                    if (selectedCategory !in listOf("All", "Artists")) return@forEach
                    val downloadedArtists = data.offlineArtists
                    if (downloadedArtists.isNotEmpty()) {
                        foldableRail("Downloaded Artists", downloadedArtists, data.seeAll("Downloaded Artists", downloadedArtists), openCatalogItem, cardWidth = 150.dp)
                    }
                }

                BuiltInHomeSection.DOWNLOADED_ALBUMS -> {
                    if (selectedCategory !in listOf("All", "Albums")) return@forEach
                    val downloadedAlbums = data.offlineAlbums
                    if (downloadedAlbums.isNotEmpty()) {
                        foldableRail("Downloaded Albums", downloadedAlbums, data.seeAll("Downloaded Albums", downloadedAlbums), openCatalogItem)
                    }
                }

                BuiltInHomeSection.DOWNLOADED_SONGS -> {
                    if (selectedCategory !in listOf("All", "Songs")) return@forEach
                    if (data.downloadedTracks.isNotEmpty()) {
                        val downloadedSongItems = data.downloadedTracks.map { CatalogItem.Song(it) }
                        foldableRail("Downloaded Songs", downloadedSongItems, data.seeAll("Downloaded Songs", downloadedSongItems), openCatalogItem)
                    }
                }
            }
        }
    }
}
