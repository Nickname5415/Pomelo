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

package xyz.stuffbyren.pomelo.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.stuffbyren.pomelo.model.Track
import xyz.stuffbyren.pomelo.ui.motion.PlayingBars
import xyz.stuffbyren.pomelo.ui.theme.PomeloArtwork
import xyz.stuffbyren.pomelo.ui.theme.PomeloSpacing
import xyz.stuffbyren.pomelo.ui.theme.PomeloTypography

sealed interface PomeloTrackLeading {
    data class Number(val value: Int) : PomeloTrackLeading
    data class Artwork(val url: String, val contentDescription: String, val size: Dp = 46.dp) : PomeloTrackLeading
}

data class PomeloTrackRowActions(
    val track: Track,
    val onPlay: () -> Unit,
    val onPlayNext: (() -> Unit)? = null,
    val onAddToQueue: (() -> Unit)? = null,
    val onAddToPlaylist: (() -> Unit)? = null,
    val onRemoveFromPlaylist: (() -> Unit)? = null,
    val onMoveUp: (() -> Unit)? = null,
    val onMoveDown: (() -> Unit)? = null,
    val onShare: (() -> Unit)? = null,
    val onDownload: (() -> Unit)? = null,
    val onRemoveDownload: (() -> Unit)? = null,
    val onViewAlbum: (() -> Unit)? = null,
    val onViewArtist: (() -> Unit)? = null,
    val isDownloaded: Boolean = false,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PomeloTrackRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: PomeloTrackLeading? = null,
    durationMs: Long = 0,
    isExplicit: Boolean = false,
    isPlaying: Boolean = false,
    rowHeight: Dp = PomeloSpacing.trackRowHeight,
    trailingContent: @Composable RowScope.() -> Unit = {},
    actions: PomeloTrackRowActions? = null,
) {
    var popupOpen by remember { mutableStateOf(false) }
    if (popupOpen && actions != null) {
        TrackActionsPopup(
            track = actions.track,
            onDismiss = { popupOpen = false },
            onPlay = {
                popupOpen = false
                actions.onPlay()
            },
            onPlayNext = actions.onPlayNext,
            onAddToQueue = actions.onAddToQueue,
            onAddToPlaylist = actions.onAddToPlaylist,
            onRemoveFromPlaylist = actions.onRemoveFromPlaylist,
            onMoveUp = actions.onMoveUp,
            onMoveDown = actions.onMoveDown,
            onDownload = if (!actions.isDownloaded) actions.onDownload else null,
            onRemoveDownload = if (actions.isDownloaded) actions.onRemoveDownload else null,
            onShare = actions.onShare,
            onViewAlbum = actions.onViewAlbum,
            onViewArtist = actions.onViewArtist,
        )
    }

    val scheme = MaterialTheme.colorScheme
    val mutedStyle = PomeloTypography.muted
    val titleStyle = PomeloTypography.trackTitle
    val durationLabel = durationText(durationMs)

    val rowMinHeight = when (val lead = leading) {
        is PomeloTrackLeading.Artwork -> maxOf(rowHeight, lead.size)
        else -> rowHeight
    }

    val clickModifier = if (actions != null) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = { popupOpen = true },
        )
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = rowMinHeight)
            .then(clickModifier)
            .padding(horizontal = PomeloSpacing.screenHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (val lead = leading) {
            is PomeloTrackLeading.Number -> {
                Box(
                    modifier = Modifier.width(PomeloSpacing.trackNumberWidth),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isPlaying) {
                        PlayingBars(color = scheme.primary, size = 14.dp)
                    } else {
                        Text(
                            text = lead.value.toString(),
                            style = mutedStyle,
                            color = scheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            is PomeloTrackLeading.Artwork -> {
                ArtworkTile(
                    url = lead.url,
                    description = lead.contentDescription,
                    modifier = Modifier.size(lead.size),
                    radius = PomeloArtwork.tileRadiusInt(lead.size),
                )
                Spacer(Modifier.width(12.dp))
            }
            null -> Unit
        }
        Text(
            text = title,
            style = titleStyle,
            color = if (isPlaying) scheme.primary else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isExplicit) {
            PomeloExplicitBadge(modifier = Modifier.padding(start = PomeloSpacing.explicitBadgeGap))
        }
        if (durationLabel.isNotBlank()) {
            Text(
                text = durationLabel,
                style = PomeloTypography.mutedTabular,
                color = scheme.onSurface,
                modifier = Modifier.padding(start = PomeloSpacing.durationLeadingGap),
            )
        }
        trailingContent()
    }
}
