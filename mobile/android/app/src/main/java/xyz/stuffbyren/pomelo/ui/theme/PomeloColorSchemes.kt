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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Exact light-scheme role values for Pomelo. */
object PomeloLightColors {
    val Primary = Color(0xFF65558F)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFFE9DDFF)
    val OnPrimaryContainer = Color(0xFF4C3D75)
    val Secondary = Color(0xFF69548D)
    val OnSecondary = Color(0xFFFFFFFF)
    val SecondaryContainer = Color(0xFFECDCFF)
    val OnSecondaryContainer = Color(0xFF513C73)
    val Tertiary = Color(0xFF844C73)
    val OnTertiary = Color(0xFFFFFFFF)
    val TertiaryContainer = Color(0xFFFFD7EE)
    val OnTertiaryContainer = Color(0xFF69345A)
    val Error = Color(0xFFBA1A1A)
    val OnError = Color(0xFFFFFFFF)
    val ErrorContainer = Color(0xFFFFDAD6)
    val OnErrorContainer = Color(0xFF93000A)
    val Background = Color(0xFFFDF7FF)
    val OnBackground = Color(0xFF1D1B20)
    val Surface = Color(0xFFFEF7FF)
    val OnSurface = Color(0xFF1D1B20)
    val SurfaceVariant = Color(0xFFE7E0EB)
    val OnSurfaceVariant = Color(0xFF49454E)
    val Outline = Color(0xFF7A757F)
    val OutlineVariant = Color(0xFFCBC4CF)
    val InverseSurface = Color(0xFF322F35)
    val InverseOnSurface = Color(0xFFF5EFF7)
    val InversePrimary = Color(0xFFCFBDFE)
    val SurfaceDim = Color(0xFFDED8E0)
    val SurfaceBright = Color(0xFFFEF7FF)
    val SurfaceContainerLowest = Color(0xFFFFFFFF)
    val SurfaceContainerLow = Color(0xFFF8F1FA)
    val SurfaceContainer = Color(0xFFF2ECF4)
    val SurfaceContainerHigh = Color(0xFFECE6EE)
    val SurfaceContainerHighest = Color(0xFFE6E0E8)
}

/** Exact dark-scheme role values for Pomelo. */
object PomeloDarkColors {
    val Primary = Color(0xFFCFBDFE)
    val OnPrimary = Color(0xFF36275D)
    val PrimaryContainer = Color(0xFF4C3D75)
    val OnPrimaryContainer = Color(0xFFE9DDFF)
    val Secondary = Color(0xFFD4BBFC)
    val OnSecondary = Color(0xFF3A255B)
    val SecondaryContainer = Color(0xFF513C73)
    val OnSecondaryContainer = Color(0xFFECDCFF)
    val Tertiary = Color(0xFFF7B2DF)
    val OnTertiary = Color(0xFF4F1E43)
    val TertiaryContainer = Color(0xFF69345A)
    val OnTertiaryContainer = Color(0xFFFFD7EE)
    val Error = Color(0xFFFFB4AB)
    val OnError = Color(0xFF690005)
    val ErrorContainer = Color(0xFF93000A)
    val OnErrorContainer = Color(0xFFFFDAD6)
    val Background = Color(0xFF141218)
    val OnBackground = Color(0xFFE6E1E9)
    val Surface = Color(0xFF141218)
    val OnSurface = Color(0xFFE6E0E8)
    val SurfaceVariant = Color(0xFF49454E)
    val OnSurfaceVariant = Color(0xFFCBC4CF)
    val Outline = Color(0xFF948F99)
    val OutlineVariant = Color(0xFF49454E)
    val InverseSurface = Color(0xFFE6E0E8)
    val InverseOnSurface = Color(0xFF322F35)
    val InversePrimary = Color(0xFF65558F)
    val SurfaceDim = Color(0xFF141218)
    val SurfaceBright = Color(0xFF3B383E)
    val SurfaceContainerLowest = Color(0xFF0F0D13)
    val SurfaceContainerLow = Color(0xFF1D1B20)
    val SurfaceContainer = Color(0xFF211F24)
    val SurfaceContainerHigh = Color(0xFF2B292F)
    val SurfaceContainerHighest = Color(0xFF36343A)
}

fun pomeloLightColorScheme(): ColorScheme = lightColorScheme(
    primary = PomeloLightColors.Primary,
    onPrimary = PomeloLightColors.OnPrimary,
    primaryContainer = PomeloLightColors.PrimaryContainer,
    onPrimaryContainer = PomeloLightColors.OnPrimaryContainer,
    secondary = PomeloLightColors.Secondary,
    onSecondary = PomeloLightColors.OnSecondary,
    secondaryContainer = PomeloLightColors.SecondaryContainer,
    onSecondaryContainer = PomeloLightColors.OnSecondaryContainer,
    tertiary = PomeloLightColors.Tertiary,
    onTertiary = PomeloLightColors.OnTertiary,
    tertiaryContainer = PomeloLightColors.TertiaryContainer,
    onTertiaryContainer = PomeloLightColors.OnTertiaryContainer,
    error = PomeloLightColors.Error,
    onError = PomeloLightColors.OnError,
    errorContainer = PomeloLightColors.ErrorContainer,
    onErrorContainer = PomeloLightColors.OnErrorContainer,
    background = PomeloLightColors.Background,
    onBackground = PomeloLightColors.OnBackground,
    surface = PomeloLightColors.Surface,
    onSurface = PomeloLightColors.OnSurface,
    surfaceVariant = PomeloLightColors.SurfaceVariant,
    onSurfaceVariant = PomeloLightColors.OnSurfaceVariant,
    outline = PomeloLightColors.Outline,
    outlineVariant = PomeloLightColors.OutlineVariant,
    inverseSurface = PomeloLightColors.InverseSurface,
    inverseOnSurface = PomeloLightColors.InverseOnSurface,
    inversePrimary = PomeloLightColors.InversePrimary,
    surfaceDim = PomeloLightColors.SurfaceDim,
    surfaceBright = PomeloLightColors.SurfaceBright,
    surfaceContainerLowest = PomeloLightColors.SurfaceContainerLowest,
    surfaceContainerLow = PomeloLightColors.SurfaceContainerLow,
    surfaceContainer = PomeloLightColors.SurfaceContainer,
    surfaceContainerHigh = PomeloLightColors.SurfaceContainerHigh,
    surfaceContainerHighest = PomeloLightColors.SurfaceContainerHighest,
)

fun pomeloDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = PomeloDarkColors.Primary,
    onPrimary = PomeloDarkColors.OnPrimary,
    primaryContainer = PomeloDarkColors.PrimaryContainer,
    onPrimaryContainer = PomeloDarkColors.OnPrimaryContainer,
    secondary = PomeloDarkColors.Secondary,
    onSecondary = PomeloDarkColors.OnSecondary,
    secondaryContainer = PomeloDarkColors.SecondaryContainer,
    onSecondaryContainer = PomeloDarkColors.OnSecondaryContainer,
    tertiary = PomeloDarkColors.Tertiary,
    onTertiary = PomeloDarkColors.OnTertiary,
    tertiaryContainer = PomeloDarkColors.TertiaryContainer,
    onTertiaryContainer = PomeloDarkColors.OnTertiaryContainer,
    error = PomeloDarkColors.Error,
    onError = PomeloDarkColors.OnError,
    errorContainer = PomeloDarkColors.ErrorContainer,
    onErrorContainer = PomeloDarkColors.OnErrorContainer,
    background = PomeloDarkColors.Background,
    onBackground = PomeloDarkColors.OnBackground,
    surface = PomeloDarkColors.Surface,
    onSurface = PomeloDarkColors.OnSurface,
    surfaceVariant = PomeloDarkColors.SurfaceVariant,
    onSurfaceVariant = PomeloDarkColors.OnSurfaceVariant,
    outline = PomeloDarkColors.Outline,
    outlineVariant = PomeloDarkColors.OutlineVariant,
    inverseSurface = PomeloDarkColors.InverseSurface,
    inverseOnSurface = PomeloDarkColors.InverseOnSurface,
    inversePrimary = PomeloDarkColors.InversePrimary,
    surfaceDim = PomeloDarkColors.SurfaceDim,
    surfaceBright = PomeloDarkColors.SurfaceBright,
    surfaceContainerLowest = PomeloDarkColors.SurfaceContainerLowest,
    surfaceContainerLow = PomeloDarkColors.SurfaceContainerLow,
    surfaceContainer = PomeloDarkColors.SurfaceContainer,
    surfaceContainerHigh = PomeloDarkColors.SurfaceContainerHigh,
    surfaceContainerHighest = PomeloDarkColors.SurfaceContainerHighest,
)
