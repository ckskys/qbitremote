package com.hjw.qbremote.ui

import com.hjw.qbremote.data.model.FilePriority
import com.hjw.qbremote.data.model.TorrentTracker
import com.hjw.qbremote.data.TorrentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Per-torrent operations and the torrent detail snapshot. Split out of
 * MainViewModel; the request-validity checks and refresh scheduling stay in the
 * ViewModel and are passed in.
 */
internal class TorrentActionsController(
    private val uiState: MutableStateFlow<MainUiState>,
    private val scope: CoroutineScope,
    private val repository: TorrentRepository,
    private val currentActiveProfileRequestVersion: () -> Long,
    private val isActiveProfileRequestValid: (profileId: String, requestVersion: Long) -> Boolean,
    private val isDetailRequestValid: (profileId: String, hash: String, requestVersion: Long) -> Boolean,
    private val refresh: () -> Unit,
    private val scheduleImmediateServerRefresh: (profileId: String) -> Unit,
) {
    fun pauseTorrent(hash: String) = runTorrentAction(hash) { profileId ->
        repository.pauseTorrent(profileId, hash).getOrThrow()
    }

    fun resumeTorrent(hash: String) = runTorrentAction(hash) { profileId ->
        repository.resumeTorrent(profileId, hash).getOrThrow()
    }

    fun deleteTorrent(hash: String, deleteFiles: Boolean) = runTorrentAction(hash) { profileId ->
        repository.deleteTorrent(profileId, hash, deleteFiles).getOrThrow()
    }

    /**
     * Applies one action to several torrents (list multi-select): all are marked
     * pending, run with bounded concurrency, and the list refreshes once at the end
     * instead of once per torrent.
     */
    internal fun runBatchTorrentAction(
        hashes: Collection<String>,
        action: BatchTorrentAction,
        deleteFiles: Boolean = false,
    ) {
        val profileId = uiState.value.activeServerProfileId?.trim().orEmpty()
        val normalizedHashes = hashes.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (profileId.isBlank() || normalizedHashes.isEmpty()) return
        val pendingKeys = normalizedHashes.map { buildPendingActionKey(profileId, it) }.toSet()
        val requestVersion = currentActiveProfileRequestVersion()

        scope.launch {
            uiState.update {
                it.copy(pendingActionKeys = it.pendingActionKeys + pendingKeys, errorMessage = null)
            }
            val permits = kotlinx.coroutines.sync.Semaphore(BATCH_ACTION_CONCURRENCY)
            val failures = supervisorScope {
                normalizedHashes.map { hash ->
                    async {
                        permits.withPermit {
                            runCatching {
                                when (action) {
                                    BatchTorrentAction.PAUSE -> repository.pauseTorrent(profileId, hash)
                                    BatchTorrentAction.RESUME -> repository.resumeTorrent(profileId, hash)
                                    BatchTorrentAction.DELETE -> repository.deleteTorrent(profileId, hash, deleteFiles)
                                }.getOrThrow()
                            }.exceptionOrNull()
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            failures.firstOrNull { it is CancellationException }?.let { throw it }
            if (isActiveProfileRequestValid(profileId, requestVersion)) {
                if (failures.isNotEmpty()) {
                    uiState.update {
                        it.copy(
                            errorMessage = UiMessage.Text(
                                "${failures.size}/${normalizedHashes.size}: " +
                                    (failures.first().message ?: "Action failed."),
                            ),
                        )
                    }
                }
                refresh()
            } else {
                scheduleImmediateServerRefresh(profileId)
            }
            uiState.update {
                it.copy(pendingActionKeys = it.pendingActionKeys - pendingKeys)
            }
        }
    }

    fun reannounceTorrent(hash: String) = runDetailAction(hash) { profileId ->
        repository.reannounceTorrent(profileId, hash).getOrThrow()
    }

    fun recheckTorrent(hash: String) = runDetailAction(hash) { profileId ->
        repository.recheckTorrent(profileId, hash).getOrThrow()
    }

    fun loadTorrentDetail(hash: String) {
        val profileId = uiState.value.activeServerProfileId?.trim().orEmpty()
        val normalizedHash = hash.trim()
        if (profileId.isBlank() || normalizedHash.isBlank()) return
        val requestVersion = currentActiveProfileRequestVersion()
        scope.launch {
            uiState.update {
                it.copy(
                    detailHash = normalizedHash,
                    detailLoading = true,
                    errorMessage = null,
                )
            }
            repository.fetchTorrentDetail(profileId, normalizedHash)
                .onSuccess { detail ->
                    val trackers = repository.fetchTorrentTrackers(profileId, normalizedHash)
                        .getOrElse { emptyList() }
                    val peers = repository.fetchTorrentPeers(profileId, normalizedHash)
                        .getOrElse { emptyList() }
                    val categoryOptions = repository.fetchCategoryOptions(profileId)
                        .getOrElse { emptyList() }
                    val tagOptions = repository.fetchTagOptions(profileId)
                        .getOrElse { emptyList() }
                    uiState.update { current ->
                        if (!isDetailRequestValid(profileId, normalizedHash, requestVersion)) {
                            current
                        } else {
                            current.copy(
                                detailLoading = false,
                                detailProperties = detail.properties,
                                detailFiles = detail.files,
                                detailTrackers = trackers,
                        detailPeers = peers,
                                categoryOptions = categoryOptions,
                                tagOptions = tagOptions,
                            )
                        }
                    }
                }
                .onFailure { error ->
                    uiState.update { current ->
                        if (!isDetailRequestValid(profileId, normalizedHash, requestVersion)) {
                            current
                        } else {
                            current.copy(
                                detailLoading = false,
                                detailProperties = null,
                                detailFiles = emptyList(),
                                detailTrackers = emptyList(),
                            detailPeers = emptyList(),
                                errorMessage = UiMessage.Text(error.message ?: "加载种子详情失败"),
                            )
                        }
                    }
                }
        }
    }

    fun setFilePriority(
        hash: String,
        fileIndexes: List<Int>,
        priority: com.hjw.qbremote.data.model.FilePriority,
    ) = runDetailAction(hash) { profileId ->
        repository.setFilePriority(profileId, hash, fileIndexes, priority).getOrThrow()
    }

    fun renameTorrent(hash: String, newName: String) = runDetailAction(hash) { profileId ->
        repository.renameTorrent(profileId, hash, newName).getOrThrow()
    }

    fun setTorrentLocation(hash: String, location: String) = runDetailAction(hash) { profileId ->
        repository.setTorrentLocation(profileId, hash, location).getOrThrow()
    }

    fun setTorrentCategory(hash: String, category: String) = runDetailAction(hash) { profileId ->
        repository.setTorrentCategory(profileId, hash, category).getOrThrow()
    }

    fun setTorrentTags(hash: String, oldTags: String, newTags: String) = runDetailAction(hash) { profileId ->
        repository.setTorrentTags(profileId, hash, oldTags, newTags).getOrThrow()
    }

    fun setTorrentSpeedLimit(hash: String, downloadLimitKb: String, uploadLimitKb: String) = runDetailAction(hash) { profileId ->
        val dl = parseLimitKbToBytes(downloadLimitKb)
        val up = parseLimitKbToBytes(uploadLimitKb)
        repository.setTorrentSpeedLimit(profileId, hash, dl, up).getOrThrow()
    }

    fun setTorrentShareRatio(hash: String, ratio: String) = runDetailAction(hash) { profileId ->
        val value = ratio.trim().toDoubleOrNull() ?: throw IllegalArgumentException("分享比率格式无效")
        repository.setTorrentShareRatio(profileId, hash, value).getOrThrow()
    }

    fun addTracker(hash: String, trackerUrl: String) = runDetailAction(hash) { profileId ->
        repository.addTracker(profileId, hash, trackerUrl.trim()).getOrThrow()
    }

    fun editTracker(
        hash: String,
        tracker: TorrentTracker,
        newUrl: String,
    ) = runDetailAction(hash) { profileId ->
        repository.editTracker(
            profileId = profileId,
            hash = hash,
            tracker = tracker,
            newUrl = newUrl.trim(),
        ).getOrThrow()
    }

    fun removeTracker(
        hash: String,
        tracker: TorrentTracker,
    ) = runDetailAction(hash) { profileId ->
        repository.removeTracker(
            profileId = profileId,
            hash = hash,
            tracker = tracker,
        ).getOrThrow()
    }

    private fun runTorrentAction(
        hash: String,
        action: suspend (String) -> Unit,
    ) {
        val profileId = uiState.value.activeServerProfileId?.trim().orEmpty()
        val normalizedHash = hash.trim()
        if (profileId.isBlank() || normalizedHash.isBlank()) return
        val pendingActionKey = buildPendingActionKey(profileId, normalizedHash)
        if (uiState.value.pendingActionKeys.contains(pendingActionKey)) return
        val requestVersion = currentActiveProfileRequestVersion()

        scope.launch {
            uiState.update {
                it.copy(pendingActionKeys = it.pendingActionKeys + pendingActionKey, errorMessage = null)
            }
            runCatching { action(profileId) }
                .onSuccess {
                    if (isActiveProfileRequestValid(profileId, requestVersion)) {
                        refresh()
                    } else {
                        scheduleImmediateServerRefresh(profileId)
                    }
                }
                .onFailure { error ->
                    if (isActiveProfileRequestValid(profileId, requestVersion)) {
                        uiState.update {
                            it.copy(errorMessage = UiMessage.Text(error.message ?: "Action failed."))
                        }
                    }
                }
            uiState.update {
                it.copy(pendingActionKeys = it.pendingActionKeys - pendingActionKey)
            }
        }
    }

    private fun runDetailAction(
        hash: String,
        action: suspend (String) -> Unit,
    ) {
        val normalizedHash = hash.trim()
        if (normalizedHash.isBlank()) return
        val requestVersion = currentActiveProfileRequestVersion()
        runTorrentAction(normalizedHash) { profileId ->
            action(profileId)
            val detail = repository.fetchTorrentDetail(profileId, normalizedHash).getOrThrow()
            val trackers = repository.fetchTorrentTrackers(profileId, normalizedHash).getOrElse { emptyList() }
            val peers = repository.fetchTorrentPeers(profileId, normalizedHash).getOrElse { emptyList() }
            val categoryOptions = repository.fetchCategoryOptions(profileId).getOrElse { emptyList() }
            val tagOptions = repository.fetchTagOptions(profileId).getOrElse { emptyList() }
            uiState.update { current ->
                if (!isDetailRequestValid(profileId, normalizedHash, requestVersion)) {
                    current
                } else {
                    current.copy(
                        detailHash = normalizedHash,
                        detailLoading = false,
                        detailProperties = detail.properties,
                        detailFiles = detail.files,
                        detailTrackers = trackers,
                        detailPeers = peers,
                        categoryOptions = categoryOptions,
                        tagOptions = tagOptions,
                    )
                }
            }
        }
    }

    internal suspend fun refreshDetailSnapshot(
        profileId: String,
        hash: String,
        requestVersion: Long,
    ) {
        val detail = repository.fetchTorrentDetail(profileId, hash).getOrNull() ?: return
        val trackers = repository.fetchTorrentTrackers(profileId, hash).getOrElse { emptyList() }
        val peers = repository.fetchTorrentPeers(profileId, hash).getOrElse { emptyList() }
        uiState.update { current ->
            if (!isDetailRequestValid(profileId, hash, requestVersion)) {
                current
            } else {
                current.copy(
                    detailProperties = detail.properties,
                    detailFiles = detail.files,
                    detailTrackers = trackers,
                        detailPeers = peers,
                )
            }
        }
    }

    private companion object {
        const val BATCH_ACTION_CONCURRENCY = 4
    }
}
