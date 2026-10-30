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

const val SLOP_THRESHOLD = 0.9f

enum class SlopAction { OFF, MARK, SKIP, REMOVE }

internal object SlopPolicy {
    const val AHEAD = 3

    fun windowIndices(size: Int, index: Int): IntRange =
        if (index !in 0 until size) IntRange.EMPTY else index..minOf(size - 1, index + AHEAD)

    fun nextAllowed(ids: List<String>, index: Int, known: SlopKnown): Int? =
        ((index + 1) until ids.size).firstOrNull { !known.flagged(ids[it]) }
}

/** YouTube "Made with AI" labels, plus fakeprint scores when the deprecated audio scan is on. */
data class SlopKnown(
    val labels: Map<String, Boolean> = emptyMap(),
    val probabilities: Map<String, Float> = emptyMap(),
    val audioScan: Boolean = false,
) {
    fun labelled(id: String): Boolean = labels[id] == true

    fun flagged(id: String): Boolean =
        labelled(id) || (audioScan && (probabilities[id] ?: -1f) >= SLOP_THRESHOLD)
}
