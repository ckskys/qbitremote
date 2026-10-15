package com.hjw.qbremote.data

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

class QbRepositoryRetryContractTest {
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
    fun `403 on read endpoint triggers relogin and retry`() = runBlocking {
        val infoCalls = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/api/v2/auth/login" -> loginOkResponse()
                    "/api/v2/transfer/info" ->
                        if (infoCalls.incrementAndGet() == 1) {
                            MockResponse().setResponseCode(403)
                        } else {
                            MockResponse().setResponseCode(200).setBody(TRANSFER_INFO_BODY)
                        }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val repository = QbRepository()
        repository.connect(testSettings()).getOrThrow()

        val result = repository.fetchTransferInfo()

        assertTrue(result.isSuccess)
        assertEquals(123L, result.getOrThrow().downloadSpeed)
        val paths = (1..4).map { server.takeRequest().path }
        assertEquals(
            listOf(
                "/api/v2/auth/login",
                "/api/v2/transfer/info",
                "/api/v2/auth/login",
                "/api/v2/transfer/info",
            ),
            paths,
        )
    }

    @Test
    fun `5xx is retried with backoff until success`() = runBlocking {
        val infoCalls = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/api/v2/auth/login" -> loginOkResponse()
                    "/api/v2/transfer/info" ->
                        if (infoCalls.incrementAndGet() == 1) {
                            MockResponse().setResponseCode(500)
                        } else {
                            MockResponse().setResponseCode(200).setBody(TRANSFER_INFO_BODY)
                        }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val repository = QbRepository()
        repository.connect(testSettings()).getOrThrow()

        val result = repository.fetchTransferInfo()

        assertTrue(result.isSuccess)
        assertEquals(2, infoCalls.get())
    }

    @Test
    fun `non-retryable 4xx fails immediately without retry`() = runBlocking {
        val infoCalls = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/api/v2/auth/login" -> loginOkResponse()
                    "/api/v2/transfer/info" -> {
                        infoCalls.incrementAndGet()
                        MockResponse().setResponseCode(400)
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val repository = QbRepository()
        repository.connect(testSettings()).getOrThrow()

        val result = repository.fetchTransferInfo()

        assertTrue(result.isFailure)
        assertEquals(400, (result.exceptionOrNull() as HttpException).code())
        assertEquals(1, infoCalls.get())
    }

    @Test
    fun `persistent 5xx gives up after three attempts`() = runBlocking {
        val infoCalls = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/api/v2/auth/login" -> loginOkResponse()
                    "/api/v2/transfer/info" -> {
                        infoCalls.incrementAndGet()
                        MockResponse().setResponseCode(500)
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val repository = QbRepository()
        repository.connect(testSettings()).getOrThrow()

        val result = repository.fetchTransferInfo()

        assertTrue(result.isFailure)
        assertEquals(500, (result.exceptionOrNull() as HttpException).code())
        assertEquals(3, infoCalls.get())
    }

    @Test
    fun `write endpoint 401 triggers relogin and retry`() = runBlocking {
        val loginCalls = AtomicInteger(0)
        val deleteCalls = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/api/v2/auth/login" -> {
                        loginCalls.incrementAndGet()
                        loginOkResponse()
                    }
                    "/api/v2/torrents/delete" ->
                        if (deleteCalls.incrementAndGet() == 1) {
                            MockResponse().setResponseCode(401)
                        } else {
                            MockResponse().setResponseCode(200)
                        }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val repository = QbRepository()
        repository.connect(testSettings()).getOrThrow()

        val result = repository.deleteTorrent("hash-1", deleteFiles = false)

        assertTrue(result.isSuccess)
        assertEquals(2, loginCalls.get())
        assertEquals(2, deleteCalls.get())
    }

    @Test
    fun `stop 404 falls back to legacy pause endpoint`() = runBlocking {
        val stopCalls = AtomicInteger(0)
        val pauseCalls = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/api/v2/auth/login" -> loginOkResponse()
                    "/api/v2/torrents/stop" -> {
                        stopCalls.incrementAndGet()
                        MockResponse().setResponseCode(404)
                    }
                    "/api/v2/torrents/pause" -> {
                        pauseCalls.incrementAndGet()
                        MockResponse().setResponseCode(200)
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val repository = QbRepository()
        repository.connect(testSettings()).getOrThrow()

        val result = repository.pauseTorrent("hash-1")

        assertTrue(result.isSuccess)
        assertEquals(1, stopCalls.get())
        assertEquals(1, pauseCalls.get())
    }

    @Test
    fun `start 405 falls back to legacy resume endpoint`() = runBlocking {
        val startCalls = AtomicInteger(0)
        val resumeCalls = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/api/v2/auth/login" -> loginOkResponse()
                    "/api/v2/torrents/start" -> {
                        startCalls.incrementAndGet()
                        MockResponse().setResponseCode(405)
                    }
                    "/api/v2/torrents/resume" -> {
                        resumeCalls.incrementAndGet()
                        MockResponse().setResponseCode(200)
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val repository = QbRepository()
        repository.connect(testSettings()).getOrThrow()

        val result = repository.resumeTorrent("hash-1")

        assertTrue(result.isSuccess)
        assertEquals(1, startCalls.get())
        assertEquals(1, resumeCalls.get())
    }

    @Test
    fun `unrelated proxy cookie does not evict the session cookie`() = runBlocking {
        val loginCalls = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/api/v2/auth/login" -> {
                        loginCalls.incrementAndGet()
                        loginOkResponse()
                    }
                    "/api/v2/transfer/info" ->
                        if (request.getHeader("Cookie").orEmpty().contains("SID=test-session")) {
                            MockResponse()
                                .setResponseCode(200)
                                .addHeader("Set-Cookie", "proxy_sticky=node-1")
                                .setBody(TRANSFER_INFO_BODY)
                        } else {
                            MockResponse().setResponseCode(403)
                        }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val repository = QbRepository()
        repository.connect(testSettings()).getOrThrow()

        repository.fetchTransferInfo().getOrThrow()
        repository.fetchTransferInfo().getOrThrow()

        assertEquals(1, loginCalls.get())
        server.takeRequest()
        server.takeRequest()
        val cookieHeader = server.takeRequest().getHeader("Cookie").orEmpty()
        assertTrue(cookieHeader.contains("SID=test-session"))
        assertTrue(cookieHeader.contains("proxy_sticky=node-1"))
    }

    @Test
    fun `concurrent requests on an expired session share a single relogin`() = runBlocking {
        val loginCalls = AtomicInteger(0)
        val bothStaleRequestsArrived = CountDownLatch(2)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/api/v2/auth/login" -> {
                        val sessionNumber = loginCalls.incrementAndGet()
                        MockResponse()
                            .setResponseCode(200)
                            .addHeader("Set-Cookie", "SID=session-$sessionNumber; HttpOnly")
                            .setBody("Ok.")
                    }
                    "/api/v2/transfer/info" ->
                        if (request.getHeader("Cookie").orEmpty().contains("SID=session-1")) {
                            // Hold both requests on the expired session until each has
                            // arrived, so their 403s are genuinely concurrent.
                            bothStaleRequestsArrived.countDown()
                            bothStaleRequestsArrived.await(5, TimeUnit.SECONDS)
                            MockResponse().setResponseCode(403)
                        } else {
                            MockResponse().setResponseCode(200).setBody(TRANSFER_INFO_BODY)
                        }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val repository = QbRepository()
        repository.connect(testSettings()).getOrThrow()

        val results = listOf(
            async { repository.fetchTransferInfo() },
            async { repository.fetchTransferInfo() },
        ).awaitAll()

        assertTrue(results.all { it.isSuccess })
        // One login from connect() plus exactly one shared relogin.
        assertEquals(2, loginCalls.get())
    }

    @Test
    fun `setTorrentTags adds new tags before removing only the dropped ones`() = runBlocking {
        val tagCalls = Collections.synchronizedList(mutableListOf<String>())
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/api/v2/auth/login" -> loginOkResponse()
                    "/api/v2/torrents/addTags", "/api/v2/torrents/removeTags" -> {
                        val tags = request.body.readUtf8()
                            .split('&')
                            .first { it.startsWith("tags=") }
                            .removePrefix("tags=")
                        tagCalls += "${request.path!!.substringAfterLast('/')}:${java.net.URLDecoder.decode(tags, "UTF-8")}"
                        MockResponse().setResponseCode(200)
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val repository = QbRepository()
        repository.connect(testSettings()).getOrThrow()

        val result = repository.setTorrentTags("hash-1", oldTags = "keep, drop", newTags = "keep, new")

        assertTrue(result.isSuccess)
        assertEquals(listOf("addTags:keep,new", "removeTags:drop"), tagCalls)
    }

    @Test
    fun `one torrent failing its peer fetch does not drop the other torrents`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                return when (url.encodedPath) {
                    "/api/v2/auth/login" -> loginOkResponse()
                    "/api/v2/sync/torrentPeers" ->
                        if (url.queryParameter("hash") == "bad") {
                            MockResponse().setResponseCode(400)
                        } else {
                            MockResponse().setResponseCode(200).setBody(
                                """{"peers":{"1.2.3.4:5000":{"ip":"1.2.3.4","port":5000,"country_code":"jp","uploaded":42}}}"""
                            )
                        }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val repository = QbRepository()
        repository.connect(testSettings()).getOrThrow()

        val snapshots = repository.fetchCountryPeerSnapshots(listOf("good", "bad")).getOrThrow()

        assertEquals(1, snapshots.size)
        assertEquals("JP", snapshots.single().countryCode)
        assertEquals(42L, snapshots.single().uploadedBytes)
    }

    @Test
    fun `fetchDashboard combines concurrent reads and tolerates a failing maindata call`() = runBlocking {
        val maindataFails = java.util.concurrent.atomic.AtomicBoolean(false)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.requestUrl!!.encodedPath) {
                    "/api/v2/auth/login" -> loginOkResponse()
                    "/api/v2/transfer/info" -> MockResponse().setResponseCode(200).setBody(TRANSFER_INFO_BODY)
                    "/api/v2/sync/maindata" ->
                        if (maindataFails.get()) {
                            MockResponse().setResponseCode(400)
                        } else {
                            MockResponse().setResponseCode(200)
                                .setBody("""{"server_state":{"free_space_on_disk":9999}}""")
                        }
                    "/api/v2/torrents/info" -> MockResponse().setResponseCode(200).setBody(
                        """[{"hash":"h1","name":"Ubuntu ISO","state":"uploading","size":1024,"save_path":"/dl","added_on":1}]"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val repository = QbRepository()
        repository.connect(testSettings()).getOrThrow()

        val dashboard = repository.fetchDashboard().getOrThrow()
        assertEquals(456L, dashboard.transferInfo.uploadSpeed)
        assertEquals(9999L, dashboard.transferInfo.freeSpaceOnDisk)
        assertEquals(listOf("h1"), dashboard.torrents.map { it.hash })

        maindataFails.set(true)
        val withoutMaindata = repository.fetchDashboard().getOrThrow()
        assertEquals(5000L, withoutMaindata.transferInfo.freeSpaceOnDisk)
        assertEquals(listOf("h1"), withoutMaindata.torrents.map { it.hash })
    }

    @Test
    fun `server version is fetched once per session`() = runBlocking {
        val versionCalls = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/api/v2/auth/login" -> loginOkResponse()
                    "/api/v2/app/version" -> {
                        versionCalls.incrementAndGet()
                        MockResponse().setResponseCode(200).setBody("v5.0.1")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val repository = QbRepository()
        repository.connect(testSettings()).getOrThrow()

        assertEquals("v5.0.1", repository.fetchServerVersion().getOrThrow())
        assertEquals("v5.0.1", repository.fetchServerVersion().getOrThrow())
        assertEquals(1, versionCalls.get())

        // A new login (e.g. after a server restart/upgrade) fetches it again.
        repository.connect(testSettings()).getOrThrow()
        repository.fetchServerVersion().getOrThrow()
        assertEquals(2, versionCalls.get())
    }

    private fun loginOkResponse(): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .addHeader("Set-Cookie", "SID=test-session; HttpOnly")
            .setBody("Ok.")
    }

    private fun testSettings(): ConnectionSettings {
        return ConnectionSettings(
            host = server.hostName,
            port = server.port,
            useHttps = false,
            username = "admin",
            password = "secret",
            serverBackendType = ServerBackendType.QBITTORRENT,
        )
    }

    private companion object {
        const val TRANSFER_INFO_BODY =
            """{"dl_info_speed":123,"up_info_speed":456,"dl_info_data":1000,"up_info_data":2000,"free_space_on_disk":5000,"dht_nodes":3}"""
    }
}
