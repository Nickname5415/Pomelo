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

#include "tidal_catalog.h"

TidalCatalog::TidalCatalog(TidalProvider *provider, QObject *parent)
    : QObject(parent), m_provider(provider) {
  Q_ASSERT(m_provider);
  connect(m_provider, &TidalProvider::resultReady, this, &TidalCatalog::handleResult);
  connect(m_provider, &TidalProvider::requestFailed, this, &TidalCatalog::handleFailure);
}

quint64 TidalCatalog::fetchHome(const QJsonObject &session) {
  return invoke(RequestKind::Home, QStringLiteral("catalog.home"),
                {{QStringLiteral("session"), session}});
}

quint64 TidalCatalog::fetchPlaylists(const QJsonObject &session) {
  return invoke(RequestKind::Playlists, QStringLiteral("catalog.playlists"),
                {{QStringLiteral("session"), session}});
}

quint64 TidalCatalog::fetchLibrary(const QJsonObject &session) {
  return invoke(RequestKind::Library, QStringLiteral("catalog.library"),
                {{QStringLiteral("session"), session}});
}

quint64 TidalCatalog::fetchSearch(const QString &query, const QString &filter,
                                  const QJsonObject &session) {
  return invoke(RequestKind::Search, QStringLiteral("catalog.search"),
                {{QStringLiteral("session"), session},
                 {QStringLiteral("query"), query.trimmed()},
                 {QStringLiteral("filter"), filter.trimmed().toLower()}});
}

quint64 TidalCatalog::fetchArtist(const QString &browseId, const QJsonObject &session) {
  return invoke(RequestKind::Artist, QStringLiteral("catalog.artist"),
                {{QStringLiteral("session"), session},
                 {QStringLiteral("browseId"), browseId.trimmed()}});
}

quint64 TidalCatalog::fetchAlbum(const QString &browseId, const QJsonObject &session) {
  return invoke(RequestKind::Album, QStringLiteral("catalog.album"),
                {{QStringLiteral("session"), session},
                 {QStringLiteral("browseId"), browseId.trimmed()}});
}

quint64 TidalCatalog::fetchPlaylist(const QString &browseId, const QJsonObject &session) {
  return invoke(RequestKind::Playlist, QStringLiteral("catalog.playlist"),
                {{QStringLiteral("session"), session},
                 {QStringLiteral("browseId"), browseId.trimmed()}});
}

quint64 TidalCatalog::fetchPlaylistPage(const QString &continuation, int startIndex,
                                        const QJsonObject &session) {
  return invoke(RequestKind::PlaylistPage, QStringLiteral("catalog.playlist.more"),
                {{QStringLiteral("session"), session},
                 {QStringLiteral("continuation"), continuation.trimmed()},
                 {QStringLiteral("startIndex"), qMax(0, startIndex)}});
}

quint64 TidalCatalog::fetchBrowse(const QJsonObject &payload, const QJsonObject &session) {
  QJsonObject request = payload;
  request.insert(QStringLiteral("session"), session);
  return invoke(RequestKind::Browse, QStringLiteral("catalog.browse"), request);
}

quint64 TidalCatalog::invoke(RequestKind kind, const QString &method, QJsonObject payload) {
  const quint64 requestId = m_provider->invoke(method, payload);
  m_requests.insert(requestId, kind);
  return requestId;
}

void TidalCatalog::handleResult(quint64 requestId, const QJsonValue &result) {
  auto it = m_requests.find(requestId);
  if (it == m_requests.end()) return;

  const RequestKind kind = it.value();
  m_requests.erase(it);

  if (kind == RequestKind::Playlists && result.isArray()) {
    emit playlistsReady(requestId, result.toArray());
    return;
  }

  if (result.isObject()) {
    const QJsonObject object = result.toObject();
    switch (kind) {
    case RequestKind::Home:
      emit homeReady(requestId, object);
      return;
    case RequestKind::Library:
      emit libraryReady(requestId, object);
      return;
    case RequestKind::Search:
      emit searchReady(requestId, object);
      return;
    case RequestKind::Artist:
      emit artistReady(requestId, object);
      return;
    case RequestKind::Browse:
      emit browseReady(requestId, object);
      return;
    case RequestKind::Album:
      emit albumReady(requestId, object);
      return;
    case RequestKind::Playlist:
      emit playlistReady(requestId, object);
      return;
    case RequestKind::PlaylistPage:
      emit playlistPageReady(requestId, object);
      return;
    case RequestKind::Playlists:
      break;
    }
  }

  emit requestFailed(requestId, QStringLiteral("Tidal returned an unexpected response."));
}

void TidalCatalog::handleFailure(quint64 requestId, const QString &message) {
  if (m_requests.remove(requestId)) {
    emit requestFailed(requestId, message);
  }
}
