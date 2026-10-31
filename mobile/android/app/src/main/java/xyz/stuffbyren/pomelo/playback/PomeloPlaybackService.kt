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

package xyz.stuffbyren.pomelo.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.tidal.sdk.eventproducer.EventProducer
import com.tidal.sdk.eventproducer.model.EventsConfig
import com.tidal.sdk.player.common.model.AudioQuality
import com.tidal.sdk.player.common.model.MediaProduct
import com.tidal.sdk.player.common.model.ProductType
import com.tidal.sdk.player.playbackengine.model.Event
import com.tidal.sdk.player.playbackengine.model.PlaybackContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import xyz.stuffbyren.pomelo.OrchardGraph
import xyz.stuffbyren.pomelo.R
import xyz.stuffbyren.pomelo.app.MainActivity
import xyz.stuffbyren.pomelo.model.Track
import xyz.stuffbyren.pomelo.widget.OrchardWidgetUpdater

/**
 * Process-level owner of playback in Pomelo.
 * Streams are resolved and played directly through Media3 ExoPlayer with MediaSession synchronization.
 */
@UnstableApi
class PomeloPlaybackService : MediaLibraryService() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession
    private lateinit var stateStore: PlaybackStateStore
    private lateinit var nowPlaying: NowPlayingPublisher
    private lateinit var playbackVolume: PlaybackVolumeMonitor
    private var tidalPlayer: com.tidal.sdk.player.Player? = null
    private var chromecastStreamServer: ChromecastStreamServer? = null
    private var chromecastPlayback: ChromecastPlayback? = null

    private var unshuffledOrder: List<String> = emptyList()
    @Volatile private var queueSnapshot: Map<String, Track> = emptyMap()
    private val handler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun authoritativePlayer(): Player =
        if (chromecastPlayback?.isActive == true && chromecastPlayback?.player != null) chromecastPlayback!!.player!!
        else player

    private val positionSaver = object : Runnable {
        override fun run() {
            persistPlayback()
            if (::player.isInitialized) {
                val source = authoritativePlayer()
                OrchardWidgetUpdater.onPlayerChanged(this@PomeloPlaybackService, source)
                nowPlaying.updateScrobbling(source)
                if (source.isPlaying) handler.postDelayed(this, POSITION_SAVE_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        stateStore = PlaybackStateStore(this)
        val graph = OrchardGraph.from(this)
        val browseTree = OrchardMediaLibrary(graph)

        val playerEq = EqualizerAudioProcessor()
        playbackVolume = PlaybackVolumeMonitor(this)
        observeAudioSettings(graph, ioScope, playbackVolume.state, listOf(playerEq))

        val toSdkQuality: (xyz.stuffbyren.pomelo.model.AudioQuality) -> AudioQuality = { quality ->
            when (quality) {
                xyz.stuffbyren.pomelo.model.AudioQuality.LOW -> AudioQuality.LOW
                xyz.stuffbyren.pomelo.model.AudioQuality.MEDIUM -> AudioQuality.HIGH
                xyz.stuffbyren.pomelo.model.AudioQuality.HIGH -> AudioQuality.LOSSLESS
                xyz.stuffbyren.pomelo.model.AudioQuality.MAX -> AudioQuality.HI_RES_LOSSLESS
            }
        }

        try {
            val eventProducer = EventProducer.getInstance(
                credentialsProvider = graph.auth.credentialsProvider,
                config = EventsConfig(
                    maxDiskUsageBytes = 10 * 1024 * 1024,
                    blockedConsentCategories = emptySet(),
                    appVersion = "2.0.0",
                ),
                context = this,
                coroutineScope = ioScope,
            )
            tidalPlayer = com.tidal.sdk.player.Player(
                application = application,
                credentialsProvider = graph.auth.credentialsProvider,
                eventSender = eventProducer.eventSender,
            )
            tidalPlayer?.playbackEngine?.apply {
                streamingWifiAudioQuality = toSdkQuality(graph.settings.settings.value.audioQuality)
                streamingCellularAudioQuality = toSdkQuality(graph.settings.settings.value.cellularAudioQuality)
            }
            listenToTidalEvents(graph)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize TIDAL Player SDK", e)
        }

        val tidalResolver = TidalStreamResolver(
            auth = graph.auth,
            http = graph.http,
            webSession = graph.tidalWebSession,
            streamingApiProvider = { tidalPlayer?.streamingApi },
            qualityProvider = { graph.audioQuality() },
        )

        ioScope.launch {
            graph.settings.settings.collect { settings ->
                tidalResolver.clearCache()
                tidalPlayer?.playbackEngine?.apply {
                    streamingWifiAudioQuality = toSdkQuality(settings.audioQuality)
                    streamingCellularAudioQuality = toSdkQuality(settings.cellularAudioQuality)
                }
            }
        }

        nowPlaying = NowPlayingPublisher(graph, handler, ioScope, tidalResolver) {
            if (::player.isInitialized) authoritativePlayer() else null
        }
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(graph.http)
        val defaultDataSourceFactory = DefaultDataSource.Factory(this, okHttpDataSourceFactory)
        val resolvingDataSourceFactory = ResolvingDataSource.Factory(
            defaultDataSourceFactory,
            object : ResolvingDataSource.Resolver {
                override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                    val uri = dataSpec.uri
                    if (MediaItemMapper.isPomeloUri(uri)) {
                        val trackId = MediaItemMapper.sourceId(uri)
                        val streamUrl = kotlinx.coroutines.runBlocking { tidalResolver.resolve(trackId) }
                        if (!streamUrl.isNullOrBlank()) {
                            Log.d(TAG, "Resolved track $trackId to: ${streamUrl.take(70)}...")
                            handler.post { nowPlaying.publishBitrate() }
                            return dataSpec.buildUpon()
                                .setUri(Uri.parse(streamUrl))
                                .build()
                        }
                        throw java.io.IOException("Failed to resolve stream URL for track $trackId")
                    }
                    return dataSpec
                }
            },
        )
        val defaultMediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(resolvingDataSourceFactory)
        player = buildOrchardPlayer(this, defaultMediaSourceFactory, playerEq, playbackVolume.state, handlesAudioFocus = true)

        unshuffledOrder = stateStore.load().also { it.restoreInto(player) }.unshuffledOrder
        snapshotQueue(player)

        val callback = OrchardSessionCallback(
            packageName = packageName,
            browseTree = browseTree,
            stateStore = stateStore,
            browseScope = ioScope,
            target = { authoritativePlayer() },
            onSetVideoMode = { _, _, _ -> },
            onLayoutChanged = ::updateCustomLayout,
        )

        mediaSession = MediaLibrarySession.Builder(this, OrchardSessionPlayer(player), callback)
            .setSessionActivity(mainActivityIntent())
            .setBitmapLoader(
                DataSourceBitmapLoader(
                    DataSourceBitmapLoader.DEFAULT_EXECUTOR_SERVICE.get(),
                    OkHttpDataSource.Factory(graph.http),
                ),
            )
            .build()

        updateCustomLayout()
        handler.post { nowPlaying.hydrateArtwork() }

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notification) },
        )

        player.addListener(playbackListener)

        val streamServer = ChromecastStreamServer(this, graph.http, tidalResolver).also {
            chromecastStreamServer = it
        }
        chromecastPlayback = ChromecastPlayback(
            context = this,
            converter = ChromecastMediaItemConverter(streamServer),
            localPlayer = { player },
            onCastStarted = { castPlayer ->
                castPlayer.addListener(castPlaybackListener)
                mediaSession.setPlayer(OrchardSessionPlayer(castPlayer))
                persistPlayback()
                updateCustomLayout()
                OrchardWidgetUpdater.onPlayerChanged(this, castPlayer)
            },
            onCastEnded = { localPlayer ->
                chromecastPlayback?.player?.removeListener(castPlaybackListener)
                mediaSession.setPlayer(OrchardSessionPlayer(localPlayer))
                persistPlayback()
                updateCustomLayout()
                OrchardWidgetUpdater.onPlayerChanged(this, localPlayer)
            },
            onError = graph::postWarning,
        ).also {
            it.start()
        }

        OrchardWidgetUpdater.onPlayerChanged(this, player)
    }

    private fun listenToTidalEvents(graph: OrchardGraph) {
        val tp = tidalPlayer ?: return
        ioScope.launch {
            tp.playbackEngine.events.collect { event ->
                when (event) {
                    is Event.PlaybackStateChange -> {
                        Log.d(TAG, "TIDAL playback state changed: ${event.playbackState}")
                    }
                    is Event.MediaProductTransition -> {
                        val trackCtx = event.playbackContext as? PlaybackContext.Track
                        Log.i(
                            TAG,
                            "TIDAL transition: product=${event.mediaProduct.productId}, presentation=${event.playbackContext.assetPresentation}, previewReason=${trackCtx?.previewReason}, quality=${trackCtx?.audioQuality}, duration=${event.playbackContext.duration}"
                        )
                    }
                    is Event.MediaProductEnded -> {
                        handler.post {
                            if (player.hasNextMediaItem()) {
                                player.seekToNextMediaItem()
                            }
                        }
                    }
                    is Event.Error -> {
                        Log.w(TAG, "TIDAL playback event error: $event")
                        graph.postWarning("TIDAL playback error: content unavailable or offline")
                    }
                    is Event.StreamingPrivilegesRevoked -> {
                        Log.w(TAG, "TIDAL streaming privileges revoked")
                        graph.postWarning("Playback paused: active on another device")
                        handler.post { player.pause() }
                    }
                    else -> {}
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        if (::player.isInitialized) {
            authoritativePlayer().runWidgetAction(intent)
        }
        return result
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::nowPlaying.isInitialized) nowPlaying.finishHistory()
        chromecastPlayback?.close()
        chromecastPlayback = null
        chromecastStreamServer?.close()
        chromecastStreamServer = null
        if (::player.isInitialized) {
            persistPlayback(sync = true)
            OrchardWidgetUpdater.onPlayerChanged(this, player, forcePaused = true)
            player.release()
        }
        tidalPlayer?.release()
        tidalPlayer = null
        ioScope.cancel()
        if (::playbackVolume.isInitialized) playbackVolume.close()
        if (::mediaSession.isInitialized) mediaSession.release()
        queueSnapshot = emptyMap()
        super.onDestroy()
    }

    private fun persistPlayback(sync: Boolean = false) {
        if (!::player.isInitialized) return
        val current = authoritativePlayer()
        val restored = savedPlayback(current, unshuffledOrder)
        if (sync) stateStore.save(restored) else ioScope.launch { stateStore.save(restored) }
    }

    private fun queueTrack(id: String): Track? {
        queueSnapshot[id]?.let { return it }
        val find = {
            if (!::player.isInitialized) null
            else {
                val current = authoritativePlayer()
                (0 until current.mediaItemCount).asSequence().map(current::getMediaItemAt)
                    .firstOrNull { it.mediaId == id }?.let(MediaItemMapper::toTrack)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) return find()
        return runBlocking { withTimeoutOrNull(QUEUE_LOOKUP_TIMEOUT_MS) { withContext(Dispatchers.Main) { find() } } }
    }

    private fun snapshotQueue(source: Player) {
        val count = source.mediaItemCount
        if (count == 0) {
            queueSnapshot = emptyMap()
            return
        }
        val currentIndex = source.currentMediaItemIndex.coerceIn(0, count - 1)
        val from = (currentIndex - 1).coerceAtLeast(0)
        val to = (currentIndex + SNAPSHOT_AHEAD).coerceAtMost(count - 1)
        queueSnapshot = (from..to).associate { index ->
            val item = source.getMediaItemAt(index)
            item.mediaId to MediaItemMapper.toTrack(item)
        }
    }

    private val castPlaybackListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(*STATE_EVENTS)) {
                persistPlayback()
                updateCustomLayout()
            }
            if (events.contains(Player.EVENT_TRACKS_CHANGED)) nowPlaying.publishBitrate()
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                )
            ) {
                snapshotQueue(player)
                nowPlaying.publishBitrate()
                nowPlaying.hydrateArtwork()
            }
            OrchardWidgetUpdater.onPlayerChanged(this@PomeloPlaybackService, player)
            nowPlaying.updateScrobbling(player)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            savePositionWhilePlaying(isPlaying)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            OrchardGraph.from(this@PomeloPlaybackService).transitionMarker.value = null
            nowPlaying.resetArtwork()
            nowPlaying.hydrateArtwork()
            nowPlaying.publishBitrate()
        }
    }

    private val playbackListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(*STATE_EVENTS)) {
                if (events.contains(Player.EVENT_TIMELINE_CHANGED)) this@PomeloPlaybackService.player.keepQueueOrderUnshuffled()
                persistPlayback()
                if (events.containsAny(
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        Player.EVENT_REPEAT_MODE_CHANGED,
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                    )
                ) updateCustomLayout()
            }
            if (events.contains(Player.EVENT_TRACKS_CHANGED)) nowPlaying.publishBitrate()
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                )
            ) {
                snapshotQueue(player)
                nowPlaying.publishBitrate()
                nowPlaying.hydrateArtwork()
            }
            OrchardWidgetUpdater.onPlayerChanged(this@PomeloPlaybackService, this@PomeloPlaybackService.player)
            nowPlaying.updateScrobbling(player)
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (shuffleModeEnabled) {
                unshuffledOrder = player.queueMediaIds()
                player.shuffleUpcoming()
            } else {
                player.restoreUpcoming(unshuffledOrder)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            savePositionWhilePlaying(isPlaying)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            Log.d(TAG, "onMediaItemTransition: item=${mediaItem?.mediaId}, reason=$reason")
            OrchardGraph.from(this@PomeloPlaybackService).transitionMarker.value = null
            nowPlaying.resetArtwork()
            nowPlaying.hydrateArtwork()
            nowPlaying.publishBitrate()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "onPlayerError: ${error.errorCodeName} - ${error.message}", error)
        }
    }

    private fun savePositionWhilePlaying(isPlaying: Boolean) {
        handler.removeCallbacks(positionSaver)
        if (isPlaying) handler.postDelayed(positionSaver, POSITION_SAVE_INTERVAL_MS) else persistPlayback()
    }

    private fun updateCustomLayout() {
        if (!::mediaSession.isInitialized || !::player.isInitialized) return
        OrchardSessionCallback.applyLayout(mediaSession, authoritativePlayer())
    }

    private fun mainActivityIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val TAG = "PomeloPlayback"
        private const val QUEUE_LOOKUP_TIMEOUT_MS = 2_000L
        private const val SNAPSHOT_AHEAD = 3
        private const val POSITION_SAVE_INTERVAL_MS = 5_000L
        private val STATE_EVENTS = intArrayOf(
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_PLAY_WHEN_READY_CHANGED,
            Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
            Player.EVENT_REPEAT_MODE_CHANGED,
            Player.EVENT_PLAYLIST_METADATA_CHANGED,
        )
    }
}

