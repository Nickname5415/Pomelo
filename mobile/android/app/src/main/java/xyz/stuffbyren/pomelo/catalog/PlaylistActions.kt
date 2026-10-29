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

package xyz.stuffbyren.pomelo.catalog

import xyz.stuffbyren.pomelo.auth.TidalAuthRepository
import xyz.stuffbyren.pomelo.model.Track

/** A playlist the listener can add a song to, as the picker reports it. */
data class PlaylistTarget(val id: String, val title: String, val containsTrack: Boolean)

/** Authenticated library writes for TIDAL. */
class PlaylistActions(
    private val catalog: TidalCatalogRepository,
    private val auth: TidalAuthRepository,
) {
    suspend fun setLiked(track: Track, liked: Boolean) {
        // Handled via user collections
    }

    suspend fun isLiked(track: Track): Boolean = false

    suspend fun targets(track: Track): List<PlaylistTarget> = emptyList()

    /** Returns the new playlist's id. */
    suspend fun create(title: String, track: Track? = null): String = ""

    suspend fun add(playlistId: String, track: Track) {
    }

    suspend fun remove(playlistId: String, track: Track, setVideoId: String = "") {
    }

    suspend fun move(playlistId: String, fromIndex: Int, toIndex: Int) {
    }

    suspend fun delete(playlistId: String) {
    }

    companion object {
        const val LIKED_MUSIC_BROWSE_ID = "liked-music"

        fun isLikedMusicPlaylist(playlistId: String): Boolean =
            playlistId.trim() in setOf("LM", LIKED_MUSIC_BROWSE_ID)
    }
}
