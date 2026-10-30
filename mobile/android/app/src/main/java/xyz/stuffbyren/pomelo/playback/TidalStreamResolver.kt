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
import android.util.Log
import com.tidal.sdk.player.common.model.AudioQuality
import com.tidal.sdk.player.common.model.UriSchemeType
import com.tidal.sdk.player.streamingapi.StreamingApi
import com.tidal.sdk.player.streamingapi.playbackinfo.model.PlaybackInfo
import com.tidal.sdk.player.streamingapi.playbackinfo.model.PlaybackMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import xyz.stuffbyren.pomelo.auth.TidalAuthRepository
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "TidalStreamResolver"
private const val TIDAL_API_BASE = "https://openapi.tidal.com/v2"
private const val CACHE_TTL_MS = 30 * 60 * 1000L

data class TidalStreamInfo(
    val url: String,
    val bitrateKbps: Int = 0,
    val bitDepth: Int = 16,
    val sampleRate: Int = 44100,
    val codec: String = "FLAC",
    val hiRes: Boolean = false,
)

/**
 * Resolves TIDAL tracks directly to playable stream URLs (Lossless FLAC, High AAC, or HLS/DASH).
 */
class TidalStreamResolver(
    private val auth: TidalAuthRepository,
    private val http: OkHttpClient,
    private val webSession: xyz.stuffbyren.pomelo.auth.TidalWebSession? = null,
    private val streamingApiProvider: () -> StreamingApi?,
) {
    private val cache = ConcurrentHashMap<String, Pair<String, Long>>()
    private val streamInfoCache = ConcurrentHashMap<String, TidalStreamInfo>()

    fun getStreamInfo(trackId: String): TidalStreamInfo? = streamInfoCache[trackId]

    suspend fun resolve(trackId: String): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = cache[trackId]
        if (cached != null && now < cached.second) {
            Log.d(TAG, "Using cached URL for $trackId")
            return@withContext cached.first
        }

        Log.d(TAG, "Resolving track $trackId (hasStreamingApi=${streamingApiProvider() != null})")
        val resolvedFromApi = resolveViaWebPlayback(trackId)
            ?: resolveViaStreamingApi(trackId)
            ?: resolveViaOpenApi(trackId)

        if (!resolvedFromApi.isNullOrBlank()) {
            cache[trackId] = Pair(resolvedFromApi, now + CACHE_TTL_MS)
            return@withContext resolvedFromApi
        }

        Log.w(TAG, "Failed to resolve stream URL for track $trackId")
        null
    }

    private suspend fun resolveViaWebPlayback(trackId: String): String? {
        val token = webSession?.getAccessToken() ?: return null
        val countryCode = auth.session()?.countryCode ?: "MX"
        val qualities = listOf("HI_RES", "LOSSLESS", "HIGH", "LOW")

        for (quality in qualities) {
            val url = "https://api.tidal.com/v1/tracks/$trackId/playbackinfopostpaywall" +
                "?playbackmode=STREAM&assetpresentation=FULL&audioquality=$quality&countryCode=$countryCode"

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("x-tidal-token", "6BDSRdpK9hqEBTgU")
                .build()

            val result = runCatching {
                http.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    Log.i(TAG, "WebPlayback response for $trackId ($quality): ${response.code} $body")
                    if (!response.isSuccessful || body.isBlank()) return@use null

                    val json = JSONObject(body)
                    val presentation = json.optString("assetPresentation", "")
                    val manifestMimeType = json.optString("manifestMimeType", "")
                    val manifest = json.optString("manifest", "")
                    val extracted = if (manifestMimeType == "application/dash+xml" && manifest.isNotBlank()) {
                        "data:application/dash+xml;base64,$manifest"
                    } else {
                        extractUrl(manifest)
                    }
                    if (!extracted.isNullOrBlank()) {
                        var bandwidthKbps = 0
                        var codec = "FLAC"
                        var xmlSampleRate: Int? = null
                        if (manifestMimeType == "application/dash+xml" && manifest.isNotBlank()) {
                            try {
                                val xml = String(Base64.getDecoder().decode(manifest))
                                val bwMatch = Regex("""bandwidth="(\d+)"""").find(xml)
                                bandwidthKbps = bwMatch?.groupValues?.get(1)?.toIntOrNull()?.div(1000) ?: 0
                                val codecsMatch = Regex("""codecs="([^"]+)"""").find(xml)
                                val codecsStr = codecsMatch?.groupValues?.get(1).orEmpty()
                                codec = if (codecsStr.contains("flac", ignoreCase = true)) "FLAC"
                                    else if (codecsStr.contains("mp4a", ignoreCase = true) || codecsStr.contains("aac", ignoreCase = true)) "AAC"
                                    else "FLAC"
                                val srMatch = Regex("""sampleRate="(\d+)"""").find(xml)
                                xmlSampleRate = srMatch?.groupValues?.get(1)?.toIntOrNull()
                            } catch (_: Exception) {}
                        }
                        val sampleRate = json.optInt("sampleRate", xmlSampleRate ?: 44100)
                        val audioQuality = json.optString("audioQuality", quality)
                        val bitDepth = json.optInt("bitDepth", if (audioQuality.contains("HI_RES", ignoreCase = true)) 24 else 16)
                        val hiRes = audioQuality.contains("HI_RES", ignoreCase = true) || bitDepth > 16 || sampleRate > 48000
                        val effectiveBitrate = if (bandwidthKbps > 0) bandwidthKbps else if (codec == "FLAC") (if (hiRes) 2304 else 850) else 320
                        streamInfoCache[trackId] = TidalStreamInfo(
                            url = extracted,
                            bitrateKbps = effectiveBitrate,
                            bitDepth = bitDepth,
                            sampleRate = sampleRate,
                            codec = codec,
                            hiRes = hiRes,
                        )
                    }
                    Pair(presentation, extracted)
                }
            }.onFailure { Log.w(TAG, "WebPlayback request failed for $trackId ($quality)", it) }.getOrNull()

            if (result != null) {
                if (result.first == "FULL" && !result.second.isNullOrBlank()) {
                    Log.i(TAG, "Found FULL stream for $trackId via WebPlayback at quality $quality")
                    return result.second
                }
                if (!result.second.isNullOrBlank()) {
                    return result.second
                }
            }
        }
        return null
    }

    private suspend fun resolveViaStreamingApi(trackId: String): String? {
        val streamingApi = streamingApiProvider() ?: return null
        val qualities = listOf(AudioQuality.LOSSLESS, AudioQuality.HIGH, AudioQuality.LOW)
        for (quality in qualities) {
            val result = runCatching {
                val playbackInfo = streamingApi.getTrackPlaybackInfo(
                    trackId = trackId,
                    audioQuality = quality,
                    playbackMode = PlaybackMode.STREAM,
                    immersiveAudio = false,
                    streamingSessionId = java.util.UUID.randomUUID().toString(),
                    enableAdaptive = false,
                    uriSchemeType = UriSchemeType.DATA,
                )
                val trackInfo = playbackInfo as? PlaybackInfo.Track ?: return@runCatching null
                Log.i(TAG, "StreamingApi info for $trackId ($quality): presentation=${trackInfo.assetPresentation}, previewReason=${trackInfo.previewReason}, quality=${trackInfo.audioQuality}")
                val url = extractUrl(trackInfo.manifest)
                Pair(trackInfo.assetPresentation, url)
            }.onFailure { Log.w(TAG, "StreamingApi threw while resolving $trackId at $quality", it) }.getOrNull()

            if (result != null) {
                if (result.first == com.tidal.sdk.player.common.model.AssetPresentation.FULL && !result.second.isNullOrBlank()) {
                    Log.i(TAG, "Found FULL stream for $trackId via StreamingApi at quality $quality")
                    val isLossless = quality == AudioQuality.LOSSLESS
                    streamInfoCache[trackId] = TidalStreamInfo(
                        url = result.second!!,
                        bitrateKbps = if (isLossless) 850 else 320,
                        bitDepth = 16,
                        sampleRate = 44100,
                        codec = if (isLossless) "FLAC" else "AAC",
                        hiRes = false,
                    )
                    return result.second
                }
                // If it's the last quality or preview, save it if nothing better
                if (quality == AudioQuality.LOW && !result.second.isNullOrBlank()) {
                    return result.second
                }
            }
        }
        return null
    }

    private suspend fun resolveViaOpenApi(trackId: String): String? {
        val token = auth.getAccessToken() ?: return null
        val session = auth.session()
        val countryCode = session?.countryCode ?: "US"

        val formatCandidates = listOf(
            "FLAC,AACLC,HEAACV1",
            "AACLC,HEAACV1",
            "HEAACV1",
        )

        for (formats in formatCandidates) {
            val uriBuilder = Uri.parse("$TIDAL_API_BASE/trackManifests/$trackId").buildUpon()
                .appendQueryParameter("manifestType", "HLS")
                .appendQueryParameter("formats", formats)
                .appendQueryParameter("uriScheme", "HTTPS")
                .appendQueryParameter("usage", "PLAYBACK")
                .appendQueryParameter("adaptive", "false")
                .appendQueryParameter("countryCode", countryCode)

            val request = Request.Builder()
                .url(uriBuilder.build().toString())
                .header("Accept", "application/vnd.api+json")
                .header("Authorization", "Bearer $token")
                .build()

            val result = runCatching {
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "OpenAPI trackManifests failed for $trackId ($formats): ${response.code} ${response.message}")
                        return@use null
                    }
                    val body = response.body.string()
                    Log.i(TAG, "OpenAPI trackManifests response for $trackId ($formats): $body")
                    if (body.isBlank()) return@use null
                    val json = JSONObject(body)
                    val attributes = json.optJSONObject("data")?.optJSONObject("attributes")
                    val presentation = attributes?.optString("trackPresentation", "")
                    val previewReason = attributes?.optString("previewReason", "")
                    val manifestUri = attributes?.optString("uri", "")
                    val url = extractUrl(manifestUri)
                    Triple(presentation, previewReason, url)
                }
            }.onFailure { Log.w(TAG, "OpenAPI request exception for $trackId ($formats)", it) }.getOrNull()

            if (result != null) {
                if (result.first == "FULL" && !result.third.isNullOrBlank()) {
                    Log.i(TAG, "Found FULL stream for $trackId via OpenAPI with formats=$formats")
                    streamInfoCache[trackId] = TidalStreamInfo(
                        url = result.third!!,
                        bitrateKbps = 320,
                        bitDepth = 16,
                        sampleRate = 44100,
                        codec = "AAC",
                        hiRes = false,
                    )
                    return result.third
                }
                if (formats == formatCandidates.last() && !result.third.isNullOrBlank()) {
                    streamInfoCache[trackId] = TidalStreamInfo(
                        url = result.third!!,
                        bitrateKbps = 320,
                        bitDepth = 16,
                        sampleRate = 44100,
                        codec = "AAC",
                        hiRes = false,
                    )
                    return result.third
                }
            }
        }
        return null
    }

    companion object {
        fun extractUrl(manifestOrUri: String?): String? {
            if (manifestOrUri.isNullOrBlank()) return null
            val trimmed = manifestOrUri.trim()
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                return trimmed
            }
            val base64Payload = if (trimmed.startsWith("data:")) {
                trimmed.substringAfter(",", "")
            } else {
                trimmed
            }
            if (base64Payload.isBlank()) return null

            return runCatching {
                val decoded = String(Base64.getDecoder().decode(base64Payload), Charsets.UTF_8).trim()
                if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
                    return decoded
                }
                if (decoded.startsWith("#EXTM3U")) {
                    for (line in decoded.lineSequence()) {
                        val trimmedLine = line.trim()
                        if (trimmedLine.startsWith("http://") || trimmedLine.startsWith("https://")) {
                            return trimmedLine
                        }
                    }
                }
                if (decoded.startsWith("{")) {
                    val json = JSONObject(decoded)
                    val urls = json.optJSONArray("urls")
                    val firstUrl = urls?.optString(0)?.takeIf { it.isNotBlank() }
                    if (firstUrl != null) return firstUrl
                }
                if (decoded.startsWith("<MPD") || decoded.startsWith("<?xml")) {
                    val baseUrlMatch = Regex("<BaseURL[^>]*>(https?://[^<]+)</BaseURL>").find(decoded)
                    if (baseUrlMatch != null && (baseUrlMatch.groupValues[1].endsWith(".mp4") || baseUrlMatch.groupValues[1].endsWith(".flac"))) {
                        return baseUrlMatch.groupValues[1]
                    }
                    return "data:application/dash+xml;base64,$base64Payload"
                }
                null
            }.getOrNull()
        }
    }
}
