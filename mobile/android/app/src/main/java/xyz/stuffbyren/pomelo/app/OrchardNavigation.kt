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

package xyz.stuffbyren.pomelo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import xyz.stuffbyren.pomelo.OrchardGraph
import xyz.stuffbyren.pomelo.model.LocalMaxActive
import xyz.stuffbyren.pomelo.model.CatalogKind
import xyz.stuffbyren.pomelo.model.LoadState
import xyz.stuffbyren.pomelo.model.PlaybackTarget
import xyz.stuffbyren.pomelo.ui.components.PlaylistPickerSheet
import xyz.stuffbyren.pomelo.ui.motion.NavMotion
import xyz.stuffbyren.pomelo.ui.navigation.Routes
import xyz.stuffbyren.pomelo.ui.screens.DetailScreen
import xyz.stuffbyren.pomelo.ui.screens.HomeActions
import xyz.stuffbyren.pomelo.ui.screens.HomeScreen
import xyz.stuffbyren.pomelo.ui.screens.rememberHomeData
import xyz.stuffbyren.pomelo.ui.screens.LibraryScreen
import xyz.stuffbyren.pomelo.ui.screens.SettingsScreen
import xyz.stuffbyren.pomelo.ui.screens.TidalLoginScreen
import xyz.stuffbyren.pomelo.ui.screens.SettingsHomeLayout
import xyz.stuffbyren.pomelo.ui.screens.WelcomeScreen

@Composable
internal fun OrchardNavigation(
    nav: NavHostController,
    viewModel: OrchardViewModel,
    playback: xyz.stuffbyren.pomelo.model.PlaybackSnapshot,
    targets: xyz.stuffbyren.pomelo.model.PlaybackTargetState,
    library: xyz.stuffbyren.pomelo.model.LibrarySnapshot,
    settings: xyz.stuffbyren.pomelo.model.OrchardSettings,
    onOpenSearch: () -> Unit,
) {
    val home by viewModel.home.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val detailRefreshing by viewModel.detailRefreshing.collectAsStateWithLifecycle()
    val detailArtwork by viewModel.detailArtwork.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val artistImages by viewModel.artistImages.collectAsStateWithLifecycle()
    val auth by viewModel.auth.collectAsStateWithLifecycle()
    val discordStatus by viewModel.discordStatus.collectAsStateWithLifecycle()
    val lastfmState by viewModel.lastfmState.collectAsStateWithLifecycle()
    val listenBrainzState by viewModel.listenBrainzState.collectAsStateWithLifecycle()
    val libraryFilter by viewModel.libraryFilter.collectAsStateWithLifecycle()
    val localSnapshot by viewModel.localSnapshot.collectAsStateWithLifecycle()
    val localBusy by viewModel.localLibrary.busy.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val downloadsList = androidx.compose.runtime.remember(downloads) { downloads.values.toList() }
    val downloadedTrackIds by viewModel.downloadedTrackIds.collectAsStateWithLifecycle()
    val downloadingTrackIds by viewModel.downloadingTrackIds.collectAsStateWithLifecycle()
    val totalBytesUsed by viewModel.totalBytesUsed.collectAsStateWithLifecycle()
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsStateWithLifecycle()
    val isClearingCache by viewModel.isClearingCache.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val connectRemoteVolume by viewModel.connectRemoteVolume.collectAsStateWithLifecycle()
    // Connect targets accept every queue command.
    val canControlQueue = true
    val canShuffle = true
    val context = androidx.compose.ui.platform.LocalContext.current
    val startDestination = if (settings.onboardingCompleted) Routes.HOME else Routes.WELCOME
    var playlistPickerTrack by remember { mutableStateOf<xyz.stuffbyren.pomelo.model.Track?>(null) }

    // Without this a track the resolver refuses shows up only as the spinner stopping,
    // which is indistinguishable from the play button having died. Keyed on the message
    // so a repeated attempt on the same track says so again rather than staying silent.
    LaunchedEffect(playback.errorMessage) {
        if (playback.errorMessage.isNotBlank()) {
            android.widget.Toast
                .makeText(context, playback.errorMessage, android.widget.Toast.LENGTH_LONG)
                .show()
        }
    }

    val graph = remember(context) { OrchardGraph.from(context) }
    val isSignedIn = auth is xyz.stuffbyren.pomelo.auth.AuthState.SignedIn
    LaunchedEffect(isSignedIn) {
        if (isSignedIn && !graph.tidalWebSession.hasToken()) {
            nav.navigate(Routes.LOGIN)
        }
    }

    NavHost(
        navController = nav,
        startDestination = startDestination,
        enterTransition = NavMotion.enter,
        exitTransition = NavMotion.exit,
        popEnterTransition = NavMotion.popEnter,
        popExitTransition = NavMotion.popExit,
    ) {
        composable(Routes.WELCOME) {
            WelcomeScreen(
                settings = settings,
                auth = auth,
                onUpdateSettings = viewModel::updateSettings,
                onSignIn = { nav.navigate(Routes.LOGIN) },
                onSignOut = viewModel::signOut,
                onFinish = {
                    viewModel.updateSettings(settings.copy(onboardingCompleted = true))
                    nav.navigate(Routes.HOME) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                rememberHomeData(
                    settings = settings,
                    state = home,
                    library = library,
                    auth = auth,
                    downloads = downloadsList,
                    downloadedTrackIds = downloadedTrackIds,
                    isOffline = !isOnline,
                    actions = HomeActions(
                        refresh = viewModel::refreshHome,
                        search = onOpenSearch,
                        play = { viewModel.play(it, "Home") },
                        openDetail = { id -> viewModel.openDetail(id); nav.navigate(Routes.detail(id)) },
                        editLayout = { nav.navigate(Routes.SETTINGS_HOME_LAYOUT) },
                        toggleLike = viewModel::toggleLiked,
                        playNext = if (canControlQueue) viewModel::playNext else null,
                        addToQueue = if (canControlQueue) viewModel::addToQueue else null,
                        addToPlaylist = { playlistPickerTrack = it },
                        share = viewModel::shareTrack,
                        openProfile = { nav.openTopLevel(Routes.SETTINGS) },
                        fetchSectionItems = viewModel::fetchHomeSectionItems,
                        playItem = viewModel::playItem,
                        playCollection = { id, title -> viewModel.playCollection(id, title) },
                    ),
                )
            )
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(
                library = library,
                filter = libraryFilter,
                onFilterChange = viewModel::selectLibraryFilter,
                downloads = downloadsList,
                downloadedTrackIds = downloadedTrackIds,
                downloadingTrackIds = downloadingTrackIds,
                totalBytesUsed = totalBytesUsed,
                onPlay = { viewModel.play(it, libraryFilter.sourceTitle()) },
                onPlayAll = { tracks, shuffle ->
                    if (shuffle) viewModel.shuffleAll(tracks, libraryFilter.sourceTitle())
                    else viewModel.playAll(tracks, contextTitle = libraryFilter.sourceTitle())
                },
                onPlayNext = if (canControlQueue) viewModel::playNext else null,
                onAddToQueue = if (canControlQueue) viewModel::addToQueue else null,
                onOpenDetail = { id -> viewModel.openDetail(id); nav.navigate(Routes.detail(id)) },
                onDownloadTrack = null,
                onRemoveDownloadTrack = null,
                onShare = viewModel::shareTrack,
                localLibrary = viewModel.localLibrary,
                localSnapshot = localSnapshot,
                localBusy = localBusy,
                remoteAvailable = auth is xyz.stuffbyren.pomelo.auth.AuthState.SignedIn,
                onCreateRemotePlaylist = { title -> viewModel.createPlaylist(title, null) },
            )
        }
        composable(Routes.DOWNLOADS) {
            xyz.stuffbyren.pomelo.ui.screens.DownloadsScreen(
                downloads = downloadsList,
                totalBytesUsed = totalBytesUsed,
                onPlay = { viewModel.play(it, "Downloads") },
                onRemoveDownload = viewModel::removeDownload,
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                settings = settings,
                auth = auth,
                discordStatus = discordStatus,
                lastfmState = lastfmState,
                listenBrainzState = listenBrainzState,
                updateState = updateState,
                cacheSizeBytes = cacheSizeBytes,
                isClearingCache = isClearingCache,
                onClearCache = {
                    viewModel.clearCache { bytesCleared ->
                        val formatted = xyz.stuffbyren.pomelo.settings.CacheManager.formatStorageSize(bytesCleared)
                        android.widget.Toast.makeText(context, "Cache cleared ($formatted freed)", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                downloadedBytes = totalBytesUsed,
                onDeleteAllDownloads = {
                    viewModel.removeAllDownloads()
                    android.widget.Toast.makeText(context, "Downloads deleted", android.widget.Toast.LENGTH_SHORT).show()
                },
                onRefreshCacheSize = viewModel::refreshCacheSize,
                onSettings = viewModel::updateSettings,
                onAutoplayEnabled = viewModel::setAutoplayEnabled,
                onSignIn = { nav.navigate(Routes.LOGIN) },
                onSwitchAccount = { nav.navigate(Routes.ACCOUNT_SWITCH) },
                onSignOut = viewModel::signOut,
                onConnectLastfm = { viewModel.connectLastfm(context) },
                onCompleteLastfm = viewModel::completeLastfmConnection,
                onDisconnectLastfm = viewModel::disconnectLastfm,
                onConnectListenBrainz = viewModel::connectListenBrainz,
                onDisconnectListenBrainz = viewModel::disconnectListenBrainz,
                onConnectSpotify = { nav.navigate(Routes.SPOTIFY_LOGIN) },
                onWelcome = { nav.navigate(Routes.WELCOME) },
                onCheckForUpdates = viewModel::checkForUpdates,
                onInstallUpdate = viewModel::installUpdate,
                onHomeLayout = { nav.navigate(Routes.SETTINGS_HOME_LAYOUT) },
            )
        }
        composable(Routes.SETTINGS_HOME_LAYOUT) {
            SettingsHomeLayout(
                settings = settings,
                auth = auth,
                onSettings = viewModel::updateSettings,
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.LOGIN) {
            val graph = OrchardGraph.from(nav.context)
            TidalLoginScreen(
                auth = graph.auth,
                tidalWebSession = graph.tidalWebSession,
                onDismiss = { nav.popBackStack(Routes.LOGIN, inclusive = true) },
            )
        }
        composable(Routes.ACCOUNT_SWITCH) {
            val graph = OrchardGraph.from(nav.context)
            TidalLoginScreen(
                auth = graph.auth,
                tidalWebSession = graph.tidalWebSession,
                onDismiss = { nav.popBackStack(Routes.ACCOUNT_SWITCH, inclusive = true) },
            )
        }
        composable(Routes.SPOTIFY_LOGIN) {
            xyz.stuffbyren.pomelo.ui.screens.SpotifyLoginScreen(
                onSpdcCaptured = { spdc ->
                    viewModel.updateSettings(settings.copy(spotifySpdc = spdc))
                    nav.popBackStack()
                },
                onCancel = { nav.popBackStack() },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("id") { type = androidx.navigation.NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            // A restored navigation stack may recreate this screen after the
            // process state holder has been lost; reload its actual route id.
            androidx.compose.runtime.LaunchedEffect(id) {
                val current = (detail as? LoadState.Content)?.value?.id
                if (id.isNotBlank() && current != id) viewModel.openDetail(id)
            }
            val isSaved = (detail as? LoadState.Content)?.value?.let { detailVal ->
                when (detailVal.kind) {
                    CatalogKind.ALBUM -> library.savedAlbums.any { it.id == detailVal.id }
                    CatalogKind.PLAYLIST -> library.savedPlaylists.any { it.id == detailVal.id }
                    CatalogKind.ARTIST -> library.savedArtists.any { it.id == detailVal.id }
                    CatalogKind.TRACK -> false
                }
            } ?: false

            // The hero is square, so the wide asset crops far better than the 9:16 one the
            // full player wants; vertical is only a fallback when there is nothing else.
            val animatedArtworkUrl = if (settings.animatedArtwork) {
                detailArtwork?.let { it.videoUrl.ifBlank { it.videoUrlVertical } }.orEmpty()
            } else ""

            // Only artist pages swap in TheAudioDB's photograph; albums keep their cover, which
            // also drives the page palette.
            val portrait = artistImages?.portraitUrl.orEmpty()
            val shownDetail = (detail as? LoadState.Content)
                ?.takeIf { portrait.isNotBlank() && it.value.kind == CatalogKind.ARTIST }
                ?.let { LoadState.Content(it.value.copy(artworkUrl = portrait)) }
                ?: detail

            // A playlist on this phone also edits its songs, cover and name from the detail page.
            val localDetail = (detail as? LoadState.Content)?.value?.takeIf { xyz.stuffbyren.pomelo.local.isLocalPlaylistId(it.id) }
            val localEdit = localDetail?.let { current ->
                xyz.stuffbyren.pomelo.ui.screens.rememberLocalPlaylistEdit(
                    repository = viewModel.localLibrary,
                    playlistId = current.id,
                    hasCustomCover = localSnapshot.playlists.firstOrNull { it.id == current.id }?.coverPath?.isNotBlank() == true,
                    onDeleted = nav::popBackStack,
                )
            }

            DetailScreen(
                state = shownDetail,
                localEdit = localEdit,
                onBack = nav::popBackStack,
                onPlayAll = { tracks, source -> viewModel.playAll(tracks, contextTitle = source) },
                onShuffle = { tracks, source -> viewModel.shuffleAll(tracks, source) },
                shuffleAvailable = canShuffle,
                onPlay = { track, source -> viewModel.play(track, source) },
                onPlayTrack = { tracks, index, source -> viewModel.playAll(tracks, startIndex = index, contextTitle = source) },
                onPlayNext = if (canControlQueue) viewModel::playNext else null,
                onAddToQueue = if (canControlQueue) viewModel::addToQueue else null,
                onAddToPlaylist = { playlistPickerTrack = it },
                onRemoveFromPlaylist = viewModel::removeTrackFromCurrentPlaylist,
                onMovePlaylistTrack = viewModel::moveTrackInCurrentPlaylist,
                onSave = viewModel::saveDetail,
                onOpenDetail = { next -> viewModel.openDetail(next); nav.navigate(Routes.detail(next)) },
                isSaved = isSaved,
                downloadedTrackIds = downloadedTrackIds,
                downloadingTrackIds = downloadingTrackIds,
                onDownloadTrack = null,
                onDownloadTracks = null,
                onRemoveDownloadTrack = null,
                onRemoveDownloadTracks = null,
                animatedArtworkUrl = animatedArtworkUrl,
                artistPortraitUrl = artistImages?.portraitUrl.orEmpty(),
                onShareTrack = viewModel::shareTrack,
                onShareCollection = viewModel::shareCollection,
                onFetchSectionItems = viewModel::fetchSectionItems,
                smartCrossfadeEnabled = settings.smartCrossfade && !LocalMaxActive.current,
                onPlayBestMix = viewModel::playBestMix,
                isRefreshing = detailRefreshing,
                onRefresh = viewModel::refreshDetail,
                playingTrackId = playback.currentTrack?.id,
            )
        }
    }

    playlistPickerTrack?.let { track ->
        PlaylistPickerSheet(
            track = track,
            playlists = viewModel.playlistChoices(track, library.savedPlaylists),
            onDismiss = { playlistPickerTrack = null },
            onSelect = { playlist ->
                playlistPickerTrack = null
                viewModel.addTrackToPlaylist(playlist.id, track)
            },
        )
    }
}
