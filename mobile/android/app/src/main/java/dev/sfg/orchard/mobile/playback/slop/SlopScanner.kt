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

package dev.sfg.orchard.mobile.playback.slop

import android.content.Context
import android.os.Process
import android.util.Log
import dev.sfg.orchard.mobile.OrchardGraph
import dev.sfg.orchard.mobile.auth.AuthState
import dev.sfg.orchard.mobile.local.isLocalTrackId
import dev.sfg.orchard.mobile.model.Track
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject

internal class SlopScanner(context: Context, private val graph: OrchardGraph, scope: CoroutineScope) {
    private val window = MutableStateFlow<List<Track>>(emptyList())
    private val directory = File(context.cacheDir, "slop-scan")
    private val model by lazy { context.assets.open("slop/fakeprint_lr.f32").use { it.readBytes() } }
    private val downloads = SlopDownload(graph.http.newBuilder().callTimeout(30, TimeUnit.SECONDS).build())
    private val failed = mutableSetOf<String>()
    private val labelFailed = mutableSetOf<String>()
    private data class Request(
        val tracks: List<Track>, val action: SlopAction, val audioScan: Boolean, val online: Boolean, val signedIn: Boolean,
    )
    private val job = scope.launch(Dispatchers.IO) {
        directory.deleteRecursively()
        graph.slopVerdicts.load()
        combine(window, graph.settings.settings.map { it.slopAction to it.slopAudioScan }.distinctUntilChanged(),
            graph.networkMonitor.isOnline, graph.auth.state) { tracks, (action, audioScan), online, auth ->
            Request(tracks, action, audioScan, online, auth is AuthState.SignedIn)
        }.distinctUntilChanged().collectLatest { (tracks, action, audioScan, online, signedIn) ->
            if (action == SlopAction.OFF) return@collectLatest
            if (online) checkLabels(tracks)
            if (!audioScan || !SlopNative.available) return@collectLatest
            for (track in tracks) {
                currentCoroutineContext().ensureActive()
                if (isLocalTrackId(track.id) || track.id.isBlank() || graph.slopVerdicts.labels.value[track.id] == true ||
                    graph.slopVerdicts.probabilities.value.containsKey(track.id) || track.id in failed) continue
                val offline = graph.downloads.getDownloadedFile(track.id)?.takeIf { it.isFile }
                if (offline == null && (!online || !signedIn)) continue
                val file = offline ?: File(directory, "scan.audio")
                try {
                    if (offline == null) downloads.save(graph.streams.resolveForSlop(track), file)
                    currentCoroutineContext().ensureActive()
                    analyze(track.id, file)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failed += track.id
                    Log.d("OrchardSlop", "Scan failed for ${track.id}", error)
                } finally {
                    if (offline == null) file.delete()
                }
            }
        }
    }

    fun update(tracks: List<Track>) { window.value = tracks }

    private suspend fun checkLabels(tracks: List<Track>) {
        for (track in tracks) {
            currentCoroutineContext().ensureActive()
            if (isLocalTrackId(track.id) || track.id.isBlank() ||
                graph.slopVerdicts.labels.value.containsKey(track.id) || track.id in labelFailed) continue
            try {
                val reply = graph.youtube.invoke("catalog.aiLabel", JSONObject().put("videoId", track.id))
                graph.slopVerdicts.recordLabel(track.id, reply.optBoolean("ai"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                labelFailed += track.id
                Log.d("OrchardSlop", "Label check failed for ${track.id}", error)
            }
        }
    }
    fun close() { job.cancel() }

    private suspend fun analyze(id: String, file: File) {
        // Audio scan disabled without PolyForm PcmDecoder
    }
}
