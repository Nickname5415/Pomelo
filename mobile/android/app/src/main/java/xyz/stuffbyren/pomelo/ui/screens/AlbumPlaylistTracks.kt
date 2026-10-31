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

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.itemsIndexed
import xyz.stuffbyren.pomelo.model.Track
import xyz.stuffbyren.pomelo.ui.components.PomeloInsetDivider
import xyz.stuffbyren.pomelo.ui.components.PomeloTrackLeading
import xyz.stuffbyren.pomelo.ui.components.PomeloTrackRow

internal fun LazyListScope.albumPlaylistTrackItems(
    tracks: List<Track>,
    playingTrackId: String?,
    onPlayTrack: (List<Track>, Int) -> Unit,
    trackRowWrapper: @Composable (index: Int, track: Track, row: @Composable () -> Unit) -> Unit =
        { _, _, row -> row() },
) {
    itemsIndexed(tracks, key = { index, track -> "${track.id}_$index" }) { index, track ->
        val trackNumber = index + 1
        trackRowWrapper(index, track) {
            PomeloTrackRow(
                title = track.title,
                onClick = { onPlayTrack(tracks, index) },
                leading = PomeloTrackLeading.Number(trackNumber),
                durationMs = track.durationMs,
                isExplicit = track.explicit,
                isPlaying = playingTrackId != null && track.id == playingTrackId,
            )
        }
        if (index < tracks.lastIndex) {
            PomeloInsetDivider()
        }
    }
}
