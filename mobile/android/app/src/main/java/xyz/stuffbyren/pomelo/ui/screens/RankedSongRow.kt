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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.stuffbyren.pomelo.model.Track
import xyz.stuffbyren.pomelo.ui.components.PomeloTrackLeading
import xyz.stuffbyren.pomelo.ui.components.PomeloTrackRow
import xyz.stuffbyren.pomelo.ui.components.PomeloTrackRowActions
import xyz.stuffbyren.pomelo.ui.components.TrackActionsPopup
import xyz.stuffbyren.pomelo.ui.motion.popOnChange
import xyz.stuffbyren.pomelo.ui.theme.PomeloSpacing

/** Home top-songs row: Pomelo track row with rank, like, and overflow menu. */
@Composable
internal fun RankedSongRow(
    rank: Int? = null,
    track: Track,
    liked: Boolean,
    onPlay: () -> Unit,
    onToggleLike: () -> Unit,
    onPlayNext: ((Track) -> Unit)?,
    onAddToQueue: ((Track) -> Unit)?,
    onAddToPlaylist: ((Track) -> Unit)?,
    onShare: ((Track) -> Unit)?,
) {
    var popupOpen by remember { mutableStateOf(false) }
    if (popupOpen) {
        TrackActionsPopup(
            track = track,
            onDismiss = { popupOpen = false },
            onPlay = {
                popupOpen = false
                onPlay()
            },
            onPlayNext = onPlayNext?.let { { it(track) } },
            onAddToQueue = onAddToQueue?.let { { it(track) } },
            onAddToPlaylist = onAddToPlaylist?.let { { it(track) } },
            onShare = onShare?.let { { it(track) } },
        )
    }

    PomeloTrackRow(
        title = track.title,
        onClick = onPlay,
        leading = rank?.let { PomeloTrackLeading.Number(it) }
            ?: PomeloTrackLeading.Artwork(track.artworkUrl, track.title),
        durationMs = track.durationMs,
        isExplicit = track.explicit,
        trailingContent = {
            RankedSongTrailing(
                liked = liked,
                onToggleLike = onToggleLike,
                onOpenMenu = { popupOpen = true },
            )
        },
    )
}

@Composable
private fun RowScope.RankedSongTrailing(
    liked: Boolean,
    onToggleLike: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    IconButton(onClick = onToggleLike, modifier = Modifier.size(PomeloSpacing.chromeCapsuleIconSize)) {
        Icon(
            if (liked) Icons.Filled.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (liked) "Unlike" else "Like",
            tint = if (liked) scheme.tertiary else scheme.onSurfaceVariant,
            modifier = Modifier.popOnChange(liked, peak = 1.5f, onlyOn = true),
        )
    }
    IconButton(onClick = onOpenMenu, modifier = Modifier.size(PomeloSpacing.chromeCapsuleIconSize)) {
        Icon(
            Icons.Rounded.MoreHoriz,
            contentDescription = "Options",
            tint = scheme.onSurfaceVariant,
        )
    }
}
