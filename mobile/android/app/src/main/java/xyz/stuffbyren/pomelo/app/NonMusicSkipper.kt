/*
 * Copyright (C) 2026 Pomelo Authors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package xyz.stuffbyren.pomelo.app

import xyz.stuffbyren.pomelo.OrchardGraph
import xyz.stuffbyren.pomelo.model.NonMusicSegment
import xyz.stuffbyren.pomelo.model.PlaybackSnapshot
import xyz.stuffbyren.pomelo.model.PlaybackTargetState
import xyz.stuffbyren.pomelo.playback.LocalPlaybackController
import xyz.stuffbyren.pomelo.social.PartyState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Non-music skipping stub for TIDAL client.
 * Official TIDAL releases provide clean studio audio tracks without video skits/intros.
 */
internal class NonMusicSkipper(
    private val graph: OrchardGraph,
    private val scope: CoroutineScope,
    private val local: LocalPlaybackController,
    private val playback: StateFlow<PlaybackSnapshot>,
    private val targets: StateFlow<PlaybackTargetState>,
    private val party: StateFlow<PartyState>,
) {
    private val _offered = MutableStateFlow<NonMusicSegment?>(null)
    val offered: StateFlow<NonMusicSegment?> = _offered.asStateFlow()

    fun skip() {
        // No-op for TIDAL studio tracks
    }

    fun observe() {
        // No-op for TIDAL studio tracks
    }
}
