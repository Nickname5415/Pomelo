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
#include "auth_diagnostics.h"
#include "browser_cookie_adapter.h"
#include "providers/youtube/youtube_provider.h"

#include <QDebug>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QSettings>
#include <QTimer>

#include <qtkeychain/keychain.h>

namespace {
constexpr auto keychainService = "dev.sfg.orchard";
constexpr auto keychainEntry = "youtube-session";
} // namespace

void AuthManager::setStatus(const QString &status) {
  if (m_status != status) {
    authDiagnostic(QStringLiteral("status %1 -> %2").arg(m_status, status));
    m_status = status;
    if (status == QStringLiteral("signed_in"))
      scheduleCookieRefresh();
    else if (status == QStringLiteral("signed_out"))
      stopCookieRefresh();
    emit statusChanged();
  }
}

void AuthManager::setErrorMessage(const QString &error) {
  if (m_errorMessage != error) {
    authDiagnostic(error.isEmpty() ? QStringLiteral("error-cleared")
                                   : QStringLiteral("error-set"));
    m_errorMessage = error;
    emit errorChanged();
  }
}

void AuthManager::restoreSession() {
  if (m_youtubeProvider && m_youtubeProvider->isAuthenticated()) {
    m_userName = QStringLiteral("Pomelo User");
    m_userHandle = QStringLiteral("@pomelo");
    m_userEmail = QStringLiteral("user@stuffbyren.xyz");
    m_status = QStringLiteral("signed_in");
    emit statusChanged();
    emit userChanged();
    emit sessionChanged();
    emit loginCompleted();
    return;
  }
  connect(m_youtubeProvider, &TidalProvider::sessionUpdated, this, [this] {
    if (m_youtubeProvider->isAuthenticated()) {
      m_userName = QStringLiteral("Pomelo User");
      m_userHandle = QStringLiteral("@pomelo");
      m_userEmail = QStringLiteral("user@stuffbyren.xyz");
      m_status = QStringLiteral("signed_in");
      emit statusChanged();
      emit userChanged();
      emit sessionChanged();
      emit loginCompleted();
    }
  });

  QSettings settings;
  settings.beginGroup(QStringLiteral("auth"));
  const QString legacyCookie = settings.value(QStringLiteral("cookie")).toString();
  const QString legacyVisitorData = settings.value(QStringLiteral("visitorData")).toString();
  const QString legacyDataSyncId = settings.value(QStringLiteral("dataSyncId")).toString();
  const int legacyAccountIndex = settings.value(QStringLiteral("accountIndex"), 0).toInt();
  const QString legacyClientVersion = settings.value(QStringLiteral("clientVersion")).toString();
  const QString legacyUserName = settings.value(QStringLiteral("userName")).toString();
  const QString legacyUserEmail = settings.value(QStringLiteral("userEmail")).toString();
  const QString legacyUserAvatar = settings.value(QStringLiteral("userAvatar")).toString();
  const QString legacyUserHandle = settings.value(QStringLiteral("userHandle")).toString();
  settings.remove(QStringLiteral("cookie"));
  settings.remove(QStringLiteral("visitorData"));
  settings.remove(QStringLiteral("dataSyncId"));
  settings.remove(QStringLiteral("accountIndex"));
  settings.remove(QStringLiteral("clientVersion"));
  settings.remove(QStringLiteral("userName"));
  settings.remove(QStringLiteral("userEmail"));
  settings.remove(QStringLiteral("userAvatar"));
  settings.remove(QStringLiteral("userHandle"));
  settings.endGroup();
  settings.sync();

  if (!legacyCookie.isEmpty() && hasYouTubeLoginCookie(legacyCookie)) {
    m_cookie = legacyCookie;
    m_visitorData = legacyVisitorData;
    m_dataSyncId = legacyDataSyncId;
    m_accountIndex = qMax(0, legacyAccountIndex);
    m_clientVersion = legacyClientVersion;
    m_userName = legacyUserName;
    m_userEmail = legacyUserEmail;
    m_userAvatar = legacyUserAvatar;
    m_userHandle = legacyUserHandle;
    m_status = QStringLiteral("signed_in");
    persistSecretSession();
    return;
  }

  restoreSecretSession();
}

void AuthManager::restoreSecretSession() {
  m_profileLoading = true;
  m_status = QStringLiteral("restoring");
  const quint64 generation = ++m_profileGeneration;
  auto *job = new QKeychain::ReadPasswordJob(QString::fromLatin1(keychainService), this);
  job->setKey(QString::fromLatin1(keychainEntry));
  job->setInsecureFallback(false);
  connect(job, &QKeychain::Job::finished, this, [this, job, generation] {
    std::unique_ptr<QKeychain::ReadPasswordJob> readJob(job);
    if (generation != m_profileGeneration)
      return;
    m_profileLoading = false;
    if (readJob->error() == QKeychain::NoError) {
      const QJsonDocument doc = QJsonDocument::fromJson(readJob->textData().toUtf8());
      if (doc.isObject()) {
        const QJsonObject secret = doc.object();
        m_cookie = secret.value(QStringLiteral("cookie")).toString();
        m_visitorData = secret.value(QStringLiteral("visitorData")).toString();
        m_dataSyncId = secret.value(QStringLiteral("dataSyncId")).toString();
        m_accountIndex = qMax(0, secret.value(QStringLiteral("accountIndex")).toInt());
        m_clientVersion = secret.value(QStringLiteral("clientVersion")).toString();
        m_userName = secret.value(QStringLiteral("userName")).toString();
        m_userEmail = secret.value(QStringLiteral("userEmail")).toString();
        m_userAvatar = secret.value(QStringLiteral("userAvatar")).toString();
        m_userHandle = secret.value(QStringLiteral("userHandle")).toString();
        setStatus(QStringLiteral("signed_in"));
        emit sessionChanged();
        if (m_userName.isEmpty() || m_userHandle.isEmpty() || m_userAvatar.isEmpty()) {
          startProfileResolution();
        }
        return;
      }
    } else if (readJob->error() != QKeychain::EntryNotFound) {
      qWarning().noquote() << "Could not read session from keychain:" << readJob->errorString();
    }
    setStatus(QStringLiteral("signed_out"));
  });
  job->start();
}

void AuthManager::persistSecretSession() {
  if (m_embeddedWebAuth || !hasYouTubeLoginCookie(m_cookie))
    return;
  const QJsonObject secret{
      {QStringLiteral("cookie"), m_cookie},
      {QStringLiteral("visitorData"), m_visitorData},
      {QStringLiteral("dataSyncId"), m_dataSyncId},
      {QStringLiteral("accountIndex"), m_accountIndex},
      {QStringLiteral("clientVersion"), m_clientVersion},
      {QStringLiteral("userName"), m_userName},
      {QStringLiteral("userEmail"), m_userEmail},
      {QStringLiteral("userAvatar"), m_userAvatar},
      {QStringLiteral("userHandle"), m_userHandle}};
  auto *job = new QKeychain::WritePasswordJob(QString::fromLatin1(keychainService), this);
  job->setKey(QString::fromLatin1(keychainEntry));
  job->setTextData(QString::fromUtf8(QJsonDocument(secret).toJson(QJsonDocument::Compact)));
  job->setInsecureFallback(false);
  connect(job, &QKeychain::Job::finished, this, [](QKeychain::Job *finishedJob) {
    if (finishedJob->error() != QKeychain::NoError) {
      qWarning().noquote() << "Could not store session in keychain:" << finishedJob->errorString();
    }
  });
  job->start();
}

void AuthManager::deleteSecretSession() {
  if (m_embeddedWebAuth)
    return;
  auto *job = new QKeychain::DeletePasswordJob(QString::fromLatin1(keychainService), this);
  job->setKey(QString::fromLatin1(keychainEntry));
  job->setInsecureFallback(false);
  connect(job, &QKeychain::Job::finished, this, [](QKeychain::Job *finishedJob) {
    if (finishedJob->error() != QKeychain::NoError && finishedJob->error() != QKeychain::EntryNotFound) {
      qWarning().noquote() << "Could not remove session from keychain:" << finishedJob->errorString();
    }
  });
  job->start();
}

void AuthManager::startProfileResolution() {
  const auto cookies = parseCookieString(m_cookie);
  if (!m_authWebViewActive && !cookies.contains(QStringLiteral("SID")) &&
      !cookies.contains(QStringLiteral("__Secure-3PSID")) &&
      !cookies.contains(QStringLiteral("__Secure-1PSID"))) {
    m_profileLoading = false;
    startLogin();
    return;
  }
  ++m_profileGeneration;
  m_profileLoading = true;
  m_pendingProfile = {};
  setStatus(QStringLiteral("resolving_profile"));
  emit sessionChanged();
  emit userChanged();
  m_profileRequestId = 0;
  if (m_authWebViewActive && m_switchMode) {
    const quint64 generation = m_profileGeneration;
    emit profileProbeRequested();
    QTimer::singleShot(8500, this, [this, generation] {
      if (m_profileLoading && generation == m_profileGeneration)
        finishProfileResolution();
    });
    return;
  }
#ifdef ORCHARD_NATIVE_COOKIE_AUTH
  if (m_authWebViewActive && !m_switchMode) {
    const quint64 generation = m_profileGeneration;
    emit profileProbeRequested();
    QTimer::singleShot(5000, this, [this, generation] {
      if (m_profileLoading && generation == m_profileGeneration)
        finishProfileResolution();
    });
    return;
  }
#endif
  if (m_authWebViewActive) {
    const QString token = QString::number(m_profileGeneration);
    emit pageAccountRequested(token, computeAuthorizationHeader(m_cookie));
    QTimer::singleShot(20000, this, [this, token] {
      if (m_profileLoading && token == QString::number(m_profileGeneration))
        failProfileResolution(QStringLiteral("Account verification timed out."));
    });
  } else {
    const QJsonObject publicChannel = publicChannelProfile();
    m_profileRequestId = publicChannel.isEmpty()
                             ? m_youtubeProvider->loadAccountProfile(sessionObject())
                             : m_youtubeProvider->enrichAccountProfile(publicChannel);
  }
}

void AuthManager::finishProfileResolution() {
  if (!m_profileLoading)
    return;
  authDiagnostic(QStringLiteral("profile-complete"));
  m_profileLoading = false;
  m_authWebViewActive = false;
  m_profileRequestId = 0;
  const QString resolvedName = m_pendingProfile.value(QStringLiteral("name")).toString().trimmed();
  if (!resolvedName.isEmpty() &&
      resolvedName.compare(QStringLiteral("Signed in"), Qt::CaseInsensitive) != 0 &&
      resolvedName.compare(QStringLiteral("YouTube Music"), Qt::CaseInsensitive) != 0) {
    m_userName = resolvedName;
  }
  QString resolvedHandle = m_pendingProfile.value(QStringLiteral("handle")).toString().trimmed();
  if (!resolvedHandle.isEmpty() && !resolvedHandle.startsWith(QLatin1Char('@')))
    resolvedHandle.prepend(QLatin1Char('@'));
  if (!resolvedHandle.isEmpty())
    m_userHandle = resolvedHandle;
  const QString resolvedAvatar = m_pendingProfile.value(QStringLiteral("avatarUrl")).toString().trimmed();
  if (!resolvedAvatar.isEmpty())
    m_userAvatar = resolvedAvatar;
  persistSecretSession();
  setStatus(QStringLiteral("signed_in"));
  emit userChanged();
  authDiagnostic(QStringLiteral("login-completed-signal"));
  emit loginCompleted();
}

void AuthManager::mergeProfile(const QJsonObject &profile) {
  const auto merge = [this, &profile](const QString &targetKey, const QStringList &sourceKeys) {
    const QString current = m_pendingProfile.value(targetKey).toString().trimmed();
    const bool placeholder =
        current.compare(QStringLiteral("Signed in"), Qt::CaseInsensitive) == 0 ||
        current.compare(QStringLiteral("YouTube Music"), Qt::CaseInsensitive) == 0 ||
        current.contains(QStringLiteral("subscriber"), Qt::CaseInsensitive);
    if (!current.isEmpty() && !placeholder)
      return;
    for (const QString &sourceKey : sourceKeys) {
      const QString value = profile.value(sourceKey).toString().trimmed();
      if (!value.isEmpty()) {
        m_pendingProfile.insert(targetKey, value);
        return;
      }
    }
  };
  merge(QStringLiteral("name"), {QStringLiteral("name")});
  merge(QStringLiteral("handle"), {QStringLiteral("handle"), QStringLiteral("byline")});
  merge(QStringLiteral("avatarUrl"), {QStringLiteral("avatarUrl"), QStringLiteral("avatar"), QStringLiteral("thumbnail")});
  merge(QStringLiteral("channelId"), {QStringLiteral("channelId")});
  merge(QStringLiteral("channelUrl"), {QStringLiteral("channelUrl")});
}

bool AuthManager::hasCompleteProfile() const {
  const QString name = m_pendingProfile.value(QStringLiteral("name")).toString().trimmed();
  const QString handle = m_pendingProfile.value(QStringLiteral("handle")).toString().trimmed();
  const QString avatar = m_pendingProfile.value(QStringLiteral("avatarUrl")).toString().trimmed();
  return !name.isEmpty() && !handle.isEmpty() && !avatar.isEmpty() &&
         name.compare(QStringLiteral("Signed in"), Qt::CaseInsensitive) != 0 &&
         name.compare(QStringLiteral("YouTube Music"), Qt::CaseInsensitive) != 0;
}

QJsonObject AuthManager::sessionObject() const {
  return {{QStringLiteral("cookie"), m_cookie},
          {QStringLiteral("visitorData"), m_visitorData},
          {QStringLiteral("dataSyncId"), m_dataSyncId},
          {QStringLiteral("clientVersion"), m_clientVersion},
          {QStringLiteral("accountIndex"), m_accountIndex}};
}

QString AuthManager::delegatedSessionIdFromPageAuth(const QString &dataSyncId,
                                                   const QString &delegatedSessionId) {
  const QString explicitId = delegatedSessionId.trimmed();
  if (!explicitId.isEmpty())
    return explicitId;
  const QString normalized = dataSyncId.trimmed();
  const int separatorIndex = normalized.indexOf(QStringLiteral("||"));
  if (separatorIndex <= 0 || normalized.mid(separatorIndex + 2).isEmpty())
    return QString();
  return normalized.left(separatorIndex);
}

void AuthManager::refreshSession() {
  if (m_embeddedWebAuth || m_authWebViewActive || m_profileLoading)
    return;
  if (!isSignedIn() || m_cookie.isEmpty())
    return;

  const QString fresh = normalizeYouTubeAuthCookie(m_browserCookies->cookies());
  if (!fresh.isEmpty() && fresh != m_cookie) {
    const auto freshParsed = parseCookieString(fresh);
    const auto savedParsed = parseCookieString(m_cookie);
    if (!freshParsed.value(QStringLiteral("SAPISID")).isEmpty() &&
        freshParsed.value(QStringLiteral("SAPISID")) ==
            savedParsed.value(QStringLiteral("SAPISID"))) {
      m_cookie = fresh;
      persistSecretSession();
      emit sessionChanged();
    }
  }

  if (!hasYouTubeLoginCookie(m_cookie)) {
    authDiagnostic(QStringLiteral("cookie-refresh-session-expired"));
    setStatus(QStringLiteral("signed_out"));
    setErrorMessage(QStringLiteral("Session expired. Please sign in again."));
    emit cookieRefreshCompleted(false);
    return;
  }

  emit cookieRefreshCompleted(true);
}

void AuthManager::scheduleCookieRefresh() {
  if (m_embeddedWebAuth)
    return;
  constexpr int refreshIntervalMs = 15 * 60 * 1000;
  m_cookieRefreshTimer.start(refreshIntervalMs);
}

void AuthManager::stopCookieRefresh() { m_cookieRefreshTimer.stop(); }
