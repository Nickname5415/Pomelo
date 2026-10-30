package xyz.stuffbyren.pomelo.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import xyz.stuffbyren.pomelo.desktop.auth.TidalDesktopAuth
import xyz.stuffbyren.pomelo.desktop.catalog.TidalDesktopCatalog
import xyz.stuffbyren.pomelo.desktop.playback.TidalDesktopPlayer
import xyz.stuffbyren.pomelo.desktop.ui.PomeloColors
import xyz.stuffbyren.pomelo.desktop.ui.PomeloTheme
import xyz.stuffbyren.pomelo.desktop.ui.components.DesktopNowPlayingBar
import xyz.stuffbyren.pomelo.desktop.ui.components.NavDestination
import xyz.stuffbyren.pomelo.desktop.ui.components.PomeloDesktopNavRail
import xyz.stuffbyren.pomelo.desktop.ui.screens.DesktopHomeScreen
import xyz.stuffbyren.pomelo.desktop.ui.screens.DesktopSearchScreen
import xyz.stuffbyren.pomelo.desktop.ui.screens.DesktopSettingsScreen
import java.util.concurrent.TimeUnit

fun main() = application {
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    val http = remember {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    val auth = remember { TidalDesktopAuth(http, scope) }
    val catalog = remember { TidalDesktopCatalog(http, auth) }
    val player = remember { TidalDesktopPlayer(http, auth, scope) }

    val playbackState by player.state.collectAsState()
    var currentNav by remember { mutableStateOf(NavDestination.HOME) }

    val windowState = rememberWindowState(width = 1120.dp, height = 760.dp)

    Window(
        onCloseRequest = {
            player.release()
            exitApplication()
        },
        title = "Pomelo",
        state = windowState,
    ) {
        PomeloTheme {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PomeloColors.Background)
            ) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    PomeloDesktopNavRail(
                        selected = currentNav,
                        onSelect = { currentNav = it },
                    )

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (currentNav) {
                            NavDestination.HOME -> DesktopHomeScreen(
                                catalog = catalog,
                                playbackState = playbackState,
                                onPlayTrack = { track, list, idx ->
                                    player.playTrack(track, list, idx)
                                },
                            )
                            NavDestination.SEARCH -> DesktopSearchScreen(
                                catalog = catalog,
                                playbackState = playbackState,
                                onPlayTrack = { track, list, idx ->
                                    player.playTrack(track, list, idx)
                                },
                            )
                            NavDestination.SETTINGS -> DesktopSettingsScreen(
                                auth = auth,
                            )
                        }
                    }
                }

                DesktopNowPlayingBar(
                    state = playbackState,
                    onToggle = { player.togglePlayPause() },
                    onNext = { player.next() },
                    onPrevious = { player.previous() },
                )
            }
        }
    }
}
