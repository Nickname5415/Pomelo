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

#include <QList>
#include <QObject>
#include <QString>
#include <QVariantList>
#include <QVariantMap>

class AuthManager;
class YouTubeProvider;

class BestMixController final : public QObject {
  Q_OBJECT
  Q_PROPERTY(bool busy READ busy NOTIFY changed)
  Q_PROPERTY(bool sorting READ sorting NOTIFY changed)
  Q_PROPERTY(bool downloading READ downloading NOTIFY changed)
  Q_PROPERTY(int downloaded READ downloaded NOTIFY changed)
  Q_PROPERTY(int completed READ completed NOTIFY changed)
  Q_PROPERTY(int total READ total NOTIFY changed)
  Q_PROPERTY(QString error READ error NOTIFY changed)
  Q_PROPERTY(QVariantMap tempos READ tempos NOTIFY changed)

public:
  explicit BestMixController(YouTubeProvider * = nullptr, AuthManager * = nullptr,
                             QObject *parent = nullptr)
      : QObject(parent) {}
  ~BestMixController() override = default;

  bool busy() const { return false; }
  bool sorting() const { return false; }
  bool downloading() const { return false; }
  int downloaded() const { return 0; }
  int completed() const { return 0; }
  int total() const { return 0; }
  QString error() const { return QString(); }
  QVariantMap tempos() const { return {}; }
  QVariantList snapshot() const { return {}; }
  void start(const QVariantList &, const QVariantMap &) {}
  void cancel() {}

signals:
  void changed();
  void orderReady(const QVariantList &snapshot, const QList<int> &order);
};
