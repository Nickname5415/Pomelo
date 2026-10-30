package xyz.stuffbyren.pomelo.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.stuffbyren.pomelo.desktop.model.Track
import xyz.stuffbyren.pomelo.desktop.ui.PomeloColors

private fun formatTrackDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return String.format("%d:%02d", minutes, seconds)
}

@Composable
fun DesktopTrackRow(
    index: Int,
    track: Track,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowBg = if (isCurrent) PomeloColors.SurfaceVariant.copy(alpha = 0.7f) else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(rowBg)
            .clickable { onPlay() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Track number or Play icon
        Box(
            modifier = Modifier.width(36.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (isCurrent) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Playing",
                    tint = PomeloColors.Primary,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Text(
                    text = "${index + 1}",
                    color = PomeloColors.TextSecondary,
                    fontSize = 13.sp,
                )
            }
        }

        // Title and explicit badge
        Column(modifier = Modifier.weight(2f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title,
                    color = if (isCurrent) PomeloColors.Primary else PomeloColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (track.explicit) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text("E", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = track.artist,
                color = PomeloColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Album name
        Text(
            text = track.album.ifBlank { "TIDAL" },
            color = PomeloColors.TextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.5f).padding(horizontal = 8.dp),
        )

        // Duration
        Text(
            text = formatTrackDuration(track.durationMs),
            color = PomeloColors.TextSecondary,
            fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
    }
}
