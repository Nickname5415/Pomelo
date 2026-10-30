package xyz.stuffbyren.pomelo.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.stuffbyren.pomelo.desktop.catalog.TidalDesktopCatalog
import xyz.stuffbyren.pomelo.desktop.model.PlaybackState
import xyz.stuffbyren.pomelo.desktop.model.Track
import xyz.stuffbyren.pomelo.desktop.ui.PomeloColors
import xyz.stuffbyren.pomelo.desktop.ui.components.DesktopTrackRow

@Composable
fun DesktopSearchScreen(
    catalog: TidalDesktopCatalog,
    playbackState: PlaybackState,
    onPlayTrack: (Track, List<Track>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PomeloColors.Background)
            .padding(32.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = query,
            onValueChange = { newQuery ->
                query = newQuery
                searchJob?.cancel()
                if (newQuery.length >= 2) {
                    searchJob = scope.launch {
                        delay(350)
                        searching = true
                        val (tracks, _, _) = catalog.search(newQuery)
                        results = tracks
                        searching = false
                    }
                } else if (newQuery.isEmpty()) {
                    results = emptyList()
                }
            },
            placeholder = { Text("Search songs, artists, or albums on TIDAL...", color = PomeloColors.TextSecondary) },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = "Search", tint = PomeloColors.TextSecondary)
            },
            trailingIcon = {
                if (searching) {
                    CircularProgressIndicator(
                        color = PomeloColors.Primary,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = PomeloColors.Surface,
                unfocusedContainerColor = PomeloColors.Surface,
                focusedBorderColor = PomeloColors.Primary,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = PomeloColors.TextPrimary,
                unfocusedTextColor = PomeloColors.TextPrimary,
            ),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        )

        Spacer(Modifier.height(28.dp))

        if (results.isNotEmpty()) {
            Text(
                "Search Results (${results.size})",
                color = PomeloColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(results) { index, track ->
                    val isCurrent = playbackState.currentTrack?.id == track.id
                    DesktopTrackRow(
                        index = index,
                        track = track,
                        isCurrent = isCurrent,
                        onPlay = { onPlayTrack(track, results, index) },
                    )
                }
            }
        } else if (query.isNotBlank() && !searching) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No results found on TIDAL for \"$query\"",
                    color = PomeloColors.TextSecondary,
                    fontSize = 15.sp,
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Type to search over 100+ million songs on TIDAL",
                    color = PomeloColors.TextSecondary.copy(alpha = 0.6f),
                    fontSize = 15.sp,
                )
            }
        }
    }
}
