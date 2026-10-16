package com.hjw.qbremote.ui

import androidx.compose.material.icons.outlined.Folder
import androidx.compose.foundation.layout.size
import com.hjw.qbremote.data.model.FilePriority
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.hjw.qbremote.data.model.TorrentFileInfo

// Folder tree built from a torrent's flat file list (pure logic, no UI).

internal data class TorrentFileTreeNode(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val size: Long,
    val progress: Float,
    val fileCount: Int,
    val orderIndex: Int,
    val pathSegments: List<String>,
    val children: List<TorrentFileTreeNode> = emptyList(),
    // Backend file indexes under this node (one for a file, all descendants for a
    // folder) and their priorities; null priority means the files disagree.
    val fileIndexes: List<Int> = emptyList(),
    val priority: FilePriority? = null,
)

internal data class TorrentFileBrowserSelection(
    val node: TorrentFileTreeNode,
    val pathSegments: List<String>,
)

private class MutableTorrentFileTreeNode(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val orderIndex: Int,
    val pathSegments: List<String>,
) {
    val children = linkedMapOf<String, MutableTorrentFileTreeNode>()
    var size: Long = 0L
    var weightedProgress: Double = 0.0
    var fileCount: Int = 0
    val fileIndexes = mutableListOf<Int>()
    val priorities = mutableSetOf<FilePriority>()
}

internal fun buildTorrentFileTree(files: List<TorrentFileInfo>): TorrentFileTreeNode {
    val root = MutableTorrentFileTreeNode(
        name = "",
        fullPath = "",
        isDirectory = true,
        orderIndex = Int.MIN_VALUE,
        pathSegments = emptyList(),
    )
    files.forEachIndexed { fallbackIndex, file ->
        val cleanPath = file.name.trim().replace('\\', '/')
        if (cleanPath.isBlank()) return@forEachIndexed
        val segments = cleanPath.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return@forEachIndexed
        val clampedProgress = file.progress.coerceIn(0f, 1f).toDouble()
        val weightedProgress = file.size.toDouble() * clampedProgress
        val fileIndex = if (file.index >= 0) file.index else fallbackIndex
        val filePriority = FilePriority.fromQbValue(file.priority)
        var current = root
        current.size += file.size
        current.weightedProgress += weightedProgress
        current.fileCount += 1
        current.fileIndexes += fileIndex
        current.priorities += filePriority
        segments.forEachIndexed { index, segment ->
            val isFile = index == segments.lastIndex
            val currentPathSegments = segments.take(index + 1)
            val fullPath = currentPathSegments.joinToString("/")
            val child = current.children.getOrPut(segment) {
                MutableTorrentFileTreeNode(
                    name = segment,
                    fullPath = fullPath,
                    isDirectory = !isFile,
                    orderIndex = if (file.index >= 0) file.index else fallbackIndex,
                    pathSegments = currentPathSegments,
                )
            }
            if (isFile) {
                child.size = file.size
                child.weightedProgress = weightedProgress
                child.fileCount = 1
            } else {
                child.size += file.size
                child.weightedProgress += weightedProgress
                child.fileCount += 1
            }
            child.fileIndexes += fileIndex
            child.priorities += filePriority
            current = child
        }
    }
    return root.toImmutableNode()
}

private fun MutableTorrentFileTreeNode.toImmutableNode(): TorrentFileTreeNode {
    val safeSize = size.coerceAtLeast(0L)
    val progress = if (safeSize > 0L) {
        (weightedProgress / safeSize.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    return TorrentFileTreeNode(
        name = name,
        fullPath = fullPath,
        isDirectory = isDirectory,
        size = safeSize,
        progress = progress,
        fileCount = fileCount.coerceAtLeast(if (isDirectory) 0 else 1),
        orderIndex = orderIndex,
        pathSegments = pathSegments,
        fileIndexes = fileIndexes.toList(),
        priority = priorities.singleOrNull(),
        children = children.values
            .sortedWith(
                compareBy<MutableTorrentFileTreeNode> { !it.isDirectory }
                    .thenBy { it.orderIndex }
                    .thenBy { it.name.lowercase() },
            )
            .map { it.toImmutableNode() },
    )
}

private fun resolveTorrentFileTreeNode(
    root: TorrentFileTreeNode,
    pathSegments: List<String>,
): TorrentFileTreeNode? {
    var current = root
    pathSegments.forEach { segment ->
        current = current.children.firstOrNull { it.name == segment } ?: return null
    }
    return current
}

internal fun resolveTorrentFileBrowserSelection(
    root: TorrentFileTreeNode,
    pathSegments: List<String>,
): TorrentFileBrowserSelection {
    val resolvedNode = resolveTorrentFileTreeNode(root, pathSegments)
    return if (resolvedNode != null) {
        TorrentFileBrowserSelection(
            node = resolvedNode,
            pathSegments = pathSegments,
        )
    } else {
        TorrentFileBrowserSelection(
            node = root,
            pathSegments = emptyList(),
        )
    }
}
