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
  if (m_accessToken.isEmpty()) acquireClientCredentials();
}

TidalProvider::~TidalProvider() { cancelAll(); }

QString TidalProvider::artworkUrl(const QString &hash) {
  if (hash.trimmed().isEmpty()) return QString();
  QString formatted = hash;
  formatted.replace(QLatin1Char('-'), QLatin1Char('/'));
  return QStringLiteral("https://resources.tidal.com/images/%1/750x750.jpg").arg(formatted);
}

QJsonObject TidalProvider::normalizeTrack(const QJsonObject &raw) {
  const QJsonObject item = raw.contains(QStringLiteral("item")) ? raw.value(QStringLiteral("item")).toObject() : raw;
  const QString id = item.value(QStringLiteral("id")).toVariant().toString();
  const QString title = item.value(QStringLiteral("title")).toString(QStringLiteral("Untitled"));
  QString artistName, artistId;
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

  const int durationSec = item.value(QStringLiteral("duration")).toInt(0);
  const QString durationStr = QStringLiteral("%1:%2").arg(durationSec / 60).arg(durationSec % 60, 2, 10, QLatin1Char('0'));
  return QJsonObject{
    {QStringLiteral("id"), id}, {QStringLiteral("browseId"), id},
    {QStringLiteral("title"), title}, {QStringLiteral("type"), QStringLiteral("song")},
    {QStringLiteral("artist"), artistName}, {QStringLiteral("album"), albumTitle},
    {QStringLiteral("albumId"), albumId}, {QStringLiteral("artistBrowseIds"), QJsonArray{artistId}},
    {QStringLiteral("artists"), QJsonArray{artistName}}, {QStringLiteral("thumbnail"), artworkUrl(cover)},
    {QStringLiteral("duration"), durationStr}, {QStringLiteral("durationSeconds"), durationSec},
    {QStringLiteral("durationMs"), static_cast<qint64>(durationSec * 1000)}, {QStringLiteral("unplayable"), false}
  };
}

QJsonObject TidalProvider::normalizeAlbum(const QJsonObject &raw) {
  const QJsonObject item = raw.contains(QStringLiteral("item")) ? raw.value(QStringLiteral("item")).toObject() : raw;
  const QString id = item.value(QStringLiteral("id")).toVariant().toString();
  const QString title = item.value(QStringLiteral("title")).toString(QStringLiteral("Untitled Album"));
  QString artistName;
  const QJsonArray artistsArr = item.value(QStringLiteral("artists")).toArray();
  if (!artistsArr.isEmpty()) artistName = artistsArr.at(0).toObject().value(QStringLiteral("name")).toString();
  else {
    const QJsonObject artistObj = item.value(QStringLiteral("artist")).toObject();
    artistName = !artistObj.isEmpty() ? artistObj.value(QStringLiteral("name")).toString()
                                      : item.value(QStringLiteral("artistName")).toString(QStringLiteral("Unknown Artist"));
  }
  return QJsonObject{{QStringLiteral("id"), id}, {QStringLiteral("browseId"), id}, {QStringLiteral("title"), title}, {QStringLiteral("type"), QStringLiteral("album")},
                     {QStringLiteral("artist"), artistName}, {QStringLiteral("thumbnail"), artworkUrl(item.value(QStringLiteral("cover")).toString())},
                     {QStringLiteral("releaseDate"), item.value(QStringLiteral("releaseDate")).toString()}};
}

QJsonObject TidalProvider::normalizeArtist(const QJsonObject &raw) {
  const QJsonObject item = raw.contains(QStringLiteral("item")) ? raw.value(QStringLiteral("item")).toObject() : raw;
  const QString id = item.value(QStringLiteral("id")).toVariant().toString();
  const QString name = item.value(QStringLiteral("name")).toString(QStringLiteral("Unknown Artist"));
  return QJsonObject{{QStringLiteral("id"), id}, {QStringLiteral("browseId"), id}, {QStringLiteral("title"), name}, {QStringLiteral("name"), name},
                     {QStringLiteral("type"), QStringLiteral("artist")}, {QStringLiteral("thumbnail"), artworkUrl(item.value(QStringLiteral("picture")).toString())}};
}

QJsonObject TidalProvider::normalizePlaylist(const QJsonObject &raw) {
  const QJsonObject item = raw.contains(QStringLiteral("item")) ? raw.value(QStringLiteral("item")).toObject() : raw;
  const QString id = item.contains(QStringLiteral("uuid")) ? item.value(QStringLiteral("uuid")).toString() : item.value(QStringLiteral("id")).toString();
  const QString title = item.value(QStringLiteral("title")).toString(QStringLiteral("Untitled Playlist"));
  const QString image = item.value(QStringLiteral("image")).toString();
  const QString squareImage = item.value(QStringLiteral("squareImage")).toString();
  const int count = item.value(QStringLiteral("numberOfTracks")).toInt(0);
  QString thumb;
  if (!squareImage.isEmpty()) {
    thumb = artworkUrl(squareImage);
  } else if (item.contains(QStringLiteral("images"))) {
    thumb = item.value(QStringLiteral("images")).toObject().value(QStringLiteral("MEDIUM")).toObject().value(QStringLiteral("url")).toString();
  } else if (!image.isEmpty()) {
    thumb = QStringLiteral("https://resources.tidal.com/images/%1/1080x720.jpg").arg(QString(image).replace(QLatin1Char('-'), QLatin1Char('/')));
  }
  return QJsonObject{
    {QStringLiteral("id"), id}, {QStringLiteral("browseId"), id},
    {QStringLiteral("title"), title}, {QStringLiteral("type"), QStringLiteral("playlist")},
    {QStringLiteral("thumbnail"), thumb}, {QStringLiteral("itemCount"), count},
    {QStringLiteral("subtitle"), item.value(QStringLiteral("subTitle")).toString(count > 0 ? QStringLiteral("%1 tracks").arg(count) : QString())}
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
  QFileInfo(path).dir().mkpath(QStringLiteral("."));
  QFile file(path);
  if (file.open(QIODevice::WriteOnly | QIODevice::Truncate))
    file.write(QJsonDocument(QJsonObject{{QStringLiteral("access_token"), m_accessToken}, {QStringLiteral("refresh_token"), m_refreshToken},
                                         {QStringLiteral("country_code"), m_countryCode}, {QStringLiteral("user_id"), m_userId}}).toJson());
}

void TidalProvider::acquireClientCredentials() {
  QNetworkRequest req{QUrl(QString::fromUtf8(kAuthTokenUrl))};
  req.setHeader(QNetworkRequest::ContentTypeHeader, QStringLiteral("application/x-www-form-urlencoded"));
  QUrlQuery q;
  q.addQueryItem(QStringLiteral("grant_type"), QStringLiteral("client_credentials"));
  q.addQueryItem(QStringLiteral("client_id"), QLatin1String(kClientId));
  q.addQueryItem(QStringLiteral("client_secret"), QLatin1String(kClientSecret));
  QNetworkReply *reply = m_network.post(req, q.toString(QUrl::FullyEncoded).toUtf8());
  connect(reply, &QNetworkReply::finished, this, [this, reply] {
    reply->deleteLater();
    if (reply->error() == QNetworkReply::NoError) {
      m_accessToken = QJsonDocument::fromJson(reply->readAll()).object().value(QStringLiteral("access_token")).toString();
      saveSessionFile();
      emit sessionUpdated();
    }
  });
}

QNetworkRequest TidalProvider::createRequest(const QUrl &url) const {
  QNetworkRequest req(url);
  req.setRawHeader("x-tidal-token", kClientId);
  if (!m_accessToken.isEmpty())
    req.setRawHeader("Authorization", QByteArray("Bearer ") + m_accessToken.toUtf8());
  return req;
}

quint64 TidalProvider::invoke(const QString &method, const QJsonValue &payload) {
  const quint64 id = m_nextRequestId++;
  const QJsonObject obj = payload.toObject();
  if (method == QStringLiteral("catalog.home")) handleHome(id);
  else if (method == QStringLiteral("catalog.search")) handleSearch(id, obj.value(QStringLiteral("query")).toString(), obj.value(QStringLiteral("filter")).toString());
  else if (method == QStringLiteral("catalog.album")) handleAlbum(id, obj.value(QStringLiteral("browseId")).toString());
  else if (method == QStringLiteral("catalog.artist")) handleArtist(id, obj.value(QStringLiteral("browseId")).toString());
  else if (method == QStringLiteral("catalog.playlist") || method == QStringLiteral("catalog.playlist.more")) handlePlaylist(id, obj.value(QStringLiteral("browseId")).toString());
  else if (method == QStringLiteral("catalog.playlists")) handlePlaylists(id);
  else if (method == QStringLiteral("catalog.library")) handleLibrary(id);
  else if (method == QStringLiteral("playback.resolve")) handleResolve(id, obj);
  else if (method == QStringLiteral("account.loadProfile")) { loadAccountProfile(obj); return id; }
  else emit requestFailed(id, QStringLiteral("Unknown method: ") + method);
  return id;
}

void TidalProvider::cancelAll() {
  for (auto it = m_activeReplies.begin(); it != m_activeReplies.end(); ++it) { it.key()->abort(); it.key()->deleteLater(); }
  m_activeReplies.clear();
}

quint64 TidalProvider::loadAccountProfile(const QJsonObject &) {
  const quint64 id = m_nextRequestId++;
  const QJsonObject p{{QStringLiteral("userName"), QStringLiteral("Pomelo User")}, {QStringLiteral("userHandle"), QStringLiteral("@pomelo")}, {QStringLiteral("userEmail"), QStringLiteral("user@stuffbyren.xyz")}};
  QMetaObject::invokeMethod(this, [this, id, p] { emit resultReady(id, p); }, Qt::QueuedConnection);
  return id;
}

quint64 TidalProvider::enrichAccountProfile(const QJsonObject &p) {
  const quint64 id = m_nextRequestId++;
  QMetaObject::invokeMethod(this, [this, id, p] { emit resultReady(id, p); }, Qt::QueuedConnection);
  return id;
}

void TidalProvider::handleHome(quint64 requestId) {
  const QString cc = m_countryCode.isEmpty() ? QStringLiteral("US") : m_countryCode;
  const QString urlStr = QStringLiteral("%1/pages/home?locale=en_US&countryCode=%2&deviceType=PHONE")
      .arg(QLatin1String(kApiV1), cc);
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
    const QJsonArray rows = root.value(QStringLiteral("rows")).toArray();
    for (const auto &rv : rows) {
      const QJsonArray modules = rv.toObject().value(QStringLiteral("modules")).toArray();
      for (const auto &mv : modules) {
        const QJsonObject mod = mv.toObject();
        const QString title = mod.value(QStringLiteral("title")).toString();
        const QString mtype = mod.value(QStringLiteral("type")).toString();
        if (title.isEmpty() || mtype == QStringLiteral("TASK_LIST") || mtype == QStringLiteral("LIVE_SESSION_LIST"))
          continue;
        const QJsonArray rawItems = mod.value(QStringLiteral("pagedList")).toObject().value(QStringLiteral("items")).toArray();
        QJsonArray normItems;
        for (const auto &itv : rawItems) {
          const QJsonObject itObj = itv.toObject();
          const QJsonObject in = itObj.contains(QStringLiteral("item")) ? itObj.value(QStringLiteral("item")).toObject() : itObj;
          const QString t = itObj.value(QStringLiteral("type")).toString().toUpper();
          if (t == QStringLiteral("ALBUM") || in.contains(QStringLiteral("numberOfVolumes")) || (in.contains(QStringLiteral("numberOfTracks")) && !in.contains(QStringLiteral("uuid"))))
            normItems.append(normalizeAlbum(itObj));
          else if (t == QStringLiteral("TRACK") || (in.contains(QStringLiteral("trackNumber")) && in.contains(QStringLiteral("album"))))
            normItems.append(normalizeTrack(itObj));
          else if (t == QStringLiteral("ARTIST") || (in.contains(QStringLiteral("picture")) && !in.contains(QStringLiteral("album"))))
            normItems.append(normalizeArtist(itObj));
          else normItems.append(normalizePlaylist(itObj));
        }
        if (!normItems.isEmpty()) sections.append(QJsonObject{{QStringLiteral("title"), title}, {QStringLiteral("items"), normItems}});
      }
    }
    if (sections.isEmpty()) { handleSearch(requestId, QStringLiteral("Top Hits"), QStringLiteral("all")); return; }
    emit resultReady(requestId, QJsonObject{{QStringLiteral("sections"), sections}});
  });
}

void TidalProvider::handleSearch(quint64 requestId, const QString &query, const QString &filter) {
  QUrl url(QStringLiteral("%1/search").arg(QLatin1String(kApiV1)));
  QUrlQuery q;
  q.addQueryItem(QStringLiteral("query"), query);
  q.addQueryItem(QStringLiteral("limit"), QStringLiteral("25"));
  q.addQueryItem(QStringLiteral("countryCode"), m_countryCode);
  url.setQuery(q);
  QNetworkReply *reply = m_network.get(createRequest(url));
  m_activeReplies.insert(reply, requestId);
  connect(reply, &QNetworkReply::finished, this, [this, reply, requestId, filter] {
    m_activeReplies.remove(reply);
    reply->deleteLater();
    if (reply->error() != QNetworkReply::NoError) { emit requestFailed(requestId, reply->errorString()); return; }
    const QJsonObject root = QJsonDocument::fromJson(reply->readAll()).object();
    QJsonArray tracks, albums, artists, playlists, sections;
    for (const auto &v : root.value(QStringLiteral("tracks")).toObject().value(QStringLiteral("items")).toArray()) tracks.append(normalizeTrack(v.toObject()));
    for (const auto &v : root.value(QStringLiteral("albums")).toObject().value(QStringLiteral("items")).toArray()) albums.append(normalizeAlbum(v.toObject()));
    for (const auto &v : root.value(QStringLiteral("artists")).toObject().value(QStringLiteral("items")).toArray()) artists.append(normalizeArtist(v.toObject()));
    for (const auto &v : root.value(QStringLiteral("playlists")).toObject().value(QStringLiteral("items")).toArray()) playlists.append(normalizePlaylist(v.toObject()));
    const bool all = filter.isEmpty() || filter == QStringLiteral("all");
    if (!tracks.isEmpty() && (all || filter == QStringLiteral("songs")))
      sections.append(QJsonObject{{QStringLiteral("key"), QStringLiteral("songs")}, {QStringLiteral("title"), QStringLiteral("Songs")}, {QStringLiteral("items"), tracks}});
    if (!albums.isEmpty() && (all || filter == QStringLiteral("albums")))
      sections.append(QJsonObject{{QStringLiteral("key"), QStringLiteral("albums")}, {QStringLiteral("title"), QStringLiteral("Albums")}, {QStringLiteral("items"), albums}});
    if (!artists.isEmpty() && (all || filter == QStringLiteral("artists")))
      sections.append(QJsonObject{{QStringLiteral("key"), QStringLiteral("artists")}, {QStringLiteral("title"), QStringLiteral("Artists")}, {QStringLiteral("items"), artists}});
    if (!playlists.isEmpty() && (all || filter == QStringLiteral("playlists")))
      sections.append(QJsonObject{{QStringLiteral("key"), QStringLiteral("playlists")}, {QStringLiteral("title"), QStringLiteral("Playlists")}, {QStringLiteral("items"), playlists}});
    emit resultReady(requestId, QJsonObject{{QStringLiteral("sections"), sections}});
  });
}

void TidalProvider::handleAlbum(quint64 requestId, const QString &albumId) {
  const QString url = QStringLiteral("%1/albums/%2/tracks?countryCode=%3&limit=100").arg(QLatin1String(kApiV1), albumId, m_countryCode);
  QNetworkReply *reply = m_network.get(createRequest(QUrl(url)));
  m_activeReplies.insert(reply, requestId);
  connect(reply, &QNetworkReply::finished, this, [this, reply, requestId, albumId] {
    m_activeReplies.remove(reply);
    reply->deleteLater();
    if (reply->error() != QNetworkReply::NoError) { emit requestFailed(requestId, reply->errorString()); return; }
    const QJsonArray raw = QJsonDocument::fromJson(reply->readAll()).object().value(QStringLiteral("items")).toArray();
    QJsonArray tracks; QString title, artist, cover;
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
    emit resultReady(requestId, QJsonObject{{QStringLiteral("browseId"), albumId}, {QStringLiteral("id"), albumId},
      {QStringLiteral("title"), title}, {QStringLiteral("artist"), artist}, {QStringLiteral("thumbnail"), artworkUrl(cover)}, {QStringLiteral("tracks"), tracks}});
  });
}

void TidalProvider::handleArtist(quint64 requestId, const QString &artistId) {
  const QString cc = m_countryCode.isEmpty() ? QStringLiteral("US") : m_countryCode;
  struct State { int left{2}; QJsonObject pageRoot; QJsonArray toptracks; };
  auto st = std::make_shared<State>();
  auto checkDone = [this, requestId, artistId, st]() {
    if (--st->left > 0) return;
    QString name, picture, desc;
    QJsonArray tracks = st->toptracks, sections;
    QJsonObject latest;
    for (const auto &rv : st->pageRoot.value(QStringLiteral("rows")).toArray()) {
      for (const auto &mv : rv.toObject().value(QStringLiteral("modules")).toArray()) {
        const QJsonObject mod = mv.toObject();
        const QString mtype = mod.value(QStringLiteral("type")).toString();
        const QString title = mod.value(QStringLiteral("title")).toString();
        if (mtype == QStringLiteral("ARTIST_HEADER")) {
          const QJsonObject a = mod.value(QStringLiteral("artist")).toObject();
          name = a.value(QStringLiteral("name")).toString(); picture = a.value(QStringLiteral("picture")).toString();
        } else if (mtype == QStringLiteral("TEXT_BLOCK")) desc = mod.value(QStringLiteral("text")).toString();
        else if (mtype == QStringLiteral("TRACK_LIST") && tracks.isEmpty()) {
          for (const auto &v : mod.value(QStringLiteral("pagedList")).toObject().value(QStringLiteral("items")).toArray()) tracks.append(normalizeTrack(v.toObject()));
        } else if (mtype == QStringLiteral("ALBUM_LIST") || mtype == QStringLiteral("ARTIST_LIST") || mtype == QStringLiteral("MIXED_TYPES_LIST")) {
          QJsonArray norm;
          for (const auto &v : mod.value(QStringLiteral("pagedList")).toObject().value(QStringLiteral("items")).toArray()) {
            if (mtype == QStringLiteral("ALBUM_LIST")) norm.append(normalizeAlbum(v.toObject()));
            else if (mtype == QStringLiteral("ARTIST_LIST")) norm.append(normalizeArtist(v.toObject()));
            else norm.append(normalizePlaylist(v.toObject()));
          }
          if (!norm.isEmpty()) {
            const QString key = mtype == QStringLiteral("ALBUM_LIST") ? (title.contains(QStringLiteral("Single"), Qt::CaseInsensitive) || title.contains(QStringLiteral("EP"), Qt::CaseInsensitive) ? QStringLiteral("singles") : QStringLiteral("albums")) : (mtype == QStringLiteral("ARTIST_LIST") ? QStringLiteral("related") : QStringLiteral("playlists"));
            sections.append(QJsonObject{{QStringLiteral("key"), key}, {QStringLiteral("title"), title}, {QStringLiteral("items"), norm}});
            if (latest.isEmpty() && mtype == QStringLiteral("ALBUM_LIST") && key == QStringLiteral("albums")) latest = norm.at(0).toObject();
          }
        }
      }
    }
    if (name.isEmpty() && !tracks.isEmpty()) name = tracks.at(0).toObject().value(QStringLiteral("artist")).toString();
    if (name.isEmpty() && tracks.isEmpty() && sections.isEmpty()) { emit requestFailed(requestId, QStringLiteral("Artist not found")); return; }
    QJsonObject res{{QStringLiteral("browseId"), artistId}, {QStringLiteral("id"), artistId}, {QStringLiteral("title"), name}, {QStringLiteral("name"), name},
                    {QStringLiteral("thumbnail"), artworkUrl(picture)}, {QStringLiteral("heroArtwork"), artworkUrl(picture)},
                    {QStringLiteral("description"), desc}, {QStringLiteral("tracks"), tracks}, {QStringLiteral("sections"), sections}};
    if (!latest.isEmpty()) res.insert(QStringLiteral("latestRelease"), latest);
    emit resultReady(requestId, res);
  };
  const QString pageUrl = QStringLiteral("%1/pages/artist?artistId=%2&locale=en_US&deviceType=PHONE&countryCode=%3").arg(QLatin1String(kApiV1), artistId, cc);
  const QString ttUrl = QStringLiteral("%1/artists/%2/toptracks?countryCode=%3&limit=25").arg(QLatin1String(kApiV1), artistId, cc);
  for (const auto &u : {pageUrl, ttUrl}) {
    QNetworkReply *rep = m_network.get(createRequest(QUrl(u)));
    m_activeReplies.insert(rep, requestId);
    connect(rep, &QNetworkReply::finished, this, [this, rep, u, ttUrl, st, checkDone] {
      m_activeReplies.remove(rep); rep->deleteLater();
      if (rep->error() == QNetworkReply::NoError) {
        const QJsonObject obj = QJsonDocument::fromJson(rep->readAll()).object();
        if (u == ttUrl) { for (const auto &v : obj.value(QStringLiteral("items")).toArray()) st->toptracks.append(normalizeTrack(v.toObject())); }
        else st->pageRoot = obj;
      }
      checkDone();
    });
  }
}

void TidalProvider::handlePlaylist(quint64 requestId, const QString &playlistId) {
  const bool isMix = !playlistId.contains(QLatin1Char('-'));
  const QString cc = m_countryCode.isEmpty() ? QStringLiteral("US") : m_countryCode;
  const QString url = isMix
      ? QStringLiteral("%1/mixes/%2/items?countryCode=%3&limit=100").arg(QLatin1String(kApiV1), playlistId, cc)
      : QStringLiteral("%1/playlists/%2/tracks?countryCode=%3&limit=100").arg(QLatin1String(kApiV1), playlistId, cc);
  QNetworkReply *reply = m_network.get(createRequest(QUrl(url)));
  m_activeReplies.insert(reply, requestId);
  connect(reply, &QNetworkReply::finished, this, [this, reply, requestId, playlistId] {
    m_activeReplies.remove(reply); reply->deleteLater();
    if (reply->error() != QNetworkReply::NoError) { emit requestFailed(requestId, reply->errorString()); return; }
    QJsonArray tracks;
    for (const auto &v : QJsonDocument::fromJson(reply->readAll()).object().value(QStringLiteral("items")).toArray()) tracks.append(normalizeTrack(v.toObject()));
    emit resultReady(requestId, QJsonObject{{QStringLiteral("browseId"), playlistId}, {QStringLiteral("id"), playlistId}, {QStringLiteral("tracks"), tracks}});
  });
}

void TidalProvider::handlePlaylists(quint64 requestId) {
  if (m_userId.isEmpty()) { emit resultReady(requestId, QJsonArray{}); return; }
  const QString cc = m_countryCode.isEmpty() ? QStringLiteral("US") : m_countryCode;
  const QString url = QStringLiteral("%1/users/%2/favorites/playlists?countryCode=%3&limit=50").arg(QLatin1String(kApiV1), m_userId, cc);
  QNetworkReply *reply = m_network.get(createRequest(QUrl(url)));
  m_activeReplies.insert(reply, requestId);
  connect(reply, &QNetworkReply::finished, this, [this, reply, requestId] {
    m_activeReplies.remove(reply); reply->deleteLater();
    if (reply->error() != QNetworkReply::NoError) { emit resultReady(requestId, QJsonArray{}); return; }
    QJsonArray playlists;
    for (const auto &v : QJsonDocument::fromJson(reply->readAll()).object().value(QStringLiteral("items")).toArray()) playlists.append(normalizePlaylist(v.toObject()));
    emit resultReady(requestId, playlists);
  });
}

void TidalProvider::handleLibrary(quint64 requestId) {
  if (m_userId.isEmpty()) {
    QJsonArray s;
    for (const auto &k : {QStringLiteral("artists"), QStringLiteral("albums"), QStringLiteral("songs"), QStringLiteral("playlists")})
      s.append(QJsonObject{{QStringLiteral("key"), k}, {QStringLiteral("items"), QJsonArray{}}});
    emit resultReady(requestId, QJsonObject{{QStringLiteral("sections"), s}});
    return;
  }
  struct State { int left{4}; QJsonArray art, alb, sng, ply; };
  auto st = std::make_shared<State>();
  auto checkDone = [this, requestId, st]() {
    if (--st->left == 0) {
      emit resultReady(requestId, QJsonObject{{QStringLiteral("sections"), QJsonArray{
        QJsonObject{{QStringLiteral("key"), QStringLiteral("artists")}, {QStringLiteral("items"), st->art}},
        QJsonObject{{QStringLiteral("key"), QStringLiteral("albums")}, {QStringLiteral("items"), st->alb}},
        QJsonObject{{QStringLiteral("key"), QStringLiteral("songs")}, {QStringLiteral("items"), st->sng}},
        QJsonObject{{QStringLiteral("key"), QStringLiteral("playlists")}, {QStringLiteral("items"), st->ply}}
      }}});
    }
  };
  const QString cc = m_countryCode.isEmpty() ? QStringLiteral("US") : m_countryCode;
  using NormFn = QJsonObject (*)(const QJsonObject &);
  const std::tuple<QString, QJsonArray *, NormFn> reqs[] = {
    {QStringLiteral("artists"), &st->art, normalizeArtist},
    {QStringLiteral("albums"), &st->alb, normalizeAlbum},
    {QStringLiteral("tracks"), &st->sng, normalizeTrack},
    {QStringLiteral("playlists"), &st->ply, normalizePlaylist}
  };
  for (const auto &[ep, target, norm] : reqs) {
    const QString u = QStringLiteral("%1/users/%2/favorites/%3?countryCode=%4&limit=50").arg(QLatin1String(kApiV1), m_userId, ep, cc);
    QNetworkReply *rep = m_network.get(createRequest(QUrl(u)));
    m_activeReplies.insert(rep, requestId);
    connect(rep, &QNetworkReply::finished, this, [this, rep, target, norm, checkDone] {
      m_activeReplies.remove(rep);
      rep->deleteLater();
      if (rep->error() == QNetworkReply::NoError) {
        const QJsonArray items = QJsonDocument::fromJson(rep->readAll()).object().value(QStringLiteral("items")).toArray();
        for (const auto &v : items) target->append(norm(v.toObject()));
      }
      checkDone();
    });
  }
}

void TidalProvider::handleResolve(quint64 requestId, const QJsonObject &payload) {
  const QJsonObject track = payload.value(QStringLiteral("track")).toObject();
  const QString trackId = track.value(QStringLiteral("id")).toVariant().toString();
  if (trackId.isEmpty()) { emit requestFailed(requestId, QStringLiteral("Missing track ID")); return; }
  const QString cc = m_countryCode.isEmpty() ? QStringLiteral("US") : m_countryCode;
  const QString urlStr = QStringLiteral("%1/tracks/%2/playbackinfopostpaywall?audioquality=LOSSLESS&playbackmode=STREAM&assetpresentation=FULL&countryCode=%3")
      .arg(QLatin1String(kApiV1), trackId, cc);
  QNetworkReply *reply = m_network.get(createRequest(QUrl(urlStr)));
  m_activeReplies.insert(reply, requestId);
  connect(reply, &QNetworkReply::finished, this, [this, reply, requestId, track] {
    m_activeReplies.remove(reply);
    reply->deleteLater();
    if (reply->error() != QNetworkReply::NoError) { emit requestFailed(requestId, reply->errorString()); return; }
    const QJsonObject root = QJsonDocument::fromJson(reply->readAll()).object();
    const QString rawManifest = root.value(QStringLiteral("manifest")).toString();
    const QString mime = root.value(QStringLiteral("manifestMimeType")).toString();
    QString streamUrl, manifestXml;
    if (!rawManifest.isEmpty()) {
      const QByteArray decoded = QByteArray::fromBase64(rawManifest.toUtf8());
      if (mime == QStringLiteral("application/dash+xml") || decoded.startsWith("<?xml")) manifestXml = QString::fromUtf8(decoded);
      else {
        const QJsonArray urls = QJsonDocument::fromJson(decoded).object().value(QStringLiteral("urls")).toArray();
        if (!urls.isEmpty()) streamUrl = urls.at(0).toString();
      }
    }
    if (streamUrl.isEmpty() && manifestXml.isEmpty()) { emit requestFailed(requestId, QStringLiteral("No playback stream URL")); return; }
    const double dur = track.contains(QStringLiteral("durationSeconds")) ? track.value(QStringLiteral("durationSeconds")).toDouble() : track.value(QStringLiteral("duration")).toDouble(0);
    QJsonObject stream{{QStringLiteral("bitrate"), 1411000}, {QStringLiteral("streamCodec"), QStringLiteral("FLAC")}, {QStringLiteral("durationSeconds"), dur}, {QStringLiteral("contentLength"), 0}};
    if (!manifestXml.isEmpty()) {
      stream.insert(QStringLiteral("manifestXml"), manifestXml);
      stream.insert(QStringLiteral("mimeType"), QStringLiteral("application/dash+xml"));
    } else stream.insert(QStringLiteral("url"), streamUrl);
    emit resultReady(requestId, stream);
  });
}
