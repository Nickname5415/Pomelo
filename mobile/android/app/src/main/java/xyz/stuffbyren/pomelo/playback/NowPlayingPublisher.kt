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

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.Request
import xyz.stuffbyren.pomelo.OrchardGraph
import xyz.stuffbyren.pomelo.artwork.highResolutionArtworkUrl
import xyz.stuffbyren.pomelo.model.PlaybackSnapshot
import xyz.stuffbyren.pomelo.model.StreamDetail

/**
 * What the rest of the phone learns about the playing song: the bitrate readout, artwork
 * embedded for watches, and scrobbles. Main thread unless noted.
 */
@UnstableApi
internal class NowPlayingPublisher(
    private val graph: OrchardGraph,
    private val handler: Handler,
    private val scope: CoroutineScope,
    /** The session's local player, or null before it exists. */
    private val player: () -> ExoPlayer?,
) {
    private var artworkHydrationId = ""

    /**
     * Publishes the bitrate of what is actually playing, for the readout under the scrubber.
     */
    fun publishBitrate() {
        val player = player() ?: return
        val item = player.currentMediaItem
        val uri = item?.let { it.requestMetadata.mediaUri ?: it.localConfiguration?.uri }
        if (item == null || MediaItemMapper.isVideoUri(uri)) {
            publish(0)
            return
        }
        val format = player.audioFormat
        val playerCodec = codecName(format?.sampleMimeType)
        val localTrack = MediaItemMapper.toTrack(item).takeIf { it.isLocal }
        if (localTrack != null) {
            val decoded = listOfNotNull(format?.averageBitrate, format?.bitrate).firstOrNull { it > 0 }?.div(1000) ?: 0
            val codec = playerCodec.ifBlank { localTrack.codec.uppercase() }
            publish(if (localTrack.localBitrateKbps > 0) localTrack.localBitrateKbps else decoded, StreamDetail(codec = codec))
            return
        }
        val decoded = listOfNotNull(format?.averageBitrate, format?.bitrate).firstOrNull { it > 0 }?.div(1000) ?: 0
        publish(decoded, StreamDetail(codec = playerCodec.ifBlank { "FLAC" }))
    }

    private fun codecName(mime: String?): String {
        val m = mime.orEmpty().lowercase()
        return when {
            "flac" in m -> "FLAC"
            "opus" in m -> "Opus"
            "mp4a" in m || "aac" in m -> "AAC"
            "vorbis" in m -> "Vorbis"
            "mpeg" in m || "mp3" in m -> "MP3"
            else -> ""
        }
    }

    private fun publish(kbps: Int, detail: StreamDetail = StreamDetail()) {
        graph.activeBitrate.value = kbps
        graph.activeStreamDetail.value = detail
    }

    fun onResolved(stableUri: String, stream: Any?) {
        handler.post {
            val item = player()?.currentMediaItem ?: return@post
            val currentUri = item.requestMetadata.mediaUri ?: item.localConfiguration?.uri
            if (currentUri?.toString() != stableUri) return@post
            publishBitrate()
        }
    }

    /** Forces the next [hydrateArtwork] to fetch again, for a new current item. */
    fun resetArtwork() {
        artworkHydrationId = ""
    }

    /**
     * WearOS media controls do not reliably dereference remote artworkUri values. Embed the
     * compressed cover in the session metadata as well, which lets the watch render it directly.
     */
    fun hydrateArtwork() {
        val item = player()?.currentMediaItem ?: return
        val currentArtwork = item.mediaMetadata.artworkUri?.toString().orEmpty()
        val url = highResolutionArtworkUrl(currentArtwork).takeIf { it.isNotBlank() } ?: return
        if (url == artworkHydrationId) return
        artworkHydrationId = url
        val targetMediaId = item.mediaId
        scope.launch {
            val bytes = runCatching {
                graph.http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (response.isSuccessful) response.body?.bytes() else null
                }
            }.getOrNull() ?: return@launch
            handler.post {
                val player = player() ?: return@post
                val current = player.currentMediaItem ?: return@post
                if (current.mediaId != targetMediaId) return@post
                val withArtwork = current.buildUpon()
                    .setMediaMetadata(
                        current.mediaMetadata.buildUpon()
                            .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                            .build(),
                    )
                    .build()
                player.replaceMediaItem(player.currentMediaItemIndex, withArtwork)
            }
        }
    }

    /** Scrobbles to external services. */
    fun updateScrobbling(source: Player) {
        val item = source.currentMediaItem ?: return
        val track = MediaItemMapper.toTrack(item)
        val duration = source.duration.takeIf { it != C.TIME_UNSET && it > 0 }
            ?: track.durationMs.takeIf { it > 0 }
            ?: 0
        val snapshot = PlaybackSnapshot(
            currentTrack = track,
            positionMs = source.currentPosition.coerceAtLeast(0),
            durationMs = duration,
            isPlaying = source.isPlaying,
        )
        graph.lastfm.updatePlayback(snapshot)
        graph.listenBrainz.updatePlayback(snapshot)
    }

    fun finishHistory() {}
}
