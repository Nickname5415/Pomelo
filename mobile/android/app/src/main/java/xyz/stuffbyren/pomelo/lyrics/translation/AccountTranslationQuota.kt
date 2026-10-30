/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package xyz.stuffbyren.pomelo.lyrics.translation

import org.json.JSONObject

data class AccountTranslationQuota(val dailyLimit: Long, val remaining: Long, val resetsAt: Long) {
    fun description(now: Long = System.currentTimeMillis() / 1000): String =
        if (resetsAt <= now) "Translation allowance unavailable."
        else "$remaining translations left today. Your account allows up to $dailyLimit per day."
}

internal fun parseAccountQuota(body: JSONObject, now: Long = System.currentTimeMillis() / 1000): AccountTranslationQuota? {
    val quota = body.optJSONObject("quota") ?: return null
    fun number(key: String): Long? = (quota.opt(key) as? Number)?.let {
        it.toLong().takeIf { value -> value >= 0 && value.toDouble() == it.toDouble() }
    }
    val limit = number("daily_limit") ?: return null
    val remaining = number("remaining")?.takeIf { it <= limit } ?: return null
    val resetsAt = number("resets_at")?.takeIf { it > now } ?: return null
    return AccountTranslationQuota(limit, remaining, resetsAt)
}
