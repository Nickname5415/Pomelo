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

package xyz.stuffbyren.pomelo.playback.slop

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import xyz.stuffbyren.pomelo.OrchardGraph
import xyz.stuffbyren.pomelo.playback.MediaItemMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@UnstableApi
internal class SlopPlayback(
    context: Context,
    private val graph: OrchardGraph,
    scope: CoroutineScope,
    private val target: () -> Player?,
    private val beforeSkip: () -> Unit,
) {
    private val scanner = SlopScanner(context, graph, scope)
    private var attached: Player? = null
    private var applying = false
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_REPEAT_MODE_CHANGED)) refresh()
        }
    }
    private val policyJob = scope.launch(Dispatchers.Main) {
        combine(graph.slopVerdicts.probabilities, graph.slopVerdicts.labels, graph.settings.settings) { _, _, _ -> Unit }
            .collect { refresh() }
    }

    init { refresh() }

    fun refresh() {
        if (applying) return
        val player = target()
        if (player !== attached) {
            attached?.removeListener(listener)
            attached = player
            player?.addListener(listener)
        }
        if (player == null) { scanner.update(emptyList()); return }
        val action = graph.settings.settings.value.slopAction
        applying = true
        try {
            if (action == SlopAction.SKIP || action == SlopAction.REMOVE) apply(player, action)
            val index = player.currentMediaItemIndex
            val tracks = SlopPolicy.windowIndices(player.mediaItemCount, index)
                .map { MediaItemMapper.toTrack(player.getMediaItemAt(it)) }.distinctBy { it.id }
            scanner.update(if (action == SlopAction.OFF) emptyList() else tracks)
        } finally { applying = false }
    }

    private fun apply(player: Player, action: SlopAction) {
        val known = known(graph)
        val ids = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        val current = player.currentMediaItemIndex
        val currentFlagged = ids.getOrNull(current)?.let(known::flagged) == true
        val nextFlagged = ids.getOrNull(player.nextMediaItemIndex)?.let(known::flagged) == true
        if (currentFlagged || nextFlagged || (action == SlopAction.REMOVE && ids.any(known::flagged))) beforeSkip()
        if (action == SlopAction.REMOVE) {
            for (index in ids.indices.reversed()) {
                if (known.flagged(ids[index])) player.removeMediaItem(index)
            }
        } else if (currentFlagged) {
            val next = SlopPolicy.nextAllowed(ids, current, known)
            if (next == null) player.pause() else player.seekToDefaultPosition(next)
        }
    }

    companion object {
        fun known(graph: OrchardGraph): SlopKnown = SlopKnown(graph.slopVerdicts.labels.value,
            graph.slopVerdicts.probabilities.value, graph.settings.settings.value.slopAudioScan)

        fun allowsTransition(graph: OrchardGraph, id: String): Boolean =
            graph.settings.settings.value.slopAction.let { action ->
                (action != SlopAction.SKIP && action != SlopAction.REMOVE) || !known(graph).flagged(id)
            }
    }

    fun close() {
        attached?.removeListener(listener)
        policyJob.cancel()
        scanner.close()
    }
}
