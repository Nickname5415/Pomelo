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
import QtQuick.Controls
import QtQuick.Layouts

// Discord text templates and display options.
Popup {
    id: root

    property var discord

    parent: Overlay.overlay
    anchors.centerIn: parent
    width: Math.min(560, parent.width - 48)
    padding: 22
    modal: true
    focus: true
    closePolicy: Popup.CloseOnEscape | Popup.CloseOnPressOutside

    Overlay.modal: Rectangle {
        color: "#80000000"
    }

    enter: Transition {
        NumberAnimation { property: "opacity"; from: 0.0; to: 1.0; duration: 160; easing.type: Easing.OutCubic }
    }

    exit: Transition {
        NumberAnimation { property: "opacity"; from: 1.0; to: 0.0; duration: 120; easing.type: Easing.InCubic }
    }

    background: Rectangle {
        color: OrchardAppearance.popupColor
        radius: 14
        border.color: "#35ffffff"
        border.width: 1
    }

    contentItem: ColumnLayout {
        spacing: 18

        RowLayout {
            Layout.fillWidth: true

            Text {
                Layout.fillWidth: true
                text: qsTr("Customize Discord")
                color: "#f2eee7"
                font.family: "Inter"
                font.pixelSize: 17
                font.weight: Font.DemiBold
            }

            SettingsButton {
                text: qsTr("Done")
                highlighted: true
                onClicked: root.close()
            }
        }

        DiscordCustomizeForm {
            id: form
            Layout.fillWidth: true
            discord: root.discord

            activityField.onTextChanged: {
                if (activityField.activeFocus)
                    root.discord.activityText = activityField.text;
            }
            detailsField.onTextChanged: {
                if (detailsField.activeFocus)
                    root.discord.detailsText = detailsField.text;
            }
            stateField.onTextChanged: {
                if (stateField.activeFocus)
                    root.discord.stateText = stateField.text;
            }

            platformSelect.currentIndex: platformValues.indexOf(root.discord.platform)
            platformSelect.onActivated: index => root.discord.platform = platformValues[index]
            activitySelect.currentIndex: activityValues.indexOf(root.discord.activityType)
            activitySelect.onActivated: index => root.discord.activityType = activityValues[index]
            statusSelect.currentIndex: statusDisplayValues.indexOf(root.discord.statusDisplay)
            statusSelect.onActivated: index => root.discord.statusDisplay = statusDisplayValues[index]
        }
    }
}
