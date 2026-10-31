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

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.stuffbyren.pomelo.model.BrowseDetail
import xyz.stuffbyren.pomelo.model.Track
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import xyz.stuffbyren.pomelo.ui.components.ArtworkTile
import xyz.stuffbyren.pomelo.ui.components.CollectionTopSearchBar
import xyz.stuffbyren.pomelo.ui.components.PomeloCapsuleIconButton
import xyz.stuffbyren.pomelo.ui.components.PomeloChromeCapsule
import xyz.stuffbyren.pomelo.ui.components.PomeloCircleChromeButton
import xyz.stuffbyren.pomelo.ui.components.PomeloDetailTopBar
import xyz.stuffbyren.pomelo.ui.components.PomeloExplicitBadge
import xyz.stuffbyren.pomelo.ui.components.PomeloPrimaryActionButton
import xyz.stuffbyren.pomelo.ui.components.PomeloSecondaryActionButton
import xyz.stuffbyren.pomelo.ui.theme.PomeloArtwork
import xyz.stuffbyren.pomelo.ui.theme.PomeloSpacing
import xyz.stuffbyren.pomelo.ui.theme.PomeloTypography

internal fun albumPlaylistDuration(totalMs: Long): String {
    if (totalMs <= 0) return ""
    val totalSec = totalMs / 1_000
    val hours = totalSec / 3_600
    if (hours > 0) {
        val minutes = (totalSec % 3_600) / 60
        val seconds = totalSec % 60
        return "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
    val minutes = totalSec / 60
    return if (minutes > 0) "${minutes}m" else "${totalSec}s"
}

internal fun albumPlaylistMetadata(year: String, trackCount: Int, totalMs: Long): String = buildString {
    if (year.isNotBlank()) append(year)
    if (trackCount > 0) {
        if (isNotEmpty()) append(" · ")
        append("$trackCount ${if (trackCount == 1) "track" else "tracks"}")
    }
    val duration = albumPlaylistDuration(totalMs)
    if (duration.isNotBlank()) {
        if (isNotEmpty()) append(" · ")
        append(duration)
    }
}

@Composable
internal fun AlbumPlaylistTopBar(
    onBack: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    isSaved: Boolean,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    isSearching: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: ((String) -> Unit)? = null,
    onCloseSearch: (() -> Unit)? = null,
    searchPlaceholder: String = "Find in playlist",
    aboutLabel: String = "About",
    onAbout: (() -> Unit)? = null,
    onBestMix: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    isDownloaded: Boolean = false,
) {
    if (isSearching && onSearchQueryChange != null && onCloseSearch != null) {
        CollectionTopSearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            onClose = onCloseSearch,
            placeholder = searchPlaceholder,
            modifier = modifier.background(MaterialTheme.colorScheme.background),
        )
        return
    }

    var menuOpen by remember { mutableStateOf(false) }

    PomeloDetailTopBar(onBack = onBack, modifier = modifier) {
        if (onSearch != null) {
            PomeloCapsuleIconButton(onSearch, Icons.Rounded.Search, "Search in collection")
        }
        PomeloCapsuleIconButton(onShare, Icons.Rounded.IosShare, "Share")
        Box {
            PomeloCapsuleIconButton({ menuOpen = true }, Icons.Rounded.MoreHoriz, "More options")
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(if (isSaved) "Remove from library" else "Add to library") },
                    onClick = {
                        menuOpen = false
                        onSave()
                    },
                )
                if (onDownload != null) {
                    DropdownMenuItem(
                        text = { Text(if (isDownloaded) "Remove downloads" else "Download all") },
                        onClick = {
                            menuOpen = false
                            onDownload()
                        },
                    )
                }
                if (onAbout != null) {
                    DropdownMenuItem(
                        text = { Text(aboutLabel) },
                        onClick = {
                            menuOpen = false
                            onAbout()
                        },
                    )
                }
                if (onBestMix != null) {
                    DropdownMenuItem(
                        text = { Text("Play with Best Mix") },
                        onClick = {
                            menuOpen = false
                            onBestMix()
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun AlbumPlaylistActionRow(
    onShuffle: () -> Unit,
    onPlay: () -> Unit,
    onAdd: () -> Unit,
    shuffleEnabled: Boolean,
    playEnabled: Boolean,
    showAdd: Boolean,
    isSaved: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PomeloSpacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(PomeloSpacing.actionButtonSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PomeloSecondaryActionButton(
            onClick = onShuffle,
            enabled = shuffleEnabled,
            width = PomeloSpacing.actionSecondaryWidthShuffle,
        ) {
            Icon(Icons.Rounded.Shuffle, "Shuffle", tint = scheme.primary, modifier = Modifier.size(22.dp))
        }
        PomeloPrimaryActionButton(
            onClick = onPlay,
            enabled = playEnabled,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.PlayArrow, "Play", tint = scheme.onPrimary, modifier = Modifier.size(28.dp))
        }
        if (showAdd) {
            PomeloSecondaryActionButton(
                onClick = onAdd,
                enabled = true,
                width = PomeloSpacing.actionSecondaryWidthAdd,
            ) {
                Icon(
                    if (isSaved) Icons.Rounded.Check else Icons.Rounded.Add,
                    if (isSaved) "Saved to library" else "Add to library",
                    tint = scheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
internal fun AlbumPlaylistHeader(
    detail: BrowseDetail,
    artistName: String,
    onShuffle: () -> Unit,
    onPlay: () -> Unit,
    onSave: () -> Unit,
    shuffleEnabled: Boolean,
    isSaved: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val totalMs = remember(detail.tracks) { detail.tracks.sumOf { it.durationMs } }
    val metadata = remember(detail.year, detail.tracks.size, totalMs) {
        albumPlaylistMetadata(detail.year, detail.tracks.size, totalMs)
    }
    val mutedStyle = PomeloTypography.muted
    val showLibraryAdd = !xyz.stuffbyren.pomelo.local.isLocalPlaylistId(detail.id)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtworkTile(
            url = detail.artworkUrl,
            description = "Artwork for ${detail.title}",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PomeloSpacing.screenHorizontal)
                .aspectRatio(1f)
                .clip(PomeloArtwork.heroShape),
            radius = PomeloArtwork.heroCorner.value.toInt(),
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PomeloSpacing.screenHorizontal),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = detail.title,
                style = PomeloTypography.collectionTitle,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (detail.explicit) {
                Spacer(Modifier.width(PomeloSpacing.explicitBadgeGap))
                PomeloExplicitBadge()
            }
        }
        if (artistName.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = artistName,
                style = PomeloTypography.collectionArtist,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = PomeloSpacing.screenHorizontal),
            )
        }
        if (metadata.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = metadata,
                style = mutedStyle,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = PomeloSpacing.screenHorizontal),
            )
        }
        Spacer(Modifier.height(20.dp))
        AlbumPlaylistActionRow(
            onShuffle = onShuffle,
            onPlay = onPlay,
            onAdd = onSave,
            shuffleEnabled = shuffleEnabled,
            playEnabled = detail.tracks.isNotEmpty(),
            showAdd = showLibraryAdd,
            isSaved = isSaved,
        )
    }
}

internal fun albumPlaylistShare(context: Context, detail: BrowseDetail, onShare: ((BrowseDetail) -> Unit)?) {
    if (onShare != null) {
        onShare(detail)
        return
    }
    val artistName = collectionArtistName(detail)
    val target = if (artistName.isNotBlank()) "${detail.title} by $artistName" else detail.title
    val intent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, "Listen to $target on Orchard")
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(intent, "Share ${detail.title}"))
}
