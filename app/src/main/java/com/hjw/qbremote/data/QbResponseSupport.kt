package com.hjw.qbremote.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * qBittorrent < 5.2 answers a successful login with 200 "Ok."; 5.2+ answers 204
 * with an empty body (the SID cookie is still set). Both mean success.
 */
internal fun isQbLoginSuccess(httpSuccessful: Boolean, httpCode: Int, bodyText: String): Boolean {
    if (!httpSuccessful) return false
    val text = bodyText.trim()
    return text.equals("Ok.", ignoreCase = true) || (httpCode == 204 && text.isEmpty())
}

/** Outcome of POST /api/v2/torrents/add, across the old text and the 5.2+ JSON replies. */
internal sealed interface QbAddTorrentOutcome {
    data object Added : QbAddTorrentOutcome
    data class Failed(val message: String) : QbAddTorrentOutcome
}

/**
 * Before 5.2 the endpoint returns "Ok." or "Fails.". 5.2+ returns JSON such as
 * {"added_torrent_ids":[...],"failure_count":0,"pending_count":0,"success_count":1}.
 * (Matching on the substring "fail" broke on 5.2: every reply contains "failure_count".)
 */
internal fun parseQbAddTorrentResult(bodyText: String): QbAddTorrentOutcome {
    val text = bodyText.trim()
    if (text.isEmpty() || text.equals("Ok.", ignoreCase = true)) return QbAddTorrentOutcome.Added
    if (text.equals("Fails.", ignoreCase = true)) {
        return QbAddTorrentOutcome.Failed("添加种子失败：服务器拒绝了该种子（Fails.）")
    }
    if (text.startsWith("{")) {
        val json = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
            ?: return QbAddTorrentOutcome.Failed("添加种子失败：无法解析服务器响应 - ${text.take(120)}")
        val success = json.intOrZero("success_count")
        val pending = json.intOrZero("pending_count")
        val failure = json.intOrZero("failure_count")
        val addedIds = runCatching { json.getAsJsonArray("added_torrent_ids")?.size() ?: 0 }.getOrDefault(0)
        val anyAdded = success > 0 || pending > 0 || addedIds > 0
        return when {
            failure > 0 && anyAdded -> QbAddTorrentOutcome.Failed(
                "部分种子添加失败：成功 ${maxOf(success, addedIds)} 个，处理中 $pending 个，失败 $failure 个",
            )
            failure > 0 -> QbAddTorrentOutcome.Failed("添加种子失败：$failure 个种子未能添加")
            // A JSON reply with no failures counts as accepted (e.g. duplicates report 0/0/0).
            else -> QbAddTorrentOutcome.Added
        }
    }
    if (text.contains("fail", ignoreCase = true)) {
        return QbAddTorrentOutcome.Failed("添加种子失败：${text.take(120)}")
    }
    return QbAddTorrentOutcome.Added
}

private fun JsonObject.intOrZero(key: String): Int =
    runCatching { get(key)?.takeIf { !it.isJsonNull }?.asInt ?: 0 }.getOrDefault(0)
