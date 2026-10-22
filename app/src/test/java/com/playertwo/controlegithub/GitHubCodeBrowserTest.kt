package com.playertwo.controlegithub

import java.net.InetSocketAddress
import java.net.URI
import java.util.Base64
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubCodeBrowserTest {
    @Test fun contentsPathUsesRefAndDoesNotGuessBranch() {
        assertEquals(
            "/repos/acme/app/contents?ref=main",
            githubContentsPath("acme", "app", "", "main")
        )
        assertEquals(
            "/repos/acme/app/contents/src/App.kt?ref=release%2F1",
            githubContentsPath("acme", "app", "src/App.kt", "release/1")
        )
        assertEquals("/repos/acme/app/tags?per_page=100", githubRefListPath("acme", "app", "tags"))
    }

    @Test fun directorySortsFoldersFirstAndFileDecodesText() {
        val listing = parseCodeListing(
            """[{"name":"b.kt","path":"b.kt","type":"file","size":3},{"name":"src","path":"src","type":"dir","size":0}]"""
        )
        val directory = listing as CodeListing.Directory
        assertEquals(listOf("src", "b.kt"), directory.entries.map { it.name })
        assertEquals(CodeEntryKind.DIR, directory.entries.first().kind)

        val encoded = Base64.getEncoder().encodeToString("fun main()".toByteArray())
        val file = parseCodeListing(
            """{"name":"App.kt","path":"App.kt","type":"file","size":11,"encoding":"base64","content":"$encoded","html_url":"https://github.com/acme/app/blob/main/App.kt"}"""
        ) as CodeListing.File
        val text = file.body as CodeFileBody.Text
        assertEquals("fun main()", text.text)
        assertEquals(CodeLanguage.KOTLIN, text.language)
        assertEquals(listOf("main", "v1"), parseRefNames("""[{"name":"main"},{"name":"v1"},{"name":" "}]"""))
    }

    @Test fun oversizedOrBinaryFileDoesNotBecomeBrokenText() {
        val large = parseCodeListing(
            """{"name":"big.bin","path":"big.bin","type":"file","size":1048577,"encoding":"none","content":"","html_url":"https://github.com/acme/app/blob/main/big.bin"}"""
        ) as CodeListing.File
        assertTrue(large.body is CodeFileBody.Unavailable)
        assertEquals("https://github.com/acme/app/blob/main/big.bin", (large.body as CodeFileBody.Unavailable).htmlUrl)

        val binary = Base64.getEncoder().encodeToString(byteArrayOf(0x00, 0x01))
        val broken = parseCodeListing(
            """{"name":"a.dat","path":"a.dat","type":"file","size":2,"encoding":"base64","content":"$binary"}"""
        ) as CodeListing.File
        assertTrue(broken.body is CodeFileBody.Unavailable)
        assertNull((broken.body as CodeFileBody.Unavailable).htmlUrl)
    }

    @Test fun kotlinHighlightMarksKeywordAndString() {
        val line = "fun main() { \"oi\" }"
        val tokens = highlightCode(line, CodeLanguage.KOTLIN)
        assertTrue(tokens.any { it.kind == CodeTokenKind.KEYWORD && line.substring(it.start, it.end) == "fun" })
        assertTrue(tokens.any { it.kind == CodeTokenKind.STRING && line.substring(it.start, it.end) == "\"oi\"" })
    }

    @Test fun lookupReadsContentsForTheSelectedRef() {
        codeServer(200, """[{"name":"src","path":"src","type":"dir"}]""").use { server ->
            val result = loadCodeListing(server.client(), "token", "acme", "app", "", "develop")
            val directory = (result as CodeLoad.Found).listing as CodeListing.Directory
            assertEquals("src", directory.entries.single().name)
            assertTrue(server.path.startsWith("/repos/acme/app/contents?ref=develop"))
        }
    }

    private fun codeServer(status: Int, body: String) = object : AutoCloseable {
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
