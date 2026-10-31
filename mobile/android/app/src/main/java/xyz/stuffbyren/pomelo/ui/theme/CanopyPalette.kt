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

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class CanopyPalette(
    val Chrome: Color,
    val Canvas: Color,
    val Surface: Color,
    val SurfaceHover: Color,
    val ReadoutBg: Color,
    val Rule: Color,
    val RuleStrong: Color,
    val Eyebrow: Color,
    val Accent: Color,
    val AccentDeep: Color,
    val OnAccent: Color,
    val SecondaryAccent: Color,
    val Text: Color,
    val Muted: Color,
    val MutedStrong: Color,
    val Favorite: Color,
    val LyricActive: Color,
    val ShimmerBackground: Color,
    val ShimmerLine: Color,
    val Overlay: Color,
    val PlayerBackdrop: Color,
    val Glass: Color,
    val GlassSecondary: Color,
    val GlassChrome: Color,
    val GlassOverlay: Color,
    val GlassBorder: Color,
    val GlassBorderHighlight: Color,
    val Warning: Color,
    val Danger: Color,
)

fun canopyPaletteFrom(
    scheme: ColorScheme,
    accent: Color,
    darkTheme: Boolean,
): CanopyPalette {
    val glassFilm = if (darkTheme) Color(0xFFE7ECF8) else scheme.surfaceContainerHighest
    val glassSecondary = if (darkTheme) Color(0xFFDCE4F0) else scheme.surfaceContainerHigh
    val shimmerBg = if (darkTheme) Color(0x33FFFFFF) else scheme.onSurface.copy(alpha = 0.08f)
    val shimmerLine = if (darkTheme) Color(0x66FFFFFF) else scheme.onSurface.copy(alpha = 0.16f)
    val ruleAlpha = if (darkTheme) 0.35f else 0.45f
    val ruleStrongAlpha = if (darkTheme) 0.55f else 0.65f
    return CanopyPalette(
        Chrome = scheme.background,
        Canvas = scheme.surfaceContainerLow,
        Surface = scheme.surfaceContainer,
        SurfaceHover = scheme.surfaceContainerHighest,
        ReadoutBg = scheme.surfaceContainerHigh,
        Rule = scheme.outline.copy(alpha = ruleAlpha),
        RuleStrong = scheme.outline.copy(alpha = ruleStrongAlpha),
        Eyebrow = scheme.onSurfaceVariant,
        Accent = accent,
        AccentDeep = scheme.primaryContainer,
        OnAccent = scheme.onPrimary,
        SecondaryAccent = scheme.secondary,
        Text = scheme.onBackground,
        Muted = scheme.onSurfaceVariant,
        MutedStrong = scheme.onSurface,
        Favorite = Color(0xFFFF4081),
        LyricActive = Color(0xFFFFE500),
        ShimmerBackground = shimmerBg,
        ShimmerLine = shimmerLine,
        Overlay = Color(0x40000000),
        PlayerBackdrop = scheme.background,
        Glass = glassFilm,
        GlassSecondary = glassSecondary,
        GlassChrome = scheme.surfaceContainerLowest,
        GlassOverlay = scheme.surfaceContainerLow,
        GlassBorder = scheme.onSurface.copy(alpha = if (darkTheme) 0.10f else 0.12f),
        GlassBorderHighlight = scheme.onSurface.copy(alpha = if (darkTheme) 0.22f else 0.18f),
        Warning = Color(0xFFFFC46B),
        Danger = scheme.error,
    )
}

fun defaultCanopyPalette(): CanopyPalette =
    canopyPaletteFrom(
        scheme = pomeloDarkColorScheme(),
        accent = PomeloDarkColors.Primary,
        darkTheme = true,
    )
