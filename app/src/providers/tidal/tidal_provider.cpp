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

#include "tidal_provider.h"

#include <QByteArray>
#include <QDir>
#include <QFile>
#include <QJsonDocument>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QStandardPaths>
#include <QUrlQuery>

namespace {
constexpr auto kClientId = "6BDSRdpK9hqEBTgU";
constexpr auto kClientSecret = "xeuPmY7nbpZ9IIbLAcQ93shka1VNheUAqN6IcszjTG8=";
constexpr auto kApiV1 = "https://api.tidal.com/v1";
constexpr auto kAuthTokenUrl = "https://auth.tidal.com/v1/oauth2/token";

QString sessionFilePath() {
  const QString configDir = QStandardPaths::writableLocation(QStandardPaths::ConfigLocation);
  return configDir + QStringLiteral("/pomelo/session.json");
}
} // namespace

TidalProvider::TidalProvider(QObject *parent) : QObject(parent), m_network(this) {
  loadSessionFile();
  if (m_accessToken.isEmpty()) {
    acquireClientCredentials();
  }
}

TidalProvider::~TidalProvider() {
  cancelAll();
}

QString TidalProvider::artworkUrl(const QString &hash) {
  if (hash.trimmed().isEmpty()) return QString();
  QString formatted = hash;
  formatted.replace(QLatin1Char('-'), QLatin1Char('/'));
  return QStringLiteral("https://resources.tidal.com/images/%1/640x640.jpg").arg(formatted);
}

QJsonObject TidalProvider::normalizeTrack(const QJsonObject &item) {
  const QString id = item.value(QStringLiteral("id")).toVariant().toString();
  const QString title = item.value(QStringLiteral("title")).toString(QStringLiteral("Untitled"));
  QString artistName;
  QString artistId;
  const QJsonArray artistsArr = item.value(QStringLiteral("artists")).toArray();
  if (!artistsArr.isEmpty()) {
    const QJsonObject first = artistsArr.at(0).toObject();
    artistName = first.value(QStringLiteral("name")).toString();
    artistId = first.value(QStringLiteral("id")).toVariant().toString();
  } else {
    const QJsonObject artistObj = item.value(QStringLiteral("artist")).toObject();
    artistName = !artistObj.isEmpty() ? artistObj.value(QStringLiteral("name")).toString()
                                      : item.value(QStringLiteral("artistName")).toString(QStringLiteral("Unknown Artist"));
    artistId = artistObj.value(QStringLiteral("id")).toVariant().toString();
  }

  const QJsonObject albumObj = item.value(QStringLiteral("album")).toObject();
  const QString albumTitle = albumObj.value(QStringLiteral("title")).toString();
  const QString albumId = albumObj.value(QStringLiteral("id")).toVariant().toString();
  const QString cover = !albumObj.isEmpty() && albumObj.contains(QStringLiteral("cover"))
      ? albumObj.value(QStringLiteral("cover")).toString()
      : item.value(QStringLiteral("cover")).toString();

  const double durationSec = item.value(QStringLiteral("duration")).toDouble(0);
  const qint64 durationMs = static_cast<qint64>(durationSec * 1000.0);

  QJsonObject out;
  out.insert(QStringLiteral("id"), id);
  out.insert(QStringLiteral("title"), title);
  out.insert(QStringLiteral("type"), QStringLiteral("song"));
  out.insert(QStringLiteral("artist"), artistName);
  out.insert(QStringLiteral("album"), albumTitle);
  out.insert(QStringLiteral("albumId"), albumId);
  out.insert(QStringLiteral("artistBrowseIds"), QJsonArray{artistId});
  out.insert(QStringLiteral("artists"), QJsonArray{artistName});
  out.insert(QStringLiteral("thumbnail"), artworkUrl(cover));
  out.insert(QStringLiteral("duration"), durationSec);
  out.insert(QStringLiteral("durationMs"), durationMs);
  out.insert(QStringLiteral("unplayable"), false);
  return out;
}

QJsonObject TidalProvider::normalizeAlbum(const QJsonObject &item) {
  const QString id = item.value(QStringLiteral("id")).toVariant().toString();
  const QString title = item.value(QStringLiteral("title")).toString(QStringLiteral("Untitled Album"));
  QString artistName;
  const QJsonArray artistsArr = item.value(QStringLiteral("artists")).toArray();
  if (!artistsArr.isEmpty()) {
    artistName = artistsArr.at(0).toObject().value(QStringLiteral("name")).toString();
  } else {
    const QJsonObject artistObj = item.value(QStringLiteral("artist")).toObject();
    artistName = !artistObj.isEmpty() ? artistObj.value(QStringLiteral("name")).toString()
                                      : item.value(QStringLiteral("artistName")).toString(QStringLiteral("Unknown Artist"));
  }
  const QString cover = item.value(QStringLiteral("cover")).toString();
  const QString releaseDate = item.value(QStringLiteral("releaseDate")).toString();

  QJsonObject out;
  out.insert(QStringLiteral("id"), id);
  out.insert(QStringLiteral("browseId"), id);
  out.insert(QStringLiteral("title"), title);
  out.insert(QStringLiteral("type"), QStringLiteral("album"));
  out.insert(QStringLiteral("artist"), artistName);
  out.insert(QStringLiteral("thumbnail"), artworkUrl(cover));
  out.insert(QStringLiteral("releaseDate"), releaseDate);
  return out;
}

QJsonObject TidalProvider::normalizeArtist(const QJsonObject &item) {
  const QString id = item.value(QStringLiteral("id")).toVariant().toString();
  const QString name = item.value(QStringLiteral("name")).toString(QStringLiteral("Unknown Artist"));
  return QJsonObject{
    {QStringLiteral("id"), id}, {QStringLiteral("browseId"), id},
    {QStringLiteral("title"), name}, {QStringLiteral("name"), name},
    {QStringLiteral("type"), QStringLiteral("artist")},
    {QStringLiteral("thumbnail"), artworkUrl(item.value(QStringLiteral("picture")).toString())}
  };
}

void TidalProvider::loadSessionFile() {
  QFile file(sessionFilePath());
  if (!file.open(QIODevice::ReadOnly)) return;
  const QJsonDocument doc = QJsonDocument::fromJson(file.readAll());
  if (!doc.isObject()) return;
  const QJsonObject root = doc.object();
  m_accessToken = root.value(QStringLiteral("access_token")).toString();
  m_refreshToken = root.value(QStringLiteral("refresh_token")).toString();
  m_countryCode = root.value(QStringLiteral("country_code")).toString(QStringLiteral("US"));
  m_userId = root.value(QStringLiteral("user_id")).toString();
}

void TidalProvider::saveSessionFile() {
  const QString path = sessionFilePath();
  QFileInfo info(path);
  info.dir().mkpath(QStringLiteral("."));
  QFile file(path);
  if (!file.open(QIODevice::WriteOnly | QIODevice::Truncate)) return;
  QJsonObject root{
    {QStringLiteral("access_token"), m_accessToken},
    {QStringLiteral("refresh_token"), m_refreshToken},
    {QStringLiteral("country_code"), m_countryCode},
    {QStringLiteral("user_id"), m_userId}
  };
  file.write(QJsonDocument(root).toJson(QJsonDocument::Indented));
}

void TidalProvider::acquireClientCredentials() {
  QNetworkRequest request{QUrl(QString::fromUtf8(kAuthTokenUrl))};
  request.setHeader(QNetworkRequest::ContentTypeHeader, QStringLiteral("application/x-www-form-urlencoded"));
  QUrlQuery query;
  query.addQueryItem(QStringLiteral("grant_type"), QStringLiteral("client_credentials"));
  query.addQueryItem(QStringLiteral("client_id"), QLatin1String(kClientId));
  query.addQueryItem(QStringLiteral("client_secret"), QLatin1String(kClientSecret));
  const QByteArray body = query.toString(QUrl::FullyEncoded).toUtf8();

  QNetworkReply *reply = m_network.post(request, body);
  connect(reply, &QNetworkReply::finished, this, [this, reply] {
    reply->deleteLater();
    if (reply->error() == QNetworkReply::NoError) {
      const QJsonDocument doc = QJsonDocument::fromJson(reply->readAll());
      if (doc.isObject()) {
        m_accessToken = doc.object().value(QStringLiteral("access_token")).toString();
        saveSessionFile();
        emit sessionUpdated();
      }
    }
  });
}

QNetworkRequest TidalProvider::createRequest(const QUrl &url) const {
  QNetworkRequest req(url);
  req.setRawHeader("x-tidal-token", kClientId);
  if (!m_accessToken.isEmpty()) {
    req.setRawHeader("Authorization", QByteArray("Bearer ") + m_accessToken.toUtf8());
  }
  return req;
}

quint64 TidalProvider::invoke(const QString &method, const QJsonValue &payload) {
  const quint64 id = m_nextRequestId++;
  const QJsonObject obj = payload.toObject();

  if (method == QStringLiteral("catalog.home")) {
    handleHome(id);
  } else if (method == QStringLiteral("catalog.search")) {
    handleSearch(id, obj.value(QStringLiteral("query")).toString(), obj.value(QStringLiteral("filter")).toString());
  } else if (method == QStringLiteral("catalog.album")) {
    handleAlbum(id, obj.value(QStringLiteral("browseId")).toString());
  } else if (method == QStringLiteral("catalog.artist")) {
    handleArtist(id, obj.value(QStringLiteral("browseId")).toString());
  } else if (method == QStringLiteral("catalog.playlist") || method == QStringLiteral("catalog.playlist.more")) {
    handlePlaylist(id, obj.value(QStringLiteral("browseId")).toString());
  } else if (method == QStringLiteral("catalog.playlists")) {
    handlePlaylists(id);
  } else if (method == QStringLiteral("catalog.library")) {
    handleLibrary(id);
  } else if (method == QStringLiteral("playback.resolve")) {
    handleResolve(id, obj);
  } else if (method == QStringLiteral("account.loadProfile")) {
    loadAccountProfile(obj);
    return id;
  } else {
    emit requestFailed(id, QStringLiteral("Unknown method: ") + method);
  }
  return id;
}

void TidalProvider::cancelAll() {
  for (auto it = m_activeReplies.begin(); it != m_activeReplies.end(); ++it) {
    it.key()->abort();
    it.key()->deleteLater();
  }
  m_activeReplies.clear();
}

quint64 TidalProvider::loadAccountProfile(const QJsonObject &) {
  const quint64 id = m_nextRequestId++;
  QJsonObject profile;
  profile.insert(QStringLiteral("userName"), QStringLiteral("Pomelo User"));
  profile.insert(QStringLiteral("userHandle"), QStringLiteral("@pomelo"));
  profile.insert(QStringLiteral("userEmail"), QStringLiteral("user@stuffbyren.xyz"));
  profile.insert(QStringLiteral("avatar"), QString());
  QMetaObject::invokeMethod(this, [this, id, profile] {
    emit resultReady(id, profile);
  }, Qt::QueuedConnection);
  return id;
}

quint64 TidalProvider::enrichAccountProfile(const QJsonObject &profile) {
  const quint64 id = m_nextRequestId++;
  QMetaObject::invokeMethod(this, [this, id, profile] {
    emit resultReady(id, profile);
  }, Qt::QueuedConnection);
  return id;
}

void TidalProvider::handleHome(quint64 requestId) {
  const QString urlStr = QStringLiteral("%1/pages/explore?countryCode=%2&deviceType=PHONE")
      .arg(QLatin1String(kApiV1), m_countryCode);
  QNetworkReply *reply = m_network.get(createRequest(QUrl(urlStr)));
  m_activeReplies.insert(reply, requestId);
  connect(reply, &QNetworkReply::finished, this, [this, reply, requestId] {
    m_activeReplies.remove(reply);
    reply->deleteLater();
    if (reply->error() != QNetworkReply::NoError) {
      handleSearch(requestId, QStringLiteral("Top Hits"), QStringLiteral("all"));
      return;
    }
    const QJsonObject root = QJsonDocument::fromJson(reply->readAll()).object();
    QJsonArray sections;
    const QJsonArray categories = root.contains(QStringLiteral("categories"))
        ? root.value(QStringLiteral("categories")).toArray() : root.value(QStringLiteral("rows")).toArray();
    for (const auto &catVal : categories) {
      const QJsonObject row = catVal.toObject();
      const QString title = row.value(QStringLiteral("title")).toString(QStringLiteral("Explore"));
      const QJsonArray items = row.value(QStringLiteral("modules")).toObject().value(QStringLiteral("items")).toArray();
      QJsonArray rowTracks;
      for (const auto &it : items) rowTracks.append(normalizeTrack(it.toObject()));
      if (!rowTracks.isEmpty()) sections.append(QJsonObject{{QStringLiteral("title"), title}, {QStringLiteral("items"), rowTracks}});
    }
    if (sections.isEmpty()) { handleSearch(requestId, QStringLiteral("Pop Hits"), QStringLiteral("all")); return; }
    emit resultReady(requestId, QJsonObject{{QStringLiteral("sections"), sections}});
  });
}

void TidalProvider::handleSearch(quint64 requestId, const QString &query, const QString &) {
  QUrl url(QStringLiteral("%1/search").arg(QLatin1String(kApiV1)));
  QUrlQuery q;
  q.addQueryItem(QStringLiteral("query"), query);
  q.addQueryItem(QStringLiteral("limit"), QStringLiteral("25"));
  q.addQueryItem(QStringLiteral("countryCode"), m_countryCode);
  url.setQuery(q);
  QNetworkReply *reply = m_network.get(createRequest(url));
  m_activeReplies.insert(reply, requestId);
  connect(reply, &QNetworkReply::finished, this, [this, reply, requestId] {
    m_activeReplies.remove(reply);
    reply->deleteLater();
    if (reply->error() != QNetworkReply::NoError) { emit requestFailed(requestId, reply->errorString()); return; }
    const QJsonObject root = QJsonDocument::fromJson(reply->readAll()).object();
    QJsonArray tracks, albums, artists, sections;
    for (const auto &v : root.value(QStringLiteral("tracks")).toObject().value(QStringLiteral("items")).toArray())
      tracks.append(normalizeTrack(v.toObject()));
    for (const auto &v : root.value(QStringLiteral("albums")).toObject().value(QStringLiteral("items")).toArray())
      albums.append(normalizeAlbum(v.toObject()));
    for (const auto &v : root.value(QStringLiteral("artists")).toObject().value(QStringLiteral("items")).toArray())
      artists.append(normalizeArtist(v.toObject()));
    if (!tracks.isEmpty()) sections.append(QJsonObject{{QStringLiteral("title"), QStringLiteral("Tracks")}, {QStringLiteral("items"), tracks}});
    if (!albums.isEmpty()) sections.append(QJsonObject{{QStringLiteral("title"), QStringLiteral("Albums")}, {QStringLiteral("items"), albums}});
    if (!artists.isEmpty()) sections.append(QJsonObject{{QStringLiteral("title"), QStringLiteral("Artists")}, {QStringLiteral("items"), artists}});
    emit resultReady(requestId, QJsonObject{{QStringLiteral("sections"), sections}});
  });
}

void TidalProvider::handleAlbum(quint64 requestId, const QString &albumId) {
  const QString urlTracks = QStringLiteral("%1/albums/%2/tracks?countryCode=%3&limit=100")
      .arg(QLatin1String(kApiV1), albumId, m_countryCode);
  QNetworkReply *reply = m_network.get(createRequest(QUrl(urlTracks)));
  m_activeReplies.insert(reply, requestId);
  connect(reply, &QNetworkReply::finished, this, [this, reply, requestId, albumId] {
    m_activeReplies.remove(reply);
    reply->deleteLater();
    if (reply->error() != QNetworkReply::NoError) { emit requestFailed(requestId, reply->errorString()); return; }
    const QJsonArray raw = QJsonDocument::fromJson(reply->readAll()).object().value(QStringLiteral("items")).toArray();
    QJsonArray tracks;
    QString title, artist, cover;
    for (const auto &v : raw) {
      const QJsonObject t = v.toObject();
      tracks.append(normalizeTrack(t));
      if (title.isEmpty()) {
        const QJsonObject al = t.value(QStringLiteral("album")).toObject();
        title = al.value(QStringLiteral("title")).toString();
        cover = al.value(QStringLiteral("cover")).toString();
        artist = t.value(QStringLiteral("artist")).toObject().value(QStringLiteral("name")).toString();
      }
    }
    emit resultReady(requestId, QJsonObject{
      {QStringLiteral("browseId"), albumId}, {QStringLiteral("id"), albumId},
      {QStringLiteral("title"), title}, {QStringLiteral("artist"), artist},
      {QStringLiteral("thumbnail"), artworkUrl(cover)}, {QStringLiteral("tracks"), tracks}});
  });
}

void TidalProvider::handleArtist(quint64 requestId, const QString &artistId) {
  const QString urlTop = QStringLiteral("%1/artists/%2/top-tracks?countryCode=%3&limit=25")
      .arg(QLatin1String(kApiV1), artistId, m_countryCode);
  QNetworkReply *reply = m_network.get(createRequest(QUrl(urlTop)));
  m_activeReplies.insert(reply, requestId);
  connect(reply, &QNetworkReply::finished, this, [this, reply, requestId, artistId] {
    m_activeReplies.remove(reply);
    reply->deleteLater();
    if (reply->error() != QNetworkReply::NoError) { emit requestFailed(requestId, reply->errorString()); return; }
    const QJsonArray raw = QJsonDocument::fromJson(reply->readAll()).object().value(QStringLiteral("items")).toArray();
    QJsonArray tracks;
    QString name;
    for (const auto &v : raw) {
      const QJsonObject t = v.toObject();
      tracks.append(normalizeTrack(t));
      if (name.isEmpty()) name = t.value(QStringLiteral("artist")).toObject().value(QStringLiteral("name")).toString();
    }
    emit resultReady(requestId, QJsonObject{
      {QStringLiteral("browseId"), artistId}, {QStringLiteral("id"), artistId},
      {QStringLiteral("title"), name}, {QStringLiteral("name"), name},
      {QStringLiteral("tracks"), tracks},
      {QStringLiteral("sections"), QJsonArray{QJsonObject{{QStringLiteral("title"), QStringLiteral("Top Songs")}, {QStringLiteral("items"), tracks}}}}
    });
  });
}

void TidalProvider::handlePlaylist(quint64 requestId, const QString &playlistId) {
  const QString urlTracks = QStringLiteral("%1/playlists/%2/tracks?countryCode=%3&limit=100")
      .arg(QLatin1String(kApiV1), playlistId, m_countryCode);
  QNetworkReply *reply = m_network.get(createRequest(QUrl(urlTracks)));
  m_activeReplies.insert(reply, requestId);

  connect(reply, &QNetworkReply::finished, this, [this, reply, requestId, playlistId] {
    m_activeReplies.remove(reply);
    reply->deleteLater();
    if (reply->error() != QNetworkReply::NoError) {
      emit requestFailed(requestId, reply->errorString());
      return;
    }
    const QJsonDocument doc = QJsonDocument::fromJson(reply->readAll());
    const QJsonArray rawTracks = doc.object().value(QStringLiteral("items")).toArray();
    QJsonArray tracks;
    for (const auto &v : rawTracks) {
      tracks.append(normalizeTrack(v.toObject()));
    }
    QJsonObject plObj;
    plObj.insert(QStringLiteral("browseId"), playlistId);
    plObj.insert(QStringLiteral("id"), playlistId);
    plObj.insert(QStringLiteral("tracks"), tracks);
    emit resultReady(requestId, plObj);
  });
}

void TidalProvider::handlePlaylists(quint64 requestId) {
  emit resultReady(requestId, QJsonArray{});
}

void TidalProvider::handleLibrary(quint64 requestId) {
  QJsonArray sections;
  for (const auto &key : {QStringLiteral("artists"), QStringLiteral("albums"), QStringLiteral("songs"), QStringLiteral("playlists")}) {
    QJsonObject section;
    section.insert(QStringLiteral("key"), key);
    section.insert(QStringLiteral("items"), QJsonArray{});
    sections.append(section);
  }
  QJsonObject result;
  result.insert(QStringLiteral("sections"), sections);
  emit resultReady(requestId, result);
}

void TidalProvider::handleResolve(quint64 requestId, const QJsonObject &payload) {
  const QJsonObject track = payload.value(QStringLiteral("track")).toObject();
  const QString trackId = track.value(QStringLiteral("id")).toVariant().toString();
  if (trackId.isEmpty()) {
    emit requestFailed(requestId, QStringLiteral("Missing track ID"));
    return;
  }

  const QString cc = m_countryCode.isEmpty() ? QStringLiteral("US") : m_countryCode;
  const QString urlStr = QStringLiteral("%1/tracks/%2/playbackinfopostpaywall?audioquality=LOSSLESS&playbackmode=STREAM&assetpresentation=FULL&countryCode=%3")
      .arg(QLatin1String(kApiV1), trackId, cc);
  QNetworkReply *reply = m_network.get(createRequest(QUrl(urlStr)));
  m_activeReplies.insert(reply, requestId);

  connect(reply, &QNetworkReply::finished, this, [this, reply, requestId, track] {
    m_activeReplies.remove(reply);
    reply->deleteLater();
    if (reply->error() != QNetworkReply::NoError) {
      emit requestFailed(requestId, reply->errorString());
      return;
    }

    const QJsonDocument doc = QJsonDocument::fromJson(reply->readAll());
    const QJsonObject root = doc.object();
    const QString rawManifest = root.value(QStringLiteral("manifest")).toString();
    const QString mime = root.value(QStringLiteral("manifestMimeType")).toString();
    QString streamUrl;
    QString manifestXml;

    if (!rawManifest.isEmpty()) {
      const QByteArray decoded = QByteArray::fromBase64(rawManifest.toUtf8());
      if (mime == QStringLiteral("application/dash+xml") || decoded.startsWith("<?xml")) {
        manifestXml = QString::fromUtf8(decoded);
      } else {
        const QJsonDocument manifestDoc = QJsonDocument::fromJson(decoded);
        if (manifestDoc.isObject()) {
          const QJsonArray urls = manifestDoc.object().value(QStringLiteral("urls")).toArray();
          if (!urls.isEmpty()) streamUrl = urls.at(0).toString();
        }
      }
    }

    if (streamUrl.isEmpty() && manifestXml.isEmpty()) {
      emit requestFailed(requestId, QStringLiteral("No playback stream URL available"));
      return;
    }

    QJsonObject stream;
    if (!manifestXml.isEmpty()) {
      stream.insert(QStringLiteral("manifestXml"), manifestXml);
      stream.insert(QStringLiteral("mimeType"), QStringLiteral("application/dash+xml"));
    } else {
      stream.insert(QStringLiteral("url"), streamUrl);
    }
    stream.insert(QStringLiteral("bitrate"), 1411000);
    stream.insert(QStringLiteral("durationSeconds"), track.value(QStringLiteral("duration")).toDouble(0));
    stream.insert(QStringLiteral("contentLength"), 0);
    emit resultReady(requestId, stream);
  });
}
