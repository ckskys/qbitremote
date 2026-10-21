package com.hjw.qbremote.ui

import com.hjw.qbremote.data.DailyUploadTrackingSnapshot
import com.hjw.qbremote.data.model.TorrentInfo
import java.time.LocalDate
import java.time.ZoneId

internal const val DAILY_UPLOAD_NO_TAG_KEY = "__NO_TAG__"

/**
 * Uploaded totals are lifetime counters, so the difference between two samples is the
 * exact upload in between, however far apart they are. On the first sample of a new day
 * the previous day's last values stay as the baseline and the gap is split at midnight
 * by elapsed time, instead of dropping everything uploaded since the last sample.
 *
 * [nowMillis] = 0 means "time unknown": the old behaviour (baselines reset at midnight).
 */
@JvmOverloads
internal fun advanceDailyUploadTrackingSnapshot(
    previousSnapshot: DailyUploadTrackingSnapshot?,
    today: LocalDate,
    torrents: List<TorrentInfo>,
    nowMillis: Long = 0L,
    zone: ZoneId = ZoneId.systemDefault(),
): Pair<DailyUploadTrackingSnapshot, List<DailyTagUploadStat>> {
    val totalsByTag = previousSnapshot?.totalsByTag?.toMutableMap() ?: linkedMapOf()
    val countedTagsByTorrent = previousSnapshot?.countedTagsByTorrent
        ?.mapValues { (_, tags) -> tags.toMutableSet() }
        ?.toMutableMap()
        ?: linkedMapOf()
    val lastSeenByTorrent = previousSnapshot?.lastSeenByTorrent?.toMutableMap() ?: linkedMapOf()
    val snapshotDate = runCatching {
        previousSnapshot?.date?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
    }.getOrNull()

    val todayShare = if (snapshotDate == today) {
        1.0
    } else {
        totalsByTag.clear()
        countedTagsByTorrent.clear()
        midnightTodayShare(previousSnapshot?.lastSampleAtMillis ?: 0L, nowMillis, today, zone)
            .also { share -> if (share <= 0.0) lastSeenByTorrent.clear() }
    }

    val activeKeys = torrents.map(::dailyUploadTorrentTrackingKey).toSet()
    lastSeenByTorrent.keys.retainAll(activeKeys)

    torrents.forEach { torrent ->
        val trackingKey = dailyUploadTorrentTrackingKey(torrent)
        val currentUploaded = torrent.uploaded.coerceAtLeast(0L)
        val previousUploaded = lastSeenByTorrent[trackingKey]
        lastSeenByTorrent[trackingKey] = currentUploaded
        if (previousUploaded == null || currentUploaded <= previousUploaded) {
            return@forEach
        }

        val tags = parseDailyUploadTags(torrent.tags).ifEmpty { listOf(DAILY_UPLOAD_NO_TAG_KEY) }
        val fullDelta = currentUploaded - previousUploaded
        val delta = if (todayShare >= 1.0) fullDelta else (fullDelta * todayShare).toLong()
        if (delta <= 0L) return@forEach

        val baseShare = delta / tags.size
        var remainder = delta % tags.size
        val countedTags = countedTagsByTorrent.getOrPut(trackingKey) { linkedSetOf() }
        tags.forEach tagLoop@{ tag ->
            val share = baseShare + if (remainder > 0L) {
                remainder -= 1L
                1L
            } else {
                0L
            }
            if (share <= 0L) return@tagLoop
            totalsByTag[tag] = (totalsByTag[tag] ?: 0L) + share
            countedTags += tag
        }
    }

    val torrentCountByTag = linkedMapOf<String, Int>()
    countedTagsByTorrent.values.forEach { tags ->
        tags.forEach { tag ->
            torrentCountByTag[tag] = (torrentCountByTag[tag] ?: 0) + 1
        }
    }

    val snapshot = DailyUploadTrackingSnapshot(
        date = today.toString(),
        totalsByTag = totalsByTag,
        countedTagsByTorrent = countedTagsByTorrent.mapValues { (_, tags) -> tags.toList() },
        lastSeenByTorrent = lastSeenByTorrent,
        lastSampleAtMillis = if (nowMillis > 0L) nowMillis else previousSnapshot?.lastSampleAtMillis ?: 0L,
    )
    val stats = totalsByTag.entries
        .filter { it.value > 0L }
        .sortedByDescending { it.value }
        .map { (tag, uploadedBytes) ->
            DailyTagUploadStat(
                tag = tag,
                uploadedBytes = uploadedBytes,
                torrentCount = torrentCountByTag[tag] ?: 0,
                isNoTag = tag == DAILY_UPLOAD_NO_TAG_KEY,
            )
        }
    return snapshot to stats
}

/**
 * Fraction of the interval (previous sample .. now) that falls on [today]; upload is
 * assumed even over it. 0 when there is no usable previous sample time.
 */
internal fun midnightTodayShare(
    previousSampleAtMillis: Long,
    nowMillis: Long,
    today: LocalDate,
    zone: ZoneId,
): Double {
    if (previousSampleAtMillis <= 0L || nowMillis <= previousSampleAtMillis) return 0.0
    val midnight = today.atStartOfDay(zone).toInstant().toEpochMilli()
    if (previousSampleAtMillis >= midnight) return 0.0 // clock went backwards
    if (nowMillis <= midnight) return 0.0
    return ((nowMillis - midnight).toDouble() / (nowMillis - previousSampleAtMillis)).coerceIn(0.0, 1.0)
}

internal fun parseDailyUploadTags(rawTags: String): List<String> {
    val normalizedByKey = linkedMapOf<String, String>()
    rawTags
        .split(',', ';', '|')
        .map { it.trim() }
        .filter { it.isNotBlank() && it != "-" && !it.equals("null", ignoreCase = true) }
        .forEach { tag ->
            val key = tag.lowercase()
            if (!normalizedByKey.containsKey(key)) {
                normalizedByKey[key] = tag
            }
        }
    return normalizedByKey.values.toList()
}

internal fun dailyUploadTorrentTrackingKey(torrent: TorrentInfo): String {
    return torrent.hash.ifBlank {
        "${torrent.name}|${torrent.addedOn}|${torrent.savePath}|${torrent.size}"
    }
}
