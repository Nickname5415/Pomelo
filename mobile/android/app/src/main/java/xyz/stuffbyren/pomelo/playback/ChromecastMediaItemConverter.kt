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

import android.net.Uri
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.common.images.WebImage
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Converts Pomelo queue items to LAN URLs and metadata understood by Google Cast receivers.
 */
class ChromecastMediaItemConverter(private val streamServer: ChromecastStreamServer) :
    MediaItemConverter {
    private val delegate = DefaultMediaItemConverter()
    private val originals = ConcurrentHashMap<String, MediaItem>()

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        originals[mediaItem.mediaId] = mediaItem
        val castUrl = streamServer.urlFor(mediaItem.mediaId)
        val track = MediaItemMapper.toTrack(mediaItem)
        val isLocal = track.isLocal || mediaItem.localConfiguration?.uri?.scheme == "file" || mediaItem.localConfiguration?.uri?.scheme == "content"
        val mimeType = if (isLocal) {
            when {
                track.codec.equals("aac", ignoreCase = true) || track.codec.equals("mp4", ignoreCase = true) -> "audio/mp4"
                track.codec.equals("mp3", ignoreCase = true) -> "audio/mpeg"
                track.codec.equals("ogg", ignoreCase = true) || track.codec.equals("opus", ignoreCase = true) -> "audio/ogg"
                track.codec.equals("wav", ignoreCase = true) -> "audio/wav"
                else -> "audio/flac"
            }
        } else {
            "application/dash+xml"
        }
        android.util.Log.i(TAG, "toMediaQueueItem: mediaId=${mediaItem.mediaId}, title=${track.title}, mimeType=$mimeType, castUrl=$castUrl")

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, track.title.ifBlank { "Unknown Title" })
            putString(MediaMetadata.KEY_ARTIST, track.artist.ifBlank { "Pomelo" })
            if (track.album.isNotBlank()) putString(MediaMetadata.KEY_ALBUM_TITLE, track.album)
            putString(MediaMetadata.KEY_ALBUM_ARTIST, track.artist.ifBlank { "Pomelo" })
            if (track.artworkUrl.isNotBlank()) {
                runCatching { addImage(WebImage(Uri.parse(track.artworkUrl))) }
            }
        }

        val customData = JSONObject().apply {
            if (track.animatedArtworkUrl.isNotBlank()) {
                put("animatedArtworkUrl", track.animatedArtworkUrl)
            }
            if (track.animatedArtworkVerticalUrl.isNotBlank()) {
                put("animatedArtworkVerticalUrl", track.animatedArtworkVerticalUrl)
            }
            if (track.title.isNotBlank()) put("title", track.title)
            if (track.artist.isNotBlank()) put("artist", track.artist)
            if (track.album.isNotBlank()) put("album", track.album)
            if (track.artworkUrl.isNotBlank()) put("artworkUrl", track.artworkUrl)
        }

        val mediaInfo = MediaInfo.Builder(castUrl)
            .setContentUrl(castUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(mimeType)
            .setMetadata(metadata)
            .setStreamDuration(track.durationMs.coerceAtLeast(0))
            .setCustomData(customData)
            .build()

        return MediaQueueItem.Builder(mediaInfo)
            .setAutoplay(true)
            .setPreloadTime(10.0)
            .setCustomData(customData)
            .build()
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        mediaQueueItem.media?.contentId?.let { originals[it] }?.let { return it }
        mediaQueueItem.media?.contentUrl?.let { url ->
            originals.values.firstOrNull { it.mediaId in url }?.let { return it }
        }
        val converted = delegate.toMediaItem(mediaQueueItem)
        return originals[converted.mediaId] ?: converted
    }

    /** The Cast timeline can contain metadata-only placeholders for queued items. */
    fun originalFor(mediaId: String): MediaItem? = originals[mediaId]

    private companion object {
        const val TAG = "PomeloCastConverter"
    }
}
