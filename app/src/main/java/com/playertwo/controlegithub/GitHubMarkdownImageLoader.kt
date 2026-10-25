package com.playertwo.controlegithub

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

internal const val MAX_MARKDOWN_IMAGE_BYTES = 5 * 1024 * 1024

internal sealed interface MarkdownImageLoadResult {
    data class Loaded(
        val bytes: ByteArray,
        val contentType: String?
    ) : MarkdownImageLoadResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Loaded) return false
            return bytes.contentEquals(other.bytes) && contentType == other.contentType
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + (contentType?.hashCode() ?: 0)
            return result
        }
    }

    data class Failed(val message: String) : MarkdownImageLoadResult
}

internal class GitHubMarkdownImageLoader(
    private val openConnection: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    }
) {
    fun load(url: String): MarkdownImageLoadResult {
        val trimmed = url.trim()
        if (trimmed.isEmpty() || trimmed.length > 8_192 || trimmed.any(Char::isISOControl)) {
            return MarkdownImageLoadResult.Failed("Apenas imagens com endereço HTTPS seguro podem ser carregadas.")
        }

        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            null
        }
        if (
            uri == null ||
            !uri.isAbsolute ||
            uri.isOpaque ||
            !uri.scheme.equals("https", ignoreCase = true) ||
            uri.rawUserInfo != null ||
            uri.port !in setOf(-1, 443) ||
            uri.host.isNullOrBlank()
        ) {
            return MarkdownImageLoadResult.Failed("Apenas imagens com endereço HTTPS seguro podem ser carregadas.")
        }

        val targetUrl = try {
            URL(uri.toASCIIString())
        } catch (_: Exception) {
            return MarkdownImageLoadResult.Failed("Apenas imagens com endereço HTTPS seguro podem ser carregadas.")
        }

        val connection = try {
            openConnection(targetUrl)
        } catch (_: Exception) {
            return MarkdownImageLoadResult.Failed("Não foi possível conectar para carregar a imagem.")
        }

        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "image/*")

            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                return MarkdownImageLoadResult.Failed("Não foi possível carregar a imagem (HTTP $status).")
            }

            if (connection.contentLengthLong > MAX_MARKDOWN_IMAGE_BYTES) {
                return MarkdownImageLoadResult.Failed("A imagem excede o limite de 5 MB para visualização no app.")
            }

            val bytes = connection.inputStream.use { input ->
                val buffer = ByteArray(8 * 1024)
                val output = ByteArrayOutputStream()
                var totalBytes = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    totalBytes += read
                    if (totalBytes > MAX_MARKDOWN_IMAGE_BYTES) {
                        return MarkdownImageLoadResult.Failed("A imagem excede o limite de 5 MB para visualização no app.")
                    }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }

            if (bytes.isEmpty()) {
                return MarkdownImageLoadResult.Failed("A imagem recebida está vazia.")
            }

            MarkdownImageLoadResult.Loaded(
                bytes = bytes,
                contentType = connection.contentType
            )
        } catch (_: Exception) {
            MarkdownImageLoadResult.Failed("Não foi possível carregar a imagem. Verifique sua conexão.")
        } finally {
            connection.disconnect()
        }
    }
}

internal fun decodeMarkdownImageBitmap(bytes: ByteArray): Bitmap? =
    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
