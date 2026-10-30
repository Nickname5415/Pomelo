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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import xyz.stuffbyren.pomelo.auth.AuthState
import xyz.stuffbyren.pomelo.download.DownloadItem
import xyz.stuffbyren.pomelo.download.DownloadStatus
import xyz.stuffbyren.pomelo.model.CatalogItem
import xyz.stuffbyren.pomelo.model.CatalogSection
import xyz.stuffbyren.pomelo.model.HomeSectionConfig
import xyz.stuffbyren.pomelo.model.LibrarySnapshot
import xyz.stuffbyren.pomelo.model.LoadState
import xyz.stuffbyren.pomelo.model.OrchardSettings
import xyz.stuffbyren.pomelo.model.Track
import xyz.stuffbyren.pomelo.ui.components.CatalogSectionBottomSheet

class HomeActions(
    val refresh: () -> Unit,
    val search: () -> Unit,
    val play: (Track) -> Unit,
    val openDetail: (String) -> Unit,
    val editLayout: () -> Unit,
    val toggleLike: (Track) -> Unit,
    val playNext: ((Track) -> Unit)?,
    val addToQueue: ((Track) -> Unit)?,
    val addToPlaylist: ((Track) -> Unit)?,
    val share: ((Track) -> Unit)?,
    val openProfile: () -> Unit,
    val fetchSectionItems: (suspend (String, String) -> List<CatalogItem>)?,
    val playItem: ((CatalogItem) -> Unit)?,
    val playCollection: ((String, String) -> Unit)?,
)

data class HomeSectionSheetState(
    val title: String,
    val initialItems: List<CatalogItem>,
    val browseId: String = "",
    val params: String = "",
)

/** Inputs, derived rails and actions shared by the phone and foldable Home layouts. */
@Stable
class HomeData internal constructor(
    settings: OrchardSettings,
    state: LoadState<List<CatalogSection>>,
    library: LibrarySnapshot,
    auth: AuthState,
    downloads: List<DownloadItem>,
    downloadedTrackIds: Set<String>,
    isOffline: Boolean,
    actions: HomeActions,
) {
    // Collections compare by reference; structural equals on every recomposition is costly.
    var settings by mutableStateOf(settings, referentialEqualityPolicy()); internal set
    var state by mutableStateOf(state, referentialEqualityPolicy()); internal set
    var library by mutableStateOf(library, referentialEqualityPolicy()); internal set
    var auth by mutableStateOf(auth); internal set
    internal var downloads by mutableStateOf(downloads, referentialEqualityPolicy())
    var downloadedTrackIds by mutableStateOf(downloadedTrackIds, referentialEqualityPolicy()); internal set
    var isOffline by mutableStateOf(isOffline); internal set
    var actions by mutableStateOf(actions, referentialEqualityPolicy()); internal set

    /** Rail whose "See all" sheet is open. */
    var sheet by mutableStateOf<HomeSectionSheetState?>(null)

    val offline by derivedStateOf { isOffline || state is LoadState.Error }
    val layout: List<HomeSectionConfig>
        get() = if (offline) settings.homeLayoutOffline else settings.homeLayoutOnline
    val sections by derivedStateOf { (state as? LoadState.Content)?.value.orEmpty() }

    val downloadedTracks by derivedStateOf {
        downloads.filter { it.status == DownloadStatus.COMPLETED && it.filePath.isNotBlank() }.map { it.track }
    }
    private val likedIds by derivedStateOf { library.likedTracks.mapTo(HashSet()) { it.id } }

    val playlists by derivedStateOf {
        library.savedPlaylists.map { CatalogItem.Collection(it) }
            .ifEmpty { itemsOfKind<CatalogItem.Collection>() }
            .distinctBy { it.stableId }
    }
    val artists by derivedStateOf {
        library.savedArtists.map { CatalogItem.Performer(it) }
            .ifEmpty { itemsOfKind<CatalogItem.Performer>() }
            .distinctBy { it.stableId }
    }
    val topSongs by derivedStateOf {
        library.mostPlayed
            .ifEmpty { library.likedTracks }
            .ifEmpty { library.recentlyPlayed }
            .ifEmpty { itemsOfKind<CatalogItem.Song>().map { it.track } }
            .distinctBy { it.id }
    }

    val offlinePlaylists by derivedStateOf {
        offlinePlaylistItems(downloadedTracks, downloadedTrackIds, library.savedPlaylists)
    }
    val offlineArtists by derivedStateOf { offlineArtistItems(downloadedTracks, library.savedArtists) }
    val offlineAlbums by derivedStateOf { offlineAlbumItems(downloadedTracks) }

    internal val spotlight by derivedStateOf { discoverEntries(state, library) }
    internal val quickGrid by derivedStateOf {
        quickGridItems(library, state, { actions.openDetail(it) }, ::open, ::play)
    }
    /** Artwork-bearing catalog items first, then saved playlists, albums and likes. */
    val heroItems by derivedStateOf {
        (
            sections.flatMap { it.items }.filter { it.artworkUrl.isNotBlank() } +
                library.savedPlaylists.map { CatalogItem.Collection(it) } +
                library.savedAlbums.map { CatalogItem.Record(it) } +
                library.likedTracks.map { CatalogItem.Song(it) }
            ).distinctBy { it.stableId }.take(6)
    }

    fun isLiked(track: Track): Boolean = track.id in likedIds

    fun open(item: CatalogItem) {
        when (item) {
            is CatalogItem.Song -> actions.play(item.track)
            else -> actions.openDetail(item.stableId)
        }
    }

    fun play(item: CatalogItem) {
        val actions = actions
        actions.playItem?.let { return it(item) }
        val playCollection = actions.playCollection ?: return open(item)
        when (item) {
            is CatalogItem.Song -> actions.play(item.track)
            is CatalogItem.Collection -> playCollection(item.playlist.id, item.title)
            is CatalogItem.Record -> playCollection(item.album.id, item.title)
            is CatalogItem.Performer -> playCollection(item.artist.id, item.title)
            is CatalogItem.Category -> actions.openDetail(item.stableId)
        }
    }

    fun seeAll(title: String, items: List<CatalogItem>, browseId: String = "", params: String = ""): () -> Unit =
        { sheet = HomeSectionSheetState(title, items, browseId, params) }

    private inline fun <reified T : CatalogItem> itemsOfKind(): List<T> =
        sections.flatMap { it.items }.filterIsInstance<T>()
}

@Composable
fun rememberHomeData(
    settings: OrchardSettings,
    state: LoadState<List<CatalogSection>>,
    library: LibrarySnapshot,
    auth: AuthState,
    downloads: List<DownloadItem>,
    downloadedTrackIds: Set<String>,
    isOffline: Boolean,
    actions: HomeActions,
): HomeData =
    remember { HomeData(settings, state, library, auth, downloads, downloadedTrackIds, isOffline, actions) }.apply {
        this.settings = settings
        this.state = state
        this.library = library
        this.auth = auth
        this.downloads = downloads
        this.downloadedTrackIds = downloadedTrackIds
        this.isOffline = isOffline
        this.actions = actions
    }

/** "See all" sheet for the rail named by [HomeData.sheet]. */
@Composable
internal fun HomeSectionSheet(data: HomeData) {
    val sheet = data.sheet ?: return
    CatalogSectionBottomSheet(
        title = sheet.title,
        initialItems = sheet.initialItems,
        browseId = sheet.browseId,
        params = sheet.params,
        onFetchFullItems = data.actions.fetchSectionItems,
        onPlay = data.actions.play,
        onPlayItem = data::play,
        onOpen = data.actions.openDetail,
        onDismiss = { data.sheet = null },
    )
}
