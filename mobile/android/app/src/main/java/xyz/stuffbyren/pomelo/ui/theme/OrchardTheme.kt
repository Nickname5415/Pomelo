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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import xyz.stuffbyren.pomelo.R

private val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun pomeloTypography(scheme: ColorScheme) = Typography(
    displayLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.8).sp,
        color = scheme.onBackground,
    ),
    headlineLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp,
        color = scheme.onBackground,
    ),
    headlineMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        color = scheme.onBackground,
    ),
    titleLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = scheme.onBackground,
    ),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = scheme.onBackground,
    ),
    titleSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = scheme.onBackground,
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = scheme.onBackground,
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = scheme.onSurfaceVariant,
    ),
    bodySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        color = scheme.onSurfaceVariant,
    ),
    labelLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp,
    ),
)

/**
 * The accent in force. Defaults to the Pomelo primary role; becomes the wallpaper's colour when the
 * user opts into system colours. Read this rather than [CanopyColors.Accent] so both paths are honoured.
 */
val LocalAccent = staticCompositionLocalOf { PomeloDarkColors.Primary }

@Composable
fun OrchardTheme(useSystemColors: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val baseScheme = remember(darkTheme) {
        if (darkTheme) pomeloDarkColorScheme() else pomeloLightColorScheme()
    }
    val accent = remember(useSystemColors, darkTheme) {
        if (useSystemColors) {
            val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dynamic.primary.legibleOnChrome(darkTheme)
        } else {
            baseScheme.primary
        }
    }
    val colorScheme = remember(baseScheme, accent) {
        baseScheme.copy(primary = accent, secondary = accent)
    }
    val typography = remember(colorScheme) { pomeloTypography(colorScheme) }
    val canopy = remember(colorScheme, accent, darkTheme) {
        canopyPaletteFrom(colorScheme, accent, darkTheme)
    }
    SideEffect {
        CanopyColorsState.palette = canopy
    }

    CompositionLocalProvider(
        LocalAccent provides accent,
        LocalCanopyPalette provides canopy,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = PomeloShapes.material,
            content = content,
        )
    }
}

/**
 * Wallpaper-derived colours are tuned for Material's lighter surfaces and can land too dark or too
 * washed out against Pomelo chrome, so saturation and lightness are held to a range that stays readable.
 */
fun Color.legibleOnDarkChrome(): Color = legibleOnChrome(darkTheme = true)

fun Color.legibleOnChrome(darkTheme: Boolean): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    val fallback = if (darkTheme) PomeloDarkColors.Primary else PomeloLightColors.Primary
    if (hsl[1] < 0.10f) return fallback
    hsl[1] = hsl[1].coerceIn(0.45f, 0.95f)
    hsl[2] = if (darkTheme) {
        hsl[2].coerceIn(0.58f, 0.78f)
    } else {
        hsl[2].coerceIn(0.35f, 0.55f)
    }
    return Color(ColorUtils.HSLToColor(hsl))
}
