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

package xyz.stuffbyren.pomelo.playback.slop

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import xyz.stuffbyren.pomelo.OrchardGraph
import xyz.stuffbyren.pomelo.model.Track
import java.io.File

internal class SlopScanner(
    context: Context,
    private val graph: OrchardGraph,
    scope: CoroutineScope,
    private val analyze: (suspend (String, File) -> Unit)? = null,
) {
    private val window = MutableStateFlow<List<Track>>(emptyList())

    suspend fun scan() {
        // Stream audio analysis is disabled for TIDAL protected streams
    }

    fun update(tracks: List<Track>) {
        window.value = tracks
    }

    fun close() {
        window.value = emptyList()
    }
}

