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

package xyz.stuffbyren.pomelo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import xyz.stuffbyren.pomelo.ui.scroll.OrchardLazyRow as LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.stuffbyren.pomelo.model.BrowseDetail
import xyz.stuffbyren.pomelo.model.CatalogKind
import xyz.stuffbyren.pomelo.model.Track
import xyz.stuffbyren.pomelo.model.CatalogItem
import xyz.stuffbyren.pomelo.ui.motion.riseIn
import xyz.stuffbyren.pomelo.ui.components.ArtistSectionBottomSheet
import xyz.stuffbyren.pomelo.ui.components.CatalogCard
import xyz.stuffbyren.pomelo.ui.components.OrchardSectionHeader
import xyz.stuffbyren.pomelo.ui.components.ReorderControls
import xyz.stuffbyren.pomelo.ui.components.filterTracks
import xyz.stuffbyren.pomelo.ui.theme.PomeloSpacing

/** Standard album and playlist presentation. */
@Composable
internal fun CollectionDetailContent(
    detail: BrowseDetail,
    onBack: () -> Unit,
    onPlayAll: (List<Track>, String) -> Unit,
    onShuffle: (List<Track>, String) -> Unit,
    onPlayTrack: (List<Track>, Int, String) -> Unit,
    onPlayNext: ((Track) -> Unit)?,
    shuffleAvailable: Boolean,
    onAdd: ((Track) -> Unit)?,
    onAddToPlaylist: ((Track) -> Unit)? = null,
    onRemoveFromPlaylist: ((Track) -> Unit)? = null,
    onMovePlaylistTrack: ((Int, Int) -> Unit)? = null,
    localEdit: LocalPlaylistEdit? = null,
    onSave: (BrowseDetail) -> Unit,
    onOpen: (String) -> Unit,
    isSaved: Boolean = false,
    downloadedTrackIds: Set<String> = emptySet(),
    downloadingTrackIds: Set<String> = emptySet(),
    onDownloadTrack: ((Track) -> Unit)? = null,
    onDownloadTracks: ((List<Track>) -> Unit)? = null,
    onRemoveDownloadTrack: ((String) -> Unit)? = null,
    onRemoveDownloadTracks: ((List<Track>) -> Unit)? = null,
    animatedArtworkUrl: String = "",
    artistPortraitUrl: String = "",
    onShareTrack: ((Track) -> Unit)? = null,
    onShareCollection: ((BrowseDetail) -> Unit)? = null,
    smartCrossfadeEnabled: Boolean = false,
    onPlayBestMix: ((List<Track>, String, (String) -> Unit, () -> Unit) -> Unit)? = null,
    onFetchSectionItems: (suspend (String, String) -> List<CatalogItem>)? = null,
    playingTrackId: String? = null,
) {
    var activeSectionSheet by remember { mutableStateOf<SectionSheetState?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    val canEdit = detail.kind == CatalogKind.PLAYLIST && detail.editable && onMovePlaylistTrack != null
    val editRows = editing && canEdit && !isSearching

    val bestMix = rememberBestMixLauncher(detail, downloadedTrackIds, onPlayAll, onPlayBestMix)

    val filteredTracks = remember(detail.tracks, searchQuery, isSearching) {
        if (isSearching && searchQuery.isNotBlank()) filterTracks(detail.tracks, searchQuery) else detail.tracks
    }

    activeSectionSheet?.let { sheet ->
        ArtistSectionBottomSheet(
            title = sheet.title,
            initialItems = sheet.initialItems,
            browseId = sheet.browseId,
            params = sheet.params,
            onFetchFullItems = onFetchSectionItems,
            onPlay = { track -> onPlayTrack(listOf(track), 0, sheet.title) },
            onOpen = onOpen,
            onDismiss = { activeSectionSheet = null },
        )
    }

    if (bestMix.showPrompt) {
        BestMixDownloadPrompt(
            trackCount = bestMix.undownloadedCount,
            estimatedMb = bestMix.estimatedMb,
            onConfirm = {
                bestMix.showPrompt = false
                bestMix.run()
            },
            onDismiss = { bestMix.showPrompt = false },
        )
    }

    AlbumPlaylistCollectionBody(
        detail = detail,
        filteredTracks = filteredTracks,
        onBack = onBack,
        onPlayAll = onPlayAll,
        onShuffle = onShuffle,
        onPlayTrack = onPlayTrack,
        shuffleAvailable = shuffleAvailable,
        onSave = onSave,
        isSaved = isSaved,
        playingTrackId = playingTrackId,
        isSearching = isSearching,
        searchQuery = searchQuery,
        onSearch = { isSearching = true },
        onSearchQueryChange = { searchQuery = it },
        onCloseSearch = {
            isSearching = false
            searchQuery = ""
        },
        onShareCollection = onShareCollection,
        bestMix = bestMix,
        smartCrossfadeEnabled = smartCrossfadeEnabled,
        downloadedTrackIds = downloadedTrackIds,
        onDownloadTracks = onDownloadTracks,
        onRemoveDownloadTracks = onRemoveDownloadTracks,
        headerExtra = {
            if (canEdit && !isSearching) {
                PlaylistEditBar(
                    title = detail.title,
                    editing = editing,
                    onEditingChange = { editing = it },
                    local = localEdit,
                )
            }
        },
        trackRowWrapper = { index, track, row ->
            if (editRows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) { row() }
                    ReorderControls(
                        onMoveUp = if (index > 0) onMovePlaylistTrack?.let { move -> { move(index, index - 1) } } else null,
                        onMoveDown = if (index < detail.tracks.lastIndex) {
                            onMovePlaylistTrack?.let { move -> { move(index, index + 1) } }
                        } else null,
                        onRemove = onRemoveFromPlaylist?.let { remove -> { remove(track) } },
                    )
                }
            } else {
                row()
            }
        },
        belowTracks = {
            if (!isSearching || searchQuery.isBlank()) {
                if (detail.sections.isNotEmpty()) {
                    detail.sections.forEach { section ->
                        val hasMoreViaApi = section.browseId.isNotBlank()
                        val canViewAll = hasMoreViaApi || section.items.size > 3
                        OrchardSectionHeader(
                            title = section.title,
                            action = if (canViewAll) "View all" else null,
                            onAction = if (canViewAll) {
                                {
                                    activeSectionSheet = SectionSheetState(
                                        title = section.title,
                                        initialItems = section.items,
                                        browseId = section.browseId,
                                        params = section.params,
                                    )
                                }
                            } else null,
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = PomeloSpacing.screenHorizontal),
                            horizontalArrangement = Arrangement.spacedBy(PomeloSpacing.screenHorizontal),
                        ) {
                            itemsIndexed(section.items, key = { idx, it -> "${it.stableId}_$idx" }) { idx, item ->
                                CatalogCard(
                                    item,
                                    modifier = Modifier.riseIn(idx, fromScale = 0.9f, cascadeOnScroll = true),
                                    onClick = {
                                        if (item is CatalogItem.Song) {
                                            onPlayTrack(listOf(item.track), 0, section.title)
                                        } else {
                                            onOpen(item.stableId)
                                        }
                                    },
                                )
                            }
                        }
                    }
                } else if (detail.related.isNotEmpty()) {
                    OrchardSectionHeader(title = "More like this")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = PomeloSpacing.screenHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(PomeloSpacing.screenHorizontal),
                    ) {
                        itemsIndexed(detail.related, key = { idx, it -> "${it.stableId}_$idx" }) { idx, item ->
                            CatalogCard(
                                item,
                                modifier = Modifier.riseIn(idx, fromScale = 0.9f, cascadeOnScroll = true),
                                onClick = {
                                    if (item is CatalogItem.Song) {
                                        onPlayAll(listOf(item.track), detail.title)
                                    } else {
                                        onOpen(item.stableId)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}
