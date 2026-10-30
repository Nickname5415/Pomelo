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
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Orchard. If not, see <https://www.gnu.org/licenses/>.
 */

#include "library_actions.h"
#include "local/local_track.h"

#include "auth/auth_manager.h"
#include "home/home_controller.h"
#include "playback/playback_controller.h"
#include "providers/youtube/youtube_provider.h"

#include <QJsonArray>

namespace {
constexpr qsizetype maxCachedLikes = 256;

QString trackIdOf(const QVariantMap &track) {
  return track.value(QStringLiteral("id")).toString();
}
} // namespace

LibraryActions::LibraryActions(YouTubeProvider *provider, AuthManager *auth,
                               PlaybackController *playback, HomeController *home,
                               QObject *parent)
    : QObject(parent), m_provider(provider), m_auth(auth), m_playback(playback),
      m_home(home) {
  Q_ASSERT(m_provider);
  Q_ASSERT(m_auth);
  Q_ASSERT(m_playback);
  Q_ASSERT(m_home);

  connect(m_provider, &YouTubeProvider::resultReady, this, &LibraryActions::receive);
  connect(m_provider, &YouTubeProvider::requestFailed, this, &LibraryActions::fail);
  // Fires on every position tick; syncTrack only acts when the track or its stream id moves.
  connect(m_playback, &PlaybackController::stateChanged, this, &LibraryActions::syncTrack);
  connect(m_auth, &AuthManager::statusChanged, this, [this] {
    if (!m_auth->isSignedIn() && !appleMode())
      forgetAccount();
  });
  connect(m_auth, &AuthManager::accountChanged, this, [this] {
    if (appleMode()) return;
    forgetAccount();
    // Ask the new account about the song that is already playing.
    m_trackId.clear();
    m_likeVideoId.clear();
    syncTrack();
  });
}

void LibraryActions::forgetAccount() {
  // A different account likes different songs. Probably. Taste is personal.
  m_likeCache.clear();
  m_likeKnown = false;
  m_liked = false;
  m_likeStatusRequest = 0;
  m_targets.clear();
  m_targetsTrackId.clear();
  emit likeChanged();
  emit targetsChanged();
}

QString LibraryActions::likeKey(const QString &trackId) const {
  if (trackId.isEmpty())
    return {};
  if (appleMode())
    return local::isLocalTrackId(trackId) ? QString() : QStringLiteral("apple:") + trackId;
  return m_playback->playbackVideoId(trackId);
}

void LibraryActions::syncTrack() {
  const QString id = trackIdOf(m_playback->shownTrack());
  const QString videoId = likeKey(id);
  if (id == m_trackId && videoId == m_likeVideoId)
    return;

  const bool sameTrack = id == m_trackId;
  m_trackId = id;
  m_likeVideoId = videoId;
  m_likeStatusRequest = 0;
  if (const auto cached = m_likeCache.constFind(videoId);
      !videoId.isEmpty() && cached != m_likeCache.cend()) {
    m_liked = cached.value();
    m_likeKnown = true;
  } else if (!sameTrack || !m_likeKnown) {
    // Unknown until the stream reports which recording it opened. A toggle made
    // before then stays put; its own result settles the state.
    m_liked = false;
    m_likeKnown = false;
    requestLikeStatus();
  }
  emit likeChanged();
}

void LibraryActions::requestLikeStatus() {
  if (m_likeVideoId.isEmpty() || m_likeSetRequest)
    return;
  if (appleMode()) {
    if (appleSignedIn())
      m_likeStatusRequest = appleRequest(
          QStringLiteral("library.like.status"),
          {{QStringLiteral("track"), QJsonObject::fromVariantMap(PlaybackController::sanitizeTrack(m_playback->shownTrack()))}},
          m_likeVideoId);
    return;
  }
  if (!m_auth->isSignedIn())
    return;
  m_likeStatusRequest = m_provider->invoke(
      QStringLiteral("library.like.status"),
      QJsonObject{{QStringLiteral("session"), m_auth->sessionObject()},
                  {QStringLiteral("videoId"), m_likeVideoId}});
}

QJsonObject LibraryActions::payloadFor(const QVariantMap &track) const {
  QJsonObject payload{
      {QStringLiteral("session"), m_auth->sessionObject()},
      {QStringLiteral("track"),
       QJsonObject::fromVariantMap(PlaybackController::sanitizeTrack(track))}};
  const QString id = trackIdOf(track);
  // Reuse an id already resolved for this row; otherwise the provider runs the playback matcher.
  QString videoId = m_playback->playbackVideoId(id);
  if (videoId.isEmpty() && id == m_targetsTrackId)
    videoId = m_targetsVideoId;
  if (!videoId.isEmpty())
    payload.insert(QStringLiteral("videoId"), videoId);
  return payload;
}

void LibraryActions::toggleCurrentLike() {
  const QVariantMap track = m_playback->shownTrack();
  if (trackIdOf(track).isEmpty() || m_likeSetRequest)
    return;
  // Likes live on the streaming service, and a file on your disk is not on it.
  if (local::isLocalTrack(track)) {
    emit noticeRequested(tr("Files on this computer can't be liked. Add this one to a local playlist instead."));
    return;
  }
  if (!(appleMode() ? appleSignedIn() : m_auth->isSignedIn())) {
    emit noticeRequested(appleMode() ? tr("Connect Apple Music in Settings to love songs.") : tr("Sign in to like songs."));
    return;
  }
  // Optimistic; fail() puts it back if YouTube disagrees.
  m_liked = !m_liked;
  m_likeKnown = true;
  m_likeStatusRequest = 0;
  m_likeSetTrackId = m_trackId;
  if (appleMode()) {
    m_likeSetRequest = appleRequest(
        QStringLiteral("library.like.set"),
        {{QStringLiteral("track"), QJsonObject::fromVariantMap(PlaybackController::sanitizeTrack(track))},
         {QStringLiteral("liked"), m_liked}},
        m_likeVideoId);
    emit likeChanged();
    return;
  }
  QJsonObject payload = payloadFor(track);
  payload.insert(QStringLiteral("liked"), m_liked);
  m_likeSetRequest = m_provider->invoke(QStringLiteral("library.like.set"), payload);
  emit likeChanged();
}

void LibraryActions::loadPlaylistTargets(const QVariantMap &track) {
  const QString id = trackIdOf(track);
  if (id.isEmpty())
    return;
  if (!(appleMode() ? appleSignedIn() : m_auth->isSignedIn())) {
    m_targetsError = appleMode() ? tr("Connect Apple Music in Settings to add songs to playlists.")
                                 : tr("Sign in to add songs to playlists.");
    emit targetsChanged();
    return;
  }
  if (id != m_targetsTrackId)
    m_targetsVideoId.clear();
  m_targetsTrackId = id;
  m_targets.clear();
  m_targetsError.clear();
  m_targetsRequest = appleMode() ? appleRequest(QStringLiteral("library.playlist.targets"), {})
                                 : m_provider->invoke(QStringLiteral("library.playlist.targets"), payloadFor(track));
  emit targetsChanged();
}

void LibraryActions::openPlaylistPicker(const QVariantMap &track, const QPointF &position) {
  if (trackIdOf(track).isEmpty())
    return;
  // Local songs pick from local playlists, which the picker reads itself.
  if (!local::isLocalTrack(track))
    loadPlaylistTargets(track);
  emit playlistPickerRequested(track, position);
}

void LibraryActions::addToPlaylist(const QVariantMap &track, const QString &playlistId,
                                   const QString &title) {
  if (trackIdOf(track).isEmpty() || playlistId.isEmpty() || m_saveRequest)
    return;
  m_saveCreates = false;
  if (appleMode()) {
    m_saveRequest = appleRequest(
        QStringLiteral("library.playlist.add"),
        {{QStringLiteral("track"), QJsonObject::fromVariantMap(PlaybackController::sanitizeTrack(track))},
         {QStringLiteral("playlistId"), playlistId},
         {QStringLiteral("title"), title}});
    emit targetsChanged();
    return;
  }
  QJsonObject payload = payloadFor(track);
  payload.insert(QStringLiteral("playlistId"), playlistId);
  payload.insert(QStringLiteral("title"), title);
  m_saveRequest = m_provider->invoke(QStringLiteral("library.playlist.add"), payload);
  emit targetsChanged();
}

void LibraryActions::createPlaylist(const QVariantMap &track, const QString &title) {
  if (trackIdOf(track).isEmpty() || title.trimmed().isEmpty() || m_saveRequest)
    return;
  m_saveCreates = true;
  if (appleMode()) {
    m_saveRequest = appleRequest(
        QStringLiteral("library.playlist.create"),
        {{QStringLiteral("track"), QJsonObject::fromVariantMap(PlaybackController::sanitizeTrack(track))},
         {QStringLiteral("title"), title.trimmed()}});
    emit targetsChanged();
    return;
  }
  QJsonObject payload = payloadFor(track);
  payload.insert(QStringLiteral("title"), title.trimmed());
  m_saveRequest = m_provider->invoke(QStringLiteral("library.playlist.create"), payload);
  emit targetsChanged();
}

void LibraryActions::createEmptyPlaylist(const QString &title) {
  if (title.trimmed().isEmpty() || m_saveRequest)
    return;
  if (appleMode()) {
    if (!appleSignedIn()) {
      emit noticeRequested(tr("Connect Apple Music in Settings to create Apple Music playlists."));
      return;
    }
    m_saveCreates = true;
    m_saveRequest = appleRequest(QStringLiteral("library.playlist.create"),
                                 {{QStringLiteral("title"), title.trimmed()}});
    emit targetsChanged();
    return;
  }
  if (!m_auth->isSignedIn()) {
    emit noticeRequested(tr("Sign in to create YouTube Music playlists."));
    return;
  }
  m_saveCreates = true;
  m_saveRequest = m_provider->invoke(
      QStringLiteral("library.playlist.create"),
      QJsonObject{{QStringLiteral("session"), m_auth->sessionObject()},
                  {QStringLiteral("title"), title.trimmed()}});
  emit targetsChanged();
}

void LibraryActions::removeFromPlaylist(const QVariantMap &track, const QString &playlistId,
                                        const QString &title) {
  if (trackIdOf(track).isEmpty() || playlistId.isEmpty() || m_removeRequest)
    return;
  // The row as stored in the playlist; payloadFor would swap in the audio version.
  m_removeRequest = m_provider->invoke(
      QStringLiteral("library.playlist.remove"),
      QJsonObject{{QStringLiteral("session"), m_auth->sessionObject()},
                  {QStringLiteral("track"), QJsonObject::fromVariantMap(track)},
                  {QStringLiteral("playlistId"), playlistId},
                  {QStringLiteral("title"), title}});
}

void LibraryActions::receive(quint64 requestId, const QJsonValue &result) {
  const QJsonObject object = result.toObject();
  const QString videoId = object.value(QStringLiteral("videoId")).toString();

  if (requestId == m_likeStatusRequest || requestId == m_likeSetRequest) {
    const bool wasSet = requestId == m_likeSetRequest;
    (wasSet ? m_likeSetRequest : m_likeStatusRequest) = 0;
    const bool liked = object.value(QStringLiteral("liked")).toBool();
    if (m_likeCache.size() >= maxCachedLikes)
      m_likeCache.clear();
    m_likeCache.insert(videoId, liked);
    if (wasSet && m_likeSetTrackId == m_trackId && m_likeVideoId.isEmpty())
      m_likeVideoId = videoId;
    if (videoId == m_likeVideoId) {
      m_liked = liked;
      m_likeKnown = true;
    }
    emit likeChanged();
    return;
  }

  if (requestId == m_removeRequest) {
    m_removeRequest = 0;
    const QString title = object.value(QStringLiteral("title")).toString();
    emit removedFromPlaylist(object.value(QStringLiteral("playlistId")).toString(),
                             object.value(QStringLiteral("setVideoId")).toString(), videoId);
    emit noticeRequested(title.isEmpty() ? tr("Removed from playlist") : tr("Removed from %1").arg(title));
    return;
  }

  if (requestId == m_targetsRequest) {
    m_targetsRequest = 0;
    m_targetsVideoId = videoId;
    m_targets = object.value(QStringLiteral("playlists")).toArray().toVariantList();
    emit targetsChanged();
    return;
  }

  if (requestId == m_saveRequest) {
    m_saveRequest = 0;
    const QString playlistId = object.value(QStringLiteral("playlistId")).toString();
    const QString title = object.value(QStringLiteral("title")).toString();
    if (m_saveCreates) {
      m_home->refreshPlaylists();
      emit targetsChanged();
      emit noticeRequested(tr("Created %1").arg(title));
      return;
    }
    for (QVariant &entry : m_targets) {
      QVariantMap target = entry.toMap();
      if (target.value(QStringLiteral("id")).toString() != playlistId)
        continue;
      target.insert(QStringLiteral("containsTrack"), true);
      entry = target;
    }
    emit targetsChanged();
    emit noticeRequested(title.isEmpty() ? tr("Added to playlist") : tr("Added to %1").arg(title));
  }
}

void LibraryActions::fail(quint64 requestId, const QString &message) {
  if (requestId == m_likeStatusRequest) {
    // Leave the heart hollow; a failed read is not worth a toast.
    m_likeStatusRequest = 0;
    return;
  }
  if (requestId == m_likeSetRequest) {
    m_likeSetRequest = 0;
    if (m_likeSetTrackId == m_trackId) {
      m_liked = !m_liked;
      emit likeChanged();
    }
    emit noticeRequested(message);
    return;
  }
  if (requestId == m_targetsRequest) {
    m_targetsRequest = 0;
    m_targetsError = message;
    emit targetsChanged();
    return;
  }
  if (requestId == m_saveRequest) {
    m_saveRequest = 0;
    emit targetsChanged();
    emit noticeRequested(message);
  }
  if (requestId == m_removeRequest) {
    m_removeRequest = 0;
    emit noticeRequested(message);
  }
}
