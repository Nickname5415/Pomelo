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
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.tidal.sdk.eventproducer.EventProducer
import com.tidal.sdk.eventproducer.model.EventsConfig
import com.tidal.sdk.player.common.model.MediaProduct
import com.tidal.sdk.player.common.model.ProductType
import com.tidal.sdk.player.playbackengine.model.Event
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
 * Playback is handled exclusively via official TIDAL SDK and synchronized with Android MediaSession.
 */
@UnstableApi
class PomeloPlaybackService : MediaLibraryService() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession
    private lateinit var stateStore: PlaybackStateStore
    private lateinit var nowPlaying: NowPlayingPublisher
    private lateinit var playbackVolume: PlaybackVolumeMonitor
    private var tidalPlayer: com.tidal.sdk.player.Player? = null

    private var unshuffledOrder: List<String> = emptyList()
    @Volatile private var queueSnapshot: Map<String, Track> = emptyMap()
    private val handler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val positionSaver = object : Runnable {
        override fun run() {
            persistPlayback()
            val source = player
            if (::player.isInitialized) {
                OrchardWidgetUpdater.onPlayerChanged(this@PomeloPlaybackService, source)
                nowPlaying.updateScrobbling(source)
            }
            if (::player.isInitialized && source.isPlaying) handler.postDelayed(this, POSITION_SAVE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        stateStore = PlaybackStateStore(this)
        val graph = OrchardGraph.from(this)
        val browseTree = OrchardMediaLibrary(graph)

        nowPlaying = NowPlayingPublisher(graph, handler, ioScope) {
            if (::player.isInitialized) player else null
        }

        val playerEq = EqualizerAudioProcessor()
        playbackVolume = PlaybackVolumeMonitor(this)
        observeAudioSettings(graph, ioScope, playbackVolume.state, listOf(playerEq))
        val defaultMediaSourceFactory = DefaultMediaSourceFactory(this)
        val pomeloMediaSourceFactory = PomeloMediaSourceFactory(defaultMediaSourceFactory)
        player = buildOrchardPlayer(this, pomeloMediaSourceFactory, playerEq, playbackVolume.state, handlesAudioFocus = false)
        player.volume = 0f

        unshuffledOrder = stateStore.load().also { it.restoreInto(player) }.unshuffledOrder
        snapshotQueue(player)

        val callback = OrchardSessionCallback(
            packageName = packageName,
            browseTree = browseTree,
            stateStore = stateStore,
            browseScope = ioScope,
            target = { player },
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

        // Initialize official TIDAL SDK Player
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
            listenToTidalEvents(graph)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize TIDAL Player SDK", e)
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

    private fun loadTidalTrack(track: Track) {
        val tp = tidalPlayer ?: return
        if (track.isTidal && track.id.isNotBlank()) {
            try {
                tp.playbackEngine.load(MediaProduct(ProductType.TRACK, track.id))
                val nextItem = if (player.hasNextMediaItem()) player.getMediaItemAt(player.nextMediaItemIndex) else null
                if (nextItem != null) {
                    val nextTrack = queueTrack(nextItem.mediaId) ?: MediaItemMapper.toTrack(nextItem)
                    if (nextTrack.isTidal && nextTrack.id.isNotBlank()) {
                        tp.playbackEngine.setNext(MediaProduct(ProductType.TRACK, nextTrack.id))
                    }
                }
                if (player.playWhenReady) {
                    runCatching { tp.playbackEngine.play() }
                } else {
                    runCatching { tp.playbackEngine.pause() }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error loading track into TIDAL player engine", e)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        if (::player.isInitialized) {
            player.runWidgetAction(intent)
        }
        return result
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::nowPlaying.isInitialized) nowPlaying.finishHistory()
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
        val restored = savedPlayback(player, unshuffledOrder)
        if (sync) stateStore.save(restored) else ioScope.launch { stateStore.save(restored) }
    }

    private fun queueTrack(id: String): Track? {
        queueSnapshot[id]?.let { return it }
        val find = {
            if (!::player.isInitialized) null
            else (0 until player.mediaItemCount).asSequence().map(player::getMediaItemAt)
                .firstOrNull { it.mediaId == id }?.let(MediaItemMapper::toTrack)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) return find()
        return runBlocking { withTimeoutOrNull(QUEUE_LOOKUP_TIMEOUT_MS) { withContext(Dispatchers.Main) { find() } } }
    }

    private fun snapshotQueue(source: Player) {
        val from = (source.currentMediaItemIndex - 1).coerceAtLeast(0)
        val to = (source.currentMediaItemIndex + SNAPSHOT_AHEAD).coerceAtMost(source.mediaItemCount - 1)
        queueSnapshot = (from..to).associate { index ->
            val item = source.getMediaItemAt(index)
            item.mediaId to MediaItemMapper.toTrack(item)
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
            if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION)) {
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

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            runCatching {
                if (playWhenReady) {
                    tidalPlayer?.playbackEngine?.play()
                } else {
                    tidalPlayer?.playbackEngine?.pause()
                }
            }.onFailure { Log.w(TAG, "Failed to update TIDAL playback engine play/pause state", it) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            Log.d(TAG, "onMediaItemTransition: item=${mediaItem?.mediaId}, reason=$reason")
            OrchardGraph.from(this@PomeloPlaybackService).transitionMarker.value = null
            nowPlaying.resetArtwork()
            nowPlaying.hydrateArtwork()
            nowPlaying.publishBitrate()

            val track = mediaItem?.let { queueTrack(it.mediaId) ?: MediaItemMapper.toTrack(it) }
            if (track != null) {
                if (track.isTidal) {
                    player.volume = 0f
                    loadTidalTrack(track)
                } else if (track.isLocal) {
                    player.volume = 1f
                }
            }
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                val seekSec = (newPosition.positionMs / 1000f)
                runCatching {
                    tidalPlayer?.playbackEngine?.seek(seekSec)
                }.onFailure { Log.w(TAG, "Failed to seek TIDAL playback engine", it) }
            }
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
        if (!::mediaSession.isInitialized) return
        OrchardSessionCallback.applyLayout(mediaSession, player)
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

@UnstableApi
private class PomeloMediaSourceFactory(
    private val defaultFactory: DefaultMediaSourceFactory,
) : MediaSource.Factory {
    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val uri = mediaItem.localConfiguration?.uri
        if (uri != null && (uri.scheme == "pomelo" || MediaItemMapper.isPomeloUri(uri))) {
            val track = MediaItemMapper.toTrack(mediaItem)
            val durationUs = (track.durationMs.takeIf { it > 0 } ?: 180_000L) * 1000L
            val silenceSource = SilenceMediaSource.Factory()
                .setDurationUs(durationUs)
                .createMediaSource()
            silenceSource.updateMediaItem(mediaItem)
            return silenceSource
        }
        return defaultFactory.createMediaSource(mediaItem)
    }

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory = apply {
        defaultFactory.setDrmSessionManagerProvider(provider)
    }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory = apply {
        defaultFactory.setLoadErrorHandlingPolicy(policy)
    }

    override fun getSupportedTypes(): IntArray = defaultFactory.supportedTypes
}

