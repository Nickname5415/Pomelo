package xyz.stuffbyren.pomelo.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import xyz.stuffbyren.pomelo.desktop.catalog.TidalDesktopCatalog
import xyz.stuffbyren.pomelo.desktop.model.PlaybackState
import xyz.stuffbyren.pomelo.desktop.model.Track
import xyz.stuffbyren.pomelo.desktop.ui.PomeloColors
import xyz.stuffbyren.pomelo.desktop.ui.components.DesktopTrackRow

@Composable
fun DesktopHomeScreen(
    catalog: TidalDesktopCatalog,
    playbackState: PlaybackState,
    onPlayTrack: (Track, List<Track>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            loading = true
            tracks = catalog.getHomeTopTracks()
            loading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PomeloColors.Background)
            .padding(32.dp)
    ) {
        // Hero Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PomeloColors.SurfaceVariant)
                .padding(28.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = PomeloColors.Primary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            "TIDAL EXCLUSIVE",
                            color = PomeloColors.Primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Lossless HiFi Audio",
                        color = PomeloColors.TextSecondary,
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Welcome to Pomelo Desktop",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PomeloColors.TextPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "High-fidelity TIDAL streaming natively running on your laptop.",
                    color = PomeloColors.TextSecondary,
                    fontSize = 14.sp,
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Popular on TIDAL",
                color = PomeloColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            if (loading) {
                CircularProgressIndicator(
                    color = PomeloColors.Primary,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Tracks List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(tracks) { index, track ->
                val isCurrent = playbackState.currentTrack?.id == track.id
                DesktopTrackRow(
                    index = index,
                    track = track,
                    isCurrent = isCurrent,
                    onPlay = { onPlayTrack(track, tracks, index) },
                )
            }
        }
    }
}
