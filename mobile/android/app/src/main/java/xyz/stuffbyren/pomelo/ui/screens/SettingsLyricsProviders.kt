/*
 * Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package xyz.stuffbyren.pomelo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import xyz.stuffbyren.pomelo.model.OrchardSettings
import xyz.stuffbyren.pomelo.model.normalizeLyricsProviders

@Composable
internal fun LyricsProvidersSection(
    settings: OrchardSettings,
    onSettings: (OrchardSettings) -> Unit,
    labelIndex: Int,
) {
    val providers = normalizeLyricsProviders(settings.lyricsProviders)
    fun move(index: Int, destination: Int) {
        if (destination !in providers.indices) return
        val reordered = providers.toMutableList().apply { add(destination, removeAt(index)) }
        onSettings(settings.copy(lyricsProviders = reordered))
    }

    SectionLabel("Lyrics providers", labelIndex)
    SettingsPanel(index = labelIndex + 1) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            RowSubtitle("Try enabled providers from top to bottom. The first lyrics found are used. You can also choose a source for one song in the lyrics player.")
        }
        providers.forEachIndexed { index, config ->
            PanelDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    RowTitle(config.provider.label)
                    RowSubtitle(if (config.enabled) "Priority ${index + 1}" else "Disabled")
                }
                IconButton(onClick = { move(index, index - 1) }, enabled = index > 0) {
                    Icon(Icons.Rounded.ArrowUpward, "Move ${config.provider.label} up", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { move(index, index + 1) }, enabled = index < providers.lastIndex) {
                    Icon(Icons.Rounded.ArrowDownward, "Move ${config.provider.label} down", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SettingsSwitch(
                    checked = config.enabled,
                    onCheckedChange = { enabled ->
                        onSettings(settings.copy(lyricsProviders = providers.toMutableList().apply {
                            set(index, config.copy(enabled = enabled))
                        }))
                    },
                    modifier = Modifier.semantics { contentDescription = "Enable ${config.provider.label}" },
                )
            }
        }
    }
}
