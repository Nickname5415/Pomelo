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

package xyz.stuffbyren.pomelo.app

import xyz.stuffbyren.pomelo.model.Artist
import xyz.stuffbyren.pomelo.model.BrowseDetail
import xyz.stuffbyren.pomelo.model.CatalogItem
import xyz.stuffbyren.pomelo.model.CatalogKind
import xyz.stuffbyren.pomelo.model.CatalogSection
import xyz.stuffbyren.pomelo.model.LibrarySnapshot
import xyz.stuffbyren.pomelo.model.LoadState
import xyz.stuffbyren.pomelo.model.SearchResults
import xyz.stuffbyren.pomelo.model.Track

internal fun findCatalogItem(
    id: String,
    homeState: LoadState<List<CatalogSection>>,
    searchState: LoadState<SearchResults>,
    libraryState: LibrarySnapshot,
    detailState: LoadState<BrowseDetail>,
): CatalogItem? {
    val homeItem = (homeState as? LoadState.Content)?.value
        ?.asSequence()?.flatMap { it.items.asSequence() }?.firstOrNull { it.stableId == id }
    if (homeItem != null) return homeItem

    val searchItem = (searchState as? LoadState.Content)?.value?.let { result ->
        buildList<CatalogItem> {
            addAll(result.albums.map(CatalogItem::Record))
            addAll(result.artists.map(CatalogItem::Performer))
            addAll(result.playlists.map(CatalogItem::Collection))
        }.firstOrNull { it.stableId == id }
    }
    if (searchItem != null) return searchItem

    return libraryState.savedAlbums.firstOrNull { it.id == id }?.let(CatalogItem::Record)
        ?: libraryState.savedArtists.firstOrNull { it.id == id }?.let(CatalogItem::Performer)
        ?: libraryState.savedPlaylists.firstOrNull { it.id == id }?.let(CatalogItem::Collection)
        ?: libraryState.recentlyPlayed.firstOrNull { it.id == id }?.let(CatalogItem::Song)
        ?: (detailState as? LoadState.Content)?.value?.related?.firstOrNull { it.stableId == id }
}

/** An artist named by a loaded track's credits, for artist links that only carry an id. */
internal fun artistFromTracks(id: String, tracks: Sequence<Track>): CatalogItem? {
    for (track in tracks) {
        track.artists.firstOrNull { it.id == id && it.name.isNotBlank() }?.let { return CatalogItem.Performer(it) }
        // Without per-artist credits the joined artist string is only safe when it names one artist.
        if (track.artistId == id && track.artists.isEmpty() && track.artist.isNotBlank()) {
            return CatalogItem.Performer(Artist(id = id, name = track.artist))
        }
    }
    return null
}

internal fun BrowseDetail.withSeed(seed: CatalogItem?): BrowseDetail {
    if (seed == null) return this
    val seedSubtitle = when (seed) {
        is CatalogItem.Record -> seed.album.artist
        is CatalogItem.Performer -> seed.artist.subtitle
        is CatalogItem.Collection -> seed.playlist.author
        is CatalogItem.Song -> seed.track.artist
        is CatalogItem.Category -> ""
    }
    val cleanSeedArtist = if (seedSubtitle.equals("Playlist", ignoreCase = true) ||
        seedSubtitle.equals("Unlisted", ignoreCase = true) ||
        seedSubtitle.equals("Public", ignoreCase = true) ||
        seedSubtitle.equals("Private", ignoreCase = true)) "" else seedSubtitle

    val resolvedKind = when (seed) {
        is CatalogItem.Performer -> CatalogKind.ARTIST
        is CatalogItem.Record -> CatalogKind.ALBUM
        is CatalogItem.Collection -> CatalogKind.PLAYLIST
        else -> kind
    }

    return copy(
        kind = resolvedKind,
        title = title.takeUnless { it.isBlank() || it.equals("Collection", true) || it.equals("Album", true) || it.equals("Artist", true) } ?: seed.title,
        artist = artist.ifBlank { cleanSeedArtist },
        subtitle = subtitle.ifBlank { seedSubtitle },
        artworkUrl = artworkUrl.ifBlank { seed.artworkUrl },
    )
}

internal fun String.isMeaningfulPlaybackSource(): Boolean = isNotBlank() && !contains(
    Regex("\\b(?:plays?|views?|listeners?|subscribers?)\\b", RegexOption.IGNORE_CASE),
)

/** Returns the same instance when [videoId] is absent; otherwise removes one playlist row. */
internal fun BrowseDetail.withPlaylistTrackRemoved(videoId: String): BrowseDetail {
    val index = tracks.indexOfFirst { it.id == videoId }
    if (index < 0) return this
    return copy(tracks = tracks.toMutableList().apply { removeAt(index) })
}

/** Returns the same instance for invalid/no-op moves; otherwise relocates exactly one row. */
internal fun BrowseDetail.withPlaylistTrackMoved(fromIndex: Int, toIndex: Int): BrowseDetail {
    if (fromIndex !in tracks.indices || toIndex !in tracks.indices || fromIndex == toIndex) return this
    return copy(tracks = tracks.toMutableList().apply { add(toIndex, removeAt(fromIndex)) })
}
