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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.stuffbyren.pomelo.model.BestMixJob
import xyz.stuffbyren.pomelo.model.BrowseDetail
import xyz.stuffbyren.pomelo.model.CatalogKind
import xyz.stuffbyren.pomelo.model.Track
import xyz.stuffbyren.pomelo.ui.components.MessagePanel
import xyz.stuffbyren.pomelo.ui.components.collectionDownloadAction
import xyz.stuffbyren.pomelo.ui.scroll.OrchardLazyColumn as LazyColumn
import xyz.stuffbyren.pomelo.ui.theme.PomeloSpacing
import xyz.stuffbyren.pomelo.ui.theme.OrchardTheme

@Preview(name = "Album normal", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AlbumPlaylistPreviewNormal() {
    OrchardTheme {
        AlbumPlaylistPreviewBody(sampleAlbumDetail())
    }
}

@Preview(name = "Long title", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AlbumPlaylistPreviewLongTitle() {
    OrchardTheme {
        AlbumPlaylistPreviewBody(
            sampleAlbumDetail().copy(title = "This Is An Extremely Long Album Title That Must Ellipsize"),
        )
    }
}

@Preview(name = "Over one hour", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AlbumPlaylistPreviewLongAlbum() {
    OrchardTheme {
        val tracks = List(18) { index ->
            Track(
                id = "t$index",
                title = "Track ${index + 1}",
                artist = "Artist",
                durationMs = 4 * 60_000L + 10_000,
                explicit = index == 3,
            )
        }
        AlbumPlaylistPreviewBody(
            BrowseDetail(
                id = "long",
                kind = CatalogKind.ALBUM,
                title = "Live Sessions",
                artist = "Artist",
                year = "2020",
                explicit = false,
                tracks = tracks,
            ),
        )
    }
}

@Composable
private fun AlbumPlaylistPreviewBody(detail: BrowseDetail) {
    AlbumPlaylistCollectionBody(
        detail = detail,
        filteredTracks = detail.tracks,
        onBack = {},
        onPlayAll = { _, _ -> },
        onShuffle = { _, _ -> },
        onPlayTrack = { _, _, _ -> },
        shuffleAvailable = true,
        onSave = {},
        isSaved = false,
        playingTrackId = detail.tracks.firstOrNull()?.id,
        isSearching = false,
        searchQuery = "",
        onSearch = {},
        onSearchQueryChange = {},
        onCloseSearch = {},
        onShareCollection = null,
        bestMix = previewBestMixLauncher(),
        smartCrossfadeEnabled = false,
        downloadedTrackIds = emptySet(),
        onDownloadTracks = null,
        onRemoveDownloadTracks = null,
    )
}

@Composable
private fun previewBestMixLauncher(): BestMixLauncher {
    val job = remember { mutableStateOf<BestMixJob?>(null) }
    return remember(job) {
        BestMixLauncher(
            start = {},
            undownloadedCount = 0,
            estimatedMb = 0,
            needsPrompt = false,
            job = job,
            key = "preview",
        )
    }
}

private fun sampleAlbumDetail(): BrowseDetail {
    val tracks = listOf(
        Track("1", "Beat 54 (All Good Now)", "Jungle", durationMs = 225_000),
        Track("2", "Keep Moving", "Jungle", durationMs = 304_000),
        Track("3", "Talk About It", "Jungle", durationMs = 213_000),
        Track("4", "Romeo", "Jungle", durationMs = 178_000, explicit = true),
        Track("5", "Lifting You", "Jungle", durationMs = 271_000),
        Track("6", "Good Times", "Jungle", durationMs = 189_000),
        Track("7", "Hands Off", "Jungle", durationMs = 198_000),
        Track("8", "Casio", "Jungle", durationMs = 205_000),
    )
    return BrowseDetail(
        id = "jungle-stereo",
        kind = CatalogKind.ALBUM,
        title = "Loving In Stereo",
        artist = "Jungle",
        year = "2021",
        explicit = true,
        tracks = tracks,
    )
}

@Composable
internal fun AlbumPlaylistCollectionBody(
    detail: BrowseDetail,
    filteredTracks: List<Track>,
    onBack: () -> Unit,
    onPlayAll: (List<Track>, String) -> Unit,
    onShuffle: (List<Track>, String) -> Unit,
    onPlayTrack: (List<Track>, Int, String) -> Unit,
    shuffleAvailable: Boolean,
    onSave: (BrowseDetail) -> Unit,
    isSaved: Boolean,
    playingTrackId: String?,
    isSearching: Boolean,
    searchQuery: String,
    onSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onShareCollection: ((BrowseDetail) -> Unit)?,
    bestMix: BestMixLauncher,
    smartCrossfadeEnabled: Boolean,
    downloadedTrackIds: Set<String>,
    onDownloadTracks: ((List<Track>) -> Unit)?,
    onRemoveDownloadTracks: ((List<Track>) -> Unit)?,
    modifier: Modifier = Modifier,
    headerExtra: @Composable () -> Unit = {},
    trackRowWrapper: @Composable (index: Int, track: Track, row: @Composable () -> Unit) -> Unit =
        { _, _, row -> row() },
    belowTracks: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val artistName = remember(detail) { collectionArtistName(detail) }
    val noun = if (detail.kind == CatalogKind.ALBUM) "album" else "playlist"
    var showAbout by remember { mutableStateOf(false) }

    if (showAbout && detail.description.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("About this $noun") },
            text = { Text(detail.description) },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("Close") }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AlbumPlaylistTopBar(
            onBack = onBack,
            onShare = { albumPlaylistShare(context, detail, onShareCollection) },
            onSave = { onSave(detail) },
            isSaved = isSaved,
            onSearch = onSearch,
            isSearching = isSearching,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onCloseSearch = onCloseSearch,
            searchPlaceholder = "Find in $noun",
            aboutLabel = "About this $noun",
            onAbout = detail.description.takeIf { it.isNotBlank() }?.let { { showAbout = true } },
            onBestMix = if (detail.supportsBestMix(smartCrossfadeEnabled)) bestMix::trigger else null,
            onDownload = collectionDownloadAction(
                detail.tracks,
                downloadedTrackIds,
                onDownloadTracks,
                onRemoveDownloadTracks,
            ),
            isDownloaded = detail.tracks.isNotEmpty() && detail.tracks.all { it.id in downloadedTrackIds },
        )
        when {
            isSearching && searchQuery.isNotBlank() && filteredTracks.isEmpty() -> {
                MessagePanel(
                    title = "No matching tracks",
                    message = "No songs matching \"$searchQuery\" found in ${detail.title}.",
                    actionLabel = "Clear search",
                    onAction = { onSearchQueryChange("") },
                    modifier = Modifier.padding(horizontal = PomeloSpacing.screenHorizontal),
                )
            }
            filteredTracks.isEmpty() && detail.tracks.isEmpty() -> {
                MessagePanel(
                    title = "No tracks",
                    message = "This $noun has no songs yet.",
                    modifier = Modifier.padding(horizontal = PomeloSpacing.screenHorizontal),
                )
            }
            else -> {
                LazyColumn(contentPadding = PaddingValues(bottom = PomeloSpacing.listBottomInset)) {
                    item {
                        AlbumPlaylistHeader(
                            detail = detail,
                            artistName = artistName,
                            onShuffle = { onShuffle(detail.tracks, detail.title) },
                            onPlay = { onPlayAll(detail.tracks, detail.title) },
                            onSave = { onSave(detail) },
                            shuffleEnabled = detail.tracks.isNotEmpty() && shuffleAvailable,
                            isSaved = isSaved,
                        )
                    }
                    item { headerExtra() }
                    if (detail.supportsBestMix(smartCrossfadeEnabled)) {
                        item {
                            BestMixButton(
                                isSorting = bestMix.isSorting,
                                statusText = bestMix.statusText,
                                onClick = bestMix::trigger,
                            )
                        }
                    }
                    albumPlaylistTrackItems(
                        tracks = filteredTracks,
                        playingTrackId = playingTrackId,
                        onPlayTrack = { list, index -> onPlayTrack(list, index, detail.title) },
                        trackRowWrapper = trackRowWrapper,
                    )
                    item { belowTracks() }
                }
            }
        }
    }
}

private fun BrowseDetail.supportsBestMix(enabled: Boolean) =
    enabled && (kind == CatalogKind.PLAYLIST || kind == CatalogKind.ALBUM) && tracks.size > 1
