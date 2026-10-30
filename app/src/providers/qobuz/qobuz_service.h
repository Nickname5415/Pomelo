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

#include <QJsonObject>
#include <QObject>
#include <QString>
#include <QVariantMap>
#include <functional>

class QobuzService final : public QObject {
  Q_OBJECT
  Q_PROPERTY(QString status READ status NOTIFY changed)
  Q_PROPERTY(bool connected READ connected NOTIFY changed)
  Q_PROPERTY(bool active READ active NOTIFY changed)
  Q_PROPERTY(QString message READ message NOTIFY changed)
  Q_PROPERTY(bool messageIsError READ messageIsError NOTIFY changed)

public:
  using Reply = std::function<void(const QJsonValue &result, const QString &error)>;
  using Bytes = std::function<void(const QByteArray &bytes, const QString &error)>;

  explicit QobuzService(QObject *parent = nullptr, bool restore = true);
  ~QobuzService() override = default;

  QString status() const { return QStringLiteral("disconnected"); }
  bool connected() const { return false; }
  void setEnabled(bool enabled) { Q_UNUSED(enabled); }
  bool active() const { return false; }
  QString message() const { return QString(); }
  bool messageIsError() const { return false; }

  Q_INVOKABLE void connectAccount() {}
  Q_INVOKABLE void connectWithToken(const QString &, const QString &,
                                    const QString & = {}, const QString & = {}) {}
  Q_INVOKABLE void cancelConnection() {}
  Q_INVOKABLE void disconnectAccount() {}

  void resolveTrack(const QVariantMap &, Reply done, bool = false) {
    if (done) done(QJsonValue(), QStringLiteral("Qobuz not available in Pomelo"));
  }
  void readRange(const QString &, qint64, qint64, Bytes done) {
    if (done) done(QByteArray(), QStringLiteral("Qobuz not available in Pomelo"));
  }
  void readChunk(const QString &, qint64, qint64, Bytes done) {
    if (done) done(QByteArray(), QStringLiteral("Qobuz not available in Pomelo"));
  }
  void playbackStarted(const QString &, double) {}
  void playbackEnded(const QString &, double) {}
  void albumQuality(const QVariantMap &, Reply done) {
    if (done) done(QJsonValue(), QString());
  }

signals:
  void changed();
  void accountConnected();
  void accountDisconnected();
};
