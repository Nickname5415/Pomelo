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

package xyz.stuffbyren.pomelo.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Active palette for the current [OrchardTheme] composition. [OrchardTheme] publishes here so
 * draw nodes, glass, and legacy call sites stay aligned with [androidx.compose.material3.MaterialTheme].
 */
object CanopyColorsState {
    var palette by mutableStateOf(defaultCanopyPalette())
        internal set
}

val LocalCanopyPalette = staticCompositionLocalOf { defaultCanopyPalette() }

/**
 * Theme-aware semantic colours for glass, player chrome, and legacy call sites.
 *
 * Prefer [androidx.compose.material3.MaterialTheme.colorScheme] in new UI; this object bridges
 * [CanopyPalette] (derived from the active scheme in [OrchardTheme]) until each screen migrates.
 * Do not remove symbols still referenced under NowPlaying* / Player* without checking grep first.
 */
object CanopyColors {
    private val p: CanopyPalette get() = CanopyColorsState.palette

    val Chrome get() = p.Chrome
    val Canvas get() = p.Canvas
    val Surface get() = p.Surface
    val SurfaceHover get() = p.SurfaceHover
    val ReadoutBg get() = p.ReadoutBg
    val Rule get() = p.Rule
    val RuleStrong get() = p.RuleStrong
    val Eyebrow get() = p.Eyebrow
    val Accent get() = p.Accent
    val AccentDeep get() = p.AccentDeep
    val OnAccent get() = p.OnAccent
    val SecondaryAccent get() = p.SecondaryAccent
    val Text get() = p.Text
    val Muted get() = p.Muted
    val MutedStrong get() = p.MutedStrong
    val Favorite get() = p.Favorite
    val LyricActive get() = p.LyricActive
    val ShimmerBackground get() = p.ShimmerBackground
    val ShimmerLine get() = p.ShimmerLine
    val Overlay get() = p.Overlay
    val PlayerBackdrop get() = p.PlayerBackdrop
    val Glass get() = p.Glass
    val GlassSecondary get() = p.GlassSecondary
    val GlassChrome get() = p.GlassChrome
    val GlassOverlay get() = p.GlassOverlay
    val GlassBorder get() = p.GlassBorder
    val GlassBorderHighlight get() = p.GlassBorderHighlight
    val Warning get() = p.Warning
    val Danger get() = p.Danger
}
