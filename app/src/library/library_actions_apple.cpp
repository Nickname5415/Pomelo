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

#include "integrations/apple_music.h"
#include "integrations/catalog_source.h"

#include <QPointer>

void LibraryActions::setAppleMusic(AppleMusic *appleMusic, CatalogSource *source) {
  m_appleMusic = appleMusic;
  m_source = source;
  m_appleConnected = appleMusic->connected();
  // Likes and targets belong to one service's account; start over when that changes.
  const auto restart = [this] {
    forgetAccount();
    m_trackId.clear();
    m_likeVideoId.clear();
    syncTrack();
  };
  connect(source, &CatalogSource::changed, this, restart);
  connect(appleMusic, &AppleMusic::changed, this, [this, restart] {
    if (m_appleMusic->connected() == m_appleConnected) return;
    m_appleConnected = m_appleMusic->connected();
    if (appleMode()) restart();
  });
}

bool LibraryActions::appleMode() const {
  return m_source && m_source->apple() && m_appleMusic;
}

bool LibraryActions::appleSignedIn() const {
  return m_appleMusic && m_appleMusic->connected();
}

quint64 LibraryActions::appleRequest(const QString &method, const QJsonObject &payload, const QString &likeKey) {
  const quint64 id = (quint64{1} << 63) | ++m_appleRequests;
  m_appleMusic->libraryAction(method, payload,
                              [self = QPointer(this), id, likeKey](const QJsonObject &result, const QString &error) {
    // Queued, so the caller has stored the id before the answer is matched against it.
    QMetaObject::invokeMethod(self, [self, id, likeKey, result, error] {
      if (!self) return;
      if (!error.isEmpty()) {
        self->fail(id, error);
        return;
      }
      QJsonObject answer = result;
      if (!likeKey.isEmpty())
        answer.insert(QStringLiteral("videoId"), likeKey);
      self->receive(id, answer);
    }, Qt::QueuedConnection);
  });
  return id;
}
