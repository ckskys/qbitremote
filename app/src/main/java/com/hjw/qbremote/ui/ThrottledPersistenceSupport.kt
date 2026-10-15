package com.hjw.qbremote.ui

import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps the latest value per key in memory and rate-limits how often it is written
 * to disk. Used for caches/trackers that change on every server refresh, where
 * rewriting the whole DataStore JSON each time is wasted I/O.
 *
 * Callers own the actual I/O: [offer] says whether to write now; values it held
 * back are returned by [drainPending] so they can be flushed (e.g. when the app
 * goes to the background).
 */
internal class ThrottledPersistence<T : Any>(
    private val minWriteIntervalMs: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val latest = ConcurrentHashMap<String, T>()
    private val pending = ConcurrentHashMap<String, T>()
    private val lastWriteAt = ConcurrentHashMap<String, Long>()

    /** Latest in-memory value, which may be newer than what is on disk. */
    fun cached(key: String): T? = latest[key]

    /** Seeds the cache with a value just loaded from disk (no write needed). */
    fun remember(key: String, value: T) {
        latest.putIfAbsent(key, value)
    }

    /** Records [value]; returns true if the caller should write it to disk now. */
    fun offer(key: String, value: T): Boolean {
        latest[key] = value
        val now = clock()
        val lastWrite = lastWriteAt[key]
        if (lastWrite != null && now - lastWrite < minWriteIntervalMs) {
            pending[key] = value
            return false
        }
        pending.remove(key)
        lastWriteAt[key] = now
        return true
    }

    /**
     * Records a write the caller made outside the throttle (state change, user
     * action): nothing is pending any more and the next [offer] writes immediately.
     */
    fun wroteUnthrottled(key: String, value: T) {
        latest[key] = value
        pending.remove(key)
        lastWriteAt.remove(key)
    }

    /** Returns and clears the values that were held back and are not on disk yet. */
    fun drainPending(): Map<String, T> {
        val drained = mutableMapOf<String, T>()
        pending.keys.toList().forEach { key ->
            pending.remove(key)?.let { value ->
                drained[key] = value
                lastWriteAt[key] = clock()
            }
        }
        return drained
    }

    /** Drops all state for keys that no longer exist (e.g. a deleted server). */
    fun retainKeys(keep: (String) -> Boolean) {
        latest.keys.removeAll { !keep(it) }
        pending.keys.removeAll { !keep(it) }
        lastWriteAt.keys.removeAll { !keep(it) }
    }
}
