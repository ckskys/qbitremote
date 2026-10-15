package com.hjw.qbremote.ui

import com.hjw.qbremote.data.BackendConnectionError
import com.hjw.qbremote.data.ServerBackendType

/**
 * Readable connection error. These strings travel as plain Strings (snapshots,
 * callbacks), so the language follows the current locale here; they used to be
 * Chinese-only, which showed up mixed into the English UI.
 */
internal fun userFacingConnectionMessage(
    error: Throwable,
    chinese: Boolean = java.util.Locale.getDefault().language == "zh",
): String {
    fun t(zh: String, en: String) = if (chinese) zh else en
    return when (error) {
        is BackendConnectionError.WrongBackend -> t(
            "服务器类型不匹配，目标看起来是 ${backendDisplayName(error.detected)}。",
            "Server type mismatch: the target looks like ${backendDisplayName(error.detected)}.",
        )

        is BackendConnectionError.RpcPathNotFound -> {
            val base = t("Transmission RPC 路径未找到。", "Transmission RPC path not found.")
            if (error.failureSummary.isBlank()) base else "$base ${error.failureSummary}"
        }

        is BackendConnectionError.AuthFailed -> t(
            "${backendDisplayName(error.backendType)} 认证失败。",
            "${backendDisplayName(error.backendType)} authentication failed.",
        )
        is BackendConnectionError.TestTimedOut -> t(
            "连接超时：${error.seconds} 秒内没有响应，请检查地址、端口和 HTTPS 设置。",
            "Timed out: no response within ${error.seconds} s. Check the host, port and HTTPS setting.",
        )
        is BackendConnectionError.StoredPasswordUnreadable -> t(
            "${backendDisplayName(error.backendType)} 保存的密码无法读取（可能是系统密钥已重置），请在服务器设置中重新输入密码。",
            "The saved ${backendDisplayName(error.backendType)} password can't be read (the device key may have been reset). Re-enter it in the server settings.",
        )
        is BackendConnectionError.HttpsMismatch -> if (error.usedHttps) {
            t(
                "${backendDisplayName(error.backendType)} 服务器在这个端口上没有启用 HTTPS，请在服务器设置中关闭 HTTPS。",
                "The ${backendDisplayName(error.backendType)} server doesn't use HTTPS on this port. Turn HTTPS off in the server settings.",
            )
        } else {
            t(
                "${backendDisplayName(error.backendType)} 服务器要求使用 HTTPS，请在服务器设置中打开 HTTPS。",
                "The ${backendDisplayName(error.backendType)} server requires HTTPS. Turn HTTPS on in the server settings.",
            )
        }
        else -> error.message?.takeIf { it.isNotBlank() } ?: t("刷新失败", "Refresh failed")
    }
}

internal fun backendDisplayName(type: ServerBackendType): String {
    return when (type) {
        ServerBackendType.QBITTORRENT -> "qBittorrent"
        ServerBackendType.TRANSMISSION -> "Transmission"
    }
}

internal fun shouldSuppressRefreshError(message: String?): Boolean {
    val normalized = message?.trim()?.lowercase().orEmpty()
    if (normalized.isBlank()) return false
    return normalized.contains("unable to resolve host") ||
        normalized.contains("no address associated with hostname")
}
