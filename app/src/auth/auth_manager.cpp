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

#include "auth_manager.h"
#include "auth_diagnostics.h"
#include "auth_session_channel.h"
#include "browser_cookie_adapter.h"
#include "providers/youtube/youtube_provider.h"

#include <QCoreApplication>
#include <QCryptographicHash>
#include <QDateTime>
#include <QDebug>
#include <QDir>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QTimer>
#include <QUrl>

// I don't feel like messing with sqlite, and this is an interesting project
// Credit to https://github.com/frankosterfeld/qtkeychain/
#include <qtkeychain/keychain.h>

namespace {
constexpr auto keychainService = "dev.sfg.orchard";
constexpr auto keychainEntry = "youtube-session";
} // namespace

AuthManager::AuthManager(YouTubeProvider *youtubeProvider, bool embeddedWebAuth,
                         QObject *parent)
    : QObject(parent), m_youtubeProvider(youtubeProvider),
      m_browserCookies(new BrowserCookieAdapter(this)),
      m_embeddedWebAuth(embeddedWebAuth) {
  connect(m_browserCookies, &BrowserCookieAdapter::errorOccurred, this,
          [this](const QString &message) {
            if (m_authWebViewActive)
              failProfileResolution(message);
          });
  connect(m_browserCookies, &BrowserCookieAdapter::cookiesChanged, this,
          [this] {
            const QString complete =
                normalizeYouTubeAuthCookie(m_browserCookies->cookies());
            const auto browser = parseCookieString(complete);
            const auto saved = parseCookieString(m_cookie);
            // Only repair the same account's session; switching accounts stays
            // in login.
            if (m_cookie.isEmpty() ||
                browser.value(QStringLiteral("SAPISID")).isEmpty() ||
                browser.value(QStringLiteral("SAPISID")) !=
                    saved.value(QStringLiteral("SAPISID")) ||
                complete == m_cookie)
              return;
            m_cookie = complete;
            if (isSignedIn())
              persistSecretSession();
            emit sessionChanged();
          });
  connect(m_youtubeProvider, &YouTubeProvider::resultReady, this,
          [this](quint64 requestId, const QJsonValue &result) {
            if (m_switchValidationRequestId && requestId == m_switchValidationRequestId) {
              finishSwitchedAccount();
              return;
            }
            if (m_switchProfileRequestId && requestId == m_switchProfileRequestId) {
              receiveSwitchedProfile(result.toObject());
              return;
            }
            if (!m_profileLoading || requestId != m_profileRequestId ||
                !result.isObject())
              return;
            mergeProfile(result.toObject());
            if (hasCompleteProfile() || !m_authWebViewActive) {
              finishProfileResolution();
              return;
            }
            const quint64 generation = m_profileGeneration;
            emit profileProbeRequested();
            QTimer::singleShot(5000, this, [this, generation] {
              if (m_profileLoading && generation == m_profileGeneration)
                finishProfileResolution();
            });
          });
  connect(m_youtubeProvider, &YouTubeProvider::requestFailed, this,
          [this](quint64 requestId, const QString &message) {
            if (m_switchValidationRequestId && requestId == m_switchValidationRequestId) {
              failSwitchedAccount(tr("Could not use the selected YouTube channel: %1").arg(message));
              return;
            }
            if (m_switchProfileRequestId && requestId == m_switchProfileRequestId) {
              m_switchProfileRequestId = 0;
              return;
            }
            if (!m_profileLoading || requestId != m_profileRequestId)
              return;
            qWarning().noquote() << "YouTube profile lookup failed:" << message;
#ifdef ORCHARD_NATIVE_COOKIE_AUTH
            if (hasYouTubeSessionCookies(m_cookie)) {
              // profile metadata is best effort once the browser's
              // native cookie session has been captured.
              finishProfileResolution();
              return;
            }
#endif
            if (!m_authWebViewActive) {
              // QtWebView's document.cookie omits HttpOnly credentials. Let
              // the browser restore and validate its own complete session.
              m_profileLoading = false;
              m_profileRequestId = 0;
              startLogin();
              return;
            }
            failProfileResolution(message);
          });
  // Cookie refresh timer, the v4 equivalent of v2's refreshBrowserAuth(). (get
  // it? becuase qml uses a v4 engine im so hilarious)
  connect(&m_cookieRefreshTimer, &QTimer::timeout, this,
          &AuthManager::refreshSession);
  // The helper starts browser authentication explicitly. Restoring the keychain
  // here can race startLogin() and enter signed_in without loginCompleted.
  if (!m_embeddedWebAuth)
    restoreSession();
  if (m_status == QStringLiteral("signed_in") &&
      (m_userName.isEmpty() || m_userHandle.isEmpty() ||
       m_userAvatar.isEmpty())) {
    QTimer::singleShot(0, this, [this] {
      if (m_status == QStringLiteral("signed_in"))
        startProfileResolution();
    });
  }
  // If we restored a signed-in session, kick off the refresh timer and do
  // one immediate check once the event loop is running.
  if (isSignedIn() && !m_embeddedWebAuth && (!m_youtubeProvider || !m_youtubeProvider->isAuthenticated())) {
    scheduleCookieRefresh();
    QTimer::singleShot(0, this, &AuthManager::refreshSession);
  }
}

QString AuthManager::loginUrl() const {
  return QStringLiteral(
      "https://accounts.google.com/"
      "ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com");
}

QString AuthManager::channelSwitcherUrl() const {
  return QStringLiteral("https://www.youtube.com/channel_switcher");
}

void AuthManager::startLogin() {
  if (m_youtubeProvider && m_youtubeProvider->isAuthenticated()) {
    m_userName = QStringLiteral("Pomelo User");
    m_userHandle = QStringLiteral("@pomelo");
    m_userEmail = QStringLiteral("user@stuffbyren.xyz");
    m_userAvatar = QStringLiteral("qrc:/qt/qml/Orchard/app/qml/assets/orchard-logo.png");
    setStatus(QStringLiteral("signed_in"));
    emit userChanged();
    emit sessionChanged();
    emit loginCompleted();
    return;
  }
  authDiagnostic(
      QStringLiteral("login-request embedded=%1").arg(m_embeddedWebAuth));
  setErrorMessage(QString());
  if (!m_embeddedWebAuth) {
    launchHelper(false);
    return;
  }
  ++m_profileGeneration;
  m_profileLoading = false;
  m_authCaptureState = -1;
  m_browserCookies->start();
  if (!m_authWebViewActive) {
    m_authWebViewActive = true;
    emit statusChanged();
  }
  setStatus(QStringLiteral("starting"));
}

void AuthManager::acceptHelperResult(const QJsonObject &result) {
  const QString cookie = normalizeYouTubeAuthCookie(
      result.value(QStringLiteral("cookie")).toString());
  if (!hasYouTubeLoginCookie(cookie)) {
    setStatus(QStringLiteral("signed_out"));
    setErrorMessage(
        QStringLiteral("The sign-in window returned an invalid session."));
    return;
  }
  ++m_profileGeneration;
  m_profileLoading = false;
  authDiagnostic(QStringLiteral("helper-session-accepted"));
  m_cookie = cookie;
  m_visitorData = result.value(QStringLiteral("visitorData")).toString();
  m_dataSyncId = result.value(QStringLiteral("dataSyncId")).toString();
  m_clientVersion = result.value(QStringLiteral("clientVersion")).toString();
  m_accountIndex =
      qMax(0, result.value(QStringLiteral("accountIndex")).toInt());
  m_userName = result.value(QStringLiteral("userName")).toString();
  m_userEmail = result.value(QStringLiteral("userEmail")).toString();
  m_userAvatar = result.value(QStringLiteral("userAvatar")).toString();
  m_userHandle = result.value(QStringLiteral("userHandle")).toString();
  m_switchProfileRequestId = 0;
  persistSecretSession();
  setStatus(QStringLiteral("signed_in"));
  emit sessionChanged();
  emit userChanged();
  emit loginCompleted();
  requestMissingProfile();
}

QJsonObject AuthManager::publicChannelProfile() const {
  QJsonObject publicChannel;
  if (m_dataSyncId.startsWith(QStringLiteral("UC")) && m_dataSyncId.size() == 24) {
    publicChannel.insert(QStringLiteral("channelId"), m_dataSyncId);
  } else if (m_userHandle.startsWith(QLatin1Char('@'))) {
    // A handle gives the public page a second chance when accounts_list sulks.
    const QString encoded = QString::fromLatin1(
        QUrl::toPercentEncoding(m_userHandle, QByteArray("@")));
    publicChannel.insert(QStringLiteral("channelUrl"),
                         QStringLiteral("https://www.youtube.com/%1").arg(encoded));
  }
  return publicChannel;
}

void AuthManager::requestMissingProfile() {
  if (!isSignedIn() || (!m_userName.isEmpty() && !m_userAvatar.isEmpty()))
    return;
  const QJsonObject publicChannel = publicChannelProfile();
  m_switchProfileRequestId = publicChannel.isEmpty()
                                 ? m_youtubeProvider->loadAccountProfile(sessionObject())
                                 : m_youtubeProvider->enrichAccountProfile(publicChannel);
}

void AuthManager::cancelLogin() {
  authDiagnostic(QStringLiteral("login-cancelled"));
  if (!m_embeddedWebAuth && m_authHelper &&
      m_authHelper->state() != QProcess::NotRunning) {
    m_authSessionServer->close();
    m_authHelper->terminate();
    setStatus(QStringLiteral("signed_out"));
    return;
  }
  if (m_authWebViewActive &&
      (m_status == QStringLiteral("starting") || m_profileLoading)) {
    signOut();
  }
}

void AuthManager::signOut() {
  authDiagnostic(QStringLiteral("sign-out"));
  m_pendingSwitch = {};
  m_switchValidationRequestId = 0;
  m_switchProfileRequestId = 0;
  m_switchingAccount = false;
  m_cookie.clear();
  m_visitorData.clear();
  m_dataSyncId.clear();
  m_accountIndex = 0;
  m_userName.clear();
  m_userEmail.clear();
  m_userAvatar.clear();
  m_userHandle.clear();
  m_clientVersion.clear();
  m_pendingProfile = {};
  m_profileLoading = false;
  m_authWebViewActive = false;
  m_profileRequestId = 0;
  ++m_profileGeneration;
  m_youtubeProvider->cancelAll();
  deleteSecretSession();
  setErrorMessage(QString());

  QSettings settings;
  settings.beginGroup(QStringLiteral("auth"));
  settings.remove(QString());
  settings.endGroup();

  setStatus(QStringLiteral("signed_out"));
  emit sessionChanged();
  emit userChanged();
}

bool AuthManager::setSession(const QString &cookie, const QString &visitorData,
                             const QString &dataSyncId) {
  const QString normalized = normalizeYouTubeAuthCookie(cookie.trimmed());
  if (!hasYouTubeLoginCookie(normalized)) {
    setErrorMessage(
        QStringLiteral("The provided cookie does not contain required YouTube "
                       "credentials (SAPISID or __Secure-3PAPISID)."));
    return false;
  }

  m_cookie = normalized;
  if (!visitorData.isEmpty()) {
    m_visitorData = visitorData.trimmed();
  }
  if (!dataSyncId.isEmpty()) {
    m_dataSyncId = dataSyncId.trimmed();
  }
  setErrorMessage(QString());
  m_authWebViewActive = false;
  startProfileResolution();
  return true;
}

bool AuthManager::handlePageAuth(const QString &authJson) {
  if (!m_authWebViewActive)
    return false;
  QJsonParseError parseError;
  const QJsonDocument doc =
      QJsonDocument::fromJson(authJson.toUtf8(), &parseError);
  if (m_authCaptureState == -1) {
    authDiagnostic(QStringLiteral("page-auth-received valid-json=%1")
                       .arg(parseError.error == QJsonParseError::NoError &&
                            doc.isObject()));
    m_authCaptureState = -2;
  }
  if (parseError.error != QJsonParseError::NoError || !doc.isObject()) {
    return false;
  }

  const QJsonObject obj = doc.object();
  const QString previousCookie = m_cookie;
  const QString previousDataSyncId = m_dataSyncId;
  const int previousAccountIndex = m_accountIndex;
  const QString cookie = obj.value(QStringLiteral("cookie")).toString();
  const QString visitorData =
      obj.value(QStringLiteral("visitorData")).toString();
  const QString delegatedSessionId =
      obj.value(QStringLiteral("delegatedSessionId")).toString();
  const QString dataSyncId = obj.value(QStringLiteral("dataSyncId")).toString();
  const QJsonValue accountIndexValue =
      obj.value(QStringLiteral("accountIndex"));
  const QString name = obj.value(QStringLiteral("name")).toString();
  const QString email = obj.value(QStringLiteral("email")).toString();
  const QString avatar = obj.value(QStringLiteral("avatar")).toString();
  const QString handle = obj.value(QStringLiteral("handle")).toString();
  const QString clientVersion =
      obj.value(QStringLiteral("clientVersion")).toString();

  if (!name.isEmpty()) {
    m_userName = name;
  }
  if (!email.isEmpty()) {
    m_userEmail = email;
  }
  if (!avatar.isEmpty()) {
    m_userAvatar = avatar;
  }
  if (!handle.isEmpty()) {
    m_userHandle = handle.startsWith(QLatin1Char('@'))
                       ? handle
                       : QStringLiteral("@%1").arg(handle);
  }
  // Only YouTube Music's own version suits the WEB_REMIX requests that follow.
  if (!clientVersion.isEmpty() &&
      obj.value(QStringLiteral("origin")).toString() == QStringLiteral("https://music.youtube.com")) {
    m_clientVersion = clientVersion;
  }

  const QString computedDelegatedId =
      delegatedSessionIdFromPageAuth(dataSyncId, delegatedSessionId);
  if (!delegatedSessionId.isEmpty() || !dataSyncId.isEmpty()) {
    m_dataSyncId = computedDelegatedId;
  }
  if (!visitorData.isEmpty()) {
    m_visitorData = visitorData;
  }
  bool accountIndexValid = false;
  const int accountIndex =
      accountIndexValue.isDouble()
          ? accountIndexValue.toInt(-1)
          : accountIndexValue.toString().toInt(&accountIndexValid);
  if ((accountIndexValue.isDouble() || accountIndexValid) &&
      accountIndex >= 0) {
    m_accountIndex = accountIndex;
  }
  if (!cookie.isEmpty()) {
    QString normalized = normalizeYouTubeAuthCookie(cookie);
    const QString complete =
        normalizeYouTubeAuthCookie(m_browserCookies->cookies());
    const auto nativeCookies = parseCookieString(complete);
    if (!nativeCookies.value(QStringLiteral("SAPISID")).isEmpty() &&
        nativeCookies.value(QStringLiteral("SAPISID")) ==
            parseCookieString(normalized).value(QStringLiteral("SAPISID")))
      normalized = complete;
#ifdef ORCHARD_NATIVE_COOKIE_AUTH
    // The native cookie snapshot arrives asynchronously. Never verify or save
    // document.cookie alone: it excludes the HttpOnly session credentials.
    const int captureState = (hasYouTubeLoginCookie(normalized) ? 1 : 0) |
                             (normalized == complete ? 2 : 0) |
                             (hasYouTubeSessionCookies(complete) ? 4 : 0);
    if (captureState != m_authCaptureState) {
      m_authCaptureState = captureState;
      authDiagnostic(
          QStringLiteral(
              "page-auth signing=%1 native-match=%2 native-session=%3")
              .arg(bool(captureState & 1))
              .arg(bool(captureState & 2))
              .arg(bool(captureState & 4)));
    }
    if (normalized != complete || !hasYouTubeSessionCookies(normalized)) {
      // WebEngine never replays stored cookies, so a switch over the existing
      // login sees no HttpOnly ones. The parent merges in the session it holds.
      if (!m_switchMode || !hasYouTubeLoginCookie(normalized))
        return false;
      normalized = mergeCookies(normalized, complete);
    }
#endif
    if (hasYouTubeLoginCookie(normalized)) {
      if (m_profileLoading && normalized == previousCookie &&
          m_dataSyncId == previousDataSyncId &&
          m_accountIndex == previousAccountIndex)
        return true;
      m_cookie = normalized;
      setErrorMessage(QString());
      startProfileResolution();
      return true;
    }
  }

  return false;
}

