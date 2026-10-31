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

#include "auth_manager.h"
#include "providers/youtube/youtube_provider.h"

#include <QCryptographicHash>
#include <QDateTime>
#include <QDebug>
#include <QJsonDocument>
#include <QJsonObject>
#include <QStringList>

void AuthManager::failProfileResolution(const QString &message) {
  signOut();
  setErrorMessage(message);
}

void AuthManager::submitPageAccount(const QString &token,
                                    const QString &responseJson) {
  if (!m_authWebViewActive || !m_profileLoading ||
      token != QString::number(m_profileGeneration))
    return;
  const QJsonDocument document = QJsonDocument::fromJson(responseJson.toUtf8());
  const QJsonObject response = document.object();
  const int status = response.value(QStringLiteral("status")).toInt();
  if (!document.isObject() || status != 200 ||
      !response.value(QStringLiteral("data")).isObject()) {
    QString reason;
    if (!document.isObject())
      reason = QStringLiteral("invalid browser response");
    else if (response.value(QStringLiteral("failure")).toString() ==
             QStringLiteral("timeout"))
      reason = QStringLiteral("request timed out");
    else if (status == 0)
      reason = QStringLiteral("network request failed");
    else if (status != 200) {
      reason = QStringLiteral("HTTP %1").arg(status);
      const QString apiStatus = response.value(QStringLiteral("data"))
                                    .toObject()
                                    .value(QStringLiteral("error"))
                                    .toObject()
                                    .value(QStringLiteral("status"))
                                    .toString();
      const QStringList knownStatuses = {QStringLiteral("INVALID_ARGUMENT"),
                                         QStringLiteral("FAILED_PRECONDITION"),
                                         QStringLiteral("UNAUTHENTICATED"),
                                         QStringLiteral("PERMISSION_DENIED"),
                                         QStringLiteral("RESOURCE_EXHAUSTED")};
      if (knownStatuses.contains(apiStatus))
        reason += QStringLiteral(": %1").arg(apiStatus);
    } else
      reason = QStringLiteral("invalid account response");
    qWarning().noquote() << "YouTube account verification failed:" << reason;
    failProfileResolution(
        QStringLiteral(
            "YouTube could not verify your account (%1). Please try again.")
            .arg(reason));
    return;
  }
  m_profileRequestId = m_youtubeProvider->invoke(
      QStringLiteral("account.parse"), response.value(QStringLiteral("data")));
}

void AuthManager::submitPageProfile(const QString &profileJson) {
  if (!m_profileLoading || !m_authWebViewActive)
    return;
  const QJsonDocument document = QJsonDocument::fromJson(profileJson.toUtf8());
  if (!document.isObject())
    return;
  mergeProfile(document.object());
  if (hasCompleteProfile()) {
    finishProfileResolution();
    return;
  }
  if (m_switchMode)
    return;
  const QJsonObject profile = m_pendingProfile;
  if (!profile.value(QStringLiteral("channelId")).toString().isEmpty() ||
      !profile.value(QStringLiteral("channelUrl")).toString().isEmpty()) {
    m_profileRequestId = m_youtubeProvider->enrichAccountProfile(profile);
  }
}

QString AuthManager::computeAuthorizationHeader(const QString &cookie,
                                                const QString &origin) const {
  const QMap<QString, QString> cookies = parseCookieString(cookie);
  QString sapisid = cookies.value(QStringLiteral("SAPISID"));
  if (sapisid.isEmpty()) {
    sapisid = cookies.value(QStringLiteral("__Secure-3PAPISID"));
  }
  if (sapisid.isEmpty()) {
    sapisid = cookies.value(QStringLiteral("APISID"));
  }

  if (sapisid.isEmpty()) {
    return QString();
  }

  const qint64 epochSeconds = QDateTime::currentSecsSinceEpoch();
  QStringList signedParts;

  const auto signCookie = [&](const QString &scheme, const QString &val) {
    const QString source =
        QStringLiteral("%1 %2 %3").arg(epochSeconds).arg(val, origin);
    const QByteArray hash =
        QCryptographicHash::hash(source.toUtf8(), QCryptographicHash::Sha1)
            .toHex();
    return QStringLiteral("%1 %2_%3")
        .arg(scheme)
        .arg(epochSeconds)
        .arg(QString::fromLatin1(hash));
  };

  signedParts.append(signCookie(QStringLiteral("SAPISIDHASH"), sapisid));

  if (cookies.contains(QStringLiteral("__Secure-1PAPISID"))) {
    signedParts.append(
        signCookie(QStringLiteral("SAPISID1PHASH"),
                   cookies.value(QStringLiteral("__Secure-1PAPISID"))));
  }
  if (cookies.contains(QStringLiteral("__Secure-3PAPISID"))) {
    signedParts.append(
        signCookie(QStringLiteral("SAPISID3PHASH"),
                   cookies.value(QStringLiteral("__Secure-3PAPISID"))));
  }

  return signedParts.join(QLatin1Char(' '));
}

bool AuthManager::hasYouTubeLoginCookie(const QString &cookie) {
  const QMap<QString, QString> cookies = parseCookieString(cookie);
  return cookies.contains(QStringLiteral("SAPISID")) ||
         cookies.contains(QStringLiteral("__Secure-3PAPISID")) ||
         cookies.contains(QStringLiteral("__Secure-1PAPISID")) ||
         cookies.contains(QStringLiteral("APISID"));
}

bool AuthManager::hasYouTubeSessionCookies(const QString &cookie) {
  const auto cookies = parseCookieString(cookie);
  return (!cookies.value(QStringLiteral("SAPISID")).isEmpty() ||
          !cookies.value(QStringLiteral("__Secure-3PAPISID")).isEmpty()) &&
         (!cookies.value(QStringLiteral("SID")).isEmpty() ||
          !cookies.value(QStringLiteral("__Secure-1PSID")).isEmpty() ||
          !cookies.value(QStringLiteral("__Secure-3PSID")).isEmpty());
}

QString AuthManager::normalizeYouTubeAuthCookie(const QString &cookie) {
  const QString trimmed = cookie.trimmed();
  const QMap<QString, QString> cookies = parseCookieString(trimmed);
  if (trimmed.isEmpty() || cookies.contains(QStringLiteral("SAPISID")) ||
      !cookies.contains(QStringLiteral("__Secure-3PAPISID"))) {
    return trimmed;
  }
  return QStringLiteral("%1; SAPISID=%2")
      .arg(trimmed, cookies.value(QStringLiteral("__Secure-3PAPISID")));
}

QMap<QString, QString> AuthManager::parseCookieString(const QString &cookie) {
  QMap<QString, QString> result;
  const QStringList parts = cookie.split(QLatin1Char(';'), Qt::SkipEmptyParts);
  for (const QString &rawPart : parts) {
    const QString part = rawPart.trimmed();
    const int separatorIndex = part.indexOf(QLatin1Char('='));
    if (separatorIndex <= 0) {
      continue;
    }
    const QString name = part.left(separatorIndex).trimmed();
    const QString value = part.mid(separatorIndex + 1).trimmed();
    if (!name.isEmpty()) {
      result.insert(name, value);
    }
  }
  return result;
}
