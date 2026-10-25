package com.playertwo.controlegithub

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubRepositorySocialTest {
    @Test
    fun loadStateReturnsFalseWhenStarAndSubscriptionReturn404() = SocialServer(
        starStatus = 404,
        subscriptionStatus = 404
    ).use { server ->
        val social = GitHubRepositorySocialClient(server.client(), "token-123")
        val result = social.loadState("acme", "Mobile")

        assertEquals(
            GitHubRepositorySocialResult.Loaded(GitHubRepositorySocialState(starred = false, watching = false)),
            result
        )
        assertEquals(
            listOf("GET /user/starred/acme/Mobile", "GET /repos/acme/Mobile/subscription"),
            server.requests
        )
    }

    @Test
    fun loadStateReturnsTrueWhenStarReturns204AndSubscriptionIsSubscribed() = SocialServer(
        starStatus = 204,
        subscriptionStatus = 200,
        subscriptionBody = """{"subscribed":true,"ignored":false}"""
    ).use { server ->
        val social = GitHubRepositorySocialClient(server.client(), "token-123")
        val result = social.loadState("acme", "Mobile")

        assertEquals(
            GitHubRepositorySocialResult.Loaded(GitHubRepositorySocialState(starred = true, watching = true)),
            result
        )
    }

    @Test
    fun setStarredAndSetWatchingSendPutAndDeleteRequests() = SocialServer().use { server ->
        val social = GitHubRepositorySocialClient(server.client(), "token-123")

        val starOn = social.setStarred("acme", "Mobile", targetStarred = true, currentWatching = false)
        assertEquals(
            GitHubRepositorySocialResult.Loaded(GitHubRepositorySocialState(starred = true, watching = false)),
            starOn
        )
        assertTrue(server.requests.contains("PUT /user/starred/acme/Mobile"))

        val watchOn = social.setWatching("acme", "Mobile", targetWatching = true, currentStarred = true)
        assertEquals(
            GitHubRepositorySocialResult.Loaded(GitHubRepositorySocialState(starred = true, watching = true)),
            watchOn
        )
        assertTrue(server.requests.contains("PUT /repos/acme/Mobile/subscription"))
        assertEquals("""{"subscribed":true}""", server.lastBody)

        val starOff = social.setStarred("acme", "Mobile", targetStarred = false, currentWatching = true)
        assertEquals(
            GitHubRepositorySocialResult.Loaded(GitHubRepositorySocialState(starred = false, watching = true)),
            starOff
        )
        assertTrue(server.requests.contains("DELETE /user/starred/acme/Mobile"))

        val watchOff = social.setWatching("acme", "Mobile", targetWatching = false, currentStarred = false)
        assertEquals(
            GitHubRepositorySocialResult.Loaded(GitHubRepositorySocialState(starred = false, watching = false)),
            watchOff
        )
        assertTrue(server.requests.contains("DELETE /repos/acme/Mobile/subscription"))
    }

    @Test
    fun returnsFailureOnHttpErrorsOrInvalidPathSegments() = SocialServer(
        starPutStatus = 403
    ).use { server ->
        val social = GitHubRepositorySocialClient(server.client(), "token-123")

        val forbidden = social.setStarred("acme", "Mobile", targetStarred = true, currentWatching = false)
        assertTrue(forbidden is GitHubRepositorySocialResult.Failed)
        assertEquals(GitHubHttpError.FORBIDDEN, (forbidden as GitHubRepositorySocialResult.Failed).error)

        val invalidOwner = social.loadState("../evil", "Mobile")
        assertTrue(invalidOwner is GitHubRepositorySocialResult.Failed)
        assertFalse(server.requests.any { it.contains("evil") })
    }

    private class SocialServer(
        private val starStatus: Int = 404,
        private val subscriptionStatus: Int = 404,
        private val subscriptionBody: String = """{"subscribed":false}""",
        private val starPutStatus: Int = 204
    ) : AutoCloseable {
        val requests = CopyOnWriteArrayList<String>()
        @Volatile var lastBody: String = ""

        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val method = exchange.requestMethod
                val path = exchange.requestURI.rawPath
                requests.add("$method $path")
                lastBody = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

                val (status, body) = when ("$method $path") {
                    "GET /user/starred/acme/Mobile" -> starStatus to ""
                    "PUT /user/starred/acme/Mobile" -> starPutStatus to ""
                    "DELETE /user/starred/acme/Mobile" -> 204 to ""
                    "GET /repos/acme/Mobile/subscription" -> subscriptionStatus to subscriptionBody
                    "PUT /repos/acme/Mobile/subscription" -> 200 to """{"subscribed":true}"""
                    "DELETE /repos/acme/Mobile/subscription" -> 204 to ""
                    else -> 404 to "{}"
                }
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                if (status == 204) {
                    exchange.sendResponseHeaders(204, -1)
                } else {
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(status, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
                exchange.close()
            }
            start()
        }

        fun client() = GitHubHttpClient(URI.create("http://127.0.0.1:${server.address.port}/"))

        override fun close() {
            server.stop(0)
        }
    }
}
