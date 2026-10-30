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
#include <QJsonObject>
#include <QObject>
#include <QString>
#include <functional>

class ConnectMixHost final : public QObject {
  Q_OBJECT

public:
  using Respond = std::function<void(const QString &id, bool ok, const QJsonObject &result, const QString &error)>;
  using SendStream = std::function<QString(const QString &sessionId, const QJsonObject &meta, const QByteArray &payload)>;

  ConnectMixHost(Respond respond, SendStream send, QObject *parent = nullptr)
      : QObject(parent), m_respond(std::move(respond)), m_send(std::move(send)) {}

  void acceptSource(const QString &, const QJsonObject &, const QByteArray &) {}
  void prepare(const QString &id, const QString &, const QJsonObject &) {
    if (m_respond) {
      m_respond(id, false, {}, QStringLiteral("Mix not supported"));
    }
  }
  void dropSession(const QString &) {}

private:
  Respond m_respond;
  SendStream m_send;
};
