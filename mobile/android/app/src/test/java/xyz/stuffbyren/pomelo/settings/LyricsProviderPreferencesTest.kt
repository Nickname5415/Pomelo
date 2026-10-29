/*
 * Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package xyz.stuffbyren.pomelo.settings

import xyz.stuffbyren.pomelo.model.LyricsProvider
import xyz.stuffbyren.pomelo.model.LyricsProviderConfig
import org.junit.Assert.*
import org.junit.Test

class LyricsProviderPreferencesTest {
    @Test
    fun defaultsAndCorruptPreferencesOfferEveryProvider() {
        for (value in listOf(null, "", "broken")) {
            val decoded = decodeLyricsProviders(value)
            assertEquals(LyricsProvider.entries, decoded.map { it.provider })
            assertTrue(decoded.all { it.enabled })
        }
    }

    @Test
    fun orderAndDisabledProvidersSurviveRoundTrip() {
        val providers = LyricsProvider.entries.reversed().map { LyricsProviderConfig(it, it == LyricsProvider.BETTER_LYRICS) }
        assertEquals(providers, decodeLyricsProviders(encodeLyricsProviders(providers)))
        val disabled = LyricsProvider.entries.map { LyricsProviderConfig(it, false) }
        assertEquals(disabled, decodeLyricsProviders(encodeLyricsProviders(disabled)))
    }

    @Test
    fun unknownAndDuplicateEntriesDoNotHideAvailableProviders() {
        val decoded = decodeLyricsProviders("""[
            {"provider":"unknown"}, {"provider":"betterlyrics","enabled":false},
            {"provider":"betterlyrics","enabled":true}
        ]""")
        assertEquals(LyricsProviderConfig(LyricsProvider.BETTER_LYRICS, false), decoded.first())
        assertEquals(LyricsProvider.entries.size, decoded.size)
        assertEquals(LyricsProvider.entries.toSet(), decoded.map { it.provider }.toSet())
    }
}
