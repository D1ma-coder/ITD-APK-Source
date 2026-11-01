package com.itd.app.data.remote

import com.itd.app.data.local.SessionStorage
import com.itd.app.data.model.AuthState
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ApiClientTest {
    private class MemorySession : SessionStorage {
        override var accessToken: String? = "expired"
        override var refreshToken: String? = "refresh-one"
        override val deviceId = "test-device"
        override fun clear() {
            accessToken = null
            refreshToken = null
        }
    }

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

    private fun client(session: SessionStorage) =
        ApiClient(session, server.url("/").toString().trimEnd('/'), "test-agent")

    @Test
    fun expiredAccessTokenRefreshesOnceAndRotatesCookie() = runBlocking {
        val session = MemorySession()
        val refreshes = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                assertEquals("android", request.getHeader("X-Platform"))
                assertEquals("1.1.0", request.getHeader("X-App-Version"))
                assertEquals("test-device", request.getHeader("X-Device-Id"))
                assertEquals("test-agent", request.getHeader("User-Agent"))
                return when (request.path) {
                    "/api/v1/auth/refresh" -> {
                        refreshes.incrementAndGet()
                        assertEquals("refresh_token=refresh-one", request.getHeader("Cookie"))
                        MockResponse()
                            .setBody("""{"accessToken":"current"}""")
                            .addHeader("Set-Cookie", "refresh_token=refresh-two; Path=/api/v1/auth; HttpOnly")
                    }
                    else -> if (request.getHeader("Authorization") == "Bearer current") {
                        MockResponse().setBody("""{"authenticated":true}""")
                    } else {
                        MockResponse().setResponseCode(401).setBody("""{"error":{"code":"UNAUTHORIZED","message":"expired"}}""")
                    }
                }
            }
        }
        val client = client(session)
        try {
            val results = (1..5).map { async { client.get<AuthState>("/api/profile") } }.awaitAll()
            assertTrue(results.all { it.authenticated })
            assertEquals(1, refreshes.get())
            assertEquals("current", session.accessToken)
            assertEquals("refresh-two", session.refreshToken)
        } finally {
            client.close()
        }
    }

    @Test
    fun rejectedRefreshRetriesOnceAndSurfacesTheError() = runBlocking {
        val session = MemorySession()
        val requests = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.incrementAndGet()
                return MockResponse().setResponseCode(401).setBody("""{"error":{"code":"UNAUTHORIZED","message":"session expired"}}""")
            }
        }
        val client = client(session)
        try {
            try {
                client.get<AuthState>("/api/profile")
                fail("Expected an API error")
            } catch (error: ApiException) {
                assertEquals(401, error.status)
                assertEquals("UNAUTHORIZED", error.code)
            }
            assertEquals(3, requests.get())
            assertEquals("refresh-one", session.refreshToken)
        } finally {
            client.close()
        }
    }

    @Test
    fun loginFailureDoesNotRefreshOrReplayCredentials() = runBlocking {
        val session = MemorySession()
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"error":{"code":"INVALID_CREDENTIALS","message":"wrong password","attemptsLeft":2}}"""),
        )
        val client = client(session)
        try {
            try {
                client.post<AuthState>("/api/v1/auth/sign-in")
                fail("Expected an API error")
            } catch (error: ApiException) {
                assertEquals("INVALID_CREDENTIALS", error.code)
                assertEquals(2, error.attemptsLeft)
            }
            assertEquals(1, server.requestCount)
            assertEquals("refresh-one", session.refreshToken)
        } finally {
            client.close()
        }
    }

    @Test
    fun revokedSessionAtStartupClearsStorage() = runBlocking {
        val session = MemorySession()
        server.enqueue(MockResponse().setResponseCode(401))
        val client = client(session)
        var expired = 0
        client.onSessionExpired = { expired++ }
        try {
            assertEquals(false, client.refreshSession())
            assertNull(session.refreshToken)
            assertEquals(1, expired)
        } finally {
            client.close()
        }
    }
}
