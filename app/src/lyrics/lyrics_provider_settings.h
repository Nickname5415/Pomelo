/*
 * Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

#pragma once

#include <QObject>
#include <QSettings>
#include <QStringList>
#include <QVariantList>

class LyricsProviderSettings final : public QObject {
  Q_OBJECT
  Q_PROPERTY(QVariantList providers READ providers NOTIFY changed)

public:
  explicit LyricsProviderSettings(QObject *parent = nullptr);
  // Only the providers that apply right now; moveProvider indexes into this list.
  QVariantList providers() const;
  QStringList enabledProviders() const;
  // Apple Music's own lyrics stand in for the mirrors of them while Apple Music is connected.
  void setAppleMusicConnected(bool connected);
  Q_INVOKABLE void setEnabled(const QString &id, bool enabled);
  Q_INVOKABLE void moveProvider(int from, int to);
  Q_INVOKABLE QString label(const QString &id) const;

signals:
  void changed();

private:
  bool shown(const QVariant &provider) const;
  void save();
  QSettings m_settings;
  QVariantList m_providers;
  bool m_appleMusicConnected{false};
};
