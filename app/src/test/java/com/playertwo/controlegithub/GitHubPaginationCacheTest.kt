package com.playertwo.controlegithub

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URI

class GitHubPaginationCacheTest {

    private data class TestItem(val id: String, val title: String)

    private fun decodeItems(payload: String): List<TestItem> {
        if (payload.isBlank()) return emptyList()
        return payload.split(";").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size >= 2) TestItem(parts[0].trim(), parts[1].trim()) else null
        }
    }

    private class InMemoryCacheStore : GitHubCacheStore {
        private val entries = mutableMapOf<Pair<String, String>, CachedResponse>()

        override fun get(accountLogin: String, resourceKey: String): CachedResponse? {
            return entries[accountLogin to resourceKey]
        }

        override fun put(
            accountLogin: String,
            resourceKey: String,
            payload: String,
            statusCode: Int,
            ttlMillis: Long,
            etag: String?
        ): Boolean {
            val now = System.currentTimeMillis()
            entries[accountLogin to resourceKey] = CachedResponse(
                accountLogin = accountLogin,
                resourceKey = resourceKey,
                payload = payload,
                statusCode = statusCode,
                createdAtMillis = now,
                expiresAtMillis = now + ttlMillis,
                etag = etag
            )
            return true
        }

        override fun updateTimestamp(
            accountLogin: String,
            resourceKey: String,
            revalidatedAtMillis: Long,
            ttlMillis: Long,
            etag: String?
        ): Boolean {
            val current = entries[accountLogin to resourceKey] ?: return false
            entries[accountLogin to resourceKey] = current.copy(
                createdAtMillis = revalidatedAtMillis,
                expiresAtMillis = revalidatedAtMillis + ttlMillis,
                etag = etag ?: current.etag
            )
            return true
        }

        override fun clear(accountLogin: String): Boolean {
            entries.keys.removeAll { it.first == accountLogin }
            return true
        }

        override fun clearAll(): Boolean {
            entries.clear()
            return true
        }
    }

    @Test
    fun cachesSuccessfulPageOneResponse() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val bytes = "1:Alpha".toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val cache = InMemoryCacheStore()
            val client = GitHubHttpClient(
                baseUrl = URI.create("http://127.0.0.1:${server.address.port}/"),
                connectTimeoutMillis = 1_000,
                readTimeoutMillis = 1_000
            )

            val paginator = GitHubPaginator(
                client = client,
                firstPath = "/user/repos",
                accessToken = "token_abc",
                decode = ::decodeItems,
                itemKey = TestItem::id,
                cacheStore = cache,
                accountLogin = "user_alpha"
            )

            val result = paginator.loadNext()
            assertTrue("Expected Loaded but was: $result", result is GitHubPageResult.Loaded)
            assertFalse((result as GitHubPageResult.Loaded).isOfflineCache)
            assertEquals(1, result.items.size)

            val cached = cache.get("user_alpha", "/user/repos")
            assertNotNull(cached)
            assertEquals("1:Alpha", cached?.payload)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun fallsBackToOfflineCacheOnNetworkFailure() {
        val cache = InMemoryCacheStore()
        cache.put("user_alpha", "/user/repos", "1:Cached Alpha")

        val client = GitHubHttpClient(
            baseUrl = URI.create("http://127.0.0.1:65534/"),
            connectTimeoutMillis = 500,
            readTimeoutMillis = 500
        )

        val paginator = GitHubPaginator(
            client = client,
            firstPath = "/user/repos",
            accessToken = "token_abc",
            decode = ::decodeItems,
            itemKey = TestItem::id,
            cacheStore = cache,
            accountLogin = "user_alpha"
        )

        val result = paginator.loadNext()
        assertTrue("Expected Loaded but was: $result", result is GitHubPageResult.Loaded)
        val loaded = result as GitHubPageResult.Loaded
        assertTrue(loaded.isOfflineCache)
        assertNotNull(loaded.cachedAtMillis)
        assertEquals(1, loaded.items.size)
        assertEquals("Cached Alpha", loaded.items.first().title)
    }

    @Test
    fun returnsHelpfulOfflineErrorMessageWhenNetworkFailsAndNoCache() {
        val cache = InMemoryCacheStore()
        val client = GitHubHttpClient(
            baseUrl = URI.create("http://127.0.0.1:65534/"),
            connectTimeoutMillis = 500,
            readTimeoutMillis = 500
        )

        val paginator = GitHubPaginator(
            client = client,
            firstPath = "/user/repos",
            accessToken = "token_abc",
            decode = ::decodeItems,
            itemKey = TestItem::id,
            cacheStore = cache,
            accountLogin = "user_alpha"
        )

        val result = paginator.loadNext()
        assertTrue(result is GitHubPageResult.Failed)
        val failed = result as GitHubPageResult.Failed
        assertEquals("Sem conexão com a rede e nenhum dado salvo para esta consulta.", failed.message)
    }

    @Test
    fun respectsAccountIsolationOnOfflineFallback() {
        val cache = InMemoryCacheStore()
        cache.put("user_beta", "/user/repos", "2:Beta Secret")

        val client = GitHubHttpClient(
            baseUrl = URI.create("http://127.0.0.1:65534/"),
            connectTimeoutMillis = 500,
            readTimeoutMillis = 500
        )

        val paginator = GitHubPaginator(
            client = client,
            firstPath = "/user/repos",
            accessToken = "token_abc",
            decode = ::decodeItems,
            itemKey = TestItem::id,
            cacheStore = cache,
            accountLogin = "user_alpha"
        )

        val result = paginator.loadNext()
        assertTrue(result is GitHubPageResult.Failed)
        val failed = result as GitHubPageResult.Failed
        assertEquals("Sem conexão com a rede e nenhum dado salvo para esta consulta.", failed.message)
    }

    @Test
    fun sendsIfNoneMatchWhenCachedEtagAvailable() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var receivedIfNoneMatch: String? = null
        server.createContext("/") { exchange ->
            receivedIfNoneMatch = exchange.requestHeaders.getFirst("If-None-Match")
            val bytes = "1:Alpha".toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("ETag", "\"etag-v1\"")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val cache = InMemoryCacheStore()
            cache.put("user_alpha", "/user/repos", "1:Alpha", etag = "\"etag-v0\"")
            val client = GitHubHttpClient(
                baseUrl = URI.create("http://127.0.0.1:${server.address.port}/"),
                connectTimeoutMillis = 1_000,
                readTimeoutMillis = 1_000
            )
            val paginator = GitHubPaginator(
                client = client,
                firstPath = "/user/repos",
                accessToken = "token_abc",
                decode = ::decodeItems,
                itemKey = TestItem::id,
                cacheStore = cache,
                accountLogin = "user_alpha"
            )

            paginator.loadNext()
            assertEquals("\"etag-v0\"", receivedIfNoneMatch)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun notModified304PreservesCachedItemsAndUpdatesTimestamp() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            exchange.responseHeaders.add("ETag", "\"etag-v2\"")
            exchange.sendResponseHeaders(304, -1)
        }
        server.start()
        try {
            val cache = InMemoryCacheStore()
            val oldTime = 100_000L
            cache.put("user_alpha", "/user/repos", "1:Alpha Cached", etag = "\"etag-v1\"")
            cache.updateTimestamp("user_alpha", "/user/repos", revalidatedAtMillis = oldTime, etag = "\"etag-v1\"")

            val client = GitHubHttpClient(
                baseUrl = URI.create("http://127.0.0.1:${server.address.port}/"),
                connectTimeoutMillis = 1_000,
                readTimeoutMillis = 1_000
            )
            val paginator = GitHubPaginator(
                client = client,
                firstPath = "/user/repos",
                accessToken = "token_abc",
                decode = ::decodeItems,
                itemKey = TestItem::id,
                cacheStore = cache,
                accountLogin = "user_alpha"
            )

            val result = paginator.loadNext()
            assertTrue("Expected Loaded: $result", result is GitHubPageResult.Loaded)
            val loaded = result as GitHubPageResult.Loaded
            assertFalse(loaded.isOfflineCache)
            assertEquals(1, loaded.items.size)
            assertEquals("Alpha Cached", loaded.items.first().title)
            assertTrue(loaded.cachedAtMillis != null && loaded.cachedAtMillis!! > oldTime)

            val updatedCache = cache.get("user_alpha", "/user/repos")
            assertEquals("\"etag-v2\"", updatedCache?.etag)
            assertTrue(updatedCache != null && updatedCache.createdAtMillis > oldTime)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun notModified304WithoutCacheTriggersFreshRequest() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var callCount = 0
        server.createContext("/") { exchange ->
            callCount++
            if (callCount == 1) {
                exchange.sendResponseHeaders(304, -1)
            } else {
                val bytes = "2:Fresh Beta".toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
        server.start()
        try {
            val cache = InMemoryCacheStore()
            val client = GitHubHttpClient(
                baseUrl = URI.create("http://127.0.0.1:${server.address.port}/"),
                connectTimeoutMillis = 1_000,
                readTimeoutMillis = 1_000
            )
            val paginator = GitHubPaginator(
                client = client,
                firstPath = "/user/repos",
                accessToken = "token_abc",
                decode = ::decodeItems,
                itemKey = TestItem::id,
                cacheStore = cache,
                accountLogin = "user_alpha"
            )

            val result = paginator.loadNext()
            assertTrue("Expected Loaded: $result", result is GitHubPageResult.Loaded)
            val loaded = result as GitHubPageResult.Loaded
            assertEquals(1, loaded.items.size)
            assertEquals("Fresh Beta", loaded.items.first().title)
            assertEquals(2, callCount)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun ok200UpdatesItemsAndEtag() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val bytes = "3:Gamma Updated".toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("ETag", "\"etag-v3\"")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val cache = InMemoryCacheStore()
            cache.put("user_alpha", "/user/repos", "1:Old Alpha", etag = "\"etag-v1\"")
            val client = GitHubHttpClient(
                baseUrl = URI.create("http://127.0.0.1:${server.address.port}/"),
                connectTimeoutMillis = 1_000,
                readTimeoutMillis = 1_000
            )
            val paginator = GitHubPaginator(
                client = client,
                firstPath = "/user/repos",
                accessToken = "token_abc",
                decode = ::decodeItems,
                itemKey = TestItem::id,
                cacheStore = cache,
                accountLogin = "user_alpha"
            )

            val result = paginator.loadNext()
            assertTrue(result is GitHubPageResult.Loaded)
            val loaded = result as GitHubPageResult.Loaded
            assertEquals(1, loaded.items.size)
            assertEquals("Gamma Updated", loaded.items.first().title)

            val cached = cache.get("user_alpha", "/user/repos")
            assertEquals("\"etag-v3\"", cached?.etag)
            assertEquals("3:Gamma Updated", cached?.payload)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun networkFailureDuringNextPagePreservesExistingItems() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/page1") { exchange ->
            val bytes = "1:Item1".toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Link", "<http://127.0.0.1:${server.address.port}/page2>; rel=\"next\"")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = GitHubHttpClient(
                baseUrl = URI.create("http://127.0.0.1:${server.address.port}/"),
                connectTimeoutMillis = 500,
                readTimeoutMillis = 500
            )
            val paginator = GitHubPaginator(
                client = client,
                firstPath = "/page1",
                accessToken = "token_abc",
                decode = ::decodeItems,
                itemKey = TestItem::id
            )

            val page1 = paginator.loadNext()
            assertTrue("Expected Loaded but was: $page1", page1 is GitHubPageResult.Loaded)
            assertEquals(1, (page1 as GitHubPageResult.Loaded).items.size)

            // Stop server to simulate network failure on page 2
            server.stop(0)

            val page2 = paginator.loadNext()
            assertTrue("Expected Failed but was: $page2", page2 is GitHubPageResult.Failed)
            val failed = page2 as GitHubPageResult.Failed
            assertEquals(1, failed.items.size)
            assertEquals("Item1", failed.items.first().title)
        } finally {
            server.stop(0)
        }
    }
}
