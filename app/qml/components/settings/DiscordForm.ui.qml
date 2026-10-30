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
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Orchard. If not, see <https://www.gnu.org/licenses/>.
 */

import QtQuick
import QtQuick.Layouts

// Tell your friends what you're listening to. They didn't ask, but now they know.
ColumnLayout {
    id: root

    property var discord: ({
        enabled: true,
        showSyncedLyrics: true,
        activityText: "{song}",
        detailsText: "{artist}",
        stateText: "",
        platform: "YouTube Music",
        activityType: "listening",
        statusDisplay: "name",
        projectButtonEnabled: false,
        animatedArtworkEnabled: false,
        connected: true,
        lastError: ""
    })
    property bool accountSignedIn: false
    property alias enableSwitch: enableSwitch
    property alias lyricsSwitch: lyricsSwitch
    property alias projectSwitch: projectSwitch
    property alias animatedSwitch: animatedSwitch
    property alias customizeButton: customizeButton

    spacing: 16

    RowLayout {
        Layout.fillWidth: true
        spacing: 20

        ColumnLayout {
            Layout.fillWidth: true
            spacing: 4

            SettingsFieldLabel {
                text: qsTr("Rich Presence")
            }

            SettingsHint {
                text: !root.discord.enabled ? qsTr("Show your current track in Discord.")
                    : root.discord.connected ? qsTr("Connected to Discord.")
                    : root.discord.lastError ? qsTr("Discord is unavailable: %1").arg(root.discord.lastError)
                    : qsTr("Connecting to Discord…")
                color: root.discord.enabled && root.discord.lastError ? "#e6a197" : "#8d968e"
            }
        }

        SettingsSwitch {
            id: enableSwitch
            Layout.alignment: Qt.AlignVCenter
            padding: 0
            checked: root.discord.enabled
            Accessible.name: qsTr("Enable Discord Rich Presence")
        }
    }

    Rectangle {
        Layout.fillWidth: true
        Layout.preferredHeight: 1
        color: "#12ffffff"
    }

    ColumnLayout {
        Layout.fillWidth: true
        spacing: 22
        enabled: root.discord.enabled
        opacity: enabled ? 1.0 : 0.46

        RowLayout {
            Layout.fillWidth: true
            spacing: 20

            ColumnLayout {
                Layout.fillWidth: true
                spacing: 4

                SettingsFieldLabel {
                    text: qsTr("Show Synced Lyrics")
                }

                SettingsHint {
                    text: qsTr("Show the current lyric line as Discord state when synced lyrics are available.")
                }
            }

            SettingsSwitch {
                id: lyricsSwitch
                Layout.alignment: Qt.AlignVCenter
                padding: 0
                checked: root.discord.showSyncedLyrics
                Accessible.name: qsTr("Show Synced Lyrics")
            }
        }

        RowLayout {
            Layout.fillWidth: true
            spacing: 20

            ColumnLayout {
                Layout.fillWidth: true
                spacing: 4

                SettingsFieldLabel {
                    text: qsTr("Orchard project button")
                }

                SettingsHint {
                    text: qsTr("Add ‘View the Orchard Project’ as the optional second Discord button.")
                }
            }

            SettingsSwitch {
                id: projectSwitch
                Layout.alignment: Qt.AlignVCenter
                padding: 0
                checked: root.discord.projectButtonEnabled
                Accessible.name: qsTr("Show the Orchard project button")
            }
        }

        // Uploads go through the Orchard account worker, so no account means no moving pictures.
        RowLayout {
            Layout.fillWidth: true
            spacing: 20
            enabled: root.accountSignedIn
            opacity: enabled ? 1.0 : 0.46

            ColumnLayout {
                Layout.fillWidth: true
                spacing: 4

                SettingsFieldLabel {
                    text: qsTr("Animated artwork")
                }

                SettingsHint {
                    text: root.accountSignedIn
                        ? qsTr("Convert motion artwork to animated WebP and show it in Discord.")
                        : qsTr("Sign in to your Orchard account in General to show animated artwork in Discord.")
                }
            }

            SettingsSwitch {
                id: animatedSwitch
                Layout.alignment: Qt.AlignVCenter
                padding: 0
                checked: root.accountSignedIn && root.discord.animatedArtworkEnabled
                Accessible.name: qsTr("Show animated artwork in Discord")
            }
        }

        SettingsButton {
            id: customizeButton
            Layout.alignment: Qt.AlignLeft
            text: qsTr("Customize text and display")
        }
    }
}
