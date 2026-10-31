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

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import xyz.stuffbyren.pomelo.ui.scroll.OrchardLazyRow as LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.stuffbyren.pomelo.model.Album
import xyz.stuffbyren.pomelo.model.CatalogItem
import xyz.stuffbyren.pomelo.model.subtitle
import xyz.stuffbyren.pomelo.ui.motion.bounceClickable
import xyz.stuffbyren.pomelo.ui.motion.pressScale
import xyz.stuffbyren.pomelo.ui.motion.riseIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.scale
import xyz.stuffbyren.pomelo.ui.theme.PomeloArtwork
import xyz.stuffbyren.pomelo.ui.theme.PomeloShapes
import xyz.stuffbyren.pomelo.ui.theme.PomeloSpacing
import xyz.stuffbyren.pomelo.ui.theme.PomeloTypography

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    OrchardSectionHeader(title, action = action, onAction = onAction)
}

@Composable
fun OrchardSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PomeloSpacing.screenHorizontal, vertical = PomeloSpacing.explicitBadgeGap),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
            ),
            color = scheme.onSurface,
        )
        if (action != null && onAction != null) {
            val source = remember { MutableInteractionSource() }
            Surface(
                onClick = onAction,
                color = scheme.surfaceContainerHigh,
                shape = CircleShape,
                interactionSource = source,
                modifier = Modifier.height(32.dp).pressScale(source, 0.9f),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text(
                        text = action,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = scheme.primary,
                    )
                }
            }
        }
    }
}

/** Rail card with Pomelo artwork radii and M3 typography. */
@Composable
fun CatalogCard(item: CatalogItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (item is CatalogItem.Category) {
        CategoryCard(item = item, onClick = onClick, modifier = modifier)
        return
    }
    val scheme = MaterialTheme.colorScheme
    val isArtist = item is CatalogItem.Performer
    val tileSize = 140.dp
    val tileShape = if (isArtist) PomeloArtwork.artistShape else PomeloArtwork.tileShape(tileSize)
    Column(
        modifier = modifier
            .width(tileSize)
            .bounceClickable(pressedScale = 0.93f, onClick = onClick),
        horizontalAlignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(tileShape),
        ) {
            ArtworkTile(
                item.artworkUrl,
                item.title,
                Modifier.fillMaxSize(),
                if (isArtist) 999 else PomeloArtwork.tileRadiusInt(tileSize),
            )
        }
        Spacer(Modifier.height(PomeloSpacing.explicitBadgeGap))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (item is CatalogItem.Song && item.track.explicit) {
                PomeloExplicitBadge()
            }
        }
        Text(
            catalogSubtitle(item),
            style = PomeloTypography.muted,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Category / genre / mood card with left accent stripe. */
@Composable
fun CategoryCard(
    item: CatalogItem.Category,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = PomeloShapes.md
    val stripeColor = item.stripeColor?.let { Color(it.toInt()) } ?: scheme.primary

    Surface(
        modifier = modifier
            .height(52.dp)
            .bounceClickable(shape, pressedScale = 0.94f, onClick = onClick),
        color = scheme.surfaceContainer,
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(stripeColor),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Hero pick card with artwork scrim. */
@Composable
fun TopPickCard(item: CatalogItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = PomeloShapes.xl
    Box(
        modifier
            .width(200.dp)
            .aspectRatio(0.85f)
            .bounceClickable(shape, pressedScale = 0.95f, onClick = onClick),
    ) {
        ArtworkTile(item.artworkUrl, item.title, Modifier.fillMaxSize(), 0)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to scheme.scrim.copy(alpha = 0.35f),
                        1f to scheme.scrim.copy(alpha = 0.85f),
                    ),
                ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Surface(
                color = scheme.primary.copy(alpha = 0.85f),
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                Text(
                    text = catalogKind(item).uppercase(),
                    color = scheme.onPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            if (item is CatalogItem.Song && item.track.explicit) {
                PomeloExplicitBadge(modifier = Modifier.padding(bottom = 6.dp))
            }
            Text(
                item.title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = scheme.onPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                catalogSubtitle(item),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onPrimary.copy(alpha = 0.80f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Filter pill chip group. */
@Composable
fun <T> OrchardFilterChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = PomeloSpacing.screenHorizontal, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(PomeloSpacing.explicitBadgeGap),
    ) {
        items(options.size) { index ->
            val option = options[index]
            val isSelected = option == selected
            val containerColor by animateColorAsState(
                if (isSelected) scheme.secondaryContainer else scheme.surfaceContainer,
                label = "ChipBg",
            )
            val textColor by animateColorAsState(
                if (isSelected) scheme.onSecondaryContainer else scheme.onSurface,
                label = "ChipText",
            )
            val chipScale by animateFloatAsState(
                if (isSelected) 1.06f else 1f,
                spring(dampingRatio = 0.45f, stiffness = 500f),
                label = "ChipScale",
            )
            val source = remember { MutableInteractionSource() }

            Surface(
                onClick = { onSelect(option) },
                shape = CircleShape,
                color = containerColor,
                interactionSource = source,
                modifier = Modifier
                    .riseIn(index + 1, fromScale = 0.8f, cascadeOnScroll = true)
                    .scale(chipScale)
                    .pressScale(source, 0.9f)
                    .height(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = PomeloSpacing.screenHorizontal)) {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = textColor,
                    )
                }
            }
        }
    }
}

@Composable
fun MessagePanel(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = PomeloShapes.lg
    Surface(
        color = scheme.surfaceContainer,
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .padding(PomeloSpacing.screenHorizontal)
            .riseIn(),
    ) {
        Column(Modifier.padding(PomeloSpacing.screenHorizontal)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
            Spacer(Modifier.height(PomeloSpacing.explicitBadgeGap))
            Text(message, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(PomeloSpacing.screenHorizontal))
                Surface(
                    onClick = onAction,
                    color = scheme.primary,
                    shape = CircleShape,
                    modifier = Modifier.height(PomeloSpacing.actionButtonHeight),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 18.dp)) {
                        Text(
                            actionLabel,
                            color = scheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

internal fun catalogSubtitle(item: CatalogItem): String = when (item) {
    is CatalogItem.Song -> item.track.artist
    is CatalogItem.Record -> albumSubtitle(item.album)
    is CatalogItem.Performer -> item.artist.subtitle.ifBlank { "Artist" }
    is CatalogItem.Collection -> item.playlist.subtitle
    is CatalogItem.Category -> ""
}

private fun albumSubtitle(album: Album): String {
    val parts = album.artist.split("•").map(String::trim).filter(String::isNotBlank)
    val artist = parts.filterNot { it.lowercase() in ALBUM_KIND_WORDS || YEAR_ONLY.matches(it) }
    val year = album.year.ifBlank { parts.lastOrNull(YEAR_ONLY::matches).orEmpty() }
    return (artist + year).filter(String::isNotBlank).distinct().joinToString(" • ")
}

private val ALBUM_KIND_WORDS = setOf("album", "single", "ep", "compilation")
private val YEAR_ONLY = Regex("""\d{4}""")

private fun catalogKind(item: CatalogItem): String = when (item) {
    is CatalogItem.Song -> "Song"
    is CatalogItem.Record -> "Album"
    is CatalogItem.Performer -> "Artist"
    is CatalogItem.Collection -> "Playlist"
    is CatalogItem.Category -> "Category"
}
