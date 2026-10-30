package xyz.stuffbyren.pomelo.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.stuffbyren.pomelo.desktop.ui.PomeloColors

enum class NavDestination(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    SEARCH("Search", Icons.Rounded.Search),
    SETTINGS("Settings", Icons.Rounded.Settings),
}

@Composable
fun PomeloDesktopNavRail(
    selected: NavDestination,
    onSelect: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(88.dp)
            .background(PomeloColors.Surface)
            .padding(vertical = 20.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // App Mark
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(PomeloColors.Primary),
            contentAlignment = Alignment.Center,
        ) {
            Text("P", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }

        Spacer(Modifier.height(32.dp))

        NavDestination.values().forEach { dest ->
            val isSelected = dest == selected
            val bg = if (isSelected) PomeloColors.SurfaceVariant else Color.Transparent
            val tint = if (isSelected) PomeloColors.Primary else PomeloColors.TextSecondary

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .clickable { onSelect(dest) }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(dest.icon, contentDescription = dest.label, tint = tint, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(4.dp))
                Text(
                    dest.label,
                    fontSize = 11.sp,
                    color = tint,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
