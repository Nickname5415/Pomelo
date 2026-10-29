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

package xyz.stuffbyren.pomelo

import xyz.stuffbyren.pomelo.BuildConfig

/** Bundled changelog and current release notes for Orchard Mobile. */
object MobileChangelog {
    const val CURRENT_VERSION = BuildConfig.VERSION_NAME
    const val CURRENT_CODENAME = BuildConfig.CODENAME

    val CURRENT_RELEASE_NOTES =
        """
        ### Added
        - **Mobile Data Quality**: Choose a separate audio quality for mobile data, applied automatically when the phone leaves Wi-Fi.
        - **SD Card Downloads**: Store new downloads on the SD card from Storage & updates.

        ### Changed
        - **Adaptive Mix**: Blends match each song's section tempo. Pairs that only work behind a filter and R&B pairs that cannot blend cleanly play out at their natural boundary. Best Mix sees these blends too.
        - **Qobuz Streaming**: Lower startup and seek latency for FLAC streams.
        - **Interface**: The bottom bar no longer shows a Settings tab, the search results panel uses lighter glass, and the Song/Video switch stays clear of the artwork.
        - **Music Video Search**: Searches run as a guest so plays stay out of YouTube search history.

        ### Fixed
        - **Uploaded Songs**: Streams from c.youtube.com hosts now play, in playback, downloads, and Best Mix analysis.
        - **Albums**: Cards that carry a video id open the album page.
        - **Autoplay**: A blank artist is treated as unknown when matching song variants.
        - **Widget**: Cover art renders at device pixel size.
        - **Queue Badge**: The queue badge in the player is no longer clipped.

        ### Maintenance
        - **Licensing**: The adaptive mix engine and its crates are licensed under OOL-1.0.
        - **Testing & Tooling**: Added section tempo, Qobuz stream server, and Autoplay tests.
        """
            .trimIndent()
}
