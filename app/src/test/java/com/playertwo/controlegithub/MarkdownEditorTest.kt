package com.playertwo.controlegithub

import java.net.URI
import java.net.InetSocketAddress
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownEditorTest {
    @Test fun boldWrapsSelectionAndInsertsMarkersWhenEmpty() {
        val wrapped = applyMarkdownShortcut("abc", 1, 2, MarkdownShortcut.BOLD)
        assertEquals("a**b**c", wrapped.text)
        assertEquals(3..3, wrapped.selection)

        val inserted = applyMarkdownShortcut("a", 1, 1, MarkdownShortcut.BOLD)
        assertEquals("a****", inserted.text)
        assertEquals(3..3, inserted.selection)
    }

    @Test fun listAndQuotePrefixTheCurrentLineOnce() {
        val listed = applyMarkdownShortcut("um\ndois", 3, 3, MarkdownShortcut.LIST)
        assertEquals("um\n- dois", listed.text)
        val again = applyMarkdownShortcut(listed.text, 5, 5, MarkdownShortcut.LIST)
        assertEquals("um\n- dois", again.text)

        val quoted = applyMarkdownShortcut("fala", 0, 4, MarkdownShortcut.QUOTE)
        assertEquals("> fala", quoted.text)
        assertEquals(
            "- um\n- dois\n- tres",
            applyMarkdownShortcut("um\ndois\ntres", 0, 12, MarkdownShortcut.LIST).text
        )
        assertEquals(
            "- um\n- dois",
            applyMarkdownShortcut("- um\ndois", 0, 9, MarkdownShortcut.LIST).text
        )
        assertEquals(
            "- \nhello",
            applyMarkdownShortcut("\nhello", 0, 0, MarkdownShortcut.LIST).text
        )
    }

    @Test fun mentionQueryStopsAtWhitespaceAndFiltersRealLogins() {
        assertEquals("an", mentionQuery("oi @an", 6))
        assertNull(mentionQuery("email@ana", 9))
        assertNull(mentionQuery("oi @ana ", 8))
        assertEquals(listOf("Ana"), filterMentionLogins(listOf("Ana", "bob", ""), "an"))
        val inserted = insertMention("oi @an", 6, "Ana")
        assertEquals("oi @Ana ", inserted.text)
        assertEquals(8..8, inserted.selection)
    }

    @Test fun collaboratorFailureKeepsOnlyLoadedParticipants() {
        val json = """[{"login":"ada"},{"login":"ada"},{"login":" "},{"name":"x"}]"""
        assertEquals(listOf("ada"), parseCollaboratorLogins(json))
        assertEquals(
            listOf("ada", "Grace"),
            mergeMentionLogins(listOf("ada"), listOf("Grace", "ada"))
        )
        assertEquals(listOf("grace"), mergeMentionLogins(null, listOf("grace")))
        assertEquals(
            "/repos/acme/app/collaborators?per_page=100",
            githubCollaboratorsPath("acme/app")
        )
    }

    @Test fun lookupUsesCollaboratorsAndDoesNotInventUsersOnForbidden() {
        mentionServer(200, """[{"login":"ada"}]""").use { server ->
            val found = lookupCollaborators(server.client(), "token", "acme/app")
            assertEquals(listOf("ada"), (found as CollaboratorLookup.Found).logins)
            assertTrue(server.path.startsWith("/repos/acme/app/collaborators"))
        }
        mentionServer(403, """{"message":"Forbidden"}""").use { server ->
            assertEquals(CollaboratorLookup.Unavailable, lookupCollaborators(server.client(), "token", "acme/app"))
        }
        mentionServer(401, """{"message":"Bad credentials"}""").use { server ->
            assertEquals(CollaboratorLookup.Unauthorized, lookupCollaborators(server.client(), "token", "acme/app"))
        }
    }

    @Test fun draftRoundTripIsScopedToAccountAndDeletedAfterSend() = runBlocking {
        val store = MemoryMarkdownDraftStore()
        val key = markdownDraftKey("Rafa", "acme/app", "issue-7-comment")
        store.save(key, "rascunho")
        assertEquals("rascunho", store.load(key))
        assertNull(store.load(markdownDraftKey("outra", "acme/app", "issue-7-comment")))
        store.delete(key)
        assertNull(store.load(key))
        store.save(key, "fica")
        store.clearAccount("Rafa")
        assertNull(store.load(key))
    }

    @Test fun mentionCacheExpiresAndDoesNotServeAnotherRepository() {
        var now = 1_000L
        val cache = MentionLoginCache(now = { now })
        cache.put("conta|acme/app", listOf("ada"))
        assertEquals(listOf("ada"), cache.get("conta|acme/app"))
        assertNull(cache.get("conta|other/app"))
        now += MENTION_CACHE_MILLIS
        assertNull(cache.get("conta|acme/app"))
    }

    private fun mentionServer(status: Int, body: String) = object : AutoCloseable {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var path = ""
        init {
            server.createContext("/") { exchange ->
                path = exchange.requestURI.toString()
                val bytes = body.toByteArray()
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
        }
        fun client() = GitHubHttpClient(
            baseUrl = URI.create("http://127.0.0.1:${server.address.port}/"),
            connectTimeoutMillis = 1_000,
            readTimeoutMillis = 1_000
        )
        override fun close() = server.stop(0)
    }
}
