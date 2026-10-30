/*
 * Copyright (C) 2026 Stuff by Ren
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

#pragma once

#include <QByteArray>
#include <QObject>
#include <QString>
#include <QUrl>
#include <QVariantMap>

class AdaptiveMixController final : public QObject {
  Q_OBJECT
  Q_PROPERTY(QString mode READ mode WRITE setMode NOTIFY modeChanged)
  Q_PROPERTY(bool available READ available NOTIFY modeChanged)
  Q_PROPERTY(QString status READ status NOTIFY changed)
  Q_PROPERTY(bool ready READ ready NOTIFY changed)
  Q_PROPERTY(QString mixStyle READ mixStyle NOTIFY changed)
  Q_PROPERTY(double mixBpm READ mixBpm NOTIFY changed)

public:
  explicit AdaptiveMixController(QObject *parent = nullptr) : QObject(parent) {}
  ~AdaptiveMixController() override = default;

  QString mode() const { return QStringLiteral("standard"); }
  void setMode(const QString &) { emit modeChanged(); }
  bool available() const { return false; }
  void setAvailable(bool) { emit modeChanged(); }
  bool enabled() const { return false; }
  QString status() const { return QString(); }
  bool ready() const { return false; }
  QString mixStyle() const { return QString(); }
  double mixBpm() const { return 0.0; }
  double outgoingStart() const { return 0.0; }
  double incomingCue() const { return 0.0; }
  double duration() const { return 0.0; }
  const QByteArray &pcm() const {
    static const QByteArray empty;
    return empty;
  }
  bool standardFallback() const { return true; }
  void prepare(const QString &, const QUrl &, const QUrl &, double, double,
               const QVariantMap &, const QVariantMap &, int, bool) {}
  void clear() {}

signals:
  void changed();
  void modeChanged();
};
