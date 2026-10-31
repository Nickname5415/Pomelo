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

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.stuffbyren.pomelo.model.Track
import xyz.stuffbyren.pomelo.ui.theme.OrchardTheme
import xyz.stuffbyren.pomelo.ui.theme.PomeloSpacing

@Preview(name = "Explicit badge", showBackground = true)
@Preview(name = "Explicit badge dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PomeloExplicitBadgePreview() {
    OrchardTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PomeloExplicitBadge(Modifier.padding(16.dp))
        }
    }
}

@Preview(name = "Inset divider", widthDp = 390, showBackground = true)
@Composable
private fun PomeloInsetDividerPreview() {
    OrchardTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.background)) {
            PomeloTrackRow(
                title = "Talk About It",
                onClick = {},
                leading = PomeloTrackLeading.Number(3),
                durationMs = 213_000,
            )
            PomeloInsetDivider()
            PomeloTrackRow(
                title = "Romeo",
                onClick = {},
                leading = PomeloTrackLeading.Number(4),
                durationMs = 178_000,
                isExplicit = true,
            )
        }
    }
}

@Preview(name = "Track row normal", widthDp = 390, showBackground = true)
@Preview(name = "Track row dark", widthDp = 390, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PomeloTrackRowPreview() {
    OrchardTheme {
        PomeloTrackRow(
            title = "Beat 54 (All Good Now)",
            onClick = {},
            leading = PomeloTrackLeading.Number(1),
            durationMs = 225_000,
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
    }
}

@Preview(name = "Track row long title", widthDp = 390, showBackground = true)
@Composable
private fun PomeloTrackRowLongTitlePreview() {
    OrchardTheme {
        PomeloTrackRow(
            title = "This Song Has An Extremely Long Title That Must Ellipsize Before The Badge",
            onClick = {},
            leading = PomeloTrackLeading.Number(2),
            durationMs = 304_000,
            isExplicit = true,
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
    }
}

@Preview(name = "Track row playing", widthDp = 390, showBackground = true)
@Composable
private fun PomeloTrackRowPlayingPreview() {
    OrchardTheme {
        PomeloTrackRow(
            title = "Keep Moving",
            onClick = {},
            leading = PomeloTrackLeading.Number(2),
            durationMs = 304_000,
            isPlaying = true,
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
    }
}

@Preview(name = "Action row", widthDp = 390, showBackground = true)
@Composable
private fun PomeloActionRowPreview() {
    OrchardTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.background).padding(vertical = 16.dp)) {
            PomeloCollectionActionRowPreviewBody()
        }
    }
}

@Composable
internal fun PomeloCollectionActionRowPreviewBody() {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PomeloSpacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(PomeloSpacing.actionButtonSpacing),
    ) {
        PomeloSecondaryActionButton(
            onClick = {},
            enabled = true,
            width = PomeloSpacing.actionSecondaryWidthShuffle,
        ) {
            Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = scheme.primary)
        }
        PomeloPrimaryActionButton(
            onClick = {},
            enabled = true,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = scheme.onPrimary)
        }
        PomeloSecondaryActionButton(
            onClick = {},
            enabled = true,
            width = PomeloSpacing.actionSecondaryWidthAdd,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = scheme.primary)
        }
    }
}

@Preview(name = "Track row trailing ⋮", widthDp = 390, showBackground = true)
@Composable
private fun PomeloTrackRowTrailingPreview() {
    OrchardTheme {
        PomeloTrackRow(
            title = "Hands Off",
            onClick = {},
            leading = PomeloTrackLeading.Artwork("", "Artwork"),
            durationMs = 198_000,
            actions = PomeloTrackRowActions(
                track = Track("1", "Hands Off", "Jungle"),
                onPlay = {},
            ),
            trailingContent = {
                IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.MoreHoriz, "Actions", tint = MaterialTheme.colorScheme.primary)
                }
            },
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
    }
}

@Preview(name = "Detail top bar", widthDp = 390, showBackground = true)
@Composable
private fun PomeloDetailTopBarPreview() {
    OrchardTheme {
        PomeloDetailTopBar(onBack = {}) {
            PomeloCapsuleIconButton({}, Icons.Rounded.Search, "Search")
            PomeloCapsuleIconButton({}, Icons.Rounded.IosShare, "Share")
        }
    }
}
