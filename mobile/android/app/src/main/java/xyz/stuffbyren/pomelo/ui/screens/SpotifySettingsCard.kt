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

package xyz.stuffbyren.pomelo.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.stuffbyren.pomelo.model.OrchardSettings
import xyz.stuffbyren.pomelo.spotify.SpotifyCanvasRepository
import xyz.stuffbyren.pomelo.ui.theme.CanopyColors
import xyz.stuffbyren.pomelo.ui.theme.LocalAccent

@Composable
fun SpotifySettingsCard(
    settings: OrchardSettings,
    onSettings: (OrchardSettings) -> Unit,
    onConnectSpotify: () -> Unit,
) {
    var showSpdcDialog by remember { mutableStateOf(false) }
    val isConnected = settings.spotifySpdc.isNotBlank()

    val shape = RoundedCornerShape(20.dp)
    Surface(
        color = Color.Transparent,
        shape = shape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            SpotifyToggleRow(
                title = "Spotify Canvas",
                subtitle = "Animated artwork from Spotify",
                checked = settings.spotifyCanvasEnabled,
                onChecked = { onSettings(settings.copy(spotifyCanvasEnabled = it)) },
            )
            Spacer(Modifier.height(8.dp))
            if (isConnected) {
                IntegrationAction("Sign out of Spotify", destructive = true) {
                    onSettings(settings.copy(spotifySpdc = ""))
                }
            } else {
                IntegrationAction("Sign in to Spotify", onClick = onConnectSpotify)
                IntegrationAction("Use a session cookie") { showSpdcDialog = true }
            }
        }
    }

    if (showSpdcDialog) {
        var input by remember { mutableStateOf(settings.spotifySpdc) }
        AlertDialog(
            onDismissRequest = { showSpdcDialog = false },
            title = { Text("Spotify sp_dc Cookie", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    Text(
                        "Paste your sp_dc cookie value from spotify.com browser session:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Paste sp_dc cookie here...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val extracted = SpotifyCanvasRepository.extractSpdc(input)
                    onSettings(settings.copy(spotifySpdc = extracted))
                    showSpdcDialog = false
                }) {
                    Text("Save", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSpdcDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        )
    }
}

@Composable
private fun SpotifyToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingsSwitch(
            checked = checked,
            onCheckedChange = onChecked,
        )
    }
}
