package xyz.stuffbyren.pomelo.desktop.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import xyz.stuffbyren.pomelo.desktop.auth.TidalDesktopAuth
import xyz.stuffbyren.pomelo.desktop.model.PlaybackState
import xyz.stuffbyren.pomelo.desktop.model.Track
import java.io.OutputStreamWriter
import java.util.Base64

class TidalDesktopPlayer(
    private val http: OkHttpClient,
    private val auth: TidalDesktopAuth,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var playerProcess: Process? = null
    private var processWriter: OutputStreamWriter? = null
    private var progressJob: Job? = null
    private var trackStartTimestamp: Long = 0L

    suspend fun resolveStreamUrl(trackId: String): String? = withContext(Dispatchers.IO) {
        val token = auth.accessToken
        val authHeader = if (!token.isNullOrBlank()) "Bearer $token" else null

        val url = "https://api.tidal.com/v1/tracks/$trackId/playbackinfopostpaywall?audioquality=LOSSLESS&playbackmode=STREAM&assetpresentation=FULL"
        val builder = Request.Builder().url(url)
        if (authHeader != null) {
            builder.header("Authorization", authHeader)
        }
        builder.header("x-tidal-token", "ZL6uNG7Udwi7M8C0")

        runCatching {
            http.newCall(builder.build()).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                val root = JSONObject(res.body.string())
                val manifestMime = root.optString("manifestMimeType")
                val rawManifest = root.optString("manifest")

                if (rawManifest.isNotBlank()) {
                    val decoded = runCatching {
                        String(Base64.getDecoder().decode(rawManifest))
                    }.getOrDefault(rawManifest)

                    val manifestJson = runCatching { JSONObject(decoded) }.getOrNull()
                    val urls = manifestJson?.optJSONArray("urls")
                    if (urls != null && urls.length() > 0) {
                        return@withContext urls.getString(0)
                    }
                }
                null
            }
        }.getOrNull()
    }

    fun playTrack(track: Track, queue: List<Track> = listOf(track), index: Int = 0) {
        scope.launch {
            stopCurrentProcess()
            _state.value = _state.value.copy(
                currentTrack = track,
                queue = queue,
                queueIndex = index,
                isPlaying = false,
                positionMs = 0L,
                durationMs = track.durationMs,
            )

            val streamUrl = resolveStreamUrl(track.id)
            if (streamUrl.isNullOrBlank()) {
                println("Could not resolve stream URL for track ${track.title}")
                return@launch
            }

            startPlayerProcess(streamUrl, track)
        }
    }

    private fun startPlayerProcess(url: String, track: Track) {
        scope.launch(Dispatchers.IO) {
            val cmd = when {
                FileCheck.hasGstPlay -> listOf("gst-play-1.0", url)
                FileCheck.hasFfplay -> listOf("ffplay", "-nodisp", "-autoexit", "-loglevel", "quiet", url)
                else -> listOf("gst-launch-1.0", "playbin", "uri=$url")
            }

            runCatching {
                val proc = ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start()

                playerProcess = proc
                processWriter = OutputStreamWriter(proc.outputStream)
                trackStartTimestamp = System.currentTimeMillis()

                _state.value = _state.value.copy(isPlaying = true)
                startProgressTracker(track.durationMs)

                proc.waitFor()
                if (_state.value.currentTrack?.id == track.id) {
                    onTrackFinished()
                }
            }.onFailure {
                it.printStackTrace()
            }
        }
    }

    private fun startProgressTracker(durationMs: Long) {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _state.value.isPlaying) {
                delay(500)
                val elapsed = System.currentTimeMillis() - trackStartTimestamp
                _state.value = _state.value.copy(
                    positionMs = elapsed.coerceAtMost(durationMs)
                )
            }
        }
    }

    fun togglePlayPause() {
        val s = _state.value
        val track = s.currentTrack ?: return
        if (s.isPlaying) {
            pause()
        } else {
            resume()
        }
    }

    fun pause() {
        processWriter?.let {
            runCatching {
                it.write(" ")
                it.flush()
            }
        }
        _state.value = _state.value.copy(isPlaying = false)
        progressJob?.cancel()
    }

    fun resume() {
        processWriter?.let {
            runCatching {
                it.write(" ")
                it.flush()
            }
        }
        _state.value = _state.value.copy(isPlaying = true)
        val currentPos = _state.value.positionMs
        trackStartTimestamp = System.currentTimeMillis() - currentPos
        startProgressTracker(_state.value.durationMs)
    }

    fun next() {
        val s = _state.value
        if (s.queue.isNotEmpty() && s.queueIndex + 1 < s.queue.size) {
            val nextIndex = s.queueIndex + 1
            playTrack(s.queue[nextIndex], s.queue, nextIndex)
        }
    }

    fun previous() {
        val s = _state.value
        if (s.queue.isNotEmpty() && s.queueIndex > 0) {
            val prevIndex = s.queueIndex - 1
            playTrack(s.queue[prevIndex], s.queue, prevIndex)
        }
    }

    private fun onTrackFinished() {
        _state.value = _state.value.copy(isPlaying = false, positionMs = 0L)
        next()
    }

    private fun stopCurrentProcess() {
        progressJob?.cancel()
        playerProcess?.destroyForcibly()
        playerProcess = null
        processWriter = null
    }

    fun release() {
        stopCurrentProcess()
    }
}

private object FileCheck {
    val hasGstPlay: Boolean by lazy { java.io.File("/usr/bin/gst-play-1.0").exists() }
    val hasFfplay: Boolean by lazy { java.io.File("/usr/bin/ffplay").exists() }
}
