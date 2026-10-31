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

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.items
import xyz.stuffbyren.pomelo.ui.scroll.OrchardLazyRow as LazyRow
import xyz.stuffbyren.pomelo.ui.theme.PomeloArtwork
import xyz.stuffbyren.pomelo.ui.theme.PomeloSpacing

@Composable
private fun shimmerBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerOffset",
    )
    return Brush.linearGradient(
        colors = listOf(
            scheme.surfaceContainerHigh,
            scheme.surfaceContainerHighest,
            scheme.surfaceContainerHigh,
        ),
        start = Offset(offset - 400f, 0f),
        end = Offset(offset, 0f),
    )
}

@Composable
fun CatalogCardShimmer(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    Column(modifier.width(140.dp)) {
        Box(
            Modifier
                .size(140.dp)
                .clip(PomeloArtwork.tileShape(140.dp))
                .background(brush),
        )
        Spacer(Modifier.height(PomeloSpacing.explicitBadgeGap))
        Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(brush))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(brush))
    }
}

@Composable
fun TrackRowShimmer(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    Row(
        modifier
            .fillMaxWidth()
            .height(PomeloSpacing.trackRowHeight)
            .padding(horizontal = PomeloSpacing.screenHorizontal),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)).background(brush))
        Spacer(Modifier.width(PomeloSpacing.explicitBadgeGap))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(brush))
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth(0.4f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(brush))
        }
        Spacer(Modifier.width(PomeloSpacing.durationLeadingGap))
        Box(Modifier.width(36.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(brush))
    }
}

@Composable
fun HomeHeroShimmer(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    Box(
        modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = PomeloSpacing.screenHorizontal)
            .clip(PomeloArtwork.heroShape)
            .background(brush),
    )
}

@Composable
fun HomeSectionShimmer(title: String = "Loading...") {
    val brush = shimmerBrush()
    Column(Modifier.padding(vertical = PomeloSpacing.screenHorizontal)) {
        Box(
            Modifier
                .padding(horizontal = PomeloSpacing.screenHorizontal, vertical = PomeloSpacing.explicitBadgeGap)
                .width(120.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush),
        )
        Spacer(Modifier.height(PomeloSpacing.explicitBadgeGap))
        LazyRow(
            userScrollEnabled = false,
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(5) {
                CatalogCardShimmer(Modifier.padding(start = PomeloSpacing.screenHorizontal))
            }
        }
    }
}
