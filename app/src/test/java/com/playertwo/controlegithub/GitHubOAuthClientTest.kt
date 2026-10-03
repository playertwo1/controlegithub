package com.playertwo.controlegithub

import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubOAuthClientTest {
    @Test
    fun requestsDeviceCodeWithConfiguredScopes() = fixture("device_code=private-code&user_code=ABCD-EFGH&verification_uri=https%3A%2F%2Fgithub.com%2Flogin%2Fdevice&expires_in=900&interval=5").use { fixture ->
        val code = fixture.client().requestDeviceCode().execute()
        assertEquals("private-code", code.deviceCode)
        assertEquals("ABCD-EFGH", code.userCode)
        assertEquals("https://github.com/login/device", code.verificationUri)
        assertEquals(5, code.intervalSeconds)
        assertTrue(fixture.requestBody.get().contains("client_id=public-client"))
        assertTrue(fixture.requestBody.get().contains("scope=read%3Auser+repo+notifications"))
    }

    @Test
    fun pollingHonorsPendingSlowDownAuthorizationDenialAndExpiry() = fixture("error=authorization_pending").use { fixture ->
        val client = fixture.client()
        assertEquals(DevicePoll.Pending(5), client.poll("device-code", 5).execute())

        fixture.response = "error=slow_down"
        assertEquals(DevicePoll.Pending(10), client.poll("device-code", 5).execute())

        fixture.response = "access_token=private-token"
        val authorized = client.poll("device-code", 10).execute()
        assertEquals(DevicePoll.Authorized("private-token"), authorized)
        assertEquals("Authorized", authorized.toString())

        fixture.response = "error=access_denied"
        assertEquals(DevicePoll.Denied, client.poll("device-code", 10).execute())

        fixture.response = "error=expired_token"
        assertEquals(DevicePoll.Expired, client.poll("device-code", 10).execute())
    }

    @Test
    fun malformedDeviceResponseFailsWithoutShowingResponseBody() = fixture("device_code=secret&user_code=ABCD-EFGH&verification_uri=https%3A%2F%2Fevil.example%2F&expires_in=900&interval=5").use { fixture ->
        val error = assertThrows(IOException::class.java) { fixture.client().requestDeviceCode().execute() }
        assertFalse(error.message.orEmpty().contains("secret"))
    }

    @Test
    fun userRequestUsesBearerAndOAuthHttpCallCanBeCancelled() = fixture("ok").use { fixture ->
        val result = fixture.client().user("private-token").execute() as GitHubHttpResult.Success
        assertEquals("Bearer private-token", fixture.requestHeaders.get().getFirst("Authorization"))
        assertEquals("ok", result.body)

        val blocked = fixture.blockNextRequest()
        val call = fixture.client().requestDeviceCode()
        val failure = AtomicReference<Throwable?>()
        val worker = Thread {
            try { call.execute() } catch (error: Throwable) { failure.set(error) }
        }
        worker.start()
        assertTrue(blocked.first.await(2, TimeUnit.SECONDS))
        call.cancel()
        worker.join(2_000)
        blocked.second.countDown()
        assertFalse("cancellation should end the HTTP call", worker.isAlive)
        assertTrue(failure.get() is IOException)
    }

    private fun fixture(body: String) = Fixture(body)

    private class Fixture(initialResponse: String) : AutoCloseable {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requestBody = AtomicReference("")
        val requestHeaders = AtomicReference<com.sun.net.httpserver.Headers>()
        @Volatile var response = initialResponse
        @Volatile private var gate: Pair<CountDownLatch, CountDownLatch>? = null

        init {
            server.createContext("/") { exchange ->
                requestHeaders.set(exchange.requestHeaders)
                requestBody.set(exchange.requestBody.bufferedReader().use { it.readText() })
                val activeGate = gate
                activeGate?.first?.countDown()
                activeGate?.second?.await(3, TimeUnit.SECONDS)
                val bytes = response.toByteArray()
                try {
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                } catch (_: IOException) {
                    exchange.close()
                }
            }
            server.start()
        }

        fun client() = GitHubOAuthClient(
            clientId = "public-client",
            authBaseUrl = URI.create("http://127.0.0.1:${server.address.port}/login/"),
            apiClient = GitHubHttpClient(URI.create("http://127.0.0.1:${server.address.port}/")),
            connectTimeoutMillis = 1_000,
            readTimeoutMillis = 1_000
        )

        fun blockNextRequest(): Pair<CountDownLatch, CountDownLatch> =
            (CountDownLatch(1) to CountDownLatch(1)).also { gate = it }

        override fun close() = server.stop(0)
    }
}
