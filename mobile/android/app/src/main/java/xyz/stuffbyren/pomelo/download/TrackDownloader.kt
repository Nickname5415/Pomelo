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

package xyz.stuffbyren.pomelo.download

import android.util.Log
import okhttp3.OkHttpClient
import xyz.stuffbyren.pomelo.model.Track
import java.io.File

/**
 * Downloads are governed by TIDAL Terms of Service and official SDK policies.
 * Direct stream scraping is strictly prohibited.
 */
class TrackDownloader(
    private val http: OkHttpClient,
    private val store: DownloadStore,
) {
    suspend fun download(
        item: DownloadItem,
        progress: ((bytesDownloaded: Long, totalBytes: Long, progress: Float) -> Unit)? = null,
    ): DownloadItem {
        Log.w(TAG, "Direct stream download not supported under TIDAL Developer Terms.")
        return item.copy(
            status = DownloadStatus.FAILED,
            errorMessage = "Direct stream scraping is prohibited by TIDAL Developer Terms."
        )
    }

    private companion object {
        const val TAG = "TrackDownloader"
    }
}
