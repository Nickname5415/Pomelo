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

package xyz.stuffbyren.pomelo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.stuffbyren.pomelo.OrchardGraph
import xyz.stuffbyren.pomelo.playback.slop.SlopAction
import xyz.stuffbyren.pomelo.playback.slop.SlopKnown

@Composable
internal fun SlopBadge(trackId: String) {
    val graph = OrchardGraph.from(LocalContext.current)
    val probabilities by graph.slopVerdicts.probabilities.collectAsStateWithLifecycle()
    val labels by graph.slopVerdicts.labels.collectAsStateWithLifecycle()
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val known = SlopKnown(labels, probabilities, settings.slopAudioScan)
    if (settings.slopAction == SlopAction.OFF || !known.flagged(trackId)) return
    val description = if (known.labelled(trackId)) "Labelled made with AI by YouTube"
    else "Likely AI-generated, ${((probabilities[trackId] ?: 0f) * 100).toInt()} percent confidence"
    Text("AI", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFC58A),
        modifier = Modifier.padding(start = 6.dp)
            .background(Color(0xFFFFC58A).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .semantics { contentDescription = description })
}
