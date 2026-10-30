/*
 * Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package xyz.stuffbyren.pomelo.model

enum class LyricsProvider(val key: String, val label: String) {
    BINI("bini", "Bini Lyrics"),
    BETTER_LYRICS("betterlyrics", "BetterLyrics"),
    LYRICS_PLUS("lyricsplus", "LyricsPlus"),
    LRCLIB("lrclib", "LRCLIB"),
    TIDAL("tidal", "TIDAL"),
}

data class LyricsProviderConfig(val provider: LyricsProvider, val enabled: Boolean = true)

fun normalizeLyricsProviders(values: List<LyricsProviderConfig>): List<LyricsProviderConfig> {
    val saved = values.distinctBy { it.provider }
    val missing = LyricsProvider.entries.filterNot { provider -> saved.any { it.provider == provider } }
    return saved + missing.map(::LyricsProviderConfig)
}
