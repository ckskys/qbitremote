package com.hjw.qbremote.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpsMismatchDetectionTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun looksLikeHttpsMismatch_matchesOnlyTheDirectionThatWasUsed() {
        assertTrue(looksLikeHttpsMismatch(usedHttps = true, failureText = "x => Unable to parse TLS packet header"))
        assertFalse(looksLikeHttpsMismatch(usedHttps = false, failureText = "x => Unable to parse TLS packet header"))
        assertTrue(
            looksLikeHttpsMismatch(
                usedHttps = false,
                failureText = "HTTP 400: The plain HTTP request was sent to HTTPS port",
            ),
        )
        assertFalse(looksLikeHttpsMismatch(usedHttps = true, failureText = "HTTP 404: not found"))
    }

    @Test
    fun transmissionOverHttpsAgainstPlainHttpServerReportsHttpsMismatch() = runBlocking {
        val result = withPlainHttpServer { port ->
            TransmissionBackend().connect(
                settings(ServerBackendType.TRANSMISSION, useHttps = true).copy(host = "127.0.0.1", port = port),
            )
        }

        val error = result.exceptionOrNull()
        assertTrue("got $error", error is BackendConnectionError.HttpsMismatch)
        assertTrue((error as BackendConnectionError.HttpsMismatch).usedHttps)
    }

    @Test
    fun qbittorrentOverHttpsAgainstPlainHttpServerReportsHttpsMismatch() = runBlocking {
        val result = withPlainHttpServer { port ->
            QbRepository().connect(
                settings(ServerBackendType.QBITTORRENT, useHttps = true).copy(host = "127.0.0.1", port = port),
            )
        }

        val error = result.exceptionOrNull()
        assertTrue("got $error", error is BackendConnectionError.HttpsMismatch)
        assertEquals(ServerBackendType.QBITTORRENT, (error as BackendConnectionError.HttpsMismatch).backendType)
    }

    @Test
    fun unreadableStoredPasswordFailsFastWithoutContactingTheServer() = runBlocking {
        val repository = TorrentRepository()
        val unreadable = settings(ServerBackendType.QBITTORRENT, useHttps = false)
            .copy(password = "", passwordUnreadable = true)

        val result = repository.connect("profile-1", unreadable)

        assertTrue(result.exceptionOrNull() is BackendConnectionError.StoredPasswordUnreadable)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aNewlyTypedPasswordOverridesTheUnreadableFlag() = runBlocking {
        repeat(4) {
            server.enqueue(
                MockResponse().setResponseCode(200).addHeader("Set-Cookie", "SID=s; HttpOnly").setBody("Ok."),
            )
        }
        val repository = TorrentRepository()
        val retyped = settings(ServerBackendType.QBITTORRENT, useHttps = false)
            .copy(password = "new-secret", passwordUnreadable = true)

        val result = repository.connect("profile-1", retyped)

        assertTrue("got ${result.exceptionOrNull()}", result.isSuccess)
    }

    /**
     * A plain-HTTP server that answers anything (including a TLS ClientHello) with
     * an HTTP 400, like real web servers do. MockWebServer instead waits silently
     * for a request line, which only produces a read timeout.
     */
    private suspend fun <T> withPlainHttpServer(block: suspend (port: Int) -> T): T {
        val serverSocket = java.net.ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
        val acceptor = Thread {
            while (!serverSocket.isClosed) {
                val socket = runCatching { serverSocket.accept() }.getOrNull() ?: break
                runCatching {
                    socket.use {
                        // Read the client's first bytes (the TLS ClientHello) before
                        // answering, then half-close: closing first resets the
                        // connection instead of surfacing the TLS parse error.
                        it.soTimeout = 2_000
                        it.getInputStream().read(ByteArray(4096))
                        it.getOutputStream().write(
                            "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                                .toByteArray(),
                        )
                        it.getOutputStream().flush()
                        it.shutdownOutput()
                        runCatching { while (it.getInputStream().read(ByteArray(4096)) >= 0) Unit }
                    }
                }
            }
        }.apply { isDaemon = true; start() }
        return try {
            block(serverSocket.localPort)
        } finally {
            serverSocket.close()
            acceptor.join(1_000)
        }
    }

    private fun settings(type: ServerBackendType, useHttps: Boolean) = ConnectionSettings(
        host = server.hostName,
        port = server.port,
        useHttps = useHttps,
        username = "admin",
        password = "secret",
        serverBackendType = type,
    )
}
