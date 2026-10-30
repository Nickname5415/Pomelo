package xyz.stuffbyren.pomelo.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object PomeloColors {
    val Background = Color(0xFF0E0E10)
    val Surface = Color(0xFF18181C)
    val SurfaceVariant = Color(0xFF24242A)
    val Primary = Color(0xFFFF5E36)
    val Secondary = Color(0xFFFF8A65)
    val TextPrimary = Color(0xFFF0F0F2)
    val TextSecondary = Color(0xFFA0A0A8)
    val Accent = Color(0xFFFF6D3B)
    val TidalCyan = Color(0xFF00FFFF)
}

val PomeloDarkColorScheme = darkColorScheme(
    primary = PomeloColors.Primary,
    secondary = PomeloColors.Secondary,
    background = PomeloColors.Background,
    surface = PomeloColors.Surface,
    surfaceVariant = PomeloColors.SurfaceVariant,
    onPrimary = Color.White,
    onBackground = PomeloColors.TextPrimary,
    onSurface = PomeloColors.TextPrimary,
    onSurfaceVariant = PomeloColors.TextSecondary,
)

@Composable
fun PomeloTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PomeloDarkColorScheme,
        content = content,
    )
}
