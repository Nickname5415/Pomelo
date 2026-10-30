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
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Orchard. If not, see <https://www.gnu.org/licenses/>.
 */

#include "apple_music.h"


#include <QCryptographicHash>
#include <QJsonValue>
#include <QLocale>

namespace {
QString sessionKey(const QString &developerToken, const QString &userToken) {
  return QString::fromLatin1(
      QCryptographicHash::hash((developerToken + QLatin1Char('|') + userToken).toUtf8(), QCryptographicHash::Sha256)
          .toHex());
}

QString regionStorefront() {
  const QString code = QLocale::territoryToCode(QLocale::system().territory()).toLower();
  return code.size() == 2 ? code : QStringLiteral("us");
}
} // namespace

void AppleMusic::finishCall(quint64 requestId, const QJsonValue &result, const QString &error) {
  if (auto callback = m_calls.take(requestId))
    callback(result, error);
}

void AppleMusic::configure(std::function<void(const QString &error)> done) {
  if (done) done(QStringLiteral("Apple Music is not supported in Pomelo"));
}

void AppleMusic::call(const QString &, const QJsonObject &, CallReply done, bool) {
  if (done) done({}, QStringLiteral("Apple Music is not supported in Pomelo"));
}

void AppleMusic::home(PageReply done) {
  if (!connected()) {
    done({}, tr("Apple Music is not connected."));
    return;
  }
  call(QStringLiteral("catalog.home"), {},
       [done](const QJsonValue &page, const QString &error) { done(page.toObject(), error); });
}

void AppleMusic::artistPage(const QString &name, PageReply done) {
  if (!connected() || name.trimmed().isEmpty()) {
    done({}, {});
    return;
  }
  call(QStringLiteral("catalog.artist.match"), {{QStringLiteral("name"), name}},
       [this, done](const QJsonValue &match, const QString &error) {
    const QString browseId = match.toObject().value(QStringLiteral("browseId")).toString();
    if (browseId.isEmpty()) {
      done({}, error);
      return;
    }
    detail(browseId, done);
  });
}

void AppleMusic::detail(const QString &browseId, PageReply done) {
  const QString type = browseId.section(QLatin1Char('.'), 1, 1);
  const QString method = type == QStringLiteral("artists")   ? QStringLiteral("catalog.artist")
                         : type == QStringLiteral("albums")  ? QStringLiteral("catalog.album")
                         : type == QStringLiteral("playlists") ? QStringLiteral("catalog.playlist")
                                                              : QString();
  if (method.isEmpty()) {
    done({}, tr("Apple Music cannot open this."));
    return;
  }
  call(method, {{QStringLiteral("browseId"), browseId}},
       [done](const QJsonValue &page, const QString &error) { done(page.toObject(), error); });
}

void AppleMusic::artistView(const QString &browseId, const QString &params, PageReply done) {
  call(QStringLiteral("catalog.artist.view"),
       {{QStringLiteral("browseId"), browseId}, {QStringLiteral("params"), params}},
       [done](const QJsonValue &result, const QString &error) { done(result.toObject(), error); });
}

void AppleMusic::search(const QString &query, const QString &filter, PageReply done) {
  if (!connected()) {
    done({}, tr("Connect Apple Music in Settings to search it."));
    return;
  }
  call(QStringLiteral("catalog.search"), {{QStringLiteral("query"), query}, {QStringLiteral("filter"), filter}},
       [done](const QJsonValue &result, const QString &error) { done(result.toObject(), error); });
}

void AppleMusic::artwork(const QJsonObject &query, PageReply done) {
  if (!connected()) {
    done({}, {});
    return;
  }
  call(QStringLiteral("catalog.artwork"), query,
       [done](const QJsonValue &result, const QString &error) { done(result.toObject(), error); });
}

void AppleMusic::library(PageReply done) {
  if (!connected()) {
    done({}, tr("Connect Apple Music in Settings to see your library."));
    return;
  }
  call(QStringLiteral("library"), {},
       [done](const QJsonValue &result, const QString &error) { done(result.toObject(), error); });
}

void AppleMusic::libraryPlaylists(PageReply done) {
  if (!connected()) {
    done({}, tr("Connect Apple Music in Settings to see your playlists."));
    return;
  }
  call(QStringLiteral("library.playlists"), {},
       [done](const QJsonValue &result, const QString &error) { done(result.toObject(), error); });
}

void AppleMusic::lyrics(const QJsonObject &track, PageReply done) {
  if (!connected()) {
    done({}, {});
    return;
  }
  call(QStringLiteral("lyrics"), track,
       [done](const QJsonValue &result, const QString &error) { done(result.toObject(), error); });
}

void AppleMusic::libraryAction(const QString &method, const QJsonObject &payload, PageReply done) {
  if (!connected()) {
    done({}, tr("Connect Apple Music in Settings first."));
    return;
  }
  call(method, payload, [done](const QJsonValue &result, const QString &error) { done(result.toObject(), error); });
}
