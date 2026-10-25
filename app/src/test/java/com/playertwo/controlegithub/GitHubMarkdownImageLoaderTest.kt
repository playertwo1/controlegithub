package com.playertwo.controlegithub

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubMarkdownImageLoaderTest {
    @Test fun rejectsNonHttpsOrUnsafeUrlsWithoutOpeningConnection() {
        var connectionOpened = false
        val loader = GitHubMarkdownImageLoader {
            connectionOpened = true
            error("Connection must not be opened for unsafe URL")
        }

        val unsafeUrls = listOf(
            "http://example.test/logo.png",
            "javascript:alert(1)",
            "file:///sdcard/logo.png",
            "../logo.png",
            "https://user:pass@example.test/logo.png",
            "https://example.test:8443/logo.png",
            ""
        )

        for (unsafe in unsafeUrls) {
            val result = loader.load(unsafe)
            assertTrue("Expected failure for $unsafe", result is MarkdownImageLoadResult.Failed)
            assertEquals(
                "Apenas imagens com endereço HTTPS seguro podem ser carregadas.",
                (result as MarkdownImageLoadResult.Failed).message
            )
        }
        assertFalse(connectionOpened)
    }

    @Test fun loadsHttpsImageWithoutSendingAuthorizationHeader() {
        val samplePng = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        var capturedConnection: FakeHttpURLConnection? = null
        val loader = GitHubMarkdownImageLoader { url ->
            FakeHttpURLConnection(
                url = url,
                status = 200,
                declaredLength = samplePng.size.toLong(),
                responseContentType = "image/png",
                streamBytes = samplePng
            ).also { capturedConnection = it }
        }

        val result = loader.load("https://example.test/logo.png")

        assertTrue(result is MarkdownImageLoadResult.Loaded)
        val loaded = result as MarkdownImageLoadResult.Loaded
        assertArrayEquals(samplePng, loaded.bytes)
        assertEquals("image/png", loaded.contentType)

        val connection = requireNotNull(capturedConnection)
        assertEquals("GET", connection.requestMethod)
        assertFalse(connection.instanceFollowRedirects)
        assertEquals("image/*", connection.getRequestProperty("Accept"))
        assertNull(connection.getRequestProperty("Authorization"))
        assertTrue(connection.disconnected)
    }

    @Test fun rejectsImagesExceedingFiveMegabytesByContentLength() {
        var streamRead = false
        val loader = GitHubMarkdownImageLoader { url ->
            FakeHttpURLConnection(
                url = url,
                status = 200,
                declaredLength = MAX_MARKDOWN_IMAGE_BYTES + 1L,
                responseContentType = "image/png",
                streamProvider = {
                    streamRead = true
                    ByteArrayInputStream(byteArrayOf(1, 2, 3))
                }
            )
        }

        val result = loader.load("https://example.test/huge.png")

        assertEquals(
            MarkdownImageLoadResult.Failed("A imagem excede o limite de 5 MB para visualização no app."),
            result
        )
        assertFalse(streamRead)
    }

    @Test fun rejectsImagesExceedingFiveMegabytesDuringStreaming() {
        val oversizedBytes = ByteArray(MAX_MARKDOWN_IMAGE_BYTES + 64) { 1 }
        val loader = GitHubMarkdownImageLoader { url ->
            FakeHttpURLConnection(
                url = url,
                status = 200,
                declaredLength = -1L,
                responseContentType = "image/png",
                streamBytes = oversizedBytes
            )
        }

        val result = loader.load("https://example.test/stream-huge.png")

        assertEquals(
            MarkdownImageLoadResult.Failed("A imagem excede o limite de 5 MB para visualização no app."),
            result
        )
    }

    @Test fun reportsClearPortugueseErrorOnHttpErrorStatus() {
        val loader404 = GitHubMarkdownImageLoader { url ->
            FakeHttpURLConnection(
                url = url,
                status = 404,
                declaredLength = 0L,
                responseContentType = "text/plain",
                streamBytes = ByteArray(0)
            )
        }
        val loader500 = GitHubMarkdownImageLoader { url ->
            FakeHttpURLConnection(
                url = url,
                status = 500,
                declaredLength = 0L,
                responseContentType = "text/plain",
                streamBytes = ByteArray(0)
            )
        }

        assertEquals(
            MarkdownImageLoadResult.Failed("Não foi possível carregar a imagem (HTTP 404)."),
            loader404.load("https://example.test/missing.png")
        )
        assertEquals(
            MarkdownImageLoadResult.Failed("Não foi possível carregar a imagem (HTTP 500)."),
            loader500.load("https://example.test/error.png")
        )
    }
}

private class FakeHttpURLConnection(
    url: URL,
    private val status: Int,
    private val declaredLength: Long,
    private val responseContentType: String?,
    streamBytes: ByteArray = ByteArray(0),
    private val streamProvider: () -> InputStream = { ByteArrayInputStream(streamBytes) }
) : HttpURLConnection(url) {
    private val headers = linkedMapOf<String, String>()
    var disconnected = false
        private set

    override fun connect() = Unit

    override fun disconnect() {
        disconnected = true
    }

    override fun usingProxy(): Boolean = false

    override fun setRequestProperty(key: String?, value: String?) {
        if (key != null && value != null) {
            headers[key] = value
        }
    }

    override fun getRequestProperty(key: String?): String? = key?.let(headers::get)

    override fun getResponseCode(): Int = status

    override fun getContentLengthLong(): Long = declaredLength

    override fun getContentType(): String? = responseContentType

    override fun getInputStream(): InputStream = streamProvider()
}
