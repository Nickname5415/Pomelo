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

#include <QHash>
#include <QJsonArray>
#include <QJsonObject>
#include <QNetworkAccessManager>
#include <QObject>
#include <QString>
#include <QUrl>

class QNetworkReply;

class TidalProvider : public QObject {
  Q_OBJECT

public:
  explicit TidalProvider(QObject *parent = nullptr);
  ~TidalProvider() override;

  quint64 invoke(const QString &method, const QJsonValue &payload);
  quint64 loadAccountProfile(const QJsonObject &session);
  quint64 enrichAccountProfile(const QJsonObject &profile);
  void cancelAll();

  QString accessToken() const { return m_accessToken; }
  QString countryCode() const { return m_countryCode; }
  bool isAuthenticated() const { return !m_accessToken.isEmpty(); }

  static QString artworkUrl(const QString &hash);
  static QJsonObject normalizeTrack(const QJsonObject &item);
  static QJsonObject normalizeAlbum(const QJsonObject &item);
  static QJsonObject normalizeArtist(const QJsonObject &item);

signals:
  void resultReady(quint64 requestId, const QJsonValue &result);
  void requestFailed(quint64 requestId, const QString &message);
  void sessionUpdated();

private:
  void loadSessionFile();
  void saveSessionFile();
  void acquireClientCredentials();

  void handleHome(quint64 requestId);
  void handleSearch(quint64 requestId, const QString &query, const QString &filter);
  void handleAlbum(quint64 requestId, const QString &albumId);
  void handleArtist(quint64 requestId, const QString &artistId);
  void handlePlaylist(quint64 requestId, const QString &playlistId);
  void handlePlaylists(quint64 requestId);
  void handleLibrary(quint64 requestId);
  void handleResolve(quint64 requestId, const QJsonObject &payload);

  QNetworkRequest createRequest(const QUrl &url) const;

  QNetworkAccessManager m_network;
  QString m_accessToken;
  QString m_refreshToken;
  QString m_countryCode{"US"};
  QString m_userId;
  quint64 m_nextRequestId{1};
  QHash<QNetworkReply *, quint64> m_activeReplies;
};
