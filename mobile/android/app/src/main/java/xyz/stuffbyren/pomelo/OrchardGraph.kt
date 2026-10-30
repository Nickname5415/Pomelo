/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Pomelo.
 *
 * Pomelo is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Pomelo is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Pomelo. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.stuffbyren.pomelo

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import xyz.stuffbyren.pomelo.artwork.ArtistImageRepository
import xyz.stuffbyren.pomelo.artwork.ArtworkRepository
import xyz.stuffbyren.pomelo.auth.TidalAuthRepository
import xyz.stuffbyren.pomelo.catalog.CatalogRepository
import xyz.stuffbyren.pomelo.catalog.PlaylistActions
import xyz.stuffbyren.pomelo.catalog.TidalCatalogRepository
import xyz.stuffbyren.pomelo.catalog.VideoVersionResolver
import xyz.stuffbyren.pomelo.connect.ConnectRepository
import xyz.stuffbyren.pomelo.download.DownloadManager
import xyz.stuffbyren.pomelo.home.HomeFeeds
import xyz.stuffbyren.pomelo.home.TidalHomeSource
import xyz.stuffbyren.pomelo.lastfm.LastfmRepository
import xyz.stuffbyren.pomelo.library.LibraryCache
import xyz.stuffbyren.pomelo.library.LibraryRepository
import xyz.stuffbyren.pomelo.listenbrainz.ListenBrainzRepository
import xyz.stuffbyren.pomelo.lyrics.LyricsRepository
import xyz.stuffbyren.pomelo.model.AudioQuality
import xyz.stuffbyren.pomelo.model.TIDAL_SOURCE
import xyz.stuffbyren.pomelo.settings.SettingsRepository
import xyz.stuffbyren.pomelo.songlinks.SongLinksRepository
import java.util.concurrent.TimeUnit

/**
 * Explicit process graph; presentation code receives repositories, not Android
 * singletons or transport objects. The graph owns only application-lifetime work.
 */
class OrchardGraph(context: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    val auth = TidalAuthRepository(context, applicationScope)
    val tidalWebSession = xyz.stuffbyren.pomelo.auth.TidalWebSession(context, http)
    val tidalCatalog = TidalCatalogRepository(http, auth, tidalWebSession)
    val catalog = CatalogRepository(tidalCatalog)
    internal val catalogRouter = xyz.stuffbyren.pomelo.catalog.CatalogRouter(catalog)
    val homeFeeds = HomeFeeds(listOf(TidalHomeSource(catalog))) { TIDAL_SOURCE }
    val playlistActions = PlaylistActions(tidalCatalog, auth)
    val videoVersions = VideoVersionResolver(catalog)

    val settings = SettingsRepository(context, applicationScope)
    val slopVerdicts by lazy { xyz.stuffbyren.pomelo.playback.slop.SlopVerdicts(context) }
    val networkMonitor = xyz.stuffbyren.pomelo.network.NetworkMonitor(context)
    val spotifyCanvas = xyz.stuffbyren.pomelo.spotify.SpotifyCanvasRepository(context, http, settings)
    val artwork = ArtworkRepository(
        http,
        spotifyCanvas,
        sourceOrder = { settings.settings.value.artworkSourceOrder },
    )
    val downloads = DownloadManager(
        context = context,
        http = http,
        scope = applicationScope,
        artworkResolver = artwork::artwork,
        downloadAnimatedArtworkProvider = { settings.settings.value.downloadAnimatedArtwork },
        useSdCardProvider = { settings.settings.value.downloadToSdCard },
    )
    val artistImages = ArtistImageRepository(http)

    val library = LibraryRepository(LibraryCache(context), catalog, applicationScope)
    val lyrics = LyricsRepository(http) { "" }

    /** Lyrics in English, courtesy of a 20 MB model that has never heard a song in its life. */
    val lyricTranslation = xyz.stuffbyren.pomelo.lyrics.translation.LyricTranslator(
        context, http, settings.settings, applicationScope,
    )
    /** Songs and playlists kept on this phone; no account or network needed. */
    val localLibrary = xyz.stuffbyren.pomelo.local.LocalLibraryRepository(context, applicationScope, notify = { postWarning(it) })
    val songLinks = SongLinksRepository()
    val lastfm = LastfmRepository(context, http, applicationScope)
    val listenBrainz = ListenBrainzRepository(context, http, applicationScope)

    /**
     * Clear hook for the active playback service's stream cache if applicable.
     */
    @Volatile
    var onClearStreamCache: (() -> Unit)? = null

    val transitionMarker = kotlinx.coroutines.flow.MutableStateFlow<xyz.stuffbyren.pomelo.model.TransitionMarker?>(null)
    val warningEvent = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 16)
    val activeBitrate = kotlinx.coroutines.flow.MutableStateFlow(0)
    val activeStreamDetail = kotlinx.coroutines.flow.MutableStateFlow(xyz.stuffbyren.pomelo.model.StreamDetail())

    /** The tier for the current network: cellular on metered connections, otherwise the Wi-Fi tier. */
    fun audioQuality(s: xyz.stuffbyren.pomelo.model.OrchardSettings = settings.settings.value): AudioQuality =
        if (networkMonitor.isMetered.value) s.cellularAudioQuality else s.audioQuality

    val maxActive: kotlinx.coroutines.flow.StateFlow<Boolean> by lazy {
        kotlinx.coroutines.flow.MutableStateFlow(false)
    }

    /** Names where audio comes from; cached streams and bytes from another source must not be reused. */
    fun streamVariant(): String {
        return "tidal-${audioQuality().name.lowercase()}"
    }

    val discordPresence = xyz.stuffbyren.pomelo.discord.DiscordPresenceCoordinator(
        context = context,
        songLinks = songLinks,
        artistImages = artistImages,
        catalog = catalog,
        scope = applicationScope,
    )

    fun postWarning(message: String) {
        warningEvent.tryEmit(message)
    }

    val updates = UpdateManager(context) { settings.settings.value.betaChannelEnabled }

    /** Orchard Connect; declared last because it reads the player, providers and settings above. */
    val connect = ConnectRepository(context, applicationScope, this)

    init {
        applicationScope.launch { auth.restore() }
        updates.checkForUpdates()
    }

    companion object {
        fun from(context: Context): OrchardGraph =
            (context.applicationContext as PomeloApplication).graph
    }
}
