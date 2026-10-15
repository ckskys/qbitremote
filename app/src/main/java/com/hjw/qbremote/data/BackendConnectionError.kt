package com.hjw.qbremote.data

sealed class BackendConnectionError(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause) {
    data class WrongBackend(
        val expected: ServerBackendType,
        val detected: ServerBackendType,
        val attemptedEndpoint: String,
        val detail: String,
    ) : BackendConnectionError(
        message = "Server type mismatch: expected $expected, but target looks like $detected. $detail",
    )

    data class RpcPathNotFound(
        val attempts: List<String>,
        val failureSummary: String,
    ) : BackendConnectionError(
        message = "Transmission RPC path not found. Tried ${attempts.joinToString()}. $failureSummary",
    )

    /**
     * The HTTPS setting doesn't match the server: TLS against a plain-HTTP port, or
     * plain HTTP against a TLS port. Reported on its own because the raw failures
     * ("Unable to parse TLS packet header", "RPC path not found") point users at
     * the wrong setting.
     */
    data class HttpsMismatch(
        val backendType: ServerBackendType,
        val usedHttps: Boolean,
        val detail: String,
    ) : BackendConnectionError(
        message = if (usedHttps) {
            "$backendType server does not accept HTTPS on this port; turn HTTPS off. $detail"
        } else {
            "$backendType server requires HTTPS on this port; turn HTTPS on. $detail"
        },
    )

    /** The saved password exists but can't be decrypted; the user must re-enter it. */
    data class StoredPasswordUnreadable(
        val backendType: ServerBackendType,
    ) : BackendConnectionError(
        message = "The saved $backendType password could not be decrypted; re-enter it in the server settings.",
    )

    /** "Test connection" got no answer in time. */
    data class TestTimedOut(
        val seconds: Long,
    ) : BackendConnectionError(
        message = "No response within $seconds s. Check the host, port and HTTPS setting.",
    )

    data class AuthFailed(
        val backendType: ServerBackendType,
        val detail: String,
    ) : BackendConnectionError(
        message = "$backendType auth failed. $detail",
    )
}

private val TLS_AGAINST_PLAIN_HTTP_MARKERS = listOf(
    "unable to parse tls packet header",
    "not an ssl/tls record",
    "unrecognized ssl message",
    "plaintext connection",
)
private val PLAIN_HTTP_AGAINST_TLS_MARKERS = listOf(
    "plain http request was sent to https port",
    "http request to an https server",
)

/** True when [failureText] shows the HTTPS setting ([usedHttps]) doesn't match the server. */
internal fun looksLikeHttpsMismatch(usedHttps: Boolean, failureText: String): Boolean {
    val normalized = failureText.lowercase(java.util.Locale.US)
    val markers = if (usedHttps) TLS_AGAINST_PLAIN_HTTP_MARKERS else PLAIN_HTTP_AGAINST_TLS_MARKERS
    return markers.any { it in normalized }
}

data class TransmissionRpcProbeResult(
    val resolvedUrl: String,
    val attempts: List<String>,
    val failureSummary: String,
)
